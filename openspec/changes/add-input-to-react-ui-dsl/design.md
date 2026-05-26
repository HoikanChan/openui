## Context

`react-ui-dsl` 通过 `defineComponent` + zod schema 暴露一套 LLM 友好的 DSL 组件。表单类已有 `Form`（字段在 schema 中声明，view 层使用 AntD `Form` + `Form.Item`）与 `Select`（schema 声明 `options/defaultValue/allowClear`，view 层渲染 AntD `Select`）。`Input` 作为最基础的单行文本控件长期缺位，导致 `Form` 字段在文本输入场景没有可用 `component`。

目标：以 `Select` 的目录结构为模板，新增 `Input`，schema 字段覆盖占位符、默认值、禁用、只读、尺寸、错误态、输入类型、最大长度；view 层渲染单行文本输入控件并与 `Form.Item` 协同。

## Goals / Non-Goals

**Goals:**
- 新增 `Input` 组件目录，结构与 `Select` 对齐：`schema.ts` / `index.tsx` / `view/` / `stories/`
- schema 字段：`placeholder` / `defaultValue` / `disabled` / `readOnly` / `size` / `hasError` / `type` / `maxLength`，均为可选
- view 层将 schema 映射到 AntD `Input` / `Input.Password` 的对应属性
- 在 `dslLibrary.tsx` 注册 `Input`
- 新增独立 stories（默认、尺寸、错误、禁用、密码、数字）
- 在 `Form` 的 stories 中补充一个使用 `Input` 作为字段控件的示例

**Non-Goals:**
- 不引入受控 `value` / `onChange` schema 字段（避免 LLM 写出无法执行的事件回调）
- 不引入 `className` / `style` / `styles` 等渲染层逸出口
- 不实现自定义校验逻辑；必填校验由 `Form` 负责
- 不引入 `Input.TextArea`、`Input.Search`、`Input.Group` 等组合控件
- 不修改 `react-ui-dsl` 以外的包

## Decisions

### 1. Schema 字段集合

**决定**：schema 包含 8 个可选字段：`placeholder` / `defaultValue` / `disabled` / `readOnly` / `size` / `hasError` / `type` / `maxLength`。

**理由**：
- `placeholder` / `defaultValue` 是 LLM 描述输入框初始状态的必要语义
- `disabled` / `readOnly` 是表单字段最常见的两种非交互态，需要明确区分
- `size` / `hasError` 与表单视觉密切相关，需要由 DSL 直接控制
- `type` 决定输入语义（密码掩码、数字键盘等），属于"非样式"语义
- `maxLength` 是 LLM 表达字段约束最常用的属性
- 不暴露 `value` / `onChange`：DSL 当前无事件回调表达能力，受控值会让 LLM 写出无法运行的 DSL
- 不暴露 `className` / `style`：DSL 一贯封装样式细节，由渲染层负责视觉

**替代方案**：仅暴露 `placeholder` + `defaultValue`。否决原因：与已有 `Select` 等表单控件不对等，无法覆盖最常见的禁用、错误、密码等表单场景。

### 2. `defaultValue` 类型：`string | number`

**决定**：`defaultValue: z.union([z.string(), z.number()]).optional()`。

**理由**：与 `Select.defaultValue` 类型对齐；当 `type` 为 `number` 时数字默认值更自然；最终渲染层会统一转为字符串传给底层 input 元素。

### 3. `size` 默认值：`medium`

**决定**：`size: z.enum(["small", "medium", "large"]).optional()`，未提供时按 `medium` 渲染。

**理由**：与项目内其他输入类组件的视觉默认尺寸一致；底层 AntD `size` 取值 `small | middle | large` 需要在 view 层做 `medium → middle` 的映射。

### 4. `type` 枚举范围

**决定**：限定为 `text` / `password` / `email` / `number` / `tel` / `url` / `search`。

**理由**：覆盖单行文本输入的全部语义；排除 `file` / `checkbox` / `radio` / `range` 等非单行文本场景（这些应由其他组件表达）。

### 5. 目录结构：与 `Select` 对齐

**决定**：
```
Input/
  schema.ts         # InputSchema
  index.tsx         # defineComponent('Input', ...)
  view/
    index.tsx       # re-export
    antd.tsx        # InputView(props)
    types.ts        # InputViewProps
  stories/
    index.stories.tsx
```

**理由**：与 `Select` 结构一致，便于维护与代码评审；view 层独立有利于未来切换底层实现。

**替代方案**：复用 `TagBlock` 那种 inline 渲染。否决原因：Input 渲染需要做 `size` 映射、`type=password` 时切换到 `Input.Password`，逻辑超过单行渲染的复杂度。

### 6. view 层渲染：分发到 AntD `Input` / `Input.Password`

**决定**：
- `type === "password"` 时渲染 `<AntInput.Password>`
- 其余 `type` 渲染 `<AntInput>` 并将 `type` 透传到 `htmlType` 或等价属性
- `size` 做 `medium → middle` 映射，其余直传
- `hasError` 为 `true` 时设置 AntD `status="error"`
- 控件默认 `width: 100%`，与 `Select` view 行为一致

**理由**：AntD `Input.Password` 内置可见性切换按钮；其他 type 通过原生 input type 即可表达；`size` 命名差异属于底层 API 名称差异，统一在 view 层屏蔽对 DSL 用户透明。

### 7. 必填语义由 `Form` 控制

**决定**：`Input` schema 不暴露 `required` 字段。`Form` 字段已有 `rules: [{ required: true }]` 表达，重复暴露会导致校验来源不唯一。

**理由**：保持 `Form` 是必填校验的唯一来源；避免 LLM 在两个层级同时写 `required` 造成行为冲突。

### 8. description 文案

**决定**：`description = "Single-line text input control"`。

**理由**：用一句话明确组件用途；不引用其他包名或上游库，保持 DSL 自描述。

## Risks / Trade-offs

- **不支持受控 `value`** → 接受。DSL 没有事件回调系统，受控模式无意义；现有 `Form` + `defaultValue` 已足够表达初始值。
- **不支持自定义样式** → 接受。与 `Select`、`Tag` 等其他 DSL 组件一致，样式由渲染层负责。
- **`type=number` 行为依赖浏览器** → 接受。`maxLength` 对 `type=number` 的浏览器行为可能不生效；属于 HTML 标准限制，不在本变更范围内特殊处理。
- **snapshot 需重新生成** → 若现有 e2e fixtures 涉及包含文本输入的表单场景，重新生成后结果会引用 `Input`；需在 PR 中一并提交。
