## Why

genui-java-sdk 目前只是一个纯内存 Java 库，demo 后台的 system prompt 仍来自前端 TS 侧静态拼装，SDK 的「注册 + 动态拼装」能力没有任何可运行的消费方；同时公司内部需要一个可以整目录拷走仿照搭建的后端参考实现。把 SDK 封装为 REST 生成服务并接入 demo，一次解决「SDK 有用例」和「内部可复制」两个问题。

## What Changes

- 新增 `examples/genui-service`：Spring Boot 2.7.18（javax 体系）+ Java 21 的 REST 服务（GenUI Service），完整生成服务边界——Extension Registration、prompt 动态拼装、调 LLM 流式返回 `openui-lang`。
- API 契约以 Swagger 2.0 描述（`swagger/genui-service.yaml`），contexts / assemble 端点由 swagger-codegen 生成接口（interfaceOnly，产物不入库）；`/generate` 流式端点手写实现，仅在 spec 中作文档声明。
- 扁平资源风格 API：`GET/PUT /v1/contexts`、`POST /v1/prompts/assemble`、`POST /v1/generate`；contextId 为请求体可选字段，与 SDK `GenUIPromptRequest` 1:1 对齐；名称碰撞返回 409。
- `/generate` 提供 Prompt Override 调试旁路（整段替换拼装产物，debug-only），与 Request Overlay（仅追加 tools/extraRules）术语严格分离。
- 服务启动时种子注册多套预制扩展（网络运维主题），并附 `register-extension.http` 展示 REST 注册姿势。
- 仓库根新增聚合 pom（packaging=pom），一条 `mvn package` 构建 SDK 与服务两个模块。
- **BREAKING**（仅 demo 范围）：`examples/react-ui-dsl-demo` 的 Express server（`server/`）退役删除，前端直连 Java 服务；prompt tab 默认值改为从 assemble 端点拉取，新增 Context ID 选择。

## Capabilities

### New Capabilities
- `genui-service-rest-api`: GenUI Service 的注册与拼装 REST 能力——Swagger 2.0 契约、Context 列表/注册（替换语义、409 碰撞）、prompt 拼装端点（Request Overlay 支持）、启动种子扩展。
- `genui-service-generation`: GenUI Service 的生成端点行为——`text/plain` chunked 流式返回 `openui-lang`、LLM 调用行为与现有 Node 版对齐（代理、错误尾巴、finish_reason 处理）、Prompt Override 调试旁路。

### Modified Capabilities
- `react-ui-dsl-demo-integration`: 「Demo server 从 `dslLibrary.prompt()` 派生 system prompt」的要求改变——demo 不再有自己的 Node server，prompt 来源改为 GenUI Service 的 assemble 端点（Java SDK 拼装，与 TS 侧字节对齐）；前端新增 Context ID 选择。

## Impact

- 新增代码：`examples/genui-service/`（Java，web/application/llm/config 四层）、仓库根聚合 `pom.xml`。
- 删除代码：`examples/react-ui-dsl-demo/server/`（index.ts、systemPrompt.ts 及测试）及 express/cors/openai/https-proxy-agent 依赖。
- 修改代码：demo 前端 `useGenerate.ts`（URL 与请求体）、`App.tsx`（Context ID 下拉、prompt tab 拉取）、README。
- 依赖/环境：要求 JDK ≥ 21 与 Maven（SDK 硬约束）；LLM 环境变量沿用 `.env` 同名变量。
- 文档：设计定稿见 `docs/superpowers/plans/2026-06-10-genui-service-rest.md`；术语已入根 `CONTEXT.md`（GenUI Service、Prompt Override）。
