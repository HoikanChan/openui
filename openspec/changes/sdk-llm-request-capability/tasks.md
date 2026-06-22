## 0. 依赖与脚手架

- [x] 0.1 新建分层包目录 `.../genui/core/llm/` 及子包 `transport/`、`protocol/`、`stream/`、`extract/`（入口类 `GenUiGenerator`/`GenUiLlmConfig`/`UiGenerationRequest` 置于 `llm` 根），测试目录镜像同结构
- [x] 0.2 `pom.xml` 加入 BSP restclient 依赖 `com.huawei.bsp:com.huawei.bsp.commonlib.resetclient:25.590.54`，`<scope>provided</scope>`，并加注释（BSP 运行时提供，不随 SDK 打包；"resetclient" 拼写待核对疑似 restclient）

## 1. 既有 core 分层重组（前置：先于 llm 实现，使新代码直接 import 最终包）

- [x] 1.1 在 `core/` 下建 `contract/` 与 `prompt/` 子包
- [x] 1.2 移契约类到 `core/contract/`：`GenerationContract`、`GenerationContractLoader`、`BuiltinSpec`、`ComponentPromptSpec`、`ComponentPropsSchema`、`ComponentGroup`、`DataModelSpec`、`ToolSpec`、`ToolAnnotations`、`GenUIGeneration`（改 package 声明）
- [x] 1.3 移 prompt 类到 `core/prompt/`：`PromptAssembler`、`GenUIPromptRequest`、`GenUIPromptAssemblyResult`、`GenUIPromptAssemblyMetadata`（改 package 声明）
- [x] 1.4 `GenerationSdk`/`GenerationSdkException` 留 `core` 根；`Json` 留根并按需改 `public` 以供子包复用；调整其余跨包可见性（`ComponentPropsSchema` 等校验工具按需放开）
- [x] 1.5 更新 SDK 内部全部 import；验证 `GenerationContractLoader` 仍能加载 `/openui/base-contract.json`（classpath 资源路径不随包移动）
- [x] 1.6 更新既有测试（`PromptGoldenTest`、`GenerationSdkTest`、`JsonTest`、`PromptAssemblySnippetTest`、`SdkPromptMergeTest`、`BaseContractFixtureTest`、`PromptGoldenTest`）的 import 与包；golden 资源不变
- [x] 1.7 更新 `examples/genui-service` 全部 import（`DtoMapper`、`GenerationAppService`、`GenerationsController`、`GenerationSummaryData`、相关测试）
- [x] 1.8 术语收敛：`generationId`→`extensionId` 字段重命名 **已完成**（SDK 与 `examples/genui-service` 全仓 `generationId` 0 处）。类型名 `GenUIGeneration` 保留不动（类型级 `GenUIExtension` 重命名不在本次）
- [ ] 1.9 编译 + 跑既有全部测试，确认重组与重命名**零行为变化**（golden 字节不变）

## 2. GenUiLlmConfig（配置）

- [x] 2.1 定义 `GenUiLlmConfig`（不可变）字段：`endpoint`、`defaultModel`、`temperature`、`enableThinking`、`jsonObjectResponse`、`extraHeaders`
- [x] 2.2 加常量 `DEFAULT_ENDPOINT="/rest/netrsn/v1/chat/completions"`、`DEFAULT_MODEL="qwen3.6-27b"`，及 `defaults()` 工厂
- [x] 2.3 加 `Builder`（每字段 setter + `putHeader` + `build()`），空值回退默认
- [x] 2.4 单测：`defaults()` 各字段值、builder 覆盖 endpoint/model/temperature、`extraHeaders` 不可变

## 3. 传输端口与异常

- [x] 3.1 定义受检异常 `LlmTransportException extends Exception`（(String) 与 (String,Throwable) 构造）
- [x] 3.2 定义端口 `LlmTransport`：`String post(String body) throws LlmTransportException`、`InputStream postStream(String body) throws LlmTransportException`

## 4. 请求体构造（ChatMessage / ChatCompletionRequest）

- [x] 4.1 定义 `ChatMessage`（record `role`/`content` + `system()`/`user()` 工厂）
- [x] 4.2 实现 `ChatCompletionRequest.of(config, model, messages, stream)`：model 空白回退 `config.defaultModel()`；从 config 取 `temperature`/`enable_thinking`/`response_format`（`jsonObjectResponse` 为真→`{"type":"json_object"}`）
- [x] 4.3 实现 `toJson()`：用 fastjson2 序列化 `model/stream/temperature/enable_thinking/response_format/messages`（messages 按 role 原样输出，LinkedHashMap 保序）
- [x] 4.4 单测：同步体含默认 model+`stream=false`+`temperature=0`+`response_format.type=json_object`；流式体 `stream=true`；多消息按 role 输出；config 自定义 temperature / 关 jsonObject 生效

## 5. 响应解析（ChatCompletionResponse）

- [x] 5.1 定义 `ChatCompletionResponse` DTO（`choices[].message{role,content}`、`finish_reason` snake_case 对齐）
- [x] 5.2 实现 `parse(json)`（非法 JSON 抛 `GenerationSdkException`）与 `firstContent()`（choices 空/缺失抛明确异常）
- [x] 5.3 单测：取首条 content；空 choices 抛异常；缺 message 抛异常；非法 JSON 抛异常

## 6. 流式拆帧与 SSE 帧

- [x] 6.1 实现 `SseDeltaParser.parse(InputStream, Consumer<String> sink)`：按 `data: ...\n\n` 拆帧、取 `choices[0].delta.content`、逐段回调非空纯文本 delta、累积返回；坏帧/`[DONE]` 跳过
- [x] 6.2 实现 `SseFrames`：`of(content)` → `data: <content>\n\n`；`done()` → `data: [DONE]\n\n`
- [x] 6.3 单测 `SseDeltaParser`：多帧粘连、半帧跨读、坏帧跳过、`[DONE]` 跳过、回调顺序与累积全文一致
- [x] 6.4 单测 `SseFrames`：`of`/`done` 帧格式

## 7. 代码抽取（OpenuiCodeExtractor）

- [x] 7.1 实现 `extract(raw)`：```openui/```openui-lang/```text 代码块优先 → 含 `root = Stack(` 的代码块回退 → 去空白原文回退；null/空返回 ""（内联内部 `CodeExtractor` 逻辑，留 TODO 标注待核对原实现）
- [x] 7.2 单测：openui 块、openui-lang 块、Stack 启发式、无标记原文、空/ null 输入

## 8. 生成入参（UiGenerationRequest）

- [x] 8.1 定义 `UiGenerationRequest`（record：`extensionId`、`userInput`、`request`、`response`、`suggestion`、`overlayTools`、`overlayRules`、`editMode`/`inlineMode`/`toolCalls`/`bindings`）
- [x] 8.2 加 `Builder` + 必要的 null 归一（集合空列表、map 不可变）
- [x] 8.3 单测：builder 构造、默认空集合、不可变性

## 9. 编排门面（GenUiGenerator）

- [x] 9.1 实现私有映射：`UiGenerationRequest` → `GenUIPromptRequest`（`extensionId`→`extensionId`、`response`→`DataModelSpec`、`suggestion` 追加 `extraRules`、overlay tools/rules、透传 flags）
- [x] 9.2 实现 `create()` / `create(config)`：内部自持 `GenerationSdk.create()` + `new RestfulLlmTransport(config)`，不对外暴露 `GenerationSdk`
- [x] 9.3 实现 `withTransport(config, transport)`（高级/测试入口）+ `register(GenUIGeneration)`（委托内部 sdk，返回 `this` 链式）
- [x] 9.4 实现 `generate(UiGenerationRequest)`：映射 → `assemblePrompt` 作 system 消息 + `userInput`(+` /no_think`) 作 user 消息 → `ChatCompletionRequest.of(..., false)` → `post` → `ChatCompletionResponse.firstContent()` → `OpenuiCodeExtractor.extract`；`LlmTransportException` 包装为 `GenerationSdkException`
- [x] 9.5 实现 `generateStream(UiGenerationRequest, sink)`：同 9.4 但 `stream=true` → `postStream` → `SseDeltaParser.parse(in, sink)` → 流尾 `extract` 返回；`IOException`/`LlmTransportException` 包装
- [x] 9.6 单测（fake `LlmTransport`）：同一实例 `register` 后 `generate`、同步/流式端到端、extensionId 选中已注册扩展（system prompt 含其组件/工具）、response 进 DataModel、未注册 extensionId 回落 base contract、endpoint/model 配置生效、默认 `create` 不暴露 `GenerationSdk`/transport

## 10. BSP 传输实现 + 离线 stub

- [x] 10.1 实现 `RestfulLlmTransport implements LlmTransport`：`post` 用 `RestfulFactory.getRestInstance().post(config.endpoint(), params)`，非 200 抛 `LlmTransportException`；`postStream` 额外 `putHttpContextHeader("SUPPORT_STREAM_CONTENT_FOR_SDK","true")` 返回 `getDataStream()`；`config.extraHeaders()` 注入；捕获底层 `Exception` 包装
- [x] 10.2 写 BSP API stub 源码（`com.huawei.bsp.roa.util.restclient`：`RestfulParametes`、`RestfulResponse`、`RestfulFactory`/client，`post(String,RestfulParametes) throws Exception`），其 `post` 用 JDK `HttpClient` 向可配 base URL（系统属性）转发，供集成测试真实打到内嵌 server
- [x] 10.3 `javac` 编译 stub → 打 jar → `mvn install:install-file` 安装到本地仓库，GAV 同 0.2（脚本化，记录到 README/迁移说明，标注仅离线构建用）
- [x] 10.4 集成测试：用 `com.sun.net.httpserver.HttpServer` 起内嵌服务，断言 `RestfulLlmTransport.post` 返回体、`postStream` 流内容与 `SUPPORT_STREAM_CONTENT_FOR_SDK` 头、非 200 抛 `LlmTransportException`

## 11. 构建与验证

- [x] 11.1 `mvn -o -Dtest=...llm.* test` 跑 llm 子包测试（受本机内存限制，独立 java 进程/限定用例，勿杀 IDE LSP）
- [x] 11.2 跑既有 `PromptGoldenTest` 等，确认重组+迁移后无回归
- [x] 11.3 `mvn -o -pl packages/genui-java-sdk -am package -DskipTests` 确认带 provided BSP 依赖可编译打包

## 12. 内部仓库迁移指引（随附文档，不在本仓改代码）

- [x] 12.1 `GenUIServiceDelegateImpl` 瘦身：sha256 → 查缓存 → `GenUiGenerator.generate/generateStream` → 写缓存；流式回调用 `SseFrames` 写出；删除自有请求体/解析/拆帧/抽取
- [x] 12.2 删除内部 `LLMService`/`ChatCompletionsRsp`（整体迁入 SDK）
- [x] 12.3 内部 `UIRequestDetail`（apiRsp/apiReq/apiUrl 旧形）→ `UiGenerationRequest`（response/request/...）字段映射对照表，列出需对照 `PromptTemplateUtil` 确认的点
- [ ] 12.4 用真 BSP 依赖替换离线 stub（删除本地 install-file 步骤），核对 "resetclient" 拼写
