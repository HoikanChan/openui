> **范围说明（2026-06-26 决策）**：本轮只落地 SDK（第 1–3 节）。GenUI Service 与 demo 的接入（第 4–5 节）暂缓——`examples/genui-service` 保持现有 `assemblePrompt` + `LlmClient`/`LlmStream` 路径不变。原因：路由 controller 到 SDK `generateStream` 会破坏 proxy bypass（spec 硬要求）、`promptOverride` 旁路、`extraRules`(list) 三项已实现行为，且与 `流前失败返回 502` 场景冲突。这些需在后续单独变更中，把 proxy/promptOverride/extraRules/finish_reason 能力下沉到 SDK 后再接入。SDK 公开 API 改动对 service 是非破坏的（service 不引用 `GenUiGenerator`）。

## 1. SDK 输出模型

- [x] 1.1 校验 `UiGenerationRequest` builder 和不可变构造测试
- [x] 1.2 新增 `GenUiGenerationResult` record，包含 `dsl`、`dataModel`，并保证 `dataModel` 防御性拷贝且空数据为 empty map
- [x] 1.3 新增 `RenderStreamEnvelope` record，并提供或测试 `dataModel`、`dsl`、`error`、`done` 四类 envelope 的字段约束

## 2. SDK 同步生成

- [x] 2.1 将 `GenUiGenerator.generate(...)` 返回类型改为 `GenUiGenerationResult`
- [x] 2.2 保持现有 prompt 组装、LLM 请求、`ChatCompletionResponse` 解析和 `OpenuiCodeExtractor` 提取逻辑不变
- [x] 2.3 在同步结果中回传请求 `response` 作为 `dataModel`，未提供时回传空 map
- [x] 2.4 覆盖同步成功、空 response 和 LLM 异常抛出测试

## 3. SDK 流式生成

- [x] 3.1 将 `GenUiGenerator.generateStream(...)` 回调类型改为 `Consumer<RenderStreamEnvelope>`，返回类型改为 `GenUiGenerationResult`
- [x] 3.2 在读取 LLM 流之前先发 `seq=0,type=dataModel` envelope，content 为请求 `response` 或空 map
- [x] 3.3 将每个 LLM delta 包装为 `type=dsl` envelope，并维护递增 `seq`
- [x] 3.4 正常结束时发送 `type=done` envelope，content 为 null
- [x] 3.5 流中异常时发送 `type=error` envelope 后发送 `type=done` envelope，不向调用方抛出该流中异常
- [x] 3.6 覆盖首帧、DSL chunk、done 无 content、error 后 done、返回累计结果测试

## 4. GenUI Service SSE 输出（暂缓 — 见范围说明）

- [ ] 4.1 将 `examples/genui-service` 的 `/v1/generate` controller 适配新的 SDK `generateStream(...)` API
- [ ] 4.2 将 `/v1/generate` 响应 `Content-Type` 改为 `text/event-stream`
- [ ] 4.3 将每个 `RenderStreamEnvelope` 序列化为 SSE `event: data` / `data: <json>` frame
- [ ] 4.4 配置或实现 JSON 序列化，使 `done` envelope 不输出 null `content`
- [ ] 4.5 移除旧的纯文本错误尾巴输出逻辑，改为依赖 SDK `error` envelope

## 5. 契约文档与前端消费（暂缓 — 见范围说明）

- [ ] 5.1 更新 `examples/genui-service/src/main/resources/swagger/genui-service.yaml` 中 `/v1/generate` 的响应类型与 envelope 文档
- [ ] 5.2 更新 `examples/react-ui-dsl-demo` 中 GenUI Service 流式消费逻辑，按 envelope `type` 分发 `dataModel`、`dsl`、`error`、`done`
- [ ] 5.3 确认 demo 将 `dataModel` 帧内容传给 `DSLRenderer`，将 `dsl` 帧内容拼接为 Renderer response

## 6. 验证

- [x] 6.1 运行 `mvn test` 或等价命令验证 `packages/genui-java-sdk`（`GenUiGeneratorTest` 12、`GenUiGenerationResultTest` 2、`UiGenerationRequestTest` 5，全部通过）
- [ ] 6.2 运行 `mvn test` 或等价命令验证 `examples/genui-service`（暂缓：service 未改动）
- [ ] 6.3 运行相关前端测试，验证 demo 的 SSE envelope 解析与渲染状态（暂缓：demo 未改动）
- [ ] 6.4 运行 OpenSpec 校验，确认 proposal、design、specs、tasks 均可 apply
