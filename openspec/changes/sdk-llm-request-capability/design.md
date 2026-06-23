## Context

`genui-java-sdk`（artifact `genui-core`）是对外的轻量 Java 库，唯一编译期依赖是 `fastjson2`，目前职责止于 `GenerationSdk.assemblePrompt()` —— 产出 system prompt 字符串。真正的"调大模型 → 拿结果 → 抽取 openui-lang"在内部业务仓库的 `interal-code/` 中：

- `LLMService`：用华为内部 `RestfulFactory` 发 `POST /rest/netrsn/v1/chat/completions`，构造请求体（model、`stream`、`enable_thinking=false`、`temperature=0`、`response_format=json_object`、`/no_think`），解析 `ChatCompletionsRsp`，含同步 `invoke` 与流式 `invokeStream`。
- `GenUIServiceDelegateImpl`：编排 prompt 构建（`PromptTemplateUtil` 的 `{userInput}`/`{apiRsp}` 模板，未用 SDK 拼装）、Redis（Jedis）sha256 缓存、调 LLM、SSE 拆帧、代码抽取（`CodeExtractor` + markdown 块）。
- `ChatCompletionsRsp`：响应 DTO。

约束：SDK 必须保持轻量，绝不能引入 `com.huawei.bsp.*`、Spring、Jedis 等内部基础设施；内部仓库代码不在本仓，本提案只产出 SDK 侧实现与一份迁移指引。

## Goals / Non-Goals

**Goals:**
- 把与提供商无关的"大模型请求能力"（请求体构造、响应解析、SSE 拆帧、openui-lang 抽取）下沉到 SDK，可单元测试、可被多接入方复用。
- 用端口-适配器隔离传输：SDK 定义 `LlmTransport`，宿主用 `RestfulFactory` 实现，基础设施依赖留在宿主侧。
- prompt 构建收敛到 `GenerationSdk.assemblePrompt`，新增 `GenUiGenerator` 编排门面统一 sync/stream 两条路径。
- 内部 `GenUIServiceDelegateImpl` 瘦身为"缓存 + 调门面"。

**Non-Goals:**
- 不自研 HTTP 协议栈：SDK 内置传输直接复用 BSP `RestfulFactory`（连接池/代理/服务发现由 BSP 运行时托管），不自己实现连接池、代理、重试。
- 不把 Redis 缓存、Spring 装配迁入 SDK（缓存留在 delegate）。
- 不改动既有 `GenerationSdk.assemblePrompt` 的拼装结果（golden 测试保持绿）。
- 不改造内部仓库代码本身（仅随附迁移指引）。

## Decisions

### 决策 1：端口切在传输层 + SDK 内置默认实现（RestfulFactory）

端口 `LlmTransport`（`post(body)` / `postStream(body)`）只接收"已构造好的请求体字符串"并返回"原始响应/流"；请求体构造、响应解析、SSE 拆帧、代码抽取全部留在 SDK。**SDK 同时内置默认实现 `RestfulLlmTransport`**，复刻内部 `LLMService`：用 `RestfulFactory.getRestInstance().post(endpoint, parametes)` 直接发请求，流式路径带 `SUPPORT_STREAM_CONTENT_FOR_SDK=true` 头。宿主可注入自定义 `LlmTransport` 覆盖默认实现。

- **理由**：用户要求 SDK 自身具备发 HTTP 能力（pom 加 BSP restclient 依赖），同时保留端口便于测试（fake transport）与未来替换传输栈；协议权威仍集中在 SDK 一处。
- **备选（仅端口、宿主实现传输）**：SDK 不引 BSP 依赖、最轻量。否决：用户明确要求 SDK 直接发请求并加依赖。
- **备选（仅默认实现、不要端口）**：少一层抽象。否决：失去可测性与可替换性，单测将被迫触达 BSP 运行时。

### 决策 2：prompt 构建收敛到 `assemblePrompt`

`GenUiGenerator` 内部直接调 `GenerationSdk.assemblePrompt(request)` 产出 prompt，取代内部 `PromptTemplateUtil` 的 `{userInput}`/`{apiRsp}` 字符串模板。

- **理由**：让 SDK 成为 prompt 拼装的唯一权威，extensionId/扩展/tools/extraRules 走统一路径，消除内部模板与 SDK 拼装的双轨漂移。
- **备选（门面只收 prompt 字符串）**：改动更小，但保留双轨。否决：用户已明确选择收敛。
- **影响**：内部 `{userInput}`/`{apiRsp}` 的语义需映射到 `GenUIPromptRequest`（userInput 入请求、apiRsp 作为 dataModel 或 extraRules 上下文）—— 列入迁移指引与 Open Questions。

### 决策 3：新建 `com.huawei.cloudsop.genui.core.llm` 子包

新增类：`LlmTransport`（端口）、`RestfulLlmTransport`（默认实现）、`GenUiLlmConfig`（配置）、`ChatCompletionRequest`、`ChatCompletionResponse`、`SseDeltaParser`、`SseFrames`（SSE 帧 helper）、`OpenuiCodeExtractor`、`GenUiGenerator`（门面）。

- **理由**：与既有 prompt 拼装类隔离，公共 API 面清晰；复用现有 `Json` 工具与 `fastjson2`。
- **`CodeExtractor` 处理**：内部 `extractOpenuiCode` 依赖内部 `CodeExtractor`，将其抽取逻辑内联进 `OpenuiCodeExtractor`，不引入内部依赖。

### 决策 4：流式以"回调 + 累积返回"建模，并提供 SSE 帧 helper

`SseDeltaParser.parse(InputStream, Consumer<String> sink)` 逐段回调输出 delta 内容并返回累积全文；`GenUiGenerator.generateStream` 在流尾对累积全文做一次代码抽取返回最终 DSL。额外提供 `SseFrames.of(content)` 把一段内容封装为 `data: <content>\n\n` 标准 SSE 帧，便于外层 controller 直接把回调内容写成 SSE 响应。

- **理由**：用户要求"返回内容容易用 SSE 转给外面"。回调给增量内容（纯文本 delta），`SseFrames` 负责帧封装 —— 二者解耦：宿主既能写 `text/plain` chunked（内部现状），也能一行封成 `text/event-stream`。与内部 `sseGenerateUI` 的"边写边缓存、流尾抽取"行为对齐。

### 决策 5：endpoint 与 model 经 `GenUiLlmConfig` 注入

`GenUiLlmConfig` 持有：`endpoint`（请求地址 path，默认内部 `/rest/netrsn/v1/chat/completions` 可覆盖）、`defaultModel`（默认 `qwen3.6-27b` 可覆盖）、可选 `extraHeaders`、`temperature`/`enableThinking` 等生成参数。`RestfulLlmTransport` 与 `ChatCompletionRequest` 从 config 取值，不再硬编码。

- **理由**：用户要求请求地址与模型可配置；把内部专属常量从代码常量提升为配置项，使 SDK 可面向不同环境/模型复用。
- **注意**：BSP `RestfulFactory` 的 base host 由运行时服务发现解析，`endpoint` 为相对 path；若未来需要绝对 URL，需在 config 留扩展位（列入 Open Questions）。

## SDK 接口定义（完整公共 API）

包路径 `com.huawei.cloudsop.genui.core.llm`。以下为本次新增的全部公共类型与签名契约（方法体在实现阶段填充）。异常族：传输层抛受检 `LlmTransportException`，门面对调用方统一抛非受检 `GenerationSdkException`（沿用既有）。

### 1. `GenUiLlmConfig` — 可配置项

```java
/** LLM 调用配置：endpoint、model 与生成参数均可覆盖；不可变。 */
public final class GenUiLlmConfig {
    public static final String DEFAULT_ENDPOINT = "/rest/netrsn/v1/chat/completions";
    public static final String DEFAULT_MODEL    = "qwen3.6-27b";

    public static GenUiLlmConfig defaults();          // 全默认
    public static Builder builder();

    public String  endpoint();                        // 请求地址（BSP 相对 path）
    public String  defaultModel();                    // model 为空时回退
    public double  temperature();                     // 默认 0
    public boolean enableThinking();                  // 默认 false
    public boolean jsonObjectResponse();              // 默认 true → response_format.type=json_object
    public Map<String,String> extraHeaders();         // 附加请求头，默认空

    public static final class Builder {
        public Builder endpoint(String endpoint);
        public Builder defaultModel(String model);
        public Builder temperature(double temperature);
        public Builder enableThinking(boolean enable);
        public Builder jsonObjectResponse(boolean enable);
        public Builder putHeader(String name, String value);
        public GenUiLlmConfig build();
    }
}
```

### 2. `LlmTransport` — 传输端口 + `LlmTransportException`

```java
/** 传输端口：只收已构造好的请求体、返回原始响应/流；不感知 chat/completions 协议。 */
public interface LlmTransport {
    /** 同步 POST，返回响应原文。 */
    String post(String requestBody) throws LlmTransportException;

    /** 流式 POST，返回原始 SSE 字节流（调用方负责关闭）。 */
    InputStream postStream(String requestBody) throws LlmTransportException;
}

/** 传输层受检异常。 */
public class LlmTransportException extends Exception {
    public LlmTransportException(String message);
    public LlmTransportException(String message, Throwable cause);
}
```

### 3. `ChatMessage` — 消息

```java
/** 单条 chat 消息。 */
public record ChatMessage(String role, String content) {
    public static ChatMessage system(String content);
    public static ChatMessage user(String content);
}
```

### 4. `ChatCompletionRequest` — 请求体构造

```java
/** 把消息列表构造为 OpenAI 兼容 chat/completions 请求体。不可变。 */
public final class ChatCompletionRequest {
    /** model 为空/空白时回退到 config.defaultModel()；其余生成参数取自 config。 */
    public static ChatCompletionRequest of(GenUiLlmConfig config, String model,
                                           List<ChatMessage> messages, boolean stream);

    public String toJson();   // stream/model/temperature/enable_thinking/response_format/messages
}
```

### 5. `ChatCompletionResponse` — 同步响应解析

```java
/** 同步响应 DTO（snake_case 字段对齐，如 finish_reason）。 */
public final class ChatCompletionResponse {
    public static ChatCompletionResponse parse(String json);  // 非法 JSON 抛 GenerationSdkException

    /** 返回 choices[0].message.content；choices 为空或缺失抛 GenerationSdkException。 */
    public String firstContent();
}
```

### 6. `SseDeltaParser` — 流式拆帧

```java
/** 按 data:...\n\n 拆 SSE 帧，抽取 choices[0].delta.content，逐段回调纯文本 delta。 */
public final class SseDeltaParser {
    /** 逐段把非空 delta 交给 sink，返回累积全文；坏帧跳过，不中断流。 */
    public static String parse(InputStream sseStream, Consumer<String> sink) throws IOException;
}
```

### 7. `SseFrames` — SSE 帧封装 helper

```java
/** 与 delta 回调解耦的 SSE 帧封装，便于宿主写 text/event-stream。 */
public final class SseFrames {
    public static String of(String content);   // 返回 "data: " + content + "\n\n"
    public static String done();                // 返回 "data: [DONE]\n\n"
}
```

### 8. `OpenuiCodeExtractor` — 代码抽取

```java
/** 从 LLM 原始输出抽取 openui-lang 代码。 */
public final class OpenuiCodeExtractor {
    /** 优先 ```openui/```openui-lang/```text 代码块，回退含 root = Stack( 的代码块，
     *  最终回退到去空白原文；null/空返回 ""。 */
    public static String extract(String rawLlmOutput);
}
```

### 9. `UiGenerationRequest` — 生成入参（对齐 `UIRequestDetail`）

入参对齐 SYSTEM-DESIGN 第 7 节 `UIRequestDetail` 的**生成相关子集**；直出/降级分支（`templateId`/`renderPiu`/`iframeUrl`/`iframeTitle`/`scenario`）与缓存键（`source`）属服务层，不进 SDK。

```java
/** SDK 生成入参，对齐 UIRequestDetail 的生成相关字段。不可变；用 builder 构造。 */
public record UiGenerationRequest(
    String extensionId,              // 选择已注册扩展；映射为 SDK extensionId
    String userInput,                // 自然语言意图 → user 消息
    Map<String,Object> request,      // 上游入参（辅助，如 UI 标题），可空
    Map<String,Object> response,     // 主数据源 → DataModel（{data.*} 绑定来源），生成场景必填
    String suggestion,               // 额外建议/约束 → 追加 extraRules
    Boolean editMode, Boolean inlineMode, Boolean toolCalls, Boolean bindings) {
    public static Builder builder();
    /* Builder 含上述每字段 setter + build() */
}
```

### 10. `GenUiGenerator` — 编排门面

默认路径下用户**无需感知 `LlmTransport`**：门面内部默认用 `RestfulLlmTransport`（`RestfulFactory`）。`LlmTransport` 仅作为可选的"从外面覆盖传输"入口（高级/测试用）。

**同一实例既注册又生成** —— 门面内部自持 `GenerationSdk`，调用方不接触它：`register(...)` 注册扩展、`generate(...)` 生成，都在同一个 `GenUiGenerator` 上。

```java
/** 自持 GenerationSdk + 传输 + GenUiLlmConfig，端到端注册与生成 openui-lang。 */
public final class GenUiGenerator {
    // —— 创建：传输零配置，内部即 RestfulFactory；不暴露 GenerationSdk ——
    public static GenUiGenerator create();                       // 全默认 config + 默认 base contract
    public static GenUiGenerator create(GenUiLlmConfig config);  // 自定义 config，仍走 RestfulFactory

    // —— 可选高级入口：替换传输（自定义客户端 / 测试 fake）——
    public static GenUiGenerator withTransport(GenUiLlmConfig config, LlmTransport transport);

    /** 注册扩展（同一实例，链式）。extensionId 即 extension.extensionId()。 */
    public GenUiGenerator register(GenUIExtension extension);

    /** 按 extensionId 选扩展 → assemblePrompt(系统提示) + userInput(用户消息) → post → 解析 → 抽取。 */
    public String generate(UiGenerationRequest request);

    /** 流式版：postStream → SseDeltaParser 逐段回调纯文本 delta → 流尾对累积全文抽取并返回。 */
    public String generateStream(UiGenerationRequest request, Consumer<String> sink);
}
```

> 说明 1：`GenerationSdk` 被门面内部持有，调用方只跟 `GenUiGenerator` 一个实例打交道；`register` 委托给内部 sdk 并返回 `this` 以便链式。
> 说明 2：传输默认内置——`create(...)` 内部构造 `new RestfulLlmTransport(config)`，零配置即用 `RestfulFactory`；只有需要替换传输栈或写测试时才用 `withTransport(...)`。
> 说明 3：`generate` 把 `assemblePrompt` 产物作为 `system` 消息、`userInput` 作为 `user` 消息（角色分离）；`/no_think` 追加在 user 消息尾部。若内部端点对 system 角色行为不一致，可在适配层退回单消息拼接——见 Open Questions。

### `UIRequestDetail` → SDK 字段映射 与 extensionId 对接

**字段映射**（门面 `generate` 内部完成）：

| UIRequestDetail 字段 | SDK 去向 |
|---|---|
| `extensionId` | `GenUIPromptRequest.extensionId` —— 选择已注册扩展 |
| `response`（必填） | `DataModelSpec.raw` —— 经 `assemblePrompt` 进系统提示，作为 `{data.*}` 主数据源 |
| `request` | 折入 user 消息上下文（辅助，如 UI 标题）；或并入 DataModel 描述 |
| `userInput` | user 消息（尾部追加 `/no_think`） |
| `suggestion` | 追加到 `GenUIPromptRequest.extraRules` |
| tools / 固定规则 | 不作为 `UiGenerationRequest` 字段传入；通过 registered `GenUIExtension.tools` / `additionalRules` 发布 |
| `templateId`/`renderPiu`/`iframeUrl`/`scenario`/`source` | **不进 SDK** —— 模板命中/PIU/iframe/场景/缓存键由服务层处理 |

**extensionId 怎么对接**（贯穿注册与生成两端，同一标识符）：

1. **注册端**（SYSTEM-DESIGN 第 8 节 `GenUIExtension`）：业务把扩展配置注册到**同一个门面实例** —— `genUiGenerator.register(GenUIExtension)`（内部委托给自持的 `GenerationSdk`），其 `extensionId` 即 `extensionId`（GenUIExtension 的构造可参考 `DtoMapper.toGeneration(extensionId, dto)`）。组件/工具/示例/规则随之入库。
2. **生成端**（第 7 节 `UIRequestDetail`）：`generate` 把 `request.extensionId` 作为 `extensionId` 传入 `assemblePrompt`，于是该扩展的组件、工具、示例、规则被注入 system prompt（`assemblePrompt` 既有能力）。
3. **术语**：SDK 内部字段名仍是 `extensionId`，SYSTEM-DESIGN 对外统一为 `extensionId`，二者是同一标识符——门面只做名称映射（与 `DtoMapper` 同一手法），本次不改 SDK 既有记录字段名（避免动 golden 测试与参考服务），术语彻底收敛列入 Open Questions。
4. **未注册 extensionId**：`assemblePrompt` 按 SDK 语义静默回落 base contract；SYSTEM-DESIGN 6.2.4.6 要求的「未注册即报错、不静默回退」属服务层职责（SDK 无注册枚举能力，见 `GenerationAppService` 注释），门面不在 SDK 内 fail-loud。
5. **数据预处理**（无用字段过滤、>2K 采样压缩与回填）属服务层（第 6.2.4.2 节「前置处理」），SDK 只接收已备好的 `response` 作为 DataModel，不做压缩。

### 11. `RestfulLlmTransport` — 内置默认传输（BSP）

```java
/** LlmTransport 的内置实现：用 BSP RestfulFactory 直接发请求。流式带 SUPPORT_STREAM_CONTENT_FOR_SDK 头。 */
public final class RestfulLlmTransport implements LlmTransport {
    public RestfulLlmTransport(GenUiLlmConfig config);

    @Override public String      post(String requestBody)       throws LlmTransportException;
    @Override public InputStream postStream(String requestBody)  throws LlmTransportException;
}
```

### 宿主接线示例

```java
// 1) 创建并注册：同一个实例，传输零配置（内部即 RestfulFactory）
GenUiGenerator generator = GenUiGenerator.create()
        .register(alarmExtension);             // extensionId 即 alarmExtension.extensionId()

// 2) 生成：入参对齐 UIRequestDetail
UiGenerationRequest req = UiGenerationRequest.builder()
        .extensionId("alarm-genui-presets")
        .userInput("查看告警列表")
        .response(responseMap)                 // 主数据源 → DataModel
        .suggestion("优先用表格，状态用 Tag 组件")
        .build();

String dsl = generator.generate(req);          // 同步

generator.generateStream(req,                  // 流式转 SSE（宿主侧）
        delta -> out.write(SseFrames.of(delta).getBytes(UTF_8)));

// 高级：仅在需要替换传输或测试时（仍是同一实例用法）
GenUiGenerator test = GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), fakeTransport)
        .register(alarmExtension);
```

## 项目文件结构

现状：`packages/genui-java-sdk` 把 17 个类全平铺在 `com.huawei.cloudsop.genui.core` 一个包里。本次**一并重组既有平铺类**为 `contract/` + `prompt/` 子包，并新增分层的 `llm/` 子包，**入口类置于各层根、机制类下沉子包**：

```
packages/genui-java-sdk/
├── pom.xml                         # +BSP restclient(provided)
├── bsp-stub/                       # 离线构建用 BSP API stub（不随 SDK 打包）
│   ├── src/com/huawei/bsp/roa/util/restclient/{RestfulFactory,RestfulParametes,RestfulResponse}.java
│   └── install-stub.(sh|ps1)       # javac→jar→mvn install:install-file
└── src/main/java/com/huawei/cloudsop/genui/core/
    ├── GenerationSdk.java                    # 编排入口（留根）
    ├── GenerationSdkException.java
    ├── Json.java                             # 内部 util（改 public 供子包复用）
    ├── contract/                             # ← 既有类重组
    │   ├── GenerationContract.java · GenerationContractLoader.java · BuiltinSpec.java
    │   ├── ComponentPromptSpec.java · ComponentPropsSchema.java · ComponentGroup.java
    │   ├── DataModelSpec.java · ToolSpec.java · ToolAnnotations.java
    │   └── GenUIExtension.java
    ├── prompt/                               # ← 既有类重组
    │   ├── PromptAssembler.java · GenUIPromptRequest.java
    │   └── GenUIPromptAssemblyResult.java · GenUIPromptAssemblyMetadata.java
    └── llm/                                  # ← 本次新增子包
        ├── GenUiGenerator.java               # 编排门面（对外入口）
        ├── GenUiLlmConfig.java               # 配置
        ├── UiGenerationRequest.java          # 生成入参（对齐 UIRequestDetail）
        ├── transport/
        │   ├── LlmTransport.java             # 端口
        │   ├── LlmTransportException.java    # 受检异常
        │   └── RestfulLlmTransport.java      # 内置默认实现（BSP）
        ├── protocol/
        │   ├── ChatMessage.java
        │   ├── ChatCompletionRequest.java
        │   └── ChatCompletionResponse.java
        ├── stream/
        │   ├── SseDeltaParser.java
        │   └── SseFrames.java
        └── extract/
            └── OpenuiCodeExtractor.java
```

测试镜像主源码结构：

```
src/test/java/com/huawei/cloudsop/genui/core/llm/
├── GenUiGeneratorTest.java          # fake LlmTransport，端到端 + 注册
├── GenUiLlmConfigTest.java
├── UiGenerationRequestTest.java
├── transport/RestfulLlmTransportIT.java   # 内嵌 HttpServer + BSP stub 集成测试
├── protocol/{ChatCompletionRequestTest,ChatCompletionResponseTest}.java
├── stream/{SseDeltaParserTest,SseFramesTest}.java
└── extract/OpenuiCodeExtractorTest.java
```

**分包依据**：`llm` 根放对外入口（`GenUiGenerator`/`GenUiLlmConfig`/`UiGenerationRequest`），机制按职责切 `transport`(传输)/`protocol`(chat 协议)/`stream`(SSE)/`extract`(抽取)。`core` 既有类切 `contract`(契约/组件/工具/数据模型)/`prompt`(拼装)，`GenerationSdk`/`GenerationSdkException`/`Json` 留根。子包公共类型保持 `public`。

**重组的 blast radius（本次承担）**：移包改 package 声明会触及 ① SDK 内部全部 import；② 既有测试（`PromptGoldenTest` 等）的 import（golden 资源不变）；③ `examples/genui-service`（`DtoMapper`/`GenerationAppService`/controller/测试）的 import。包私有的 `Json` 因跨子包复用须改 `public`。`GenerationContractLoader` 用 classpath 绝对路径加载 `/openui/base-contract.json`，不随包移动。**验收准绳：重组零行为变化，golden 字节不变。**

## Risks / Trade-offs

- **SDK 引入 BSP restclient 依赖** → SDK 从"零基础设施"变为耦合 BSP 运行时，纯单元测试不能触达 `RestfulFactory`；通过 `LlmTransport` 端口 + fake 实现把协议/编排逻辑与传输解耦测试，`RestfulLlmTransport` 仅做集成验证。
- **BSP restclient 的 Maven 坐标未知** → pom 先写占位 `groupId:artifactId:version` + TODO 注释，由内部确认后填；坐标未定前 `RestfulLlmTransport` 无法编译，可先标 `@Disabled`/隔离子模块，不阻塞其余协议层落地。
- **默认 model `qwen3.6-27b` 与 endpoint 内置** → 经 `GenUiLlmConfig` 可覆盖，默认值仅作回退。
- **prompt 收敛改变发给 LLM 的 prompt 文本** → 可能影响内部生成结果质量；迁移时以内部真实样例做一次 A/B 比对，必要时用 extraRules 补齐原模板中的固定段落。
- **SSE 拆帧基于字符级读取** → 保留内部已验证的拆帧算法（按 `\n\n` 边界、`data: ` 前缀），补单元测试覆盖坏帧、半帧、多帧粘连。
- **响应 DTO 字段命名（`finishReason` vs `finish_reason`）** → 用 fastjson2 的命名策略或显式注解对齐 snake_case，单测固定。

## Migration Plan

1. SDK pom 加入 BSP restclient 依赖（坐标待补）；落地新子包与单元测试，`mvn test` 全绿（含既有 golden 测试不回归）。
2. SDK 内置 `RestfulLlmTransport`（含 endpoint、`SUPPORT_STREAM_CONTENT_FOR_SDK` 头、`RestfulFactory`），由 `GenUiLlmConfig` 配置 endpoint/model。
3. 内部 `GenUIServiceDelegateImpl` 改为：sha256 → 查缓存 → 调 `GenUiGenerator.generate/generateStream`（直接用 SDK 内置 transport）→ 写缓存；流式回调内容用 `SseFrames` 写出。删除自有请求体构造、响应解析、拆帧、抽取代码。
4. 删除内部 `LLMService`/`ChatCompletionsRsp`（已整体迁入 SDK）。
5. 回滚策略：内部 delegate 改动与 SDK 升级解耦；SDK 对既有 `assemblePrompt` 调用方为纯新增（additive），可独立回退 delegate。

## Open Questions

- **BSP restclient 的 Maven GAV** —— 已确认 `com.huawei.bsp:com.huawei.bsp.commonlib.resetclient:25.590.54`（"resetclient" 拼写实现时核对，疑似 restclient）。
- **术语收敛 `extensionId`**：SYSTEM-DESIGN 对外统一 `extensionId`，SDK 记录字段与类型名已分别收敛到 `extensionId` 和 `GenUIExtension`；如后续还要调整 metadata 与参考服务命名，单列 change。
- **`request`（上游入参）落点**：折入 user 消息上下文，还是并入 DataModel 描述？需对照真实样例确认对生成质量的影响。
- `GenUiLlmConfig.endpoint` 当前按 BSP 相对 path 建模（host 由服务发现解析）；若需支持绝对 URL / 非 BSP 环境，是否要让 `LlmTransport` 抽象出 base URL —— 暂列后续。
- **system vs 单 user 消息**：门面默认 system+user 角色分离；内部端点若对 system 角色行为不一致，是否需可切换的单消息拼接模式。
