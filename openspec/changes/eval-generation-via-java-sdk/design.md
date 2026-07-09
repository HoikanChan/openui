# Design: eval 生成链路切换到 Java Generation SDK

## Context

react-ui-dsl 的 eval 流水线分四个可恢复阶段（regen / render / screenshot / judge，见 `genui-eval-loop` spec）。其中 regen 阶段与 vitest 快照缺失兜底目前都走 `src/__tests__/e2e/llm.ts` 的 `generateDsl()`：TS 侧 `dslLibrary.prompt()` 拼 system prompt，`openai` npm 包直连 OpenAI 兼容端点。

genui-java-sdk 已具备完整生成链路：`GenerationSdk`（契约注册 + prompt 拼装，与 lang-core 字节对齐）、`GenUiGenerator`（请求体构造、响应解析、`OpenuiCodeExtractor` 提取）、`LlmTransport` 接口（默认实现 `RestfulLlmTransport` 面向华为 BSP 网关，本地不可用）。`GenUiGenerator.withTransport(config, transport)` 支持自定义 transport；`GenerationSdk.builder().baseContract(...)` 支持外部契约；`GenerationContractLoader.fromJson()` 为 public。缺口：`GenUiGenerator` 工厂无法同时注入自定义 `GenerationSdk` 与 transport。

现有脚本 `pnpm run generate:base-contract` 从 `dslLibrary` 导出 `{...dslLibrary.toSpec(), builtins: getBuiltinsManifest()}` 到 `generated/base-contract.json` 并同步 SDK resources 副本。

决策过程已由 grill 会话完成并记录于 `docs/adr/0005-eval-generation-via-java-sdk.md`；本文展开实施设计。

## Goals / Non-Goals

**Goals:**

- eval 生成侧测量生产同款链路：prompt 拼装、请求构造、响应提取全部由 Java Generation SDK 完成。
- 生成的契约永远新鲜：每次运行从当前仓库的 `dslLibrary` 导出 base contract 注入 SDK。
- eval 簿记逻辑（run 目录、断点续跑、staging 原子写入、并发上限）行为不变。
- prompt 存档反映 Java 实际拼装结果。

**Non-Goals:**

- 裁判侧（judge-runner）不动，继续用 `openai` npm 包（vision 多模态不是生成 SDK 的职责）。
- 不给 Java SDK 增加 strictness / strict 模式（生产无此概念）。
- 不做历史 run 数据迁移或跨引擎分数换算。
- 不改 GenUI Service（examples/genui-service）。

## Decisions

### D1 通道：薄 Java CLI 子进程（批量模式）

新包 `packages/genui-eval-cli`（根 pom 新 module，maven-shade fat jar）。Node 以子进程调用。

备选与否决理由：
- 经 GenUI Service HTTP —— 引入 Spring Boot 服务生命周期管理与端口问题，且是"经服务"而非"直接用 SDK"；
- 常驻 JVM + stdio 双向协议 —— 复杂度不值，JVM 冷启动（1~2s）相对 LLM 调用（每 case 数秒）不是瓶颈，批量模式已摊薄；
- 在 vitest 内直接 JNI/GraalVM —— 不考虑。

### D2 SDK 路径：`GenUiGenerator` 全路径 + 自定义 transport

CLI 内部零 prompt 逻辑，全部委托 SDK：

- 新增 SDK 通用重载 `GenUiGenerator.withTransport(GenerationSdk, GenUiLlmConfig, LlmTransport)`（现有工厂内部固定 `GenerationSdk.create()`）。
- CLI 实现 `OpenAiCompatibleLlmTransport`：JDK HttpClient，`LLM_BASE_URL` + `/chat/completions`，Bearer `LLM_API_KEY`，`HTTPS_PROXY` 代理，connect timeout 15s——照抄 GenUI Service `LlmClient` 的写法但实现 `LlmTransport` 接口。
- `GenUiLlmConfig`：`defaultModel` 取 `LLM_MODEL`（Node 侧显式传递，缺省 `deepseek-chat`，不依赖 SDK 默认 `qwen3.6-27b`）、`temperature(0)`、`jsonObjectResponse(false)`。

随生产语义接受的行为变化（快照不可比的根源）：` /no_think` 后缀、`OpenuiCodeExtractor` 围栏提取、`DataModelSpec("Response data", raw)` 固定描述。

### D3 I/O 契约：CLI 是无状态计算器

```
regen.ts (Node)                            genui-eval-cli (Java 子进程)
  ├─ 进程内导出 base contract 写入 run 目录
  ├─ 写 jobs.json: [{id, userInput, dataModel}]
  ├─ 启动: java -jar genui-eval-cli.jar generate
  │        --base-contract=<path> --jobs=<path>
  │        --concurrency=<EVAL_REGEN_CONCURRENCY>     → 读契约注入 GenerationSdk
  │                                                     有界线程池并发跑用例
  ├─ 逐行收 stdout JSONL ←──────────────────           {"id":…,"status":"ok","dsl":…}
  │  （实时进度）                                        {"id":…,"status":"error","error":…}
  └─ Node 写 staging → 原子替换快照 / run 清单 / 存档
```

- LLM 连接配置（`LLM_API_KEY` / `LLM_BASE_URL` / `LLM_MODEL` / `HTTPS_PROXY`）走环境变量，`.env` 约定不动。
- 快照文件、run manifest、失败记录全部仍由 Node 写——eval 目录结构知识不泄漏进 Java；`genui-eval-loop` 的 staging 原子替换与断点续跑逻辑一行不动。
- 并发控制移入 CLI（`--concurrency`，Node 从 `EVAL_REGEN_CONCURRENCY` 取值，默认 6），满足既有 spec 要求。
- stderr 留给日志；exit code 非零仅表示基础设施失败（参数错误、契约文件缺失），单 case LLM 失败以 `status:"error"` 行表达，批次继续。
- `--print-prompt` 子命令：以占位 dataModel（与现 `prompt-artifact.ts` 相同的 `__EVAL_DATA_MODEL_PLACEHOLDER__`）调 SDK `assemblePrompt`，stdout 输出拼装结果；`prompt-artifact.ts` 改为消费它。

### D4 契约新鲜度：进程内导出、显式注入

不用 SDK 内置打包的 `base-contract.json`（可能过期）。`regen.ts` 与 vitest 兜底路径都跑在 TS 进程内，`dslLibrary` 就在手边：直接进程内计算 `{...dslLibrary.toSpec(), builtins: getBuiltinsManifest()}` 写入 run 目录（复用 `base-contract.test.ts` 的拼法），不依赖 prebuild 产物、不拉脚本子进程。`generate:base-contract` 脚本与 SDK resources 副本的既有同步机制不动。

### D5 构建：jar 缺失自动构建，不做新旧检测

- jar 路径约定：`packages/genui-eval-cli/target/genui-eval-cli.jar`（shade finalName 固定，免版本号耦合）。
- Node 侧检查 jar 存在：缺失则 `mvn -q -pl packages/genui-eval-cli -am package -DskipTests`（`-am` 连带构建 genui-java-sdk；`-DskipTests` 规避本机 surefire fork OOM）。
- 不做 pom/src mtime 启发式——改 Java 代码需手动 `pnpm eval build-cli` 重建（新增 package.json script）。
- 硬依赖 JDK + Maven；检测不到时报错信息给出安装指引。

### D6 全量替换与退役清单

- `llm.ts`：删除 `generateDsl()`、`OpenAI` / `HttpsProxyAgent` import、strictness 逻辑；`loadOrGenerate` 保留（快照读取 + 缺失时经 CLI 兜底生成，无 `LLM_API_KEY` 时报错指向 `pnpm eval regen`）。
- eval CLI：`--strictness` 参数删除；`types.ts` run manifest 删 `strictness`、增 `generator`（值形如 `genui-eval-cli (<contractVersion>)`），读旧 manifest 容忍未知字段。
- `prompt-artifact.ts`：`generateCanonicalPrompt` 改为调 CLI `--print-prompt`；hash 逻辑不变。
- npm 依赖 `openai` / `https-proxy-agent` 保留（judge 使用）。

## Risks / Trade-offs

- [切换后新旧 run 分数不可比] → run manifest `generator` 字段标记链路；prompt hash 变化会被 delta-verifier 天然拦截；ADR 已记录。
- [首次全量 regen 覆盖 gitignored 快照目录] → 执行前必须备份 `snapshots/`、`benchmark-snapshots/`（git 救不回来）。
- [JDK/Maven 成为 eval 硬依赖] → 报错信息给出指引；跑 vitest 不触发兜底时仍完全离线，不受影响。
- [vitest 兜底拉起 JVM（可能触发 mvn 构建）导致测试变慢] → 仅在"快照缺失且设了 LLM_API_KEY"时发生，与现状（当场调 LLM）的时延同数量级；jar 已构建时开销仅为 JVM 冷启动。
- [`GenUiLlmConfig.endpoint` 默认值指向 BSP 网关路径] → 自定义 transport 自持完整 URL（`LLM_BASE_URL` + `/chat/completions`），忽略 config.endpoint，避免误用。
- [丢失 TS/Java prompt A/B 对照能力] → 接受；字节对齐由 genui-java-sdk 既有 prompt-golden 测试守护。

## Migration Plan

1. SDK 重载 + CLI 模块合入（不影响现有 eval 行为）。
2. Node 侧接线切换（regen / vitest 兜底 / prompt-artifact / manifest）。
3. 备份快照目录 → 全量 `pnpm eval regen` → 人工抽查 + judge 基线重建。
4. 回滚策略：切换在 Node 侧是集中接线点（`llm.ts` / `regen.ts`），git revert 即可回到 TS 路径；快照从备份恢复。

## Open Questions

无——设计决策已在 grill 会话中全部收口（见 ADR 0005）。
