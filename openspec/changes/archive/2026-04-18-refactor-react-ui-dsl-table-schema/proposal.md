## Why

`packages/react-ui-dsl` 里的 `Table` 组件目前更像要求 LLM 生成一段完整的列配置 JSON，再由运行时透传给 antd。这种表达方式不符合 openui lang 以组件调用为中心的习惯，也让模型不得不关心过多底层实现细节。

## What Changes

- 重构 `packages/react-ui-dsl/src/genui-lib/Table` 的组件 schema，让 `Table` 和列定义更接近 openui lang 的组件表达，而不是要求生成完整 JSON 结构。
- 保留底层基于 antd `Table` 的渲染实现，只调整 DSL 输入层和转换层。
- 将列定义改造成更适合 LLM 生成的组件式或参数式声明，减少 `title`、`field`、`sortable` 这类匿名对象堆叠。
- 更新 `dslLibrary` 中与表格相关的描述、规则和示例，让 prompt 默认产出新的 table 写法。
- **BREAKING**: 现有 `Table({ properties: { columns: [...] } })` 的推荐写法将被新的 openui lang 风格 schema 取代；是否保留兼容层由实现阶段决定。

## Capabilities

### New Capabilities
- `openui-lang-table-schema`: React UI DSL 提供更符合 openui lang 组件调用习惯的表格 schema，使 LLM 可以通过组件式声明生成表格，而不是手写完整列配置 JSON。

### Modified Capabilities

None.

## Impact

- Affected code: `packages/react-ui-dsl/src/genui-lib/Table/*`、`packages/react-ui-dsl/src/genui-lib/dslLibrary.tsx`，以及相关示例、测试和 prompt 元数据。
- APIs: `@openuidev/react-ui-dsl` 的 `Table` 组件 schema 和推荐调用方式会变化，但底层仍然渲染到 antd `Table`。
- Systems: 主要影响 LLM prompt 生成、DSL 解析输入，以及 table schema 到 antd columns 的转换逻辑。
