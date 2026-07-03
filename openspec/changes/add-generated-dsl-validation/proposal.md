## Why

当前 `genui-java-sdk` 已经收敛了 prompt 组装、LLM 调用、SSE delta 解析和 `openui-lang` 提取，但生成结果仍然缺少服务端可信校验：错误的 DSL 可能被流式发给 DSLEngine、写入缓存，或只能等前端 parser/runtime 暴露问题。随着 SmartCanvasService 需要向 DSLEngine 提供可渲染、可缓存、可修复的生成结果，SDK 需要在生成链路中引入 Generated DSL Validation，并把流式场景中的 gate、repair 和最终成功判定收敛在后端。

## What Changes

- 在 `packages/genui-java-sdk` 新增 Java 原生 `openui-lang` 验证模块，覆盖语法解析、组件契约校验、root 存在性、最终引用完整性和结构化诊断输出。
- 新增 Generated DSL Validation API，允许调用方独立校验一段完整 DSL，也允许 `GenUiGenerator.generate(...)` 在返回前校验并按配置触发 repair。
- 新增流式 statement gate：SDK 对 LLM delta 做 buffer，按完整 `openui-lang` statement 边界校验和放行；错误语句不流出 SDK。
- 新增 Fail-Fast Reask 修复策略：当完整流式语句确定无效时，SDK 内部取消当前 LLM stream，用 accepted prefix、invalid statement 和 validation issues 发起新的 repair-and-continue 请求，修复成功后继续只向前端输出正确 DSL。
- 保持前端流式协议精简：公开 `RenderStreamEnvelope` 只保留 `dataModel`、`dsl`、`error`、`done`；validation/repair 状态作为 SDK 内部状态、日志和最终结果 metadata，不外溢给前端。
- 新增 reflection repair prompt 构建器和 retry policy，最多执行受控次数的 statement repair 或 full repair；所有 repair 输出必须再次经过 validator。
- 用 TypeScript `packages/lang-core` parser 作为测试 oracle，建立 Java validator 与前端 parser 的 golden/fixture parity 覆盖，降低 parser drift 风险。
- 不引入 Node/Graal/ANTLR/tree-sitter 运行时依赖；Java SDK 生产默认使用 Java-native parser/validator。

## Capabilities

### New Capabilities
- `generated-dsl-validation`: 定义 Java SDK 对 AI 生成 `openui-lang` 的语法校验、组件契约校验、流式 statement gate、Fail-Fast Reask、reflection repair 和最终成功/缓存判定语义。

### Modified Capabilities
- `genui-service-generation`: 生成端点的流式输出调整为“SDK 只输出已接受 DSL”，坏语句在后端修复或失败，不要求前端参与校验和提交控制。
- `genui-service-rest-api`: REST/Swagger 契约需要说明 `dsl` chunk 已经过 SDK gate，`done` 仅表示流结束，`error` 表示后端无法修复或传输失败。

## Impact

- 影响 `packages/genui-java-sdk`：新增 `validation` / `parser` / `repair` / `stream` 相关子包，扩展 `GenUiGenerator` 同步和流式生成路径，新增配置项和测试。
- 影响 `packages/lang-core` 与 `packages/react-ui-dsl` 的测试协作：新增用于 Java validator parity 的 TS oracle fixtures 或生成脚本，但不改变前端运行时代码。
- 影响 GenUI Service / SmartCanvasService 消费方式：服务层需要把 SDK 的 `dataModel`、`dsl`、`error`、`done` envelope 原样序列化为 SSE，并避免缓存未通过 final validation 的 DSL。
- 影响 DSLEngine/前端消费语义：前端继续消费 `dataModel` 与 `dsl`；它不接收 validation/repair 细节，只需在 `error` 时展示失败状态。
- 不新增外部生产依赖；repair 复用现有 `LlmTransport` 和 `GenUiLlmConfig`。
