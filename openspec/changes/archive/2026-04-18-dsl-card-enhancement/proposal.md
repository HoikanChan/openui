## Why

`packages/react-ui-dsl` 的 `Card` 组件目前依赖 Ant Design 的 `Card`，并通过 `tag`、`headerAlign` 这类 Ant 专有 hack 来控制标题区外观。这带来两个问题：一是样式与 react-ui 体系不一致，二是 DSL schema 的表达能力弱——LLM 无法生成带副标题、右上角操作按钮的卡片。

## What Changes

- 移除 `antd/Card` 依赖，用自实现的 `div` 结构替代。
- 将 schema 字段对齐 react-ui `Card` + `CardHeader` 的设计语言：字段直接铺平（无 `properties` 包裹），引入 `variant`（card / clear / sunk）、`width`（standard / full）、以及结构化的 `header` 对象（title / subtitle / actions）。
- 删除旧字段 `tag` 和 `headerAlign`（Ant Design 专有，不属于语义化 DSL）。
- 更新 `dslLibrary` 里 Card 的描述和示例，让 LLM 默认产出新 schema。
- **BREAKING**: 旧的 `properties.tag` / `properties.headerAlign` 字段将不再生效；实现阶段决定是否静默忽略或发出告警。

## Capabilities

### New Capabilities

- `dsl-card-variant`: Card 支持三种视觉变体（card / clear / sunk），对应有边框阴影、无边框、内陷背景三种风格。
- `dsl-card-header-object`: Card header 从单一字符串升级为对象，支持 title、subtitle 和 actions（DSL 节点数组）。
- `dsl-card-width`: Card 支持 standard / full 宽度控制。

### Modified Capabilities

- `dsl-card-header`: 原 `properties.header` 字符串字段变更为 `properties.header.title`，含义不变但结构不同。

## Impact

- Affected code: `packages/react-ui-dsl/src/genui-lib/Card/schema.ts`、`packages/react-ui-dsl/src/genui-lib/Card/index.tsx`、`packages/react-ui-dsl/src/genui-lib/dslLibrary.tsx`。
- APIs: `@openuidev/react-ui-dsl` 的 `Card` 组件 schema 变化，旧的 `tag` / `headerAlign` 字段失效。
- Systems: 影响 LLM prompt 生成和 DSL 运行时渲染，不涉及其他组件。
