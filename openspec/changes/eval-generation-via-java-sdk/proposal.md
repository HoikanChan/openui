# Proposal: eval 生成链路切换到 Java Generation SDK

## Why

react-ui-dsl 的 eval 目前在 Node 内用 TS `dslLibrary.prompt()` 拼 prompt、`openai` npm 包直连模型生成 DSL 快照——测量的是一条生产上并不存在的链路。genui-java-sdk 已具备完整的生成能力（prompt 拼装、请求构造、响应解析、代码提取，且 prompt 与 lang-core 字节对齐），内部服务迁移后走的就是这条链路。eval 应当测量生产同款链路，同时守护 SDK 生成质量。

## What Changes

- 新增 Java 命令行工具 **Eval Generation CLI**（`packages/genui-eval-cli`，根 pom 新 module，maven-shade fat jar）：包装 `GenUiGenerator.withTransport()` 全路径，配自实现的 OpenAI 兼容 `LlmTransport`（JDK HttpClient，支持 `LLM_BASE_URL` / Bearer key / HTTPS 代理）。CLI 是无状态计算器：`--base-contract` + `--jobs`（用例清单 JSON）进，JSONL 逐行出；另有 `--print-prompt` 子命令输出规范 system prompt 供存档。
- genui-java-sdk 增加通用重载：`GenUiGenerator` 支持注入自定义 `GenerationSdk`（携带外部 base contract），eval 每次从当前 `dslLibrary` 进程内导出 base contract 显式传入，不依赖 SDK 内置打包契约。
- **BREAKING** eval 生成侧全量替换：删除 `llm.ts` 中 `generateDsl()`（TS 拼 prompt + openai 直连）；`regen` 阶段与 vitest 快照缺失兜底均改为调用 Eval Generation CLI 子进程。
- **BREAKING** 废除 prompt `strictness`（standard/strict）概念：删除 eval CLI `--strictness` 参数与 run manifest `strictness` 字段（读旧 run 容忍该字段存在）；strict 模式在 Java SDK 中不存在且实际无人使用。
- run manifest 新增 `generator` 字段，标记生成链路与 base contract 版本；新旧 run 分数不可比，不做迁移。
- 接受生产语义带来的行为变化：user message 追加 ` /no_think`、输出经 `OpenuiCodeExtractor` 围栏提取、dataModel 带固定描述 "Response data"。切换后需全量 regen 快照。
- `openai` / `https-proxy-agent` npm 依赖保留（judge 裁判侧继续使用，不在本次范围）。

## Capabilities

### New Capabilities

- `eval-generation-cli`: Eval Generation CLI 的行为契约——输入输出协议（jobs JSON 进、JSONL 出）、base contract 显式注入、`--print-prompt`、LLM 连接配置经环境变量、jar 缺失时的自动构建、并发与错误语义。

### Modified Capabilities

- `genui-eval-loop`: regen 阶段的 LLM 生成改为经由 Eval Generation CLI（Java Generation SDK 链路）完成；`--strictness` 参数移除；run manifest 增加 `generator` 字段。并发（`EVAL_REGEN_CONCURRENCY`）与 staging 原子写入要求保持不变。
- `eval-system-prompt-recording`: 规范 system prompt 存档改为取自 Eval Generation CLI（Java SDK 实际拼装结果），不再由 TS prompt 路径生成；strictness 维度从要求中移除。

## Impact

- **新增代码**：`packages/genui-eval-cli`（Java module）；根 `pom.xml` 增加 module 声明。
- **修改代码**：`packages/genui-java-sdk`（`GenUiGenerator` 重载）；`packages/react-ui-dsl` 的 `llm.ts`、`eval/regen.ts`、`eval/prompt-artifact.ts`、`eval-loop.ts`、`eval/types.ts`、`llm.test.ts`、`regen.test.ts`、`prompt-artifact.test.ts`。
- **硬依赖**：`pnpm eval regen` 及 vitest 快照缺失兜底从此要求本机 JDK + Maven。
- **数据影响**：切换后首次全量 regen 覆盖 `snapshots/`、`benchmark-snapshots/`（gitignored，覆盖前必须备份）；历史 run 与新 run 分数不可比。
- **文档**：已随设计落盘——`docs/adr/0005-eval-generation-via-java-sdk.md`、`packages/react-ui-dsl/CONTEXT.md`（Eval Generation CLI 术语）、`CONTEXT-MAP.md`（工具边界）。
