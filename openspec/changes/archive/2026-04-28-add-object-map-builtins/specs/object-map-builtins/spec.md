## ADDED Requirements

### Requirement: Object map builtins SHALL be available as eager data transforms
The system SHALL provide `@ObjectEntries(obj)` and `@ObjectKeys(obj)` as eager builtins in `lang-core`. Both builtins SHALL be recognized by builtin classification, evaluable by the runtime, and documented in generated builtin prompt sections.

#### Scenario: Builtins are recognized by the parser/runtime registry
- **WHEN** the parser encounters `@ObjectEntries(data.devicesById)` or `@ObjectKeys(data.devicesById)` in DSL source
- **THEN** `isBuiltin("ObjectEntries")` returns `true`
- **AND** `isBuiltin("ObjectKeys")` returns `true`
- **AND** both builtins are registered in the eager builtin registry rather than the lazy builtin registry

#### Scenario: Builtins appear in prompt docs
- **WHEN** prompt documentation is generated from builtin definitions
- **THEN** `@ObjectEntries(obj) -> {key: string, value: any}[]` appears in the builtin docs
- **AND** `@ObjectKeys(obj) -> string[]` appears in the builtin docs

### Requirement: @ObjectEntries SHALL convert a plain record map into ordered key/value rows
`@ObjectEntries(obj)` SHALL accept a plain record-map input and return an array of row objects in the form `[{ key, value }, ...]`. The returned array SHALL preserve the input object's original property enumeration order.

#### Scenario: Dynamic-key object becomes ordered rows
- **WHEN** `@ObjectEntries({ "dev-001": { status: "up" }, "dev-002": { status: "down" } })` is evaluated
- **THEN** the result is `[{ key: "dev-001", value: { status: "up" } }, { key: "dev-002", value: { status: "down" } }]`
- **AND** the row order matches the original object property order

#### Scenario: Entry rows compose with field access
- **WHEN** `rows = @ObjectEntries(data.devicesById)` is evaluated
- **THEN** `rows.key` yields an array of keys in the same order
- **AND** `rows.value.status` yields the nested `status` field for each entry value

#### Scenario: Unsupported input fails soft
- **WHEN** `@ObjectEntries(null)`, `@ObjectEntries([1, 2])`, or `@ObjectEntries("bad")` is evaluated
- **THEN** the result is `[]`
- **AND** the evaluator does not throw

### Requirement: @ObjectKeys SHALL convert a plain record map into an ordered key array
`@ObjectKeys(obj)` SHALL accept a plain record-map input and return an array of string keys. The returned key array SHALL preserve the input object's original property enumeration order.

#### Scenario: Dynamic-key object becomes ordered key array
- **WHEN** `@ObjectKeys({ "dev-001": { status: "up" }, "dev-002": { status: "down" } })` is evaluated
- **THEN** the result is `["dev-001", "dev-002"]`
- **AND** the key order matches the original object property order

#### Scenario: Unsupported input fails soft
- **WHEN** `@ObjectKeys(null)`, `@ObjectKeys([1, 2])`, or `@ObjectKeys(42)` is evaluated
- **THEN** the result is `[]`
- **AND** the evaluator does not throw

### Requirement: React UI DSL prompt guidance SHALL steer dynamic-key objects toward object-map builtins
The default React UI DSL prompt SHALL document that dynamic-key object maps should be converted with `@ObjectEntries` or `@ObjectKeys` instead of hardcoding sample keys. The prompt SHALL include at least one example that turns a dynamic-key object map into iterable rows before rendering.

#### Scenario: Prompt includes dynamic-key object guidance
- **WHEN** the default React UI DSL prompt is generated
- **THEN** it contains guidance to use `@ObjectEntries` or `@ObjectKeys` for dynamic-key object maps
- **AND** it discourages hardcoding object keys from sample data

#### Scenario: Prompt includes a row-conversion example
- **WHEN** the default React UI DSL prompt is generated
- **THEN** it includes an example where a dynamic-key object is first converted with `@ObjectEntries(data)`
- **AND** the converted rows are then consumed by a table, list, or similar iterable component pattern
