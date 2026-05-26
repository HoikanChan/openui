## Why

`react-ui-dsl` 当前已注册 `Select` 作为表单字段控件，但缺少最基础的单行文本输入控件 `Input`。当 LLM 通过 `Form` 描述包含文本字段的表单（姓名、邮箱、关键字搜索等）时，无法表达「单行文本输入」语义，只能退化为 Select 或忽略该字段。补齐 `Input` 后，LLM 可以在 `Form` 的字段 `component` 中直接声明 `Input(...)`，与 `Select` 形成对等的表单控件能力。

## What Changes

- **新增** `Input` 组件，schema 涵盖：占位符、默认值、禁用、只读、尺寸（small / medium / large）、错误态、输入类型（text / password / email / number / tel / url / search）、最大长度
- 在 `dslLibrary` 中注册 `Input`
- 新增 stories 文件，覆盖默认、尺寸、错误态、禁用、密码、数字等典型用法
- 补充 `Form` 字段使用 `Input` 的示例 story
- 重新生成受影响的 e2e snapshot

## Capabilities

### New Capabilities

- `dsl-input-component`：DSL 中表达单行文本输入控件的能力，定义输入框的 schema、渲染语义与在 `Form` 字段中作为子组件的使用方式

## Impact

- `packages/react-ui-dsl/src/genui-lib/Input/` — 新增目录（schema.ts、index.tsx、view/、stories/）
- `packages/react-ui-dsl/src/genui-lib/dslLibrary.tsx` — 注册 `Input`
- `packages/react-ui-dsl/src/__tests__/e2e/snapshots/` — 重新生成相关快照
