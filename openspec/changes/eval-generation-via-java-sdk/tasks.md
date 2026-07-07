## 1. SDK 重载（genui-java-sdk）

- [ ] 1.1 新增 `GenUiGenerator.withTransport(GenerationSdk, GenUiLlmConfig, LlmTransport)` 通用重载（含 characterization 变体或经既有 builder 复用），单测覆盖自定义 `GenerationSdk`（外部 base contract）生效
- [ ] 1.2 `mvn -q -pl packages/genui-java-sdk package -DskipTests` 编译通过；跑受影响的 SDK 单测（独立 java 方式，避开 surefire fork OOM）

## 2. Eval Generation CLI 新模块（packages/genui-eval-cli）

- [ ] 2.1 创建 maven module `packages/genui-eval-cli`：pom（依赖 genui-java-sdk、maven-shade fat jar、finalName 固定 `genui-eval-cli`）、根 `pom.xml` 注册 module
- [ ] 2.2 实现 `OpenAiCompatibleLlmTransport`：JDK HttpClient，URL = `LLM_BASE_URL` + `/chat/completions`，Bearer `LLM_API_KEY`，`HTTPS_PROXY` 代理，connect timeout 15s（照抄 genui-service `LlmClient` 写法，实现 `LlmTransport` 接口）
- [ ] 2.3 实现 `generate` 命令：读 `--base-contract`（`GenerationContractLoader.fromJson` → `GenerationSdk.builder().baseContract()`）与 `--jobs` 清单，`--concurrency`（缺省 6）有界线程池并发，每用例经 `GenUiGenerator.generate()`，stdout 逐行 JSONL（`{id,status,dsl|error}`）；单 case 失败继续、exit 0，基础设施失败 exit 非零 + stderr 报错
- [ ] 2.4 实现 `--print-prompt` 子命令：占位 dataModel（`__EVAL_DATA_MODEL_PLACEHOLDER__` 同现有 prompt-artifact）调 `assemblePrompt`，stdout 输出，不发 HTTP、不要求 `LLM_API_KEY`
- [ ] 2.5 CLI 单测：参数校验、jobs 解析、JSONL 输出格式、print-prompt 离线性（transport 打桩）；构建 fat jar 并冒烟 `java -jar … --print-prompt`

## 3. Node 侧接线（packages/react-ui-dsl）

- [ ] 3.1 新增 CLI 调用封装（如 `eval/generation-cli.ts`）：进程内导出 base contract（`{...dslLibrary.toSpec(), builtins: getBuiltinsManifest()}`）写入 run 工作区；jar 缺失时自动 `mvn -q -pl packages/genui-eval-cli -am package -DskipTests`，检测不到 JDK/Maven 报错给指引；spawn 子进程并逐行消费 JSONL
- [ ] 3.2 `regen.ts` 改走 CLI 封装：写 jobs.json、`EVAL_REGEN_CONCURRENCY` 透传 `--concurrency`、结果回写 staging（原子替换与断点续跑逻辑不动）
- [ ] 3.3 `llm.ts` 瘦身：删除 `generateDsl()`、`OpenAI`/`HttpsProxyAgent` import 与 strictness 逻辑；`loadOrGenerate` 快照缺失且有 `LLM_API_KEY` 时经 CLI 单用例兜底生成，无 key 报错指向 `pnpm eval regen`
- [ ] 3.4 `prompt-artifact.ts` 改为消费 CLI `--print-prompt` 输出（hash 逻辑不变）
- [ ] 3.5 eval-loop / types：删除 `--strictness` 参数与 manifest `strictness` 字段（读旧 run 容忍）；新增 `generator` 字段（`genui-eval-cli (<contractVersion>)`）
- [ ] 3.6 package.json 新增 `eval build-cli` 显式重建命令（或 eval 子命令）；确认 `openai`/`https-proxy-agent` 依赖保留（judge 使用）

## 4. 测试与验证

- [ ] 4.1 更新 `llm.test.ts`（删 generateDsl 用例，补快照缺失兜底/报错路径）、`regen.test.ts`、`prompt-artifact.test.ts`、`eval-loop` 相关断言（strictness 移除、generator 写入）
- [ ] 4.2 `pnpm --filter @cloudsop/openui-react-ui-dsl run test` 全绿；`pnpm run ci`（lint + format）通过
- [ ] 4.3 端到端冒烟：备份 `snapshots/`、`benchmark-snapshots/` 后，对小样本跑一次真实 regen（`.env` 已配 key），确认 DSL 生成、JSONL 流转、快照写入、`system-prompt.txt` 与 `generator` 字段均符合预期

## 5. 收尾

- [ ] 5.1 更新 `packages/react-ui-dsl/CLAUDE.md` 与 README 中 regen 说明（JDK/Maven 依赖、build-cli 命令）；确认 CONTEXT.md / CONTEXT-MAP / ADR 0005 与实现一致
- [ ] 5.2 全量 regen 与基线重建单独另行执行（不在本 change 内自动触发），在 PR 描述中注明操作步骤与备份要求
