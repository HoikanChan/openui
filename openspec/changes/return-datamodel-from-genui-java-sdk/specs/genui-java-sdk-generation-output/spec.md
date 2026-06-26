## ADDED Requirements

### Requirement: Synchronous generation returns render result
Java Generation SDK SHALL expose synchronous generation as a structured render result containing generated DSL, response data as `dataModel`, and optional `traceId`. The SDK SHALL populate `dsl` with extracted `openui-lang`, SHALL populate `dataModel` from `UiGenerationRequest.response`, and SHALL use an empty map when the request has no response data.

#### Scenario: 同步生成返回 DSL 与 dataModel
- **WHEN** 调用 SDK 同步生成接口且请求包含 `response` 数据
- **THEN** 返回结果包含提取后的 `dsl`
- **AND** 返回结果的 `dataModel` 等于请求中的 `response`

#### Scenario: 同步生成空数据返回空 map
- **WHEN** 调用 SDK 同步生成接口且请求未包含 `response` 数据
- **THEN** 返回结果的 `dataModel` 是空 map

#### Scenario: 同步生成透传 traceId
- **WHEN** 调用 SDK 同步生成接口且请求包含 `traceId`
- **THEN** 返回结果包含同一个 `traceId`

### Requirement: Streaming generation emits render stream envelopes
Java Generation SDK SHALL expose streaming generation as `RenderStreamEnvelope` callbacks. The first callback SHALL always be `type=dataModel` with `seq=0` and content equal to `UiGenerationRequest.response` or an empty map. Model output chunks SHALL be emitted as `type=dsl` envelopes with string content and increasing `seq`. A terminal `type=done` envelope SHALL be emitted when streaming ends.

#### Scenario: 流式生成首帧返回 dataModel
- **WHEN** 调用 SDK 流式生成接口
- **THEN** 第一个回调 envelope 的 `type` 是 `dataModel`
- **AND** `seq` 是 `0`
- **AND** `content` 是请求中的 `response` 或空 map

#### Scenario: 流式 DSL chunk 使用 dsl 类型
- **WHEN** LLM 返回一个或多个文本 delta
- **THEN** SDK 为每个 delta 发出 `type=dsl` envelope
- **AND** 每个 `dsl` envelope 的 `content` 是该 delta 文本
- **AND** `seq` 从 `1` 开始递增

#### Scenario: 流式完成帧不携带 content
- **WHEN** LLM 流正常结束
- **THEN** SDK 发出 `type=done` envelope
- **AND** 该 envelope 的 `content` 为 null，序列化输出时不包含 `content` 字段

### Requirement: Streaming failures emit error then done
Java Generation SDK SHALL represent mid-stream failures as render stream envelopes instead of throwing to the streaming caller after the stream has started. When a stream fails after the initial `dataModel` frame, the SDK SHALL emit `type=error` with code, message, and retryable flag, then SHALL emit `type=done`. The returned `GenUiGenerationResult` SHALL contain the accumulated extracted DSL and the same `dataModel`.

#### Scenario: 流中失败发出 error 和 done
- **WHEN** LLM 流在首帧之后发生读取或传输错误
- **THEN** SDK 发出 `type=error` envelope
- **AND** 随后发出 `type=done` envelope
- **AND** 流式生成方法不向调用方抛出该流中异常

#### Scenario: 流中失败返回已累计结果
- **WHEN** LLM 流在产生部分 DSL 后失败
- **THEN** SDK 返回结果中的 `dsl` 来自已累计内容的提取结果
- **AND** 返回结果中的 `dataModel` 仍等于请求 `response` 或空 map

### Requirement: TraceId is optional and caller-owned
Java Generation SDK SHALL accept optional `traceId` on `UiGenerationRequest` and SHALL copy it to synchronous results and all stream envelopes. The SDK SHALL NOT generate a traceId when the request omits it.

#### Scenario: 流式 envelope 透传 traceId
- **WHEN** 流式生成请求包含 `traceId`
- **THEN** 每个 `RenderStreamEnvelope` 都包含同一个 `traceId`

#### Scenario: SDK 不生成 traceId
- **WHEN** 生成请求未包含 `traceId`
- **THEN** 同步结果和流式 envelope 均不包含由 SDK 新生成的 traceId
