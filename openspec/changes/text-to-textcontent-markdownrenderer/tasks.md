## 1. 共享内部渲染器

- [ ] 1.1 在 `src/genui-lib/Components/MarkdownView/` 创建目录结构（`index.tsx`、`markdownView.module.css`）
- [ ] 1.2 将 `TextView` 的 markdown 渲染逻辑迁移至 `MarkdownView`，仅保留 markdown 模式，移除 `default`（纯文本）和 `html` 分支
- [ ] 1.3 保留 `p:first-child { margin-top: 0 }` 和 `p:last-child { margin-bottom: 0 }` CSS 处理

## 2. TextContent 组件

- [ ] 2.1 创建 `src/genui-lib/TextContent/schema.ts`，定义 Zod schema：`{ text: string, size?: enum[5] }`
- [ ] 2.2 创建 `src/genui-lib/TextContent/textContent.module.css`，复制 `text.module.css` 中的 5 档 size class
- [ ] 2.3 创建 `src/genui-lib/TextContent/index.tsx`，`defineComponent` 包装 `MarkdownView`，外层容器为 `<div>`，根据 size 应用 CSS class
- [ ] 2.4 description 写明：`'Text block. Supports markdown. Optional size: "small" | "default" | "large" | "small-heavy" | "large-heavy".'`

## 3. MarkDownRenderer 组件

- [ ] 3.1 创建 `src/genui-lib/MarkDownRenderer/schema.ts`，定义 Zod schema：`{ textMarkdown: string, variant?: "clear" | "card" | "sunk" }`
- [ ] 3.2 创建 `src/genui-lib/MarkDownRenderer/markDownRenderer.module.css`，定义 `.clear`、`.card`、`.sunk` class，复用 Card 的 CSS 变量（`--openui-foreground`、`--openui-sunk`、`--openui-border-default`、`--openui-radius-3xl`、`--openui-space-l`）
- [ ] 3.3 创建 `src/genui-lib/MarkDownRenderer/index.tsx`，`defineComponent` 包装 `MarkdownView`，根据 `variant` 应用对应 CSS class
- [ ] 3.4 description 写明：`'Renders markdown text with optional container variant ("clear" | "card" | "sunk"). Use for long-form documents, README content, or markdown with code blocks. For short UI text and labels, use TextContent.'`

## 4. 更新 dslLibrary

- [ ] 4.1 在 `dslLibrary.tsx` 中移除 `Text` 的 import 和注册
- [ ] 4.2 import 并注册 `TextContent` 和 `MarkDownRenderer`
- [ ] 4.3 更新 library 的 examples，将示例中的 `Text(...)` 替换为 `TextContent(...)`

## 5. 清理旧 Text 组件

- [ ] 5.1 删除 `src/genui-lib/Text/` 目录（包含 `index.tsx`、`schema.ts`、`text.module.css`、`view/` 子目录）

## 6. 更新测试

- [ ] 6.1 删除 `src/genui-lib/Text/index.test.tsx` 和 `src/genui-lib/Text/text-style.test.ts`（旧测试）
- [ ] 6.2 为 `TextContent` 创建单元测试：验证渲染为 `<div>`、size class 正确应用、`Text` 不在注册表中
- [ ] 6.3 为 `MarkDownRenderer` 创建单元测试：验证 markdown 渲染、三种 variant CSS class 应用
- [ ] 6.4 为 `MarkdownView` 创建单元测试：验证 markdown 渲染、`p` 标签 margin 处理

## 7. Snapshot 重新生成

- [ ] 7.1 确认 `packages/react-ui-dsl/.env` 中 `LLM_API_KEY` 已配置（通常已存在，无需手动输入）
- [ ] 7.2 在 `packages/react-ui-dsl` 目录下执行 `pnpm test:e2e:regen` 重新生成所有 e2e snapshot
- [ ] 7.3 检查 regen 后的 `.dsl` 文件，确认 `Text(...)` 已全部替换为 `TextContent(...)`

## 8. 验证

- [ ] 8.1 执行 `pnpm --filter @cloudsop/openui-react-ui-dsl run test` 确认所有单元测试通过
- [ ] 8.2 执行 `pnpm --filter @cloudsop/openui-react-ui-dsl run build` 确认构建无报错
- [ ] 8.3 （可选）执行 `pnpm --filter @cloudsop/openui-react-ui-dsl run ci` 确认 lint 和 format 通过
