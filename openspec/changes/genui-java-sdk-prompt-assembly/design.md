## Context

当前前端链路通过 `dslLibrary.prompt()` 调用 `lang-core` 的 `generatePrompt()`，把 openui-lang 语法、组件签名、builtins、data model、tools、examples 和 additional rules 组装成 system prompt。这个链路适合前端 demo 和 eval，但通用 GenUI 服务还需要两类能力：按业务 `contextId` 注册扩展组件和工具，以及在 Java 后端按注册上下文组装 prompt。

新的 Java Generation SDK 是后端能力内核，SmartCanvasService 后续只集成它并对外暴露服务接口。DSLEngine 仍然拥有基础组件库和渲染实现，Java SDK 不从 React/Zod 组件源码推导 schema，而是消费前端构建产物导出的 model-visible contract。

## Goals / Non-Goals

**Goals:**

- Java SDK 默认内置 DSLEngine base contract，并提供 `register(GenUIContextExtension)` 与 `assemblePrompt(GenUIPromptRequest)`；两个 DTO 都携带 `contextId`。
- `contextId` 是业务上下文隔离边界，重复注册同一个 `contextId` 替换该 context 的扩展 contract。
- `GenUIContextExtension` 只包含扩展组件、工具、组件分组、examples 和 additional rules，不包含 base contract。
- `GenUIPromptRequest` 只包含本次 prompt 组装的动态输入：data model、动态 tools、extraRules 和 prompt flags。
- 前端 `Library` 保持不可变，并提供最小 `extend(...)`；generation contract 导出优先复用现有 `toSpec()`/CLI JSON 输出能力。
- 前端构建阶段生成 DSLEngine base contract JSON，Java SDK 发布包内置该产物，并保留 contract version。

**Non-Goals:**

- 不实现 SmartCanvasService 的模型调用、SSE、鉴权、持久化、观测或降级治理。
- 不引入 request 级 `extraPrompt`。
- 不让 Java SDK 解析 React/Zod 组件源码。
- 不改 openui-lang 语法、parser、runtime 渲染语义。
- 不改变 `lang-core` prompt section 的既有组装顺序，除非 Java 端镜像实现时需要等价移植。

## Decisions

### D1: Java SDK 只消费已编译的 model-visible contract

`GenUIContextExtension` 和 DSLEngine base contract 都是已编译的 contract JSON。Java SDK 不承担组件 schema 反射和签名推导。

替代方案是在 Java SDK 中维护组件 schema 或从原始 schema 转换 prompt contract。拒绝原因是会让 DSLEngine 的渲染契约和 Java prompt 契约分叉，跨语言维护成本高。

### D2: DSLEngine base contract 随 Java SDK 默认内置

前端构建阶段输出 `base-contract.json`，Java SDK 将它作为 resources 打包，`GenerationSdk.create()` 默认加载。高级测试或私有发行版可以保留覆盖入口，但主路径不要求调用方显式传 base contract。

替代方案是 SmartCanvasService 启动时必须外部加载 base contract。拒绝原因是主路径集成成本更高，也更容易出现未加载或版本不一致。

### D3: 注册以 contextId 为替换粒度

`register(extension)` 以 `extension.contextId` 为完整替换粒度。再次注册同一个 `contextId` 时替换旧扩展 contract；不同 `contextId` 完全隔离。

替代方案是引入 `registrationId` 做同一 context 内多来源增量注册。P0 不采用，因为当前业务语义是每个业务上下文单独注册自己的扩展，先按 context 替换足够简单。

### D4: 冲突检查发生在最终 Generation Context 内

最终上下文为 base contract + context extension + request overlay。组件和工具同名时失败，不做覆盖。请求级动态 tool 也不能和 base 或已注册 tool 同名。

替代方案是同名覆盖。拒绝原因是模型看到的 tool/component contract 与实际执行或渲染实现容易错配，且排查困难。

### D5: Request Overlay 只支持动态 tools 和 extraRules

`GenUIPromptRequest` 通过 `contextId` 选择 Generation Context，并支持本次生成临时 tools 和 `extraRules`，不持久化到该 `contextId`。暂不支持 `extraPrompt`，业务意图仍由调用方作为 user message 传给模型调用层。

替代方案是允许请求直接追加 prompt 文本。拒绝原因是会混淆 system prompt 约束与用户意图，降低 prompt contract 的可审计性。

### D6: 前端 Library 扩展保持不可变

`library.extend(extension)` 返回新的 Library；原始 base library 不变。扩展后 Library 继续提供 runtime rendering、`prompt()`、`toSpec()` 和 `toJSONSchema()`。P0 不强制新增 `toGenerationContract()`；现有 `toSpec()` 已经是 PromptSpec 形态的 model-visible component contract，构建产物可以在其外层补充 `contractVersion`。

替代方案是 mutate 原始 Library。拒绝原因是会污染基础 DSLEngine SDK，影响多业务、多页面和测试隔离。

### D7: Java SDK P0 放在 monorepo 的 packages/genui-java-sdk

本 change 在 monorepo 内新增 `packages/genui-java-sdk` Maven 模块，先服务 SmartCanvasService 集成和本仓库内的 contract 对齐测试。后续如果需要独立发布，再把该模块拆到独立 Maven 仓库或配置发布流水线。

替代方案是立即拆独立仓库。拒绝原因是当前还需要和前端 base contract 构建产物紧密对齐，放在 monorepo 内更利于同步演进。

### D8: assemblePrompt 返回 prompt 与 metadata

Java SDK 的 `assemblePrompt` 返回 `GenUIPromptAssemblyResult`，包含 system prompt 字符串以及 base contract version、extension version、request tool names 等 metadata。这样 SmartCanvasService 后续可以直接把版本信息写入日志、响应 metadata 或缓存键。

替代方案是只返回字符串。拒绝原因是版本审计和缓存归因会被迫依赖外部日志拼接。

### D9: Java prompt 与 TypeScript 逐字节对齐，Java 成为权威

Java SDK 的 prompt 组装目标是与前端 `generatePrompt()` 对**同一份合并后输入逐字节一致**，避免后端 prompt 与前端 eval 校验的 prompt 漂移。落地分三部分：

1. **builtins 走构建产物**：`@Count`/`@Each`/`@Render` 等 builtin 文档来自 `lang-core` 运行时注册表（`BUILTINS` + `LAZY_BUILTIN_DEFS`），Java 无法手工同步。前端导出有序 builtins manifest 写入 `base-contract.json`，Java 消费后稳定过滤渲染 `## Template Built-ins` / `## Data Built-ins` 两段。manifest 不表达 eager/lazy（Java 不执行 builtin，仅渲染文档），只保留 `{ signature, description, templateBuiltin }` 与顺序。
2. **其余 section 由 Java 逐字节移植 `prompt.ts`**：preamble、syntax rules、component signatures（含 ActionExpression / `$binding` 提示行）、data model、query、mutation、action、interactive filters + forms、data workflow、tools（`renderToolSignature` + 默认值 hint）、streaming（`## Hoisting & Streaming (CRITICAL)`）、examples、edit/inline mode、important rules + final verification、additionalRules；并复刻 flag 逻辑与 section 顺序。
3. **跨语言 golden 测试**：对比缝定在合并后 PromptSpec / `PromptAssembler` 层。共享 fixture（PromptSpec JSON）覆盖各分支；TS 跑 `generatePrompt` 写 `golden/*.txt` 提交，Java 读同一 fixture + golden 断言逐字节相等。SDK 三件套（base + extension + request）合并逻辑另有单测覆盖，不在 golden 重复。

方向：P0 用 TS 作为对齐 oracle 证明等价，之后 prompt 组装以 **Java SDK 为权威**，`prompt.ts` 标记后续弃用（本 change 不删除 TS 路径）。

替代方案 A 是 Java 维持手写改写、仅做 `.contains()` 断言——拒绝，因为 builtins 注册表演进会静默漂移、后端与前端 prompt 不等价。替代方案 B 是 TS 预渲染整段文本作为产物供 Java 原样插入——拒绝，因为这样 Java 不是 prompt 真源，无法逐步弃用 TS。

## Data Structures

P0 的 Java DTO 按以下字段定义。字段名称保持和前端 PromptSpec / ToolSpec 语义一致，便于从 JSON contract 直接反序列化。

```java
public record GenerationContract(
    String contractVersion,
    String root,
    Map<String, ComponentPromptSpec> components,
    List<ComponentGroup> componentGroups,
    List<ToolSpec> tools,
    List<String> examples,
    List<String> additionalRules
) {}
```

`GenerationContract` 是前端导出的完整 model-visible contract。DSLEngine base contract 必须包含 `contractVersion` 和 `root`；extension contract 不直接使用该类型注册，因为 extension 不携带 base root。

```java
public record GenUIContextExtension(
    String contextId,
    String version,
    Map<String, ComponentPromptSpec> components,
    List<ComponentGroup> componentGroups,
    List<ToolSpec> tools,
    List<String> examples,
    List<String> additionalRules
) {}
```

`GenUIContextExtension` 是 `register(extension)` 的请求体，只包含下游扩展部分。`contextId` 是注册隔离边界；`version` 可为空，为空时 assemble metadata 不报告 extension version。

```java
public record GenUIPromptRequest(
    String contextId,
    DataModelSpec dataModel,
    List<ToolSpec> tools,
    List<String> extraRules,
    Boolean editMode,
    Boolean inlineMode,
    Boolean toolCalls,
    Boolean bindings
) {}
```

`GenUIPromptRequest` 是单次 prompt 组装的 request overlay，并通过 `contextId` 选择已注册的 Generation Context。`tools` 和 `extraRules` 只对本次 assemble 生效；不包含 `extraPrompt`。

```java
public record GenUIPromptAssemblyResult(
    String prompt,
    GenUIPromptAssemblyMetadata metadata
) {}

public record GenUIPromptAssemblyMetadata(
    String contextId,
    String baseContractVersion,
    String extensionVersion,
    List<String> registeredToolNames,
    List<String> requestToolNames
) {}
```

`GenUIPromptAssemblyResult` 是 Java SDK 的 assemble 返回值。SmartCanvasService 后续可以把 metadata 写入日志、响应 metadata 或缓存键。

```java
public record ComponentPromptSpec(
    String signature,
    String description
) {}

public record ComponentGroup(
    String name,
    List<String> components,
    List<String> notes
) {}

public record DataModelSpec(
    String description,
    Map<String, Object> raw
) {}

public record ToolSpec(
    String name,
    String description,
    Map<String, Object> inputSchema,
    Map<String, Object> outputSchema,
    ToolAnnotations annotations
) {}

public record ToolAnnotations(
    Boolean readOnlyHint,
    Boolean destructiveHint
) {}
```

这些低层 DTO 镜像现有 TypeScript `PromptSpec` 使用的结构。P0 不引入 Java 类型系统里的 schema builder，JSON Schema 字段以 `Map<String, Object>` 保存。

builtins manifest 是 `base-contract.json` 的全局顶层字段（不进 `GenUIContextExtension`），由前端 `getBuiltinsManifest()` 导出，Java 侧反序列化为有序列表：

```java
public record BuiltinSpec(
    String signature,
    String description,
    boolean templateBuiltin
) {}
```

导出顺序 = `[...Object.values(BUILTINS), ...Object.values(LAZY_BUILTIN_DEFS)]`，Java 按 `templateBuiltin` 稳定过滤即可逐字节复现 `prompt.ts` 的两段 builtin 顺序（Template 段 = `Switch` → `Each` → `Render`）。`fn` 在导出时丢弃。

## Risks / Trade-offs

- [Java prompt 组装与 TypeScript `generatePrompt()` 漂移] → 见 D9：builtins 走构建产物、其余 section 逐字节移植，并以跨语言 golden 测试（合并后 PromptSpec 层）锁死逐字节一致；仅 `.contains()` 断言不足以护栏。
- [builtins 注册表演进导致 Java 文档过期] → builtins 来自前端导出的 manifest，注册表变更时 manifest 与 golden 一并 regen，Java 不硬编码 builtin 签名。
- [只按 contextId 替换导致一个业务内部无法多来源增量注册] → P0 接受该限制；如后续需要，再引入 registrationId。
- [前端构建产物和 Java SDK 内置 contract 版本不一致] → base contract 必须包含 `contractVersion`，SDK 的 assemble 结果暴露使用到的版本。
- [request 级 tools 影响 prompt 缓存] → assemble 结果应包含 request overlay 摘要，缓存键至少区分 contextId、contractVersion 和 request tool names。
- [componentGroups 引用不存在组件] → register 和 front-end export 都必须校验分组引用，失败时给出缺失组件名。

## Migration Plan

1. 定义 shared contract JSON 结构：base contract、`GenUIContextExtension`、`GenUIPromptRequest` 对应字段，确保 extension 和 request DTO 都携带 `contextId`。
2. 在 `lang-core` Library 层新增 immutable `extend(...)`，并明确 `toSpec()`/CLI JSON 输出是 generation contract 的组件契约来源。
3. 在 `react-lang` 透出 React Library 扩展 API。
4. 增加前端构建脚本，从基础 `dslLibrary` 导出 `base-contract.json`，包含 `contractVersion`。
5. 新增 Java SDK 模块，内置 base contract resources，提供 register 与 assemblePrompt。
6. 移植或镜像 `prompt.ts` 的 prompt assembly 行为，确保 Java assemble 与 TypeScript prompt contract 对齐。
7. 增加单元测试覆盖注册替换、上下文隔离、冲突检查、request overlay、contract version 暴露。
8. 后续 SmartCanvasService 集成 SDK，对外包装 register 和 generate 接口。

Rollback: 若 Java SDK 集成遇到阻塞，保留前端 Library contract export，不启用 Java register/assemble 路径；现有前端 demo 和 eval 仍走 `dslLibrary.prompt()`。

## Open Questions

- ~~`contractVersion` 的格式采用 npm package version、独立 schema version，还是二者组合。~~ 已定：采用 `<pkg>@<version>` 形式（如 `react-ui-dsl@0.1.0`），由前端构建产物写入。
