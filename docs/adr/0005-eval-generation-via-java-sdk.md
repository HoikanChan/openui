# Eval 生成链路改走 Java Generation SDK（Eval Generation CLI）

react-ui-dsl 的 eval 原本在 Node 内用 TS `dslLibrary.prompt()` 拼 prompt、`openai` npm 包直连模型生成 DSL 快照——测量的是一条生产上并不存在的链路。我们决定生成侧全量切换到 Java Generation SDK：新增独立 Java 工具 **Eval Generation CLI**（`packages/genui-eval-cli`，fat jar），内部走 `GenUiGenerator.withTransport()` 全路径（SDK 拼 prompt、构造请求体、解析响应、`OpenuiCodeExtractor` 提取），配自定义的 OpenAI 兼容 `LlmTransport`。从此 eval 测量的就是生产同款生成链路（LLM_MIGRATION 后内部服务的目标形态）。

## 关键取舍

- **全量替换而非双引擎**：TS 生成路径与 prompt `strictness`（strict 模式）一并删除；strict 在 Java SDK 中不存在，实际也无人使用。放弃了 TS/Java prompt 的 A/B 对照能力。
- **CLI 是无状态计算器**：用例清单进（jobs JSON）、DSL 逐行出（JSONL）；快照文件、run 清单、prompt 存档全部仍由 Node 侧簿记，eval 目录结构知识不泄漏进 Java。
- **base contract 显式注入**：eval 不用 SDK 内置打包的 `base-contract.json`（可能过期），每次从当前 `dslLibrary` 导出并以 `--base-contract` 传入（需给 `GenUiGenerator` 加接收自定义 `GenerationSdk` 的通用重载）。保证评的永远是仓库当前的组件库。
- **接受生产语义带来的行为变化**：user message 追加 ` /no_think`、输出经 markdown 围栏提取、dataModel 带固定描述 "Response data"。因此新旧 run 分数不可比，run manifest 以 `generator` 字段标记链路，历史 run 不迁移。
- **被否决的替代方案**：经 GenUI Service HTTP 调用（引入服务生命周期管理，且"经服务"而非"直接用 SDK"）；常驻 JVM stdio 协议（复杂度不值）；仅用 SDK `assemblePrompt` + 自发 HTTP（链路覆盖少一截）。

## 后果

- `pnpm eval regen` 及 vitest 快照缺失补生成均要求本机 JDK + Maven（jar 缺失时自动 `mvn package -DskipTests` 构建）。
- 裁判侧（judge）不在范围内，继续用 `openai` npm 包；该依赖保留。
- 切换后需全量 regen 快照；`snapshots/`、`benchmark-snapshots/` 为 gitignored，覆盖前必须先备份。
