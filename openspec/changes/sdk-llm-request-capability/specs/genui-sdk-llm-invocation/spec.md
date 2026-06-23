## ADDED Requirements

### Requirement: LLM 传输端口与内置默认实现

SDK SHALL 定义传输端口 `LlmTransport`，暴露 `post(requestBody)`（同步，返回响应原文）与 `postStream(requestBody)`（流式，返回 `InputStream`），两者 SHALL 抛受检 `LlmTransportException`（失败/非 200 状态统一包装为该异常）。SDK SHALL 内置默认实现 `RestfulLlmTransport`，用 BSP `RestfulFactory` 直接发请求（流式路径带 `SUPPORT_STREAM_CONTENT_FOR_SDK=true` 头），并把 BSP restclient 作为 `pom.xml` 依赖引入。请求体构造、响应解析、SSE 拆帧、代码抽取 SHALL 留在 SDK 而非传输端口内。宿主 SHALL 能注入自定义 `LlmTransport` 覆盖默认实现。

#### Scenario: 内置传输直接发请求
- **WHEN** 不注入自定义 transport，调用 `GenUiGenerator`
- **THEN** SDK 经 `RestfulLlmTransport` 用 `RestfulFactory` 向配置的 endpoint 发请求

#### Scenario: 宿主覆盖传输实现
- **WHEN** 宿主注入自定义 `LlmTransport`（如 fake 或其他客户端）
- **THEN** SDK 通过该实现完成请求，协议构造/解析/抽取逻辑不变

#### Scenario: 协议逻辑可脱离传输测试
- **WHEN** 用 fake `LlmTransport` 返回预置响应
- **THEN** 请求体构造、响应解析与代码抽取可在不触达 BSP 运行时的情况下被单元测试覆盖

### Requirement: endpoint 与 model 可配置

SDK SHALL 提供 `GenUiLlmConfig`，至少包含请求地址 `endpoint`（默认值可被覆盖）与默认 `model`（默认值可被覆盖），并可携带可选 header 与生成参数。`RestfulLlmTransport` 与 `ChatCompletionRequest` SHALL 从该配置取值，SHALL NOT 硬编码 endpoint 或 model 名。

#### Scenario: 覆盖 endpoint 与 model
- **WHEN** 以自定义 endpoint 与 model 构造 `GenUiLlmConfig` 并驱动生成
- **THEN** 请求发往该 endpoint，请求体 model 为该自定义值

#### Scenario: 使用默认配置
- **WHEN** 未指定 endpoint/model
- **THEN** 回退到内置默认 endpoint 与默认 model

### Requirement: chat/completions 请求体构造

SDK SHALL 提供 `ChatMessage`（`role`/`content`，含 `system()`/`user()` 工厂）与 `ChatCompletionRequest`。`ChatCompletionRequest.of(config, model, messages, stream)` SHALL 把消息列表构造为 OpenAI 兼容 chat/completions 请求体：model 为空时回退到 `config.defaultModel()`；`temperature`、`enable_thinking`、`response_format`（`jsonObjectResponse` 为真时取 `{"type":"json_object"}`）取自 `config`；按传入的 `messages` 列表与 `role` 原样序列化；`stream` 按调用路径取 `true`/`false`。请求体 SHALL 可序列化为 JSON 字符串供 `LlmTransport` 发送。门面 SHALL 以 `system`(装配产物) + `user`(userInput) 两条消息构造，并在 `user` 消息尾部追加 ` /no_think`。

#### Scenario: 构造同步请求体（system+user）
- **WHEN** 以 system(装配 prompt) + user(userInput) 消息、空 model 构造同步请求
- **THEN** 序列化结果含默认 model、`stream=false`、`temperature=0`、`response_format.type=json_object`；messages 含 `role=system` 与 `role=user` 两条，且 `user` 消息以 ` /no_think` 结尾

#### Scenario: 构造流式请求体
- **WHEN** 以流式路径构造请求
- **THEN** 序列化结果中 `stream=true`，其余字段与同步路径一致

#### Scenario: 生成参数取自 config
- **WHEN** `GenUiLlmConfig` 设置了自定义 `temperature` 或关闭 `jsonObjectResponse`
- **THEN** 请求体相应字段反映该配置，而非硬编码默认值

### Requirement: chat/completions 响应解析

SDK SHALL 提供 `ChatCompletionResponse`，解析同步响应并返回 `choices[0].message.content`。当 `choices` 为空或缺失时 SHALL 抛出明确异常而非返回 null。

#### Scenario: 解析成功取首条内容
- **WHEN** 响应含至少一个 choice
- **THEN** 返回 `choices[0].message.content` 文本

#### Scenario: 空 choices 抛异常
- **WHEN** 响应的 `choices` 为空或缺失
- **THEN** 抛出说明"choices 为空"的异常

### Requirement: SSE 流式拆帧

SDK SHALL 提供 `SseDeltaParser`，按 `data: ...\n\n` 事件边界拆分 SSE 流，对每帧抽取 `choices[0].delta.content`，以回调形式逐段输出非空内容并累积全文返回。无法解析的单帧 SHALL 跳过而不中断整个流。

#### Scenario: 逐段输出 delta 内容
- **WHEN** 输入包含多个 `data:` 事件帧的流
- **THEN** 每帧的 `delta.content` 依次经回调输出，最终返回拼接后的全文

#### Scenario: 跳过坏帧
- **WHEN** 流中某一帧 JSON 非法
- **THEN** 该帧被跳过，后续合法帧仍正常解析输出

### Requirement: openui-lang 代码抽取

SDK SHALL 提供 `OpenuiCodeExtractor`，从 LLM 原始输出中抽取 openui-lang 代码：优先匹配 ```` ```openui ```` / ```` ```openui-lang ```` / ```` ```text ```` 代码块，回退到含 `root = Stack(` 的任意代码块，最终回退到原文。空输入 SHALL 返回空字符串。

#### Scenario: 抽取 openui 代码块
- **WHEN** 原始输出含 ```` ```openui ... ``` ```` 代码块
- **THEN** 返回代码块内去除首尾空白的内容

#### Scenario: 回退到 Stack 启发式
- **WHEN** 原始输出无显式 openui 标记，但某代码块含 `root = Stack(`
- **THEN** 返回该代码块内容

#### Scenario: 空输入
- **WHEN** 原始输出为 null 或空串
- **THEN** 返回空字符串

### Requirement: 生成入参对齐 UIRequestDetail 并以 extensionId 选扩展

SDK SHALL 提供生成入参 `UiGenerationRequest`，对齐 `UIRequestDetail` 的生成相关字段（`extensionId`、`userInput`、`request`、`response`、`suggestion` 与 assemble 标志）。门面 SHALL 把 `extensionId` 映射为 `GenUIPromptRequest.extensionId` 选择已注册扩展，把 `response` 作为 `DataModelSpec` 主数据源，把 `userInput` 作为用户消息，把 `suggestion` 追加为 extraRules。`templateId`/`renderPiu`/`iframeUrl`/`scenario`/`source` SHALL NOT 进入 SDK（属服务层）。数据预处理/压缩 SHALL NOT 在 SDK 内进行。

#### Scenario: extensionId 选中已注册扩展
- **WHEN** `UiGenerationRequest.extensionId` 命中已 `register` 的扩展
- **THEN** 发给 LLM 的 system prompt 含该扩展的组件、工具、示例与规则

#### Scenario: response 作为数据模型
- **WHEN** 请求携带 `response` 数据
- **THEN** 该数据经 `assemblePrompt` 作为 `{data.*}` 主数据源进入 system prompt

#### Scenario: 未注册 extensionId 不在 SDK 内 fail-loud
- **WHEN** `extensionId` 未注册
- **THEN** 门面按 SDK 语义经 `assemblePrompt` 回落 base contract，不在 SDK 层抛错（fail-loud 由服务层负责）

### Requirement: GenUiGenerator 编排门面

SDK SHALL 提供 `GenUiGenerator`，**自持** `GenerationSdk`、`LlmTransport` 与抽取器，在**同一实例**上提供扩展注册 `register(GenUIExtension)`（返回 `this`，委托内部 sdk）与生成 `generate(UiGenerationRequest)` / `generateStream(UiGenerationRequest, sink)`。`GenerationSdk` SHALL NOT 出现在默认创建 API 中（调用方不需手动构造或传入）。`generate` SHALL 依次执行 映射入参 → `assemblePrompt` → 构造请求体 → `post` → 响应解析 → 代码抽取，返回最终 openui-lang。`generateStream` SHALL 依次执行 映射入参 → `assemblePrompt` → 构造流式请求体 → `postStream` → SSE 拆帧（逐段回调）→ 代码抽取，返回累积全文经抽取后的最终代码。门面默认内部使用 `RestfulLlmTransport`，调用方零配置即用 `RestfulFactory`；`LlmTransport` 仅经可选高级入口 `withTransport` 注入。

#### Scenario: 同步生成端到端
- **WHEN** 以合法 `UiGenerationRequest` 调用 `generate`
- **THEN** 门面经 `assemblePrompt` 拼装 prompt、调 `LlmTransport.post`、解析响应并抽取，返回 openui-lang 文本

#### Scenario: 流式生成端到端
- **WHEN** 以合法请求调用 `generateStream` 并提供分段回调
- **THEN** 门面经 `postStream` 取流、用 `SseDeltaParser` 逐段回调输出，并返回抽取后的最终 openui-lang

#### Scenario: 默认传输零配置
- **WHEN** 用 `GenUiGenerator.create(...)` 且不注入传输
- **THEN** 门面内部用 `RestfulLlmTransport`（`RestfulFactory`）发请求，调用方不接触 `LlmTransport`

#### Scenario: 同一实例注册并生成
- **WHEN** 在同一个 `GenUiGenerator` 上先 `register(extension)` 再 `generate(req)`，且 `req.extensionId` 等于该 extension 的 extensionId
- **THEN** 无需手动构造或传入 `GenerationSdk`，生成的 system prompt 即含该已注册扩展

### Requirement: 流式输出便于转写为 SSE

`generateStream` 的增量回调 SHALL 输出纯文本 delta 内容（不含传输帧），使宿主可原样写入 `text/plain` chunked 响应。SDK SHALL 另行提供 `SseFrames` helper：`of(content)` 把一段内容封装为标准 `data: <content>\n\n` SSE 帧，`done()` 返回结束帧 `data: [DONE]\n\n`，便于宿主把回调内容直接写成 `text/event-stream` 响应。帧封装 SHALL NOT 与 delta 回调耦合（宿主自行选择是否封帧）。

#### Scenario: 回调输出纯文本 delta
- **WHEN** 流式生成过程中收到增量内容
- **THEN** 回调收到的是纯文本 delta，宿主可原样转发

#### Scenario: helper 封装 SSE 帧
- **WHEN** 宿主用 `SseFrames.of(content)` 封装一段内容
- **THEN** 得到以 `data: ` 开头、以 `\n\n` 结尾的标准 SSE 帧文本
