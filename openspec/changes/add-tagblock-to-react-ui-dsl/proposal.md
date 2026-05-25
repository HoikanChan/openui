## Why

`react-ui-dsl` 当前只注册了 `Tag` 单个标签组件，缺少批量标签容器 `TagBlock`。当 LLM 需要展示一组标签时（如文章分类、技能标签、状态标记集合），只能手动排列多个独立 `Tag`，没有语义化容器，DSL 冗长且难以维护。添加 `TagBlock` 后，LLM 可以用一个组件表达"标签集合"的概念，生成更简洁的 DSL。

## What Changes

- **新增** `TagBlock` 组件，schema：`tags: string[]`
- `dslLibrary` 中注册 `TagBlock`
- 新增 stories 文件
- 重新生成受影响的 e2e snapshot

## Capabilities

### New Capabilities

- `dsl-tag-block`：批量展示字符串标签的容器组件，对应 `TagBlock(["标签1", "标签2", "标签3"])`

## Impact

- `packages/react-ui-dsl/src/genui-lib/TagBlock/` — 新增目录（schema.ts、index.tsx、stories/）
- `packages/react-ui-dsl/src/genui-lib/dslLibrary.tsx` — 注册 TagBlock
- `packages/react-ui-dsl/src/__tests__/e2e/snapshots/` — 重新生成相关快照
