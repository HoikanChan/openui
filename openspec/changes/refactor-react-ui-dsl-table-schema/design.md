## Context

当前 `packages/react-ui-dsl/src/genui-lib/Table` 的 schema 是一个典型的对象树：

- `Table`
- `properties.columns`
- `columns[*].title | field | sortable | filterable | filterOptions | format | tooltip | customized`

这对运行时转换到 antd 来说没有问题，但对 openui lang 和 LLM 生成都不理想。openui lang 的优势在于组件调用和引用关系清晰，例如 `Table(...)`、`Col(...)`、`Series(...)` 这类声明，而不是要求模型直接拼出一个完整对象字面量。

这次变更的边界已经明确：

- 底层继续使用 antd `Table`
- 不重做表格渲染库，不重做数据层
- 重点是把 `Table` 的组件 schema 改成更符合 openui lang 的定义

## Goals / Non-Goals

**Goals:**

- 把 `Table` 从 JSON 配置风格重构为 openui lang 风格的组件 schema。
- 让列定义以 `Col` 之类的组件化形式出现，而不是匿名对象数组。
- 保留排序、过滤、格式化、tooltip、自定义渲染等表格能力，但这些能力通过组件 schema 暴露。
- 让 `dslLibrary` 的表格示例和提示文本默认生成新的 schema 写法。

**Non-Goals:**

- 替换 antd `Table` 作为底层渲染实现。
- 重做表格数据来源、远程分页、服务端排序等机制。
- 对 `react-ui` 包现有 `Table` 实现做同步重构。

## Decisions

### Keep Ant Design as the rendering backend

这次变更只发生在 DSL 输入层和转换层。运行时仍然把 schema 翻译成 antd `columns`、`render`、`filters`、`sorter` 等配置，然后交给 antd 渲染。

Alternative considered: 顺手替换或抽象掉 antd。
Rejected because 用户目标很明确，只是调整 schema，不做底层替换。

### Move from anonymous JSON column objects to component-style column declarations

新的 table 定义应以 openui lang 熟悉的组件模式出现，例如 `Table([...])` 配合 `Col(...)`，或者等价的参数式组件 schema。重点不是具体采用哪种参数顺序，而是：

- LLM 生成的是组件调用
- 列是显式声明单元
- 不再要求模型拼整段 `properties.columns = [{...}]`

Alternative considered: 保留对象数组，只是简化字段名。
Rejected because 这仍然是“生成 JSON 配置”，没有解决 openui lang 表达不自然的问题。

### Keep feature parity by translating the new schema to the existing antd mapping

排序、过滤、格式化、tooltip、自定义单元格这些能力仍然保留，但它们应归属到列组件 schema，再由内部转换层统一映射到 antd 配置。

这意味着：

- DSL 面向 LLM 暴露的是组件能力
- 渲染层继续复用现有 antd 机制
- schema 和 runtime 之间增加一个更清晰的转换边界

Alternative considered: 为了简化 schema 直接砍掉高级能力。
Rejected because 这会降低现有 Table 的表达能力，不符合“只改 schema，不降能力”的方向。

### Treat compatibility as a migration decision, not a design goal

是否兼容旧的 `properties.columns[*]` 结构，属于迁移策略问题，不应主导新 schema 设计。设计上先把新的 openui lang 表达定清楚；实现时可以选择：

- 短期兼容旧写法并加告警
- 直接报出明确迁移错误

Alternative considered: 为了兼容旧写法而继续把新 schema 绑定在旧对象结构上。
Rejected because 这会稀释新 schema，最后两边都不够干净。

## Risks / Trade-offs

- [新 schema 如果仍然过于贴近 antd 字段命名，收益会很有限] -> 在组件描述和示例层优先使用 openui lang 术语，而不是底层库术语。
- [兼容旧写法会增加运行时复杂度] -> 把兼容逻辑限制在 Table 内部适配层，不扩散到 prompt 和示例。
- [如果 prompt 不同步更新，LLM 仍会产出旧 JSON 结构] -> 同步修改 `dslLibrary` 示例、组件描述和规则文本。
- [列组件参数设计不清晰会影响可读性] -> 在实现前先固定一个最小、稳定的 `Table`/`Col` 调用形态，并用示例驱动验证。

## Migration Plan

1. 设计新的 `Table` 与列组件 schema，确定 openui lang 风格的调用方式。
2. 在 `genui-lib/Table` 内部实现新 schema 到 antd 配置的转换。
3. 视实现成本决定是否保留旧 `properties.columns[*]` 的兼容层或迁移报错。
4. 更新 `dslLibrary` 的表格说明、示例和 prompt 规则，默认输出新写法。
5. 为新 schema 和转换逻辑补充测试，验证生成与渲染路径都成立。

Rollback strategy: 保留旧 schema 的转换入口，必要时先恢复 prompt 默认写法而不动 antd 渲染层。

## Open Questions

- 新 schema 的最终调用形态是 `Table([Col(...)], rows)`，还是 `Table({ columns: [Col(...)] })` 这种更轻量的组件对象形式？
- `Col` 应该尽量使用位置参数，还是在部分高级能力上保留一个小型 options 对象？
- 自定义单元格能力在新 schema 下应命名为 `cell`、`render` 还是别的 openui lang 术语？
