## ADDED Requirements

### Requirement: Generate endpoint documents render stream envelopes
GenUI Service Swagger 2.0 contract SHALL document `/v1/generate` as a `text/event-stream` endpoint whose SSE `data:` frames contain `RenderStreamEnvelope` JSON. The documented envelope SHALL include `type`, `seq`, optional `traceId`, and data-carrying `content`; `type=done` SHALL be documented as not carrying `content`.

#### Scenario: Swagger describes SSE envelope output
- **WHEN** 调用方查阅 `swagger/genui-service.yaml` 中的 `/v1/generate`
- **THEN** 文档说明响应类型为 `text/event-stream`
- **AND** 文档描述 `dataModel`、`dsl`、`error` 和 `done` envelope

#### Scenario: Swagger documents dataModel first frame
- **WHEN** 调用方查阅 `/v1/generate` 响应说明
- **THEN** 文档说明流式响应首帧总是 `type=dataModel`
- **AND** 空数据时 `content` 为 `{}`
