# GenUI Service:genui-java-sdk REST 化设计

日期:2026-06-10 · 来源:grill-with-docs 会话 · 状态:已定稿,待实施

## 目标

1. examples 用上 genui-java-sdk:demo 后台的 prompt 从静态文件改为 SDK 动态拼装。
2. 外部拓展能力可见可玩:Extension Registration 在 demo 里有端到端效果。
3. 公司内部可整目录拷走仿照搭建:Swagger 2.0 契约优先 + codegen 接口 + 简洁分层。

## 决策记录(grill 会话定案,不另立 ADR)

| # | 决策 | 理由 / 备注 |
|---|---|---|
| 1 | **完整生成服务**:注册 + 拼装 + LLM 流式生成;demo 的 Express server 退役 | 更接近真实 SmartCanvasService;前端直连 Java |
| 2 | **Spring Boot 2.7.18 + swagger-codegen**(javax 体系) | 对齐公司内部框架体系;Boot 2.7 虽 EOL,但这是唯一选它的理由。**Java 21 是 SDK 硬约束**,Boot 2.7.16+ 才支持 Java 21。若内部升 Boot 3,只改 codegen 配置,契约不变 |
| 3 | **Prompt Override 调试旁路**:/generate 可选字段,整段替换拼装产物 | 保住 demo「改 prompt 做实验」玩法;术语与 Request Overlay 严格分离(已入 CONTEXT.md);Swagger 描述标注 debug-only |
| 4 | **examples/genui-service + 仓库根聚合 pom** | packages/ 保持纯库;根 pom(packaging=pom)串起 SDK 与服务,一条 mvn 命令构建 |
| 5 | **预制多套扩展 + 前端 Context ID 选择** | 启动种子注册(网络运维主题,如告警工具集/自定义业务组件);另附 register-extension.http 展示 REST 注册姿势 |
| 6 | **扁平资源风格 API**(contextId 为请求体可选字段) | 与 SDK 的 GenUIPromptRequest(contextId 可空 = 只用 base contract)1:1 对齐,映射层最薄 |

## API 契约(`swagger/genui-service.yaml`,Swagger 2.0,basePath `/v1`)

```
GET  /contexts
  → 200: [{ contextId, version, componentCount, toolCount }]

PUT  /contexts/{contextId}                    # Extension Registration(替换语义,幂等)
  body: { version, components{}, componentGroups[], tools[], examples[], additionalRules[] }
  → 200 注册/替换成功(返回摘要)
  → 409 Contract Name Collision(组件名或工具名碰撞)

POST /prompts/assemble
  body: { contextId?, dataModel?, tools[], extraRules[],     # Request Overlay
          editMode?, inlineMode?, toolCalls?, bindings? }
  → 200: { prompt, metadata{ contextId, baseContractVersion, extensionVersion,
                             registeredToolNames[], requestToolNames[] } }

POST /generate                                # 手写流式实现,spec 中仅文档声明
  produces: text/plain; charset=utf-8 (chunked)
  body: { prompt, contextId?, dataModel?, tools[], extraRules[],
          promptOverride? }                   # debug-only,绕过拼装
  → 200: openui-lang 文本流
```

codegen 范围:contexts / assemble 端点由 swagger-codegen-maven-plugin 生成接口(interfaceOnly,产物进 `target/generated-sources`,**不入库**);/generate 的 controller 手写(StreamingResponseBody),生成的接口签名无法表达流式,仅作契约文档。

## 模块与分层

```
examples/genui-service/
├── pom.xml                                  # 依赖 genui-core、spring-boot-starter-web、codegen 插件
├── register-extension.http                  # REST 注册姿势示例(公司同事照抄)
└── src/main/
    ├── resources/
    │   ├── swagger/genui-service.yaml       # 契约源头(codegen 输入)
    │   ├── seed/*.json                      # 预制扩展(2-3 套,网络运维主题)
    │   └── application.yml                  # server.port=3001、CORS、LLM 环境变量映射
    └── java/com/huawei/cloudsop/genui/service/
        ├── web/          # 实现 codegen 接口的 Controller + 手写 GenerateController
        ├── application/  # GenerationAppService:DTO↔SDK 映射、调 GenerationSdk / LlmClient
        ├── llm/          # LlmClient:JDK HttpClient 调 OpenAI 兼容接口,解析 SSE 转文本 chunk
        └── config/       # GenerationSdk @Bean(启动种子注册)、CORS、异常→HTTP 映射
```

分层规则:web 只做 DTO 转换与状态码;application 持有用例编排;SDK 即领域层,服务内不复制其校验逻辑。`GenerationSdkException` 映射:名称碰撞 → 409,其余非法输入 → 400。

## LLM 调用(对齐现 Node 版行为)

- 环境变量同 `.env`:`LLM_API_KEY` / `LLM_BASE_URL` / `LLM_MODEL` / `HTTPS_PROXY` / `NO_PROXY`(含 bypass 判断)。
- JDK HttpClient 解析 chat/completions SSE,只转发 delta.content 文本。
- `finish_reason != "stop"` 时按现有前端约定追加 `\n\n[ERROR: ...]` 文本尾巴;deepseek-v4* 关闭 thinking。

## 前端改动(examples/react-ui-dsl-demo)

- 删除 `server/`(index.ts、systemPrompt.ts 及其测试);package.json 移除 express/cors/openai/https-proxy-agent 与对应脚本。
- `useGenerate`:URL → `http://localhost:3001/v1/generate`,请求体带 contextId / promptOverride(仅 dirty 时)。
- `App`:新增 Context ID 下拉(数据来自 `GET /v1/contexts`);prompt tab 默认值改为 `POST /v1/prompts/assemble` 拉取(替代本地 `dslLibrary.prompt()`,展示 Java 拼装产物);用户编辑后(dirty)作为 Prompt Override 发送——实验环保留。
- Renderer / parser 路径不变。byte-aligned 如有偏差,以 Java 为准并回归 `PromptGoldenTest`。

## 构建与运行

```
mvn -q package                                   # 根聚合 pom,两模块一次构建
mvn -pl examples/genui-service spring-boot:run   # 起服务(3001)
pnpm --filter react-ui-dsl-demo dev              # 起前端
```

## 风险

- 公司内部 JDK 必须 ≥ 21,否则 SDK 本身不可用——这是比 REST 服务更早生效的约束。
- 注册纯内存,重启丢失:调用方启动时重注册(替换语义保证幂等);种子机制保证 demo 开箱即有。

## 实施切片(建议顺序,每片可独立验证)

1. 根聚合 pom + genui-service 骨架 + swagger yaml + codegen 跑通(空实现编译过)。
2. contexts / assemble 端点 + 种子扩展 + .http 脚本(纯 JSON,可单测,golden 对齐)。
3. /generate 手写流式 + LlmClient(行为对齐 Node 版,真机连一次 LLM)。
4. 前端切换(useGenerate / Context ID 选择 / prompt tab 拉取)+ 删除 Express。
5. README 更新 + 端到端跑通 demo。
