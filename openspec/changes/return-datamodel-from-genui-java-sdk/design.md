## Context

`genui-java-sdk` 目前把 `UiGenerationRequest.response` 用作 prompt 的 `DataModelSpec`，但生成接口只把模型输出提取成 `openui-lang` 字符串返回。调用方如果要让前端 Renderer 使用同一份业务数据，必须在服务层自行保存并回传请求中的 `response`，这会让 SDK、GenUI Service 和前端 demo 对返回协议的理解分散。

本变更将 Java SDK 从“只返回 DSL 文本”升级为“返回可渲染结果”。同步接口返回 DSL 与 `dataModel`；流式接口返回统一的 `RenderStreamEnvelope`，由服务层原样序列化为 `text/event-stream`。用户已确认当前 SDK 无外部兼容负担，因此采用破坏式 API 变更，不保留旧字符串返回方法。

## Goals / Non-Goals

**Goals:**

- 让 SDK 同步生成接口直接返回 `dsl`、`dataModel` 和可选 `traceId`。
- 让 SDK 流式生成接口先发 `dataModel` envelope，再发 `dsl` envelope，最后发 `done` envelope。
- 让 `dataModel` 始终来自 `UiGenerationRequest.response`，未提供时为空 map / `{}`。
- 让 GenUI Service `/v1/generate` 输出 `text/event-stream`，每个 SSE `data:` 都是统一 JSON envelope。
- 在流式中途失败时通过 `error` envelope 通知前端，并继续发 `done` 结束帧。

**Non-Goals:**

- 不处理模板直出、Generated Template Cache、PIU 直出或 iframe 直出。
- 不设计前端渲染组件本身，只定义前端消费 envelope 的数据契约。
- 不引入 SDK 自主生成 traceId 的能力；traceId 只由上游可选透传。
- 不保留旧 `String generate(...)` / 裸 delta stream API。

## Decisions

### 1. SDK 同步接口返回结构化结果

SDK public API 改为：

```java
GenUiGenerationResult generate(UiGenerationRequest request);
```

结果类型：

```java
public record GenUiGenerationResult(
    String dsl,
    Map<String, Object> dataModel,
    String traceId
) {}
```

`dsl` 是 `OpenuiCodeExtractor` 提取后的完整 `openui-lang`；`dataModel` 是 `request.response()` 的防御性不可变拷贝；`traceId` 来自 `request.traceId()`，可以为 `null`。

备选方案是保留旧 `generate(...) -> String` 并新增 `generateResult(...)`。该方案被否，因为当前没有调用方兼容要求，保留两套 API 会让新契约不够明确。

### 2. SDK 流式接口回调 envelope，而不是裸文本 delta

SDK public API 改为：

```java
GenUiGenerationResult generateStream(
    UiGenerationRequest request,
    Consumer<RenderStreamEnvelope> sink
);
```

envelope 类型：

```java
public record RenderStreamEnvelope(
    String type,
    int seq,
    String traceId,
    Object content
) {}
```

帧顺序固定为：

1. `type=dataModel`，`seq=0`，`content=request.response()` 或空 map。
2. `type=dsl`，`seq` 递增，`content` 为模型 delta 文本。
3. `type=done`，`seq` 递增，`content=null`。

`done` 在 Java record 中允许 `content == null`；服务层 JSON 序列化时忽略 null 字段，使前端看到的完成帧不包含 `content`。

备选方案是在服务层封装裸 delta。该方案被否，因为会让不同 SmartCanvasService 实现重复维护 `seq`、首帧 `dataModel`、错误帧和完成帧规则。

### 3. `content` 按 `type` 使用不同 JSON 类型

流式 envelope 顶层字段固定为 `type`、`seq`、可选 `traceId`、数据帧的 `content`。`content` 不做额外包装：

```json
{"type":"dataModel","seq":0,"traceId":"t1","content":{"alarms":[]}}
{"type":"dsl","seq":1,"traceId":"t1","content":"root = Stack([])"}
{"type":"done","seq":2,"traceId":"t1"}
```

这样前端可以用 `type` 做判别联合类型，并把 `content` 直接用于目标状态：`dataModel` 设置 Renderer 数据，`dsl` 拼接 Renderer response。

备选方案是统一把 `content` 包成对象，例如 `{"dataModel": ...}` 或 `{"text": ...}`。该方案被否，因为用户明确希望避免额外包装，协议应保持薄而直接。

### 4. 流中错误通过 `error` envelope 表达，随后仍发 `done`

同步生成失败继续抛 `GenerationSdkException`，由服务层转成 HTTP 错误。流式生成在首帧已发出后发生错误时，SDK 不向调用方抛异常，而是发送：

```json
{"type":"error","seq":7,"content":{"code":"LLM_STREAM_FAILED","message":"connection dropped","retryable":true}}
{"type":"done","seq":8}
```

`done` 只表示流结束，不表示成功。前端通过是否收到 `error` 帧判断结果状态。

备选方案是发 `error` 后抛异常。该方案被否，因为服务层可能已经开始写 SSE 响应，再抛异常容易造成重复错误输出或破坏响应生命周期。

### 5. 服务层只做 SSE 序列化

GenUI Service `/v1/generate` 的 `Content-Type` 改为 `text/event-stream`。controller 调用 SDK 的 envelope stream API，并把每个 envelope 写成：

```text
event: data
data: {"type":"dsl","seq":1,"content":"root = Stack([])"}

```

服务层不重新解释 SDK envelope，不自行追加文本错误尾巴。Swagger 文档同步描述 envelope 类型。

## Risks / Trade-offs

- [Risk] 破坏式 SDK API 会让现有测试和参考服务无法编译 → Mitigation: 同一变更内更新 SDK 测试、GenUI Service controller、demo 消费端和 Swagger。
- [Risk] `RenderStreamEnvelope.content` 是 `Object`，Java 编译期类型约束较弱 → Mitigation: 通过静态工厂方法或测试约束每个 `type` 的 content 类型，并在 Swagger / TypeScript 示例中定义判别联合类型。
- [Risk] `done.content == null` 依赖服务层 JSON 序列化忽略 null → Mitigation: 增加序列化测试，确保输出 `done` 帧不包含 `content` 字段。
- [Risk] 流式中途失败不抛异常可能被服务层误认为成功 → Mitigation: `generateStream(...)` 返回的结果仍可包含已累计 DSL；错误状态以已发送的 `error` envelope 为准，服务层不再二次判断成功。
- [Risk] 前端 demo 从纯文本流切换到 SSE JSON 后解析复杂度增加 → Mitigation: 提供小型 frame parser，只处理 `data:` 行并按 `type` 分发。

## Migration Plan

1. 在 SDK 中新增 `GenUiGenerationResult`、`RenderStreamEnvelope`，并给 `UiGenerationRequest` 增加 `traceId`。
2. 将 `GenUiGenerator.generate(...)` 改为返回 `GenUiGenerationResult`。
3. 将 `GenUiGenerator.generateStream(...)` 改为回调 `RenderStreamEnvelope`，内部维护 `seq`。
4. 更新 `examples/genui-service` 的 `/v1/generate` 为 `text/event-stream` envelope 输出。
5. 更新 Swagger、参考 demo 和测试。
6. 回滚时恢复旧 SDK API 与 `/v1/generate` 文本流契约；由于本变更不做数据持久化迁移，回滚只涉及代码和 API 调用方。

## Open Questions

- 后续模板直出、PIU 直出和 iframe 直出是否复用同一个 `RenderStreamEnvelope` 结构，本变更暂不决策。
- 是否需要在 `GenUiGenerationResult` 中记录流式生成时是否出现过 error envelope，本变更暂不加入该字段。
