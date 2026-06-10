## Context

genui-java-sdk（`packages/genui-java-sdk`，Java 21，纯内存）已实现 Generation Context 注册与 byte-aligned prompt 拼装，但没有任何可运行消费方。demo（`examples/react-ui-dsl-demo`）的后台是一个 Express server：system prompt 由 TS 侧 `dslLibrary.prompt()` 静态产生，LLM 以 `text/plain` chunked 流式返回 `openui-lang`，前端裸读 body 流。公司内部体系为 Spring Boot 2.7 / javax + Swagger 2.0 + codegen，参考实现必须与之对齐才能整目录拷走复用。

完整决策过程与 grill 会话记录见 `docs/superpowers/plans/2026-06-10-genui-service-rest.md`；领域术语（GenUI Service、Prompt Override、Request Overlay、Contract Name Collision 等）见根 `CONTEXT.md`。

## Goals / Non-Goals

**Goals:**
- examples 提供 GenUI Service：SDK 注册 + 动态拼装 + LLM 流式生成的完整 REST 参考实现。
- Swagger 2.0 契约优先，codegen 生成接口，web/application/llm/config 简洁分层。
- 外部拓展能力端到端可见：预制扩展种子 + 前端 Context ID 选择 + REST 注册脚本。
- demo 前端直连 Java 服务，Express server 退役。

**Non-Goals:**
- 不做注册持久化（SDK 纯内存，重启丢失，调用方重注册——替换语义保证幂等）。
- 不做鉴权、多租户、限流等生产化能力（参考实现，公司内部落地时自行叠加）。
- 不改动 SDK 本身的拼装语义与 `packages/` 任何库代码。
- 不支持 SSE 协议（前端契约是裸 chunked 文本流）。

## Decisions

1. **完整生成服务，而非纯 SDK 包装**：`/generate` 包含 LLM 调用与流式返回，前端直连 Java——更接近真实 SmartCanvasService 契约，公司仿照时无需再拼第二个服务。备选「纯拼装服务 + Express 保留」被否：双后端维护成本高，且 SDK 的用例不完整。
2. **Spring Boot 2.7.18（javax）+ swagger-codegen，而非 Boot 3 + openapi-generator**：唯一理由是与公司内部体系对齐（参考实现的可拷贝性优先于技术新旧）。Java 21 是 SDK 硬约束，Boot 2.7.16+ 才支持 Java 21。若内部升级 Boot 3，仅改 codegen 配置，Swagger 契约不变。
3. **扁平资源风格 API，contextId 为请求体可选字段**：与 SDK `GenUIPromptRequest`（contextId 可空 = 仅 base contract）1:1 对齐，DTO 映射层最薄。备选「context 子资源风格」被否：需发明伪 contextId（如 `base`），与词汇表 Context ID 语义冲突。
4. **`/generate` 手写流式，绕过 codegen**：swagger-codegen 生成的接口签名（`ResponseEntity<String>`）无法表达 chunked 流式；该端点在 Swagger 中仅作文档声明（`produces: text/plain`），controller 手写 `StreamingResponseBody`。其余端点 codegen interfaceOnly，产物进 `target/generated-sources` 不入库。
5. **Prompt Override 调试旁路**：`/generate` 可选字段，整段替换拼装产物，保住 demo「改 prompt 做实验」玩法；与 Request Overlay（仅追加）术语严格分离，Swagger 描述标注 debug-only、生产调用方不应使用。备选「砍掉/映射为 extraRules」被否：实验环表达力不可替代。
6. **examples/genui-service + 根聚合 pom**：`packages/` 保持纯库语义；根 pom（packaging=pom）串 SDK 与服务，一条 `mvn package` 构建。备选「SDK 改多模块父工程」被否：重排现有结构且违反 CONTEXT-MAP 的 package=库边界。
7. **LLM 客户端用 JDK HttpClient**：解析 OpenAI 兼容 SSE 转文本 chunk，零额外依赖；代理（`HTTPS_PROXY`/`NO_PROXY` 含 bypass 判断）、`finish_reason != "stop"` 追加 `[ERROR: ...]` 尾巴、deepseek-v4* 关闭 thinking，均对齐现有 Node 版行为。

## Risks / Trade-offs

- [公司内部 JDK < 21 则 SDK 不可用] → 这是比 REST 更早生效的约束，README 与设计文档显著标注，落地前先确认运行时版本。
- [Boot 2.7 已 EOL，安全补丁停止] → 参考实现非生产部署；文档注明升级 Boot 3 的迁移路径（仅 codegen 配置变化）。
- [TS 与 Java 拼装产物出现字节偏差] → prompt tab 改从 Java assemble 端点拉取后以 Java 为准；偏差回归 `PromptGoldenTest` 修复。
- [demo 运行门槛升高（需 JDK + Maven）] → README 给出最短启动路径（两条命令）；Express 退役是已接受的代价。
- [纯内存注册重启丢失] → 启动种子机制保证 demo 开箱即用；`register-extension.http` 演示调用方重注册姿势。

## Migration Plan

1. 先落 Java 服务（骨架 → contexts/assemble → generate），全程不动 demo 前端，Express 照常可用。
2. 前端切换为最后一步（URL、Context ID 下拉、prompt tab 拉取），验证端到端后删除 `server/` 与相关依赖。
3. 回滚策略：前端切换是单 commit，revert 即回到 Express 路径；Java 服务为纯新增目录，无回滚负担。

## Open Questions

- 预制扩展的具体内容（几套、各含哪些组件/工具）在实施切片 2 时按网络运维主题具体化，不阻塞契约设计。
