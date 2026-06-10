## ADDED Requirements

### Requirement: API contract is Swagger 2.0 contract-first
GenUI Service 的 REST 契约 SHALL 由仓库内的 Swagger 2.0 文档（`swagger/genui-service.yaml`）描述；contexts 与 assemble 端点的 Java 接口 SHALL 在构建时由 codegen 从该文档生成（interfaceOnly，产物不提交入库），手写 controller 实现生成的接口。

#### Scenario: 构建时从契约生成接口
- **WHEN** 执行 `mvn package` 构建 `examples/genui-service`
- **THEN** codegen 从 `swagger/genui-service.yaml` 生成 contexts / assemble 端点的接口与 DTO 到 `target/generated-sources`
- **AND** 仓库中不存在提交的生成代码

#### Scenario: 契约即文档
- **WHEN** 调用方查阅 `swagger/genui-service.yaml`
- **THEN** 文档完整描述全部端点（含手写实现的 `/generate`），可直接用于公司内部 codegen 复制

### Requirement: Service lists registered Generation Contexts
GenUI Service SHALL 通过 `GET /v1/contexts` 返回当前已注册的全部 Generation Context 摘要（contextId、version、组件数、工具数），供前端枚举选择。

#### Scenario: 列出种子注册的 contexts
- **WHEN** 服务启动完成后调用 `GET /v1/contexts`
- **THEN** 响应包含全部预制扩展的 contextId 与版本信息

### Requirement: Extension Registration via REST with replace semantics
GenUI Service SHALL 通过 `PUT /v1/contexts/{contextId}` 接受 Extension Registration（components、componentGroups、tools、examples、additionalRules、version）；同一 contextId 重复注册 SHALL 整体替换原有扩展契约（幂等）；与 base contract 或扩展自身发生组件名/工具名碰撞 SHALL 返回 409，其余非法输入返回 400。

#### Scenario: 注册新扩展
- **WHEN** 调用 `PUT /v1/contexts/team-a` 提交合法扩展契约
- **THEN** 返回 200 与注册摘要
- **AND** 后续 `GET /v1/contexts` 包含 `team-a`

#### Scenario: 重复注册替换
- **WHEN** 对已存在的 `team-a` 再次 PUT 新版本扩展
- **THEN** 原扩展被整体替换，`GET /v1/contexts` 中 `team-a` 的 version 更新

#### Scenario: 名称碰撞被拒绝
- **WHEN** 注册的扩展包含与 base contract 同名的组件或工具
- **THEN** 返回 409 Contract Name Collision，且该 contextId 未被注册或修改

### Requirement: Prompt assembly endpoint with Request Overlay
GenUI Service SHALL 通过 `POST /v1/prompts/assemble` 暴露 SDK 拼装能力：contextId 为可选字段（缺省时仅用 base contract）；请求可携带 Request Overlay（一次性 tools 与 extraRules，不持久化）；响应 SHALL 包含拼装产物 prompt 与 metadata（contextId、baseContractVersion、extensionVersion、registeredToolNames、requestToolNames）。Overlay 工具名与已注册工具碰撞 SHALL 返回 409。

#### Scenario: 仅 base contract 拼装
- **WHEN** 调用 assemble 且不带 contextId
- **THEN** 返回的 prompt 仅含 base contract 内容，metadata.extensionVersion 为空

#### Scenario: 选择扩展 context 拼装
- **WHEN** 调用 assemble 且 contextId 指向已注册扩展
- **THEN** 返回的 prompt 含该扩展的组件与工具描述，metadata 标明扩展版本

#### Scenario: Overlay 工具名碰撞
- **WHEN** assemble 请求的 tools 中包含与已注册工具同名的工具
- **THEN** 返回 409，且不产生拼装结果

### Requirement: Preset Generation Contexts seeded at startup
GenUI Service SHALL 在启动时从打包资源中种子注册多套预制扩展（网络运维主题），保证 demo 开箱即可选择扩展 context；服务重启后种子 SHALL 重新注册（注册不持久化，调用方自行重注册）。

#### Scenario: 开箱即有预制扩展
- **WHEN** 以默认配置启动服务
- **THEN** `GET /v1/contexts` 至少返回两套预制扩展

### Requirement: Tool execution endpoint
GenUI Service SHALL 通过 `POST /v1/tools/{toolName}/execute` 执行工具并返回结果 JSON,作为生成 DSL 中 Query/Mutation 节点的服务端运行时;参考实现 SHALL 内置种子工具的 mock 执行器;未注册执行器的工具 SHALL 返回 404。

#### Scenario: 种子工具按入参执行
- **WHEN** 调用 `POST /v1/tools/queryAlarms/execute` 且入参含 severity
- **THEN** 返回按 severity 过滤的告警列表与 total

#### Scenario: 未知工具被拒绝
- **WHEN** 调用未注册执行器的工具名
- **THEN** 返回 404 与错误说明

#### Scenario: 重启后种子恢复
- **WHEN** 服务重启
- **THEN** 预制扩展重新可用，而运行期通过 REST 注册的扩展不再存在

### Requirement: Repository provides REST registration example
仓库 SHALL 在 `examples/genui-service` 下提供可执行的 REST 注册示例（`register-extension.http` 或等价 curl 脚本），展示外部调用方注册扩展的完整请求姿势。

#### Scenario: 照抄示例完成注册
- **WHEN** 使用者按示例文件内容向运行中的服务发起注册请求
- **THEN** 注册成功并能在 `GET /v1/contexts` 与 assemble 产物中观察到扩展生效
