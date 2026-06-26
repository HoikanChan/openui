## Context

`generateComponentSpecs` 当前定义在 [packages/lang-core/src/library.ts](packages/lang-core/src/library.ts) 并从 lang-core / react-lang / react-ui-dsl 的 barrel 对外导出。它同时被 `Library.toSpec()` 内部调用（[library.ts:572](packages/lang-core/src/library.ts#L572)），因此**不能删除**，只能撤掉对外导出。

`@openuidev/cli` 已存在一个**未接线、未提交**的半成品命令 [generate-component-spec.ts](packages/openui-cli/src/commands/generate-component-spec.ts)：它没注册进 [index.ts](packages/openui-cli/src/index.ts)，且它向 worker 传的 `--component-spec` / `--generation-id` 在 [generate-worker.ts](packages/openui-cli/src/commands/generate-worker.ts) 里**没有实现**。CLI 包自身不依赖 lang-core/react-lang，worker 通过 esbuild 打包用户入口文件并在用户 cwd 下求值。

经过澄清，目标是让业务方维护一个**完整的 extension 定义对象**，用一条 CLI 命令把其中的真实组件编译成 renderer-free 规格，产出可注册的 Extension JSON，取代手写脚本。

## Goals / Non-Goals

**Goals:**
- 新增 `openui generate-extension` 命令，输入完整 extension 对象，输出可直接注册的 Extension 形 JSON。
- 把 `components`（`DefinedComponent[]`，含 React 实现）编译成 `{ 组件名: { description, propsSchema } }`，`extensionId`/`version`/`componentGroups`/`tools`/`examples`/`additionalRules` 原样透传。
- 从对外 barrel 移除 `generateComponentSpecs`，使 CLI 成为唯一对外路径。
- 文档（extending-openui.mdx、SYSTEM-DESIGN.md）与新路径一致。

**Non-Goals:**
- 不删除 lang-core 内部的 `generateComponentSpecs` 实现（`toSpec` 仍需它）。
- 不做全局术语重命名（CONTEXT.md 中 "Generation ID" → "Extension ID" 另案处理）。
- 不引入 base 组件差集逻辑（入口即扩展，本就不含 base）。
- 不改变 `Library.toSpec()` 的现有行为与输出。

## Decisions

### 决策 1：入口导出「完整 extension 对象」
入口文件 export 一个完整 extension 对象 `{ extensionId, version, components: DefinedComponent[], componentGroups?, tools?, examples?, additionalRules? }`。它与输出 extension 的唯一差别在于 `components`：入口是 `DefinedComponent[]`（带 React 实现），输出是 `{ 组件名: { description, propsSchema } }`。CLI 的职责就是编译这个差别，其余字段透传。
- **理由**：单一来源——对象本身就是 extension，业务方维护一处即可；同一对象还能传给 `dslLibrary.extend(...)` 用于前端渲染，前后端同源。
- **备选**：(a) 入口只导出「扩展定义对象」（不含 id/version），id/version 走 flag —— 把发布元数据与组件定义割裂，对象不再是完整 extension；(b) 导出扩展后的 Library 再减 base —— CLI 耦合 base、易碎。均否决。

### 决策 2：worker 用 `createLibrary(...).toSpec()` 编译组件，不公开 `generateComponentSpecs`
worker 的 `--extension` 分支：识别 extension 对象 → `createLibrary({ components, componentGroups, tools, examples, additionalRules })` → `.toSpec()` 得到编译后的 `components` 与透传字段 → 再叠加 `extensionId`/`version` 组装最终 JSON。
- **理由**：`toSpec()` 内部本就调用 `generateComponentSpecs`，产出的 specs 与手写脚本完全一致；这样能安全撤掉公开导出且**零逻辑重复**。`createLibrary`/`toSpec` 是稳定公开 API。注意 `extensionId`/`version` 是 extension 级字段、`createLibrary` 不感知，直接从对象/flag 取。
- **解析来源**：worker 用 esbuild 把用户入口与 `export { createLibrary } from "@cloudsop/openui-react-lang"` 一起 bundle（stdin stub），使组件的 `defineComponent` 与 `createLibrary` 共享**同一个 bundled zod 实例**——跨实例 zod introspection 易碎，故不另行 `require.resolve` 外部 react-lang。与现有 worker 在用户 cwd 下打包/求值的方式一致。
- **备选**：worker 自行重写 props→propsSchema 转换 —— 逻辑重复、易与 lang-core 漂移，否决。

### 决策 3：入口识别启发式 + `--export`
默认在模块导出里查找 extension 对象：含数组型 `components`、元素具备 `name` + `props`（DefinedComponent 特征）。命中多个或需指定时用 `--export <name>`。
- **理由**：与现有 `generate` 命令的 `findLibrary`/`--export` 体验一致。
- **风险兜底**：未命中时报错并列出可用导出名，提示用 `--export`。

### 决策 4：`extensionId`/`version` 以对象为源，flag 作可选 override
`extensionId`/`version` 默认取自 extension 对象；`--extension-id`/`--version` 传入时覆盖对象值。`extensionId` 在对象与 flag 中都缺失 → 报错；`version` 都缺失 → 输出 `"version": ""`。
- **理由**：保持「对象即完整 extension」的单一来源心智，同时让发布元数据（尤其 version）可在 CI 用 `--version $TAG` 注入，零额外负担。
- **命名**：flag 用 `--extension-id`（非旧 `--generation-id`），与迁移后的 `extensionId` 术语一致。

### 决策 5：命令名 `generate-extension`
新命令命名为 `generate-extension`，由现有半成品文件 `generate-component-spec.ts` 改名而来并注册进 index.ts。
- **理由**：输入是完整 extension、输出是可注册 extension JSON，命令做的是「生成 extension」；`generate-component-spec` 把它说小了。与现有 `generate` 命令命名风格一致。
- **备选**：保留 `generate-component-spec` —— 与「输入/输出都是 extension」的新定位不符，否决。

## Risks / Trade-offs

- [移除公开导出是 BREAKING，外部脚本/消费者会编译失败] → 在 proposal 与 changelog 标注 BREAKING；文档同步给出 CLI 等价用法；现仓内消费者仅文档与包内单测（走相对路径），影响可控。
- [worker 从用户项目解析 `@cloudsop/openui-react-lang` 失败（未安装/路径异常）] → 求解析失败时报清晰错误，提示在含 OpenUI 依赖的项目内运行。
- [入口识别启发式误判，多个候选导出] → 命中多个或零个时报错并提示 `--export`；文档示例统一用具名导出。
- [对象与 flag 同时给 id/version 时来源歧义] → 明确「flag 覆盖对象」并在 `--help` 与文档写清。
- [子命令 `--version` 与 commander 内置 `program.version()` 冲突,导致打印 CLI 版本号而非执行] → 把全局工具版本改用 `-V, --cli-version`(短旗 `-V` 保留),腾出 `--version` 给 `generate-extension` 的扩展版本 override。已用真实 CLI 运行回归验证。
- [SYSTEM-DESIGN.md 大量提及 `generateComponentSpecs`，遗漏会与实现不一致] → 改文档时全文检索逐处替换，作为 tasks 的显式验收项。

## Migration Plan

1. worker 实现 `--extension` 分支并改名/接线命令、改 flag。
2. 验证 CLI 在示例 extension 对象上产出与旧脚本一致的 JSON。
3. 移除 barrel 导出，修复包内单测的 import 路径（改相对路径）。
4. 更新两份文档。
5. 回滚策略：以上为相互独立的提交，若 barrel 移除引发外部问题，可单独 revert 导出移除而保留 CLI 命令（CLI 不依赖该导出）。
