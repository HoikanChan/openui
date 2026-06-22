## Why

目前 `genui-java-sdk`（`genui-core`）只做到 `GenerationSdk.assemblePrompt()`，产出 prompt 字符串就停止；真正"调大模型 → 拿结果 → 抽取 openui-lang"的能力散落在内部业务代码 `interal-code/`（`LLMService`、`GenUIServiceDelegateImpl`、`ChatCompletionsRsp`）里，与华为内部基础设施（`RestfulFactory`、Jedis、Spring、OssLog）深度耦合，无法复用、无法测试、各接入方各写一遍。需要把与提供商无关的"大模型请求能力"沉到 SDK，宿主只提供传输实现。

## What Changes

- SDK 新增 chat/completions 协议层：请求体构造（model 默认值、`stream`、`temperature=0`、`enable_thinking=false`、`response_format=json_object`、`/no_think` 后缀、messages），以及响应解析（`choices[0].message.content`）。
- SDK 新增 SSE 流式拆帧能力：解析 `data: ...\n\n` 事件帧、抽取 `choices[0].delta.content`，以回调形式逐段输出并累积全文。
- SDK 新增 openui-lang 代码抽取能力（markdown code block / `root = Stack(` 启发式抽取），从内部 `CodeExtractor`/`extractOpenuiCode` 迁移并内联。
- SDK 定义传输端口 `LlmTransport`（`post(body)` / `postStream(body)`）**并内置默认实现 `RestfulLlmTransport`**：复刻内部 `LLMService`，用 `RestfulFactory` 直接发请求（含 `SUPPORT_STREAM_CONTENT_FOR_SDK` 流式头）。宿主仍可替换为自定义实现。
- **SDK 引入 BSP restclient 依赖**（`RestfulFactory` 所在 artifact）到 `pom.xml`，使 SDK 可自行发 HTTP。
- **请求地址（endpoint path）与 model 可配置**：新增 `GenUiLlmConfig`（endpoint、默认 model、可选 header/超时），由调用方注入，不再硬编码内部 endpoint 与 `qwen3.6-27b`。
- SDK 新增编排门面 `GenUiGenerator`：内部直接调 `GenerationSdk.assemblePrompt` 产出 prompt（prompt 构建收敛到 SDK），再经 `LlmTransport` 发请求、解析、抽取，提供同步 `generate` 与流式 `generateStream` 两条路径；**流式以增量回调输出并提供 SSE 帧封装 helper，便于外层直接转写为 SSE 响应**。
- 内部 `GenUIServiceDelegateImpl` 瘦身为：算 sha256 → 查/写 Redis 缓存 → 调 `GenUiGenerator`；不再自行构造请求体、解析响应、拆帧或抽取（迁移指引随附，实际改动在内部仓库执行）。
- **既有 core 分层重组**：把现有平铺的 17 个类重组为 `core/contract/` 与 `core/prompt/` 子包（`GenerationSdk`/`Json` 留根），并把 `generationId` 术语收敛为 `extensionId`；同步更新既有测试与 `examples/genui-service` 的 import。重组保证零行为变化（golden 字节不变）。

## Capabilities

### New Capabilities
- `genui-sdk-llm-invocation`: SDK 端大模型调用能力 —— `LlmTransport` 传输端口及内置 `RestfulLlmTransport`（RestfulFactory）默认实现、可配置 `GenUiLlmConfig`（endpoint + model）、chat/completions 请求/响应模型、SSE 流式拆帧、openui-lang 代码抽取，以及 `GenUiGenerator` 编排门面（assemblePrompt → 发请求 → 解析/抽取，含 sync 与 stream 两条路径，流式输出便于转写为 SSE）。

### Modified Capabilities
<!-- 无既有 capability 的 requirement 变更。内部 GenUIServiceDelegateImpl 属公司内部仓库代码，不在本仓 openspec 规格覆盖范围；其瘦身仅作迁移指引随附。 -->

## Impact

- **新增代码**：`packages/genui-java-sdk` 新增 `com.huawei.cloudsop.genui.core.llm` 子包（`LlmTransport`、`RestfulLlmTransport`、`GenUiLlmConfig`、`UiGenerationRequest`、`ChatCompletionRequest`、`ChatCompletionResponse`、`SseDeltaParser`、`SseFrames` helper、`OpenuiCodeExtractor`、`GenUiGenerator`）及单元测试。
- **入参对齐 `UIRequestDetail`**：`GenUiGenerator.generate(UiGenerationRequest)` 入参对齐 SYSTEM-DESIGN 第 7 节生成相关字段；`extensionId` 映射为 SDK `extensionId` 选择已注册扩展，`response` 作为 DataModel 主数据源。直出/缓存分支（templateId/renderPiu/iframeUrl/scenario/source）与数据预处理留服务层。
- **依赖**：复用现有 `fastjson2`；**新增 BSP restclient 依赖（`RestfulFactory` 所在 artifact）到 `pom.xml`**。坐标待补：`groupId:artifactId:version`（先占位 TODO，由内部确认后填）。仍不引入 Spring/Jedis。
- **API**：SDK 对外新增 `GenUiGenerator`、`LlmTransport`、`GenUiLlmConfig` 公共类型；现有 `GenerationSdk.assemblePrompt` 不变。
- **下游接入**：内部 `interal-code/`（`LLMService`/`GenUIServiceDelegateImpl`/`ChatCompletionsRsp`）瘦身 —— 直接复用 SDK 的 `GenUiGenerator` + `RestfulLlmTransport`，缓存留在 delegate；自有协议逻辑删除。
