## 1. TagBlock 组件实现

- [ ] 1.1 新建 `packages/react-ui-dsl/src/genui-lib/TagBlock/schema.ts`，定义 `TagBlockSchema = z.object({ tags: z.array(z.string()) })`
- [ ] 1.2 新建 `packages/react-ui-dsl/src/genui-lib/TagBlock/index.tsx`，实现 `TagBlock` 组件（name、props、description、component），inline 渲染：将 tags 映射为多个 `<TagView text={tag} />`，包在 `display: flex; flexWrap: wrap; gap: 4px` 的 div 容器中；description 使用 `"tags is an array of strings"`

## 2. 库注册更新

- [ ] 2.1 在 `packages/react-ui-dsl/src/genui-lib/dslLibrary.tsx` 中导入并注册 `TagBlock`

## 3. Stories

- [ ] 3.1 新建 `packages/react-ui-dsl/src/genui-lib/TagBlock/stories/index.stories.tsx`，包含基础展示 story（展示 3-5 个字符串标签）和空数组 story

## 4. Snapshot 重新生成

- [ ] 4.1 运行 `pnpm test:e2e:regen`（在 `packages/react-ui-dsl` 目录下），重新生成受影响的 e2e 快照

## 5. 验证

- [ ] 5.1 运行 `pnpm test` 确认所有单元测试通过
- [ ] 5.2 确认 TypeScript 编译无报错（`pnpm --filter @openuidev/react-ui-dsl run build`）
