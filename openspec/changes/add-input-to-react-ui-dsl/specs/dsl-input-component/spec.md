## ADDED Requirements

### Requirement: Input SHALL be a first-class component in the DSL library
DSL 库 SHALL 注册 `Input` 作为公开组件，可在 `Form` 字段的 `component` 中引用，也可在 `Stack` 等容器中作为独立组件出现。

#### Scenario: 库注册中包含 Input
- **WHEN** 调用 `createLibrary` 构造导出库
- **THEN** 组件列表包含 `Input`
- **AND** `Input` 的名称、props schema、description 通过 `defineComponent` 注册并暴露到 prompt 与 JSON schema 中

#### Scenario: Input 出现在 prompt 描述中
- **WHEN** 消费者读取库导出的 prompt 字符串
- **THEN** prompt 中包含 `Input` 组件签名
- **AND** 签名展示其 schema 字段名与类型

### Requirement: Input SHALL define a schema covering single-line text input semantics
`Input` 的 props schema MUST 覆盖以下字段，每个字段均为可选（无强制必填项）：

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| `placeholder` | `string` | 占位符文案，输入为空时展示 |
| `defaultValue` | `string \| number` | 初始默认值（非受控） |
| `disabled` | `boolean` | 是否禁用输入；禁用态下不可聚焦、不可编辑 |
| `readOnly` | `boolean` | 是否只读；可聚焦但不可编辑 |
| `size` | `"small" \| "medium" \| "large"` | 控件尺寸；默认值为 `medium` |
| `hasError` | `boolean` | 是否处于错误态，用于在校验失败时呈现错误视觉 |
| `type` | `"text" \| "password" \| "email" \| "number" \| "tel" \| "url" \| "search"` | 输入类型；默认值为 `text` |
| `maxLength` | `number` | 允许输入的最大字符数 |

schema 不包含事件回调、`value`（受控值）、`className`、`style` 等渲染层细节。

#### Scenario: Schema 包含全部声明字段且均为可选
- **WHEN** 消费者读取 `Input` 的 JSON schema
- **THEN** schema 暴露字段 `placeholder`、`defaultValue`、`disabled`、`readOnly`、`size`、`hasError`、`type`、`maxLength`
- **AND** 全部字段在 schema 中标记为可选
- **AND** 不暴露 `value`、`onChange`、`className`、`style`、`styles` 等非声明字段

#### Scenario: size 与 type 限定为枚举
- **WHEN** 消费者读取 schema 中 `size` 与 `type` 字段定义
- **THEN** `size` 的枚举值集合恰好为 `{small, medium, large}`
- **AND** `type` 的枚举值集合恰好为 `{text, password, email, number, tel, url, search}`

#### Scenario: defaultValue 接受字符串与数字
- **WHEN** DSL 中将 `Input` 的 `defaultValue` 设为字符串或数字
- **THEN** schema 校验通过
- **AND** 渲染层将其作为初始字符串值展示

### Requirement: Input SHALL render as a single-line editable text field
`Input` 渲染结果 MUST 是一个单行文本输入控件，支持键盘输入、聚焦、失焦，并按 schema 字段语义呈现外观与交互。

#### Scenario: 默认渲染
- **WHEN** 渲染未提供任何 props 的 `Input`
- **THEN** 输出为单行输入控件
- **AND** 尺寸视觉对应 `medium`
- **AND** 输入类型按 `text` 处理
- **AND** 输入控件占满其所在表单单元的水平空间

#### Scenario: 占位符与默认值
- **WHEN** 渲染时提供 `placeholder` 与 `defaultValue`
- **THEN** 控件初始值为 `defaultValue`
- **AND** 当控件清空后，`placeholder` 文案展示在空输入区域

#### Scenario: 禁用态
- **WHEN** 渲染时 `disabled` 为 `true`
- **THEN** 控件不可聚焦
- **AND** 控件不可编辑
- **AND** 控件以禁用视觉呈现

#### Scenario: 只读态
- **WHEN** 渲染时 `readOnly` 为 `true`
- **THEN** 控件可被聚焦
- **AND** 控件内容不可被编辑
- **AND** 控件保持非禁用视觉

#### Scenario: 尺寸映射
- **WHEN** 渲染时 `size` 分别为 `small`、`medium`、`large`
- **THEN** 渲染层将其映射到对应的紧凑、标准、宽松视觉尺寸
- **AND** 不要求消费者传入原始样式

#### Scenario: 错误态
- **WHEN** 渲染时 `hasError` 为 `true`
- **THEN** 控件以错误视觉呈现（错误色边框或等价语义信号）
- **AND** 控件仍可被聚焦与编辑（除非同时 `disabled`）

#### Scenario: 输入类型
- **WHEN** 渲染时 `type` 为 `password`
- **THEN** 输入字符被掩码显示
- **WHEN** 渲染时 `type` 为 `number`
- **THEN** 控件仅接受数字字符或数字相关的键盘行为
- **WHEN** 渲染时 `type` 为 `email`、`tel`、`url`、`search`
- **THEN** 控件分别按对应语义提示输入法或浏览器行为
- **WHEN** 渲染时 `type` 未提供或为 `text`
- **THEN** 控件作为普通文本输入

#### Scenario: 最大长度限制
- **WHEN** 渲染时 `maxLength` 为正整数 `n`
- **THEN** 用户至多能向控件输入 `n` 个字符
- **AND** 超过 `n` 的输入被拒绝或截断

### Requirement: Input SHALL integrate with the Form component as a field control
`Form` 字段的 `component` 字段 SHALL 接受 `Input` 作为子组件，并由 `Form` 负责标签、必填校验与表单布局，由 `Input` 负责输入框自身的视觉与交互。

#### Scenario: Form 字段使用 Input 作为控件
- **WHEN** `Form` 的某个字段在 `component` 中声明 `Input(...)`
- **THEN** 该字段渲染为一个带标签的表单项
- **AND** 标签内部嵌入 `Input` 渲染结果
- **AND** `Input` 自身的 `disabled`、`readOnly`、`size`、`hasError`、`type`、`maxLength`、`placeholder`、`defaultValue` 在表单项内生效

#### Scenario: 必填校验由 Form 控制
- **WHEN** `Form` 字段在 `rules` 中标记 `required: true` 且其 `component` 为 `Input`
- **THEN** `Form` 负责在提交或失焦时呈现必填校验提示
- **AND** `Input` 不引入额外的 `required` schema 字段
