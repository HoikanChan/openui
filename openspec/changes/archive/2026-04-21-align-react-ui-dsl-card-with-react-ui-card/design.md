## Context

`packages/react-ui-dsl/src/genui-lib/Card` 当前实现的是一个自定义卡片视图：schema 暴露 `header` 与 `width`，view 层负责渲染 header/body 分块，stories 和测试也围绕这个结构编写。与此同时，`packages/react-ui/src/genui-lib/Card` 已经采用了另一套更简单的边界：`Card` 只是一个带视觉 variant 的 flex 容器，标题能力由独立的 `CardHeader` 组件提供，`Card` 自身的 schema 只接受 `children`、`variant` 和继承自 `Stack` 的 flex 参数。

这两个库的 DSL surface 不一致会直接反映到 `dslLibrary.toSpec()` 和 `toJSONSchema()` 输出，导致模型在不同库之间学习到冲突的 Card authoring 方式。仓库当前也已经在 `react-ui-dsl` 中使用 CSS Modules 取代部分旧样式实现，因此这次变更适合同时统一组件边界与样式实现方式。

## Goals / Non-Goals

**Goals:**
- 让 `react-ui-dsl` 的 `Card` schema、默认布局行为和组件边界对齐 `react-ui` 的 genui-lib `Card`。
- 在 `react-ui-dsl` 中新增独立的 `CardHeader` DSL 组件，并让 `Card` 通过 children 组合它。
- 用 CSS Modules 重写 `Card` 与 `CardHeader` 的展示层，局部实现所需 token 和 classNames 工具，不依赖 `react-ui` 的 scss 基础设施。
- 更新 `dslLibrary`、stories 和测试，确保导出的 prompt/schema surface 与新契约一致。

**Non-Goals:**
- 不让 `react-ui-dsl` 直接依赖或复用 `packages/react-ui` 的运行时代码。
- 不保留旧 `Card.header` / `Card.width` 的向后兼容层。
- 不重构与本次组件无关的其它 DSL 组件或全局 design token 体系。

## Decisions

### 1. 将 `Card` 收敛为纯容器组件，并删除内嵌 header schema

`Card` 将对齐 `react-ui/src/genui-lib/Card/schema.ts`：保留 `children`、`variant`，并 merge `FlexPropsSchema`。渲染层固定 `width="full"` 的语义，同时在根节点加入 `flex: 1` 和 `minWidth: 0`，保证它在 row/wrap 场景下与 `react-ui` 保持一致。

选择这个方向而不是保留兼容字段，是因为用户明确要求 schema 与 `react-ui` 一样；保留 `header` 或 `width` 只会继续扩大两套 Card 契约的差距。

### 2. 新增独立的 `CardHeader` 组件，而不是继续把标题区挂在 `Card` 上

`CardHeader` 将新增到 `packages/react-ui-dsl/src/genui-lib/CardHeader`，schema 与 `react-ui` 一致，只接受 `title` 和 `subtitle`。`Card` children union 会显式允许 `CardHeader`，从而把原来的：

`Card({ header: { title, subtitle }, children })`

迁移为：

`Card({ children: [CardHeader({ title, subtitle }), ...] })`

这个拆分能让 prompt/schema surface 学到更稳定的组合模型，也避免 `Card` 承担标题语义和布局语义两种职责。

### 3. 继续使用 CSS Modules，并在组件目录内实现最小样式依赖

`Card` 与 `CardHeader` 都使用本地 `*.module.css`，通过组件目录内的 `classNames` util 组合类名。所需 spacing、radius、color、shadow token 直接在 css 文件中声明为 CSS 自定义属性并附带 fallback，以维持当前 `react-ui-dsl` 包无需接入 `react-ui` scss token 的独立性。

替代方案是直接引入 `clsx` 或共享全局 token 文件；这里不采用前者是因为用户明确允许本地 util，且该工具足够简单；不采用后者是为了避免把这次局部对齐变成更大的样式系统迁移。

### 4. 通过测试和导出面验证 breaking schema 迁移

测试会覆盖三层：
- view/stories：验证新的 variant 和布局 props 渲染方式；
- schema/library surface：验证 `Card` 不再暴露 `header`/`width`，并新增 `CardHeader`；
- 目标化回归：验证 `dslLibrary.toSpec()` / `toJSONSchema()` 能导出新的组件签名。

这样既能保证实现行为正确，也能保护最重要的 LLM-facing contract。

## Risks / Trade-offs

- [Breaking DSL contract] 现有使用 `Card.header` 或 `Card.width` 的 DSL 会失效 → Mitigation: 在 proposal/spec/tasks 中明确这是 breaking change，并通过测试固定新 authoring 路径。
- [Style drift from `react-ui`] CSS Modules 版本不直接复用 `react-ui` scss，视觉可能只做到语义接近而不是像素级一致 → Mitigation: 在 design 中明确目标是组件边界和行为对齐，局部 token 尽量映射 `react-ui` 的 spacing/variant 语义。
- [Union wiring regressions] 新增 `CardHeader` 后，`Card` children union 和 `dslLibrary` 导出可能遗漏 → Mitigation: 添加 library surface 测试，直接对 `toSpec()` / `toJSONSchema()` 断言。
- [Story/test churn] 旧 stories/tests 依赖 header object，需要整批迁移 → Mitigation: 先写失败测试，再最小化改造 stories 和 view 结构，避免实现与验证脱节。

## Migration Plan

1. 新增 `CardHeader` 组件目录与 schema/view/style。
2. 重写 `Card` schema 与 view，使其对齐 `react-ui` 的 layout Card 模型。
3. 更新 `dslLibrary` 和 `Card` children union，让 `CardHeader` 成为可导出的独立组件。
4. 迁移 `Card` 相关 stories 和测试，从 `header` object 改为 `CardHeader` children。
5. 运行 `packages/react-ui-dsl` 的针对性测试与 type/build 验证。

回滚策略：如果新契约引发不可接受的回归，可整体回退该 change；不提供运行时兼容层。

## Open Questions

- 暂无需要在设计阶段阻塞实现的开放问题。`CardHeader` 允许作为普通 child 出现在 `Card` children 中，且不强制必须排在第一个位置，这是本次已确认的 authoring model。
