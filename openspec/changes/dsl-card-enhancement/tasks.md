## 1. Schema 更新

- [ ] 1.1 替换 `packages/react-ui-dsl/src/genui-lib/Card/schema.ts`：删除 `tag`、`headerAlign`，新增 `variant`、`width`、`header`（含 title / subtitle / actions）
- [ ] 1.2 确认 Zod schema 类型导出正确，`CardSchema` 类型推断与新字段匹配

## 2. 组件实现

- [ ] 2.1 新建 `packages/react-ui-dsl/src/genui-lib/Card/card.module.css`，实现 variant（card / clear / sunk）、width（standard / full）、header 区域和 body 的全部样式类，使用 CSS 变量并提供 fallback
- [ ] 2.2 替换 `packages/react-ui-dsl/src/genui-lib/Card/index.tsx`：移除 `antd/Card` import，改用自实现 div + CSS Modules（`clsx` 拼接 variant / width 类名）
- [ ] 2.3 实现 header 区域渲染：title、subtitle 左侧堆叠，actions 右侧横排，通过 `renderNode` 渲染 actions 节点
- [ ] 2.4 确认 `props.style` 透传到根元素，允许消费方做尺寸/位置微调

## 3. dslLibrary 更新

- [ ] 3.1 更新 `dslLibrary.tsx` 中 Card 的 `description` 字段，描述新的 variant / header 用法
- [ ] 3.2 在 dslLibrary Card 条目中补充示例，覆盖：仅 title、title + subtitle + actions、sunk variant 三种典型写法
