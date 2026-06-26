## Context

`react-ui-dsl` 是独立于 `react-ui` 的 Generative UI 包，两者共享同一套 DSL 语言基础（`@cloudsop/openui-react-lang`），但各自维护独立的组件库。当前 `Text` 组件是 react-ui-dsl 中唯一的文字渲染组件，其内部 `TextView` 支持 `default/markdown/html` 三种模式，但 DSL schema 只暴露 `text` 和 `size`，LLM 无法感知 markdown 能力。`react-ui` 已将文字组件拆分为 `TextContent`（带 `size` 控制）和 `MarkDownRenderer`（带 `variant` 容器样式），两者均始终以 markdown 渲染。本次变更对齐这一设计。

## Goals / Non-Goals

**Goals:**
- 以 `TextContent` 和 `MarkDownRenderer` 替换 `Text`，schema 与 `react-ui` genui-lib 完全一致
- 将共享 markdown 渲染逻辑抽取为 `Components/MarkdownView`，供两个新组件复用
- 移除 `html` 渲染模式（安全隐患，无 DSL 使用场景）
- 更新 `dslLibrary.tsx` 注册表，触发 snapshot 全量 regen

**Non-Goals:**
- 不引入对 `@openuidev/react-ui` 包的依赖
- 不修改 `react-ui` 侧的任何代码
- 不为 `Text` 保留向后兼容别名
- 不修改 CSS token 体系（`TextContent` 沿用现有 `text.module.css` 的 per-size class 方案）

## Decisions

### 1. 共享渲染器位置：`Components/MarkdownView`

**决策**：将简化后的 `TextView` 迁移至 `src/genui-lib/Components/MarkdownView/`，仅保留 markdown 渲染能力（去掉 `html` 分支）。

**理由**：`TextContent` 和 `MarkDownRenderer` 都需要 `react-markdown` 渲染，提取为内部组件避免重复代码。`Components/` 文件夹语义上明确为"内部共享 UI 基元"，不对外暴露为 DSL 组件。

**备选方案**：各组件独立实现 markdown 渲染 → 代码重复，`react-markdown` 配置（p 标签 margin 等）需维护两份。

### 2. `TextContent` 的 size 实现：保留现有 CSS module class 方案

**决策**：`TextContent` 沿用 `text.module.css` 中 per-size class 的方式（`.small`、`.large` 等直接设置 CSS 变量），而非对齐 `react-ui` 的 `--openui-text-body-*` 复合变量方案。

**理由**：`react-ui-dsl` 的 CSS token 体系与 `react-ui` 不同，`--openui-text-body-sm` 等变量在 react-ui-dsl 主题中不存在。保持现有方案不引入新的 token 依赖。

**备选方案**：引入 `--openui-text-body-*` 变量 → 需同步修改主题配置，超出本次 scope。

### 3. `MarkDownRenderer` 的 variant 样式：复用 Card CSS 变量

**决策**：`MarkDownRenderer` 的 `card/sunk/clear` 容器样式直接复用 `Card` 组件中已定义的 CSS 变量（`--openui-foreground`、`--openui-sunk`、`--openui-border-default`、`--openui-radius-3xl`、`--openui-space-l` 等）。

**理由**：这些变量已在 react-ui-dsl 主题体系中存在，视觉效果与 `Card` 保持一致，无需新增 token。

**备选方案**：自定义 `MarkDownRenderer` 专属颜色 → 视觉不一致，维护成本高。

### 4. 渲染容器：`<div>`（block）

**决策**：`TextContent` 和 `MarkDownRenderer` 均使用 `<div>` 作为外层容器，而非旧 `Text` 的 `<span>`。

**理由**：markdown 内容天然是块级元素（`<p>`、`<h*>`、`<ul>` 等），外层用 `<span>` 会产生无效嵌套。对齐 `react-ui` TextContent 的 `<div>` 用法。

**影响**：这是 breaking change，现有使用 `Text` 的布局可能因 block 特性发生微小变化，但 snapshot regen 会覆盖所有 `.dsl` 文件，视觉回归通过 eval 捕获。

### 5. snapshot 更新策略：全量 regen

**决策**：通过 `pnpm test:e2e:regen` 重新生成所有 e2e snapshot，不手动编辑 `.dsl` 文件。

**理由**：CLAUDE.md 明确规定 snapshot 只能通过 regen 更新。`Text` 被移除后，LLM 会自然生成 `TextContent`，regen 是唯一正确的更新路径。

## Risks / Trade-offs

- **[风险] snapshot regen 依赖外部 LLM API** → 需要 `LLM_API_KEY` 环境变量；CI 中若无 key 则 regen 步骤需手动执行
- **[风险] `<span>` → `<div>` 引发布局回归** → 通过 eval 评分捕获；regen 后的 snapshot 会反映新的块级渲染
- **[取舍] `MarkDownRenderer` 无 `size` 控制** → 与 `react-ui` 一致，需要 size 时用 `TextContent`；长文档场景不需要精细字号控制

## Migration Plan

1. 实现 `Components/MarkdownView`（简化的 markdown 渲染器）
2. 实现 `TextContent` 和 `MarkDownRenderer` 组件
3. 更新 `dslLibrary.tsx`：移除 `Text`，注册两个新组件
4. 更新现有单元测试
5. 执行 `pnpm test:e2e:regen` 重新生成所有 e2e snapshot（API key 已配置在 `.env` 中）
6. 运行 `pnpm test` 验证全套测试通过
7. （可选）运行 `pnpm eval` 对比 snapshot 质量
