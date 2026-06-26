## Why

目前 `generateComponentSpecs` 作为前端编程 API 对外暴露，业务方必须手写一段 Node 脚本（`import { generateComponentSpecs }` + 手拼 `extensionId`/`version` 外层 + `writeFileSync`）才能把扩展组件导出成可注册的 Extension JSON。这要求业务方理解前端包 API、自行拼装外层结构，门槛高且易错。应当把这条链路收敛到 `@openuidev/cli`：业务方直接维护一个**完整的 extension 定义对象**，用一条命令把其中的真实组件编译成 renderer-free 规格，产出可注册的 Extension JSON。

## What Changes

- 新增 CLI 命令 `openui generate-extension <entry>`：读取入口文件导出的**完整 extension 对象**（`extensionId`、`version`、`components: DefinedComponent[]`，以及可选 `componentGroups`/`tools`/`examples`/`additionalRules`），产出可直接注册的 Extension 形 JSON。
  - CLI 把 `components`（真实组件，含 React 实现）**编译**成 `{ 组件名: { description, propsSchema } }`，其余字段**原样透传**。
  - flag：`--extension-id`、`--version`（均为对象值的**可选 override**，便于 CI 注入版本）、`--export <name>`、`-o/--out`。
  - `extensionId` 在对象与 flag 中都缺失时报错；`version` 都缺失时输出 `""`。
  - 实现复用公开 API：worker 对 extension 对象跑 `createLibrary(...).toSpec()` 完成组件编译，无需公开 `generateComponentSpecs`。
- **BREAKING**：从 `@cloudsop/openui-react-lang` 和 `@cloudsop/openui-lang-core` 的对外 barrel 移除 `generateComponentSpecs` 导出。该函数仅保留为 lang-core 包内部实现（继续供 `Library.toSpec()` 使用）。
- 文档同步：
  - [extending-openui.mdx](docs/content/docs/openui-lang/extending-openui.mdx) 的「从前端库导出组件 spec」小节由脚本写法改为 CLI 写法。
  - [docs/SYSTEM-DESIGN.md](docs/SYSTEM-DESIGN.md) 多处「DSLEngine 提供 `generateComponentSpecs`」改为「CLI 生成 Extension JSON」。

## Capabilities

### New Capabilities
- `cli-extension-export`: `@openuidev/cli` 从完整 extension 定义对象编译组件并导出可注册 Extension JSON 的命令行能力，含入口识别、组件编译、字段透传、`extensionId`/`version` 来源与 override、错误处理。

### Modified Capabilities
<!-- 无既有 spec 的需求级行为变化；generateComponentSpecs 的公开导出移除属包导出契约调整，由本 change 的 Impact 与 BREAKING 标注覆盖，不涉及既有 capability spec 的需求改写。 -->

## Impact

- 受影响代码：
  - `packages/openui-cli/src/index.ts`（注册 `generate-extension` 命令）
  - `packages/openui-cli/src/commands/generate-extension.ts`（由现有半成品 `generate-component-spec.ts` 改名而来：接线、flag 改名、向 worker 传参）
  - `packages/openui-cli/src/commands/generate-worker.ts`（新增 `--extension` 分支：识别 extension 对象、`createLibrary(...).toSpec()` 编译组件、组装输出）
  - `packages/react-lang/src/index.ts`、`packages/lang-core/src/index.ts`（移除 `generateComponentSpecs` 导出）
- 受影响 API/契约：`@cloudsop/openui-react-lang`、`@cloudsop/openui-react-ui-dsl`（barrel 透传）不再导出 `generateComponentSpecs`，属 **BREAKING**。
- 受影响运行时消费者：PIU 扩展 demo 在浏览器里动态注册生成契约时用过 `generateComponentSpecs`，迁移到等价公开 API `createLibrary({ components }).toSpec().components`（输出一致、运行时安全）：
  - `examples/react-ui-dsl-demo/src/piu-extension-demo/AlarmExtension.tsx`
  - `examples/react-ui-dsl-demo/docs/extension-guide.md`
- 受影响文档：`extending-openui.mdx`、`docs/SYSTEM-DESIGN.md`、`examples/react-ui-dsl-demo/docs/extension-guide.md`。
- 不受影响：lang-core 包内单测走相对路径 `import ... from "../library"`，`Library.toSpec()` 行为不变。
