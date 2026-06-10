## ADDED Requirements

### Requirement: Demo generates through GenUI Service
demo 前端 SHALL 直连 GenUI Service 的 `POST /v1/generate` 完成生成：请求体携带 prompt、可选 dataModel、可选 contextId；继续以裸 chunked 文本流方式读取并逐段渲染 `openui-lang`。demo SHALL 不再拥有自己的 Node 生成后端。

#### Scenario: 前端直连 Java 服务生成
- **WHEN** 用户在 demo 中点击 Generate
- **THEN** `useGenerate` 调用 GenUI Service 的 `/v1/generate` 并流式渲染返回文本
- **AND** 仓库中不存在 `examples/react-ui-dsl-demo/server/` 目录

### Requirement: Demo prompt tab reflects service-assembled prompt
demo 的 prompt tab 默认内容 SHALL 来自 GenUI Service 的 `POST /v1/prompts/assemble`（随当前 dataModel 与所选 contextId 变化），展示 Java SDK 的拼装产物；用户编辑后（dirty）SHALL 将编辑稿作为 Prompt Override 随生成请求发送，保留「改 prompt 做实验」玩法。

#### Scenario: 默认 prompt 来自服务拼装
- **WHEN** 用户未编辑 prompt tab 且修改了 dataModel 或 contextId
- **THEN** prompt tab 刷新为 assemble 端点返回的最新拼装产物

#### Scenario: 编辑稿作为 Prompt Override 发送
- **WHEN** 用户编辑过 prompt tab 后点击 Generate
- **THEN** 生成请求携带编辑稿作为 Prompt Override，LLM 收到的 system prompt 即编辑稿原文

### Requirement: Demo supports Generation Context selection
demo SHALL 提供 Context ID 选择控件，选项来自 `GET /v1/contexts`（含预制扩展）；选中后拼装与生成均使用该 Generation Context，未选中时仅用 base contract。

#### Scenario: 选择预制扩展后生效
- **WHEN** 用户在下拉中选择一套预制扩展的 contextId
- **THEN** prompt tab 的拼装产物立即包含该扩展的组件/工具描述
- **AND** 后续生成请求携带该 contextId

### Requirement: Demo executes tools through GenUI Service
demo 的 Renderer SHALL 配置 toolProvider,把生成 DSL 中 Query/Mutation 节点的工具调用转发到 GenUI Service 的工具执行端点,使工具型扩展的生成结果渲染出执行器返回的真实数据而非空示例数据。

#### Scenario: Query 节点渲染工具数据
- **WHEN** 生成的 DSL 含 `Query("queryAlarms", ...)` 且服务在运行
- **THEN** 预览表格渲染工具执行器返回的告警行

### Requirement: Demo pairs front-end library extensions with component-extension contexts
对于含自定义组件的 Generation Context,demo SHALL 通过 `dslLibrary.extend()` 注册与后端 Component Contract 同名同 props 的前端实现,并在选中该 context 时把扩展库同时用于 Renderer 与 parser;未选中时 SHALL 回落到 base 库,扩展组件不可渲染(上下文隔离)。

#### Scenario: 扩展组件端到端渲染
- **WHEN** 选中 `noe-biz-components` 并生成含 `AlarmBadge(...)` 的 DSL
- **THEN** 预览渲染出前端扩展实现的徽章组件

#### Scenario: 切回 base 上下文后扩展组件失效
- **WHEN** 同一份含 `AlarmBadge` 的 DSL 下把 Context 切回 base
- **THEN** AlarmBadge 不再渲染(基础库不认识该组件)

## MODIFIED Requirements

### Requirement: Demo setup documents real-library prerequisites
The demo documentation SHALL describe the dependency, build, and runtime prerequisites required to run the example against `@openuidev/react-ui-dsl` inside the workspace, including the GenUI Service backend (JDK ≥ 21, Maven) and the startup order between the Java service and the front-end dev server.

#### Scenario: Contributor follows documented setup
- **WHEN** a contributor reads `examples/react-ui-dsl-demo/README.md`
- **THEN** the README lists the required workspace dependency and any build or peer dependency prerequisites for `@openuidev/react-ui-dsl`
- **AND** the README states the JDK ≥ 21 and Maven prerequisites for `examples/genui-service`
- **AND** the instructions describe the startup order: start GenUI Service first, then the front-end dev server

## REMOVED Requirements

### Requirement: Demo server generates prompts from the same library contract
**Reason**: demo 的 Express server 退役，demo 不再拥有自己的生成后端；system prompt 的来源从 TS 侧 `dslLibrary.prompt()` 改为 GenUI Service 的拼装端点（Java SDK 拼装，与 TS 侧字节对齐）。
**Migration**: prompt 拼装由 `POST /v1/prompts/assemble` 提供（见 `genui-service-rest-api` 能力）；demo 前端按「Demo prompt tab reflects service-assembled prompt」要求获取展示。若 TS 与 Java 拼装产物出现字节偏差，以 Java 为准并回归 `PromptGoldenTest`。
