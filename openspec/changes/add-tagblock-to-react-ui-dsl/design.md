## Context

`react-ui-dsl` 目前有 `Tag` 组件（单个标签，schema：text/variant/icon/size），view 层基于 Ant Design 实现，渲染为 `<AntTag>` 元素，并暴露 `TagView` 供复用。DSL 库缺少批量标签容器 `TagBlock`。

目标：添加 `TagBlock` 组件，schema 与参考库 genui-lib 层完全一致（`tags: string[]`），view 层复用现有 `TagView`，不引入 view 子目录。

## Goals / Non-Goals

**Goals:**
- 新增 `TagBlock` 组件，schema 为 `{ tags: z.array(z.string()) }`
- 在 `index.tsx` 中 inline 渲染（不建 view/ 子目录）
- 复用 `Tag` 组件已导出的 `TagView` 渲染每个 tag item
- 在 `dslLibrary.tsx` 中注册
- 新增 stories 文件

**Non-Goals:**
- 不支持 per-tag variant/icon/size（需要此能力时直接用独立 Tag 组件）
- 不引入新的样式文件或 CSS 类
- 不修改 `react-ui-dsl` 以外的包

## Decisions

### 1. Schema：`tags: string[]`

**决定**：TagBlock schema 仅包含 `tags: z.array(z.string())`，不支持 per-tag 属性。

**理由**：与参考库 genui-lib 层的接口完全一致；TagBlock 定位为快捷批量展示，不需要每个 tag 有独立样式控制；需要差异化样式时 LLM 可以直接使用多个独立 `Tag`。

### 2. View：inline 在 index.tsx，复用 TagView

**决定**：不新建 view/ 子目录，直接在 `index.tsx` 的 component 函数中渲染 flex 容器 + 多个 `<TagView text={tag} />`。

**理由**：TagBlock 逻辑极简（遍历 string[] → 渲染 TagView），无需独立 view 层；减少文件数量，降低维护成本；`TagView` 已从 `Tag` 组件目录导出，可直接复用。

**替代方案**：建立独立 view/ 目录（如 Card、Tag 的做法）。否决原因：TagBlock 无自定义渲染逻辑，view 目录只会引入空壳文件。

### 3. 容器样式：plain div + inline styles

**决定**：用 `display: flex; flexWrap: wrap; gap: 4px` 的 plain div 包裹所有 TagView。

**理由**：与参考库 tagBlock.scss 语义等价（flex-wrap + gap-s），不引入新的样式类或 Ant Design 依赖；inline style 足够简单，无需外部文件。

### 4. description 与参考库一致

**决定**：description 使用 `"tags is an array of strings"`，与参考库 genui-lib 层完全一致。

**理由**：跨包接口层面全部一致；LLM 在两个库之间的迁移成本最低。

## Risks / Trade-offs

- **TagBlock 无 per-tag 样式控制** → 接受，这是设计决定；差异化场景用多个 Tag 表达
- **snapshot 需重新生成** → 若现有 e2e fixtures 涉及标签集合场景，重新生成后结果可能引用 TagBlock；需在 PR 中一并提交
