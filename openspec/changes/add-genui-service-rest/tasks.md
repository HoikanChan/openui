## 1. 构建骨架与契约

- [x] 1.1 仓库根新增聚合 pom（packaging=pom，modules：packages/genui-java-sdk、examples/genui-service），`mvn -q package` 可一次构建两模块
- [x] 1.2 创建 `examples/genui-service` Maven 模块：Spring Boot 2.7.18 + Java 21，依赖 `com.huawei.cloudsop:genui-core`，application.yml（server.port=3001、CORS、LLM 环境变量映射）
- [x] 1.3 编写 `src/main/resources/swagger/genui-service.yaml`（Swagger 2.0）：GET/PUT /v1/contexts、POST /v1/prompts/assemble、POST /v1/generate（produces text/plain，描述标注手写实现与 Prompt Override debug-only）
- [x] 1.4 配置 swagger-codegen-maven-plugin（interfaceOnly，产物到 target/generated-sources，不入库），空实现编译通过

## 2. 注册与拼装端点

- [x] 2.1 application 层 GenerationAppService：DTO↔SDK 映射、GenerationSdk @Bean 装配；GenerationSdkException → HTTP 映射（名称碰撞 409，其余非法 400）
- [x] 2.2 实现 `GET /v1/contexts`（contextId、version、组件数、工具数摘要）与 `PUT /v1/contexts/{contextId}`（替换语义、碰撞 409），单测覆盖三个碰撞/替换场景
- [x] 2.3 实现 `POST /v1/prompts/assemble`（contextId 可选、Request Overlay、metadata 完整返回、overlay 工具碰撞 409），单测对齐 PromptGoldenTest 的拼装产物
- [x] 2.4 预制扩展种子：`resources/seed/*.json`（≥2 套，网络运维主题）+ 启动注册（ApplicationRunner）；重启后种子恢复的集成测试
- [x] 2.5 编写 `register-extension.http` REST 注册示例，照抄可成功注册并在 contexts/assemble 中观察到生效

## 3. 生成端点

- [x] 3.1 llm 层 LlmClient：JDK HttpClient 调 OpenAI 兼容 chat/completions，解析 SSE 转文本 chunk；HTTPS_PROXY/NO_PROXY（含后缀 bypass）；deepseek-v4* 关闭 thinking
- [x] 3.2 手写 GenerateController（StreamingResponseBody，text/plain chunked）：拼装→LLM→流式转发；空 prompt 400；Prompt Override 旁路（有值时绕过拼装）
- [x] 3.3 错误行为对齐 Node 版：流前失败 502 JSON、流中失败/finish_reason 非 stop 追加 `[ERROR: ...]` 尾巴；真机连一次 LLM 验证端到端流式

## 4. demo 前端切换

- [x] 4.1 `useGenerate.ts` 切到 `POST /v1/generate`（请求体加 contextId、dirty 时带 promptOverride），流式读取逻辑不变
- [x] 4.2 `App.tsx` 新增 Context ID 下拉（GET /v1/contexts）；prompt tab 默认值改为 POST /v1/prompts/assemble 拉取（随 dataModel/contextId 变化），dirty 逻辑保留
- [x] 4.3 删除 `server/` 目录与 express/cors/openai/https-proxy-agent 依赖及相关脚本、测试；前端测试相应更新

## 5. 文档与验收

- [x] 5.1 更新 demo README：JDK ≥ 21 + Maven 前置条件、启动顺序（先 GenUI Service 后 pnpm dev）、register-extension.http 用法
- [x] 5.2 端到端验收：起 Java 服务 + 前端，跑通「选预制扩展 → prompt tab 见扩展 → 生成渲染」与「编辑 prompt → Override 生效」两条链路

## 6. 工具执行通道（浏览器验证轮发现的语义空档后补）

- [x] 6.1 Swagger 增加 `POST /v1/tools/{toolName}/execute` + codegen；SeedToolExecutors mock 执行器（queryAlarms 按 severity 过滤、acknowledgeAlarm）；未知工具 404；ToolsApiTest 4 例
- [x] 6.2 demo Renderer 接 toolProvider（MCP 形态，转发到工具执行端点）
- [x] 6.3 浏览器 e2e 复测：Query 节点渲染出工具返回的真实告警行

## 7. 组件型扩展链路

- [x] 7.1 新增 seed `noe-biz-components`（AlarmBadge 组件契约 + 组件组），SeedRecoveryAfterRestartTest 同步
- [x] 7.2 demo `src/extensions.tsx`：defineComponent 实现 AlarmBadge + `dslLibrary.extend()` 不可变扩展，contextId→Library 注册表；App 的 Renderer/parser 随 context 切换扩展库；extensions 单测 3 例
- [x] 7.3 浏览器 e2e：选 noe-biz-components → prompt 含 AlarmBadge 契约 → 生成渲染出着色徽章；切回 base 组件失效、切回扩展恢复（隔离探针）
