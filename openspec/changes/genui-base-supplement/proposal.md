# GenUI Base Supplement（宿主基础增补包）

## Why

`genui-java-sdk` 的基础组件库（base contract）是前端导出后打进 jar 的冻结资源，宿主业务无法向其中加入自己的组件。现有 `GenUIExtension` 只能按 `extensionId` 逐请求叠加且禁止与 base 同名碰撞，无法满足「公司内部组件全局可用、且可修正 base 组件的提示词文档」的诉求；`builder().baseContract(...)` 整体替换则只是测试后门，宿主用它必须整份复制维护 base contract，升级 SDK 时极易脱节。

## What Changes

- 新增 **GenUI Base Supplement** 机制：宿主在构建 `GenerationSdk` 时提供一份 JSON 增补包，**全局**（对所有请求、不依赖 extensionId）生效。
- 增补包能力边界：
  - `components`：追加新组件；同名组件**整体替换** base 的 spec（description 与 propsSchema 一起换）；不支持删除。
  - `componentGroups`：追加新组；同名 group 按 **components 并集**合并（notes 追加）。
  - `examples` / `additionalRules`：追加。
  - `tools`、`root`、`builtins`、`contractVersion` **不可**通过增补包修改。
- 输入形态：仅 JSON 文件（与 base-contract.json 同构的子集），SDK 提供 loader；不提供程序化拼装 API。单个 SDK 实例最多一份增补包。
- 合并发生在 `GenerationSdk` 构建期，产出 Effective Base Contract；下游（prompt 组装、`GenUIExtension` 碰撞校验）只见合并结果。
- 版本字段选填，不上报 `GenUIPromptAssemblyMetadata`。
- 无增补包时行为与现状**字节级一致**（`PromptGoldenTest` 不受影响）。
- 增补包 JSON 的生产链路（及与宿主前端渲染能力的对齐）属宿主责任，本仓库只定义格式并提供 Java 侧加载/合并/校验。

## Capabilities

### New Capabilities

- `sdk-base-supplement`: GenUI Base Supplement 的 JSON 格式、加载入口、合并语义（组件替换 / group 并集 / 示例与规则追加）、构建期校验与错误报告。

### Modified Capabilities

<!-- 无：GenUIExtension 的碰撞规则语义不变（仍是「与 base 同名即拒绝」），只是 base 的定义变为 Effective Base Contract；genui-service REST 层不在本次范围内。 -->

## Impact

- `packages/genui-java-sdk`：
  - 新增 `GenUIBaseSupplement` record 与 JSON loader；`GenerationSdk.Builder` 新增入口。
  - `GenerationSdk` 构造期新增合并步骤；现有校验（propsSchema、group 引用、extension 碰撞）改为针对 Effective Base Contract 执行。
  - README 新增增补包格式文档与使用说明。
- 不涉及 `packages/react-ui-dsl`、`lang-core`、`genui-eval-cli` 与 `examples/genui-service` 的代码变更。
- 术语已录入 `packages/genui-java-sdk/CONTEXT.md`（Base Contract / GenUI Base Supplement / Effective Base Contract / GenUI Extension）。
