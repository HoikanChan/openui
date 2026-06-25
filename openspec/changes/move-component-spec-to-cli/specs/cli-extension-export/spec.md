## ADDED Requirements

### Requirement: 从完整 extension 对象编译并导出 Extension JSON

CLI SHALL 提供 `generate-extension` 命令，读取入口文件导出的完整 extension 对象（含 `components: DefinedComponent[]`，可选 `extensionId`/`version`/`componentGroups`/`tools`/`examples`/`additionalRules`），把 `components` 编译成「组件名 → `{ description, propsSchema }`」对象，产出可直接注册的 Extension 形 JSON。输出 MUST 包含 `extensionId`、`version`、`components` 三个顶层字段。

#### Scenario: 编译完整 extension 对象

- **WHEN** 入口导出 `{ extensionId: "team-a-billing", version: "1.0.0", components: [BillingCard] }` 且执行 `openui generate-extension <entry>`
- **THEN** 输出 JSON 顶层为 `{ "extensionId": "team-a-billing", "version": "1.0.0", "components": { "BillingCard": { "description": ..., "propsSchema": ... } } }`
- **AND** `propsSchema.properties` 字段顺序与组件 Zod props 声明顺序一致

#### Scenario: 输出与旧脚本一致

- **WHEN** 对同一组扩展组件分别用旧 `generateComponentSpecs(...)` 脚本与新 CLI 命令导出
- **THEN** 两者产出的 `components` 内容（描述与 `propsSchema`）完全一致

### Requirement: 扩展字段透传

CLI SHALL 把入口对象中存在的 `componentGroups`、`tools`、`examples`、`additionalRules` 原样写入输出 JSON 的同名字段；缺省（不存在或为空）的字段 MUST 不出现在输出中。

#### Scenario: 携带 componentGroups

- **WHEN** 入口对象包含 `componentGroups: [{ name: "Billing", components: ["BillingCard"], notes: ["..."] }]`
- **THEN** 输出 JSON 含相同的 `componentGroups` 数组

#### Scenario: 无扩展字段时不输出空键

- **WHEN** 入口对象只有 `extensionId`/`version`/`components`，没有 `componentGroups`/`tools`/`examples`/`additionalRules`
- **THEN** 输出 JSON 不包含 `componentGroups`/`tools`/`examples`/`additionalRules` 键

### Requirement: extensionId 与 version 的来源与 override

`extensionId` 与 `version` SHALL 默认取自入口 extension 对象；当提供 `--extension-id` 或 `--version` flag 时，flag 值 MUST 覆盖对象中的对应值。`extensionId` 在对象与 flag 中都缺失时，CLI MUST 报错退出。`version` 在两者中都缺失时，CLI MUST 输出 `"version": ""`。

#### Scenario: flag 覆盖对象中的 version

- **WHEN** 入口对象含 `version: "1.0.0"` 且执行命令时传 `--version 2.3.4`
- **THEN** 输出 JSON 含 `"version": "2.3.4"`

#### Scenario: version 两处都缺失

- **WHEN** 入口对象无 `version` 且未提供 `--version`
- **THEN** 输出 JSON 含 `"version": ""`

#### Scenario: extensionId 两处都缺失

- **WHEN** 入口对象无 `extensionId` 且未提供 `--extension-id`
- **THEN** 命令报错并以非零状态码退出

### Requirement: 入口对象识别与选择

CLI SHALL 默认在入口模块的导出中识别 extension 对象（含数组型 `components`，且元素具备 `name` 与 `props`）。当存在多个候选或需显式指定时，调用方 SHALL 可用 `--export <name>` 选择导出名。无法识别时 CLI MUST 报错并列出可用导出名，提示使用 `--export`。

#### Scenario: 显式指定导出名

- **WHEN** 入口文件有多个导出且执行 `--export billingExtension`
- **THEN** CLI 使用名为 `billingExtension` 的导出作为 extension 对象

#### Scenario: 未找到 extension 对象

- **WHEN** 入口文件未导出任何符合特征的 extension 对象
- **THEN** CLI 报错，列出可用导出名，并提示使用 `--export`

### Requirement: 输出目标

CLI SHALL 在提供 `-o/--out <file>` 时把 JSON 写入该文件（必要时创建父目录），否则写入 stdout。

#### Scenario: 写入文件

- **WHEN** 执行命令时提供 `-o dist/billing-extension.json`
- **THEN** CLI 在该路径写入 Extension JSON 并创建缺失的父目录

#### Scenario: 写入 stdout

- **WHEN** 执行命令时不提供 `-o/--out`
- **THEN** CLI 把 Extension JSON 打印到 stdout

### Requirement: 必填先于可选的校验

CLI 编译组件时 SHALL 校验每个组件的必填 props 排在可选 props 之前（对应 openui-lang 位置参数顺序），违反时 MUST 导出失败并指明出错的组件与属性。

#### Scenario: 可选 props 排在必填之前

- **WHEN** 某组件的 props 中存在可选属性排在必填属性之前
- **THEN** CLI 导出失败并报告该组件名与属性名

## REMOVED Requirements

### Requirement: 公开导出 generateComponentSpecs 编程 API

**Reason**: 导出 Extension 组件规格的对外路径收敛到 `@openuidev/cli` 的 `generate-extension` 命令，不再以前端编程 API 暴露。`generateComponentSpecs` 仅保留为 lang-core 包内部实现，继续供 `Library.toSpec()` 使用。

**Migration**: 改用 `openui generate-extension <entry> -o <out>`，入口文件导出完整 extension 对象（`extensionId`/`version`/`components` 等；同一对象可传给 `dslLibrary.extend(...)` 用于前端渲染）。
