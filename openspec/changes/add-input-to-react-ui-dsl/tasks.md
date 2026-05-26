## 1. Input schema

- [x] 1.1 新建 `packages/react-ui-dsl/src/genui-lib/Input/schema.ts`，定义 `InputSchema`：`placeholder?: string`、`defaultValue?: string | number`、`disabled?: boolean`、`readOnly?: boolean`、`size?: "small" | "medium" | "large"`、`hasError?: boolean`、`type?: "text" | "password" | "email" | "number" | "tel" | "url" | "search"`、`maxLength?: number`（全部可选）

## 2. Input view

- [x] 2.1 新建 `packages/react-ui-dsl/src/genui-lib/Input/view/types.ts`，定义 `InputViewProps`（与 schema 字段一一对应）
- [x] 2.2 新建 `packages/react-ui-dsl/src/genui-lib/Input/view/antd.tsx`，实现 `InputView`：当 `type === "password"` 时渲染 `AntInput.Password`，其余渲染 `AntInput`；`size` 做 `medium → middle` 映射；`hasError` 为 `true` 时设置 `status="error"`；默认 `width: 100%`
- [x] 2.3 新建 `packages/react-ui-dsl/src/genui-lib/Input/view/index.tsx`，re-export `antd.tsx` 与 `types.ts`

## 3. Input 组件入口

- [x] 3.1 新建 `packages/react-ui-dsl/src/genui-lib/Input/index.tsx`，调用 `defineComponent({ name: "Input", props: InputSchema, description: "Single-line text input control", component: ({ props }) => <InputView ... /> })`，将 schema 字段透传给 `InputView`

## 4. 库注册

- [x] 4.1 在 `packages/react-ui-dsl/src/genui-lib/dslLibrary.tsx` 中导入 `Input`
- [x] 4.2 将 `Input` 加入 `createLibrary({ components: [...] })` 数组（建议放在 `Select` 邻近位置，保持表单控件聚集）

## 5. Stories

- [x] 5.1 新建 `packages/react-ui-dsl/src/genui-lib/Input/stories/index.stories.tsx`，包含以下 stories：默认（仅 placeholder）、Sizes（small / medium / large 三个尺寸并排）、ErrorState（hasError=true）、Disabled（disabled=true）、ReadOnly（readOnly=true + defaultValue）、Password（type=password + defaultValue）、Number（type=number + maxLength）、WithDefaultValue
- [x] 5.2 在 `packages/react-ui-dsl/src/genui-lib/Form/stories/index.stories.tsx` 中追加一个 story：`Form` 包含两个字段，且字段的 `component` 为 `Input(...)`，其中一个字段标记 `required`

## 6. Snapshot 重新生成

- [x] 6.1 在 `packages/react-ui-dsl` 目录下运行 `pnpm test:e2e:regen`，重新生成受影响的 e2e 快照（仅 `form-contact` 受影响，已单独重生）
- [x] 6.2 人工抽检快照差异，确认 `Input` 出现的位置语义合理

## 7. 验证

- [x] 7.1 运行 `pnpm test` 确认所有单元测试通过（8 个失败项均为变更前已存在的 Stack/Separator/prompt-artifact 测试，与本变更无关；本变更新增的 Input/Form 测试全部通过）
- [x] 7.2 运行 `pnpm --filter @openuidev/react-ui-dsl run build` 确认 TypeScript 编译无报错
- [ ] 7.3 在 Storybook 中目视检查所有 Input stories 与含 Input 的 Form story 渲染正常（需用户手动执行 `pnpm storybook`）
