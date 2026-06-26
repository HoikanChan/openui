## Why

当前 `genui-java-sdk` 的生成接口只返回生成后的 `openui-lang` 文本，调用方需要在服务层或前端另行保存请求中的 `response` 数据，才能把它作为 Renderer 的 `dataModel` 传入。随着 SmartCanvasService 需要统一向 DSLEngine 返回可渲染结果，SDK 应直接产出包含 DSL 与 `dataModel` 的结构化结果，并为流式场景提供统一的 SSE envelope 数据模型。

## What Changes

- **BREAKING**: `genui-java-sdk` 的同步生成接口从返回 `String` 改为返回结构化 `GenUiGenerationResult`，包含 `dsl`、`dataModel`、可选 `traceId`。
- **BREAKING**: `genui-java-sdk` 的流式生成回调从裸 DSL delta 改为 `RenderStreamEnvelope`，首帧固定返回 `type=dataModel`，后续 DSL chunk 使用 `type=dsl`，结束帧使用 `type=done`。
- `UiGenerationRequest` 增加可选 `traceId`，SDK 只透传，不自行生成。
- `dataModel` 始终来自 `UiGenerationRequest.response` 的防御性拷贝；请求未提供数据时返回空 map / `{}`。
- 流式中途失败时 SDK 发出 `type=error` envelope，再发 `type=done` envelope，不向调用方抛出流中途异常。
- GenUI Service `/v1/generate` 的流式响应从 `text/plain` 裸文本升级为 `text/event-stream`，每个 `data:` 行承载一个 JSON envelope。
- 模板直出、缓存命中和 PIU/iframe 直出场景不纳入本变更范围。

## Capabilities

### New Capabilities
- `genui-java-sdk-generation-output`: 定义 Java Generation SDK 的同步结构化结果、流式 envelope、`dataModel` 回传和错误结束语义。

### Modified Capabilities
- `genui-service-generation`: 将生成端点的流式输出从裸 `openui-lang` 文本改为 `text/event-stream` envelope，并调整流中错误收尾语义。
- `genui-service-rest-api`: 更新 REST 契约文档，使 `/v1/generate` 描述新的 SSE envelope 输出而不是 `text/plain` chunked 文本。

## Impact

- 影响 `packages/genui-java-sdk` 的 public API、LLM 生成实现、stream envelope 类型、JSON 序列化和单元测试。
- 影响 `examples/genui-service` 的 `/v1/generate` controller、Swagger 文档、流式测试和 demo 消费端。
- 前端消费方需要按 envelope `type` 分发：`dataModel` 设置 Renderer `dataModel`，`dsl` 拼接 Renderer `response`，`error` 展示错误，`done` 结束 streaming。
- 不引入新的外部依赖；如需 JSON 输出忽略 `done.content == null`，优先使用现有 JSON/HTTP 序列化能力实现。
