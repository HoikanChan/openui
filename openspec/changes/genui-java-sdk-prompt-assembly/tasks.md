## 1. 前端 Contract 类型与 Library 扩展

- [x] 1.1 在 `packages/lang-core/src/library.ts` 定义 generation contract 相关类型：`GenerationContract`、`LibraryExtensionDefinition`，并覆盖 components、componentGroups、tools、examples、additionalRules、contractVersion 字段
- [x] 1.2 在 `Library` 接口上新增不可变 `extend(extension)` 方法，返回新的 Library 实例且不修改原 Library
- [x] 1.3 在 `createLibrary` 内实现 component 同名冲突校验，覆盖 base 与 extension 冲突、extension 内重复名称
- [x] 1.4 在 `createLibrary` 或 `extend` 内实现 componentGroups 引用校验，缺失组件时报出缺失名称
- [x] 1.5 复用并规范化现有 `Library.toSpec()` / CLI `--json-schema` 输出作为 generation contract 的组件契约来源，避免新增重复的 `toGenerationContract()` API
- [x] 1.6 在 `packages/react-lang/src/library.ts` 透出 React Library 的 `extend` 类型，并保持 `toSpec()` 导出可用于 generation contract
- [x] 1.7 增加 `lang-core` 或 `react-lang` 单元测试，覆盖 immutable extend、同名冲突、componentGroups 缺失引用、`toSpec()` contract 导出不包含 renderer

## 2. DSLEngine Base Contract 构建产物

- [x] 2.1 为 `packages/react-ui-dsl` 增加或复用 base contract 导出脚本，从 `dslLibrary.toSpec()` 生成 JSON 文件
- [x] 2.2 在生成的 base contract JSON 中写入 `contractVersion`
- [x] 2.3 将 base contract 生成脚本接入合适的 package script，供 Java SDK build 或发布前执行
- [x] 2.4 增加测试或快照断言，确认 base contract 包含 `Stack` 等基础组件、componentGroups 和 contractVersion
- [x] 2.5 文档化 base contract 生成命令和 Java SDK 内置该产物的关系

## 3. Java SDK 模块与 DTO

- [x] 3.1 新建 `packages/genui-java-sdk` Maven 模块，使用 Java 21 和 JUnit 5
- [x] 3.2 按 `design.md` 的 Data Structures 定义 Java DTO：`GenerationContract`、`GenUIContextExtension`、`GenUIPromptRequest`、`GenUIPromptAssemblyResult`、`ComponentPromptSpec`、`ComponentGroup`、`ToolSpec`、`DataModelSpec`
- [x] 3.3 将前端生成的 base contract JSON 放入 Java SDK resources，并实现默认加载器
- [x] 3.4 实现 `GenerationSdk.create()` 主入口，默认加载内置 base contract
- [x] 3.5 为测试提供可覆盖 base contract 的 builder 或 factory 入口，但主路径不要求调用方传 base contract

## 4. Java SDK 注册与上下文合并

- [x] 4.1 实现 `register(GenUIContextExtension extension)`，按 `extension.contextId` 存储扩展 contract
- [x] 4.2 实现重复注册同一个 contextId 时完整替换旧 extension
- [x] 4.3 实现 context 隔离，组装 ctxA 时不读取 ctxB 的 extension 内容
- [x] 4.4 实现注册期 component 冲突校验，extension component 不能和 base component 同名，extension 内不能重复
- [x] 4.5 实现 componentGroups 引用校验，注册失败时返回或抛出包含缺失组件名的错误
- [x] 4.6 增加 Java 单元测试覆盖注册、替换、隔离、component 冲突和 group 引用校验

## 5. Java Prompt Assembly

- [x] 5.1 在 Java SDK 中实现 prompt assembler，镜像 `packages/lang-core/src/parser/prompt.ts` 的核心 section：preamble、syntax rules、component signatures、data model、builtins、tools、streaming rules、examples、additionalRules
- [x] 5.2 实现 `assemblePrompt(GenUIPromptRequest request)`，按 `request.contextId` 读取 context extension，并与 base contract、request overlay、dataModel 合并生成 system prompt
- [x] 5.3 实现 request-scoped dynamic tools 合并，且不写回 registered context
- [x] 5.4 实现 request-scoped extraRules 追加，且不写回 registered context
- [x] 5.5 拒绝 request tool 与 base 或 registered tool 同名冲突
- [x] 5.6 `GenUIPromptAssemblyResult` 返回 prompt、base contract version、extension version 和 request tool names
- [x] 5.7 增加 Java 单元测试覆盖无 extension 组装、有 extension 组装、dataModel request-scoped、dynamic tools request-scoped、extraRules request-scoped、tool 冲突和 metadata

## 6. 对齐与验证

- [x] 6.1 增加 TypeScript 到 Java 的 contract fixture，使用同一份 base contract 验证关键组件签名和分组在两端一致
- [x] 6.2 增加 Java prompt 输出 snippet 测试，确认包含 base 组件、extension 组件、request tools、extraRules 和 Data Model section
- [x] 6.3 运行前端相关单元测试，覆盖 Library 扩展和 contract 导出
- [x] 6.4 运行 Java SDK 单元测试
- [x] 6.5 运行 `openspec status --change genui-java-sdk-prompt-assembly`，确认 artifacts apply-ready

## 7. Java↔TS Prompt 逐字节对齐（消除漂移，见 design D9）

- [x] 7.1 在 `packages/lang-core` 导出 `getBuiltinsManifest()`：按 `[...Object.values(BUILTINS), ...Object.values(LAZY_BUILTIN_DEFS)]` 顺序产出 `{ signature, description, templateBuiltin }[]`，丢弃 `fn`；增加单测锁定顺序与 `Switch`/`Each`/`Render` 的 templateBuiltin 归属
- [x] 7.2 在 `generate-base-contract.mts` 的 `base-contract.json` 顶层写入全局 `builtins` 有序数组（不进 `GenUIContextExtension`），并更新 `base-contract.test.ts` 断言 manifest 存在且有序
- [x] 7.3 Java 新增 `BuiltinSpec` record；`GenerationContractLoader` 解析顶层 `builtins`，`GenerationSdk` 持有全局 manifest 并传入 `PromptAssembler`
- [x] 7.4 `PromptAssembler` 改用 manifest 稳定过滤渲染 `## Template Built-ins` / `## Data Built-ins`，逐字节对齐 `prompt.ts`（含两段固定散文：compose 规则、@Each/@Render 提醒）
- [x] 7.5 将 `PromptAssembler` 其余 section 逐字节移植 `prompt.ts`：preamble、syntax rules、component signatures（补 ActionExpression 与 `$binding<type>` 提示行）、data model、query、mutation、action、interactive filters + forms、data workflow、tools（`renderToolSignature` + 默认值 hint，弃用 `name(inputSchemaJSON)`）、streaming（`## Hoisting & Streaming (CRITICAL)`）、examples、edit mode、inline mode、important rules + final verification、additionalRules
- [x] 7.6 复刻 `prompt.ts` 的 flag 逻辑与 section 顺序：`toolCalls=spec.toolCalls??hasTools`、`bindings=spec.bindings??toolCalls`、`supportsExpressions=toolCalls||bindings`、dataBuiltins 包含条件 `supportsExpressions||hasRawDataModel`、filters 包含条件 `toolCalls&&bindings`、`usesActionExpression` 由组件签名含 `"ActionExpression"` 探测、顺序 mutation→action→filters→workflow→tools
- [x] 7.7 新增共享 fixture（合并后 PromptSpec JSON），覆盖分支：无 tools / toolCalls / bindings / dataModel / editMode / inlineMode / ActionExpression 组件 / 分组+ungrouped / examples / additionalRules
- [x] 7.8 TS 侧脚本对每个 fixture 跑 `generatePrompt` 写 `golden/*.txt` 并提交，提供 regen 命令；接入 package script
- [x] 7.9 Java golden 测试：读同一 fixture + golden，`PromptAssembler.assemble` 断言逐字节相等；并加 1-2 个 SDK 端 e2e fixture 确认 base+extension+request 合并正确喂入 assembler
- [x] 7.10 运行前端 + Java 测试，确认 builtins manifest 与 golden 逐字节对齐通过
