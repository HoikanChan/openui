## MODIFIED Requirements

### Requirement: Object map builtins SHALL be available as eager data transforms
The system SHALL provide `@ObjectEntries(obj)` and `@ObjectKeys(obj)` as eager builtins in `lang-core`. Both builtins SHALL be recognized by builtin classification, evaluable by the runtime, and documented in generated builtin prompt sections. Java SDK 侧的词表与类型规则 SHALL 从 base-contract.json 的 builtins 条目派生：`ObjectEntries` 的条目 SHALL 以 `Record<string, T>` 参数与 `{key: string, value: T}[]` 返回类型结构化承载其字段并集精度，MUST NOT 退化为无元素的粗类型。

#### Scenario: Builtins are recognized by the parser/runtime registry
- **WHEN** the parser encounters `@ObjectEntries(data.devicesById)` or `@ObjectKeys(data.devicesById)` in DSL source
- **THEN** `isBuiltin("ObjectEntries")` returns `true`
- **AND** `isBuiltin("ObjectKeys")` returns `true`
- **AND** both builtins are registered in the eager builtin registry rather than the lazy builtin registry

#### Scenario: Builtins appear in prompt docs
- **WHEN** prompt documentation is generated from builtin definitions
- **THEN** `@ObjectEntries(obj) -> {key: string, value: any}[]` appears in the builtin docs
- **AND** `@ObjectKeys(obj) -> string[]` appears in the builtin docs

#### Scenario: 契约条目保留字段级追踪精度
- **WHEN** Java SDK 校验 `@ObjectEntries(data.devicesById)` 且实参为字段类型不同的 ObjectType
- **THEN** 返回类型推断为 `{key: string, value: <字段类型并集>}[]`
- **AND** 下游对 `value.<field>` 的访问按该并集继续校验
