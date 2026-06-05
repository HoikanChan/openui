## Why

OpenUI 需要从前端实验链路走向通用的生成式 UI 服务：下游业务可以注册自己的扩展组件和工具，后端按 `contextId` 组装稳定的 system prompt，再由 SmartCanvasService 对外提供生成服务。

现有 `prompt.ts` 只解决通用 openui-lang prompt assembly，不承担业务上下文注册、上下文隔离、请求级动态工具和规则叠加，也无法让 Java 服务直接复用前端 DSLEngine 的基础组件契约。

## What Changes

- 新增 Java Generation SDK 能力：
  - SDK 内置 DSLEngine 基础组件 contract，由前端构建产物生成并随 SDK 发布。
  - 提供 `register(GenUIContextExtension)`，按 payload 内的 `contextId` 注册扩展组件、工具、规则和 examples。
  - 重复注册同一个 `contextId` 时替换该 context 的扩展 contract。
  - 提供 `assemblePrompt(GenUIPromptRequest)`，按 request 内的 `contextId` 读取 context extension，并结合 base contract、request overlay 和 data model 组装 prompt。
  - 请求级 overlay 支持动态 tools 和 `extraRules`。
  - component/tool 同名冲突在同一个最终 Generation Context 内失败，不做覆盖。
  - Java prompt 组装与前端 `generatePrompt()` 对同一份合并输入**逐字节一致**：builtins 文档来自前端导出的 manifest，其余 section 逐字节移植 `prompt.ts`，并以跨语言 golden 测试锁死。prompt 组装后续以 Java SDK 为权威，`prompt.ts` 计划弃用（本 change 不删）。
- 新增前端 Library 最小扩展能力：
  - `Library` 保持不可变。
  - 基础 `dslLibrary` 可以通过 `extend(...)` 生成新的扩展 Library。
  - 扩展 Library 可以通过现有 `toSpec()`/CLI JSON 输出能力导出 Java SDK 可消费的 model-visible contract JSON，并补充 contract version。
  - 前端构建流程生成 DSLEngine base contract JSON（含全局 builtins manifest），供 Java SDK 内置。
- SmartCanvasService 后续集成 Java Generation SDK，对外提供 register 和 generate 服务接口。本 change 只定义 SDK 与 contract 能力，不实现模型调用、SSE、鉴权或服务治理。

## Capabilities

### New Capabilities

- `genui-generation-context-sdk`: Java Generation SDK 支持按 `GenUIContextExtension.contextId` 注册扩展，并基于 `GenUIPromptRequest.contextId` 选择注册上下文和请求 overlay 组装 system prompt。
- `dsl-library-contract-extension`: 前端 Library 支持不可变扩展和 generation contract 导出，基础 DSLEngine contract 可在构建时生成给 Java SDK 使用。

### Modified Capabilities

- 无。

## Impact

- `packages/lang-core/src/library.ts`: 增加 Library 不可变扩展，并规范化现有 `toSpec()` 作为 generation contract 导出基础。
- `packages/react-lang/src/library.ts`: 透出 React Library 的扩展和 contract 导出 API。
- `packages/react-ui-dsl/src/genui-lib/dslLibrary.tsx`: 作为 DSLEngine 基础组件库 contract 的源头。
- `packages/lang-core/src/parser/builtins.ts`: 新增 `getBuiltinsManifest()` 导出，供 base contract 产物写入 builtins manifest。
- `packages/lang-core/src/parser/prompt.ts`: 标注后续以 Java SDK 为权威、计划弃用；作为 golden 测试的对齐 oracle。
- 新增或调整前端构建脚本：生成 base contract JSON（含 builtins manifest），并生成跨语言 golden 产物。
- 新增 Java SDK 模块或目录：提供 `GenUIContextExtension`、`GenUIPromptRequest`、注册表、prompt assembler 与 golden 对齐测试。
- `CONTEXT.md`: 已补充 Java Generation SDK、Generation Context、Context ID、Request Overlay、Contract Version 等术语边界。
