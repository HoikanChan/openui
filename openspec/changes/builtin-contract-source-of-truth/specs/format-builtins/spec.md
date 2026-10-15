## MODIFIED Requirements

### Requirement: OpenUI Lang SHALL provide expression-level formatting builtins for common display cases
The system SHALL provide `@FormatDate`, `@FormatBytes`, `@FormatNumber`, `@FormatPercent`, and `@FormatDuration` as builtin expression functions. These builtins SHALL be available anywhere other data builtins are allowed, and their signatures and descriptions SHALL appear in prompt documentation so LLMs can generate them intentionally. Java SDK 侧的函数词表与类型校验规则 SHALL 从 base-contract.json 的 builtins 条目派生（含 `FormatNumber` 与 `FormatPercent` 的结构化条目），MUST NOT 依赖与契约平行的硬编码定义。

#### Scenario: Prompt surface documents the formatting builtin family
- **WHEN** prompt documentation is generated from the builtin registry
- **THEN** the prompt includes `@FormatDate`, `@FormatBytes`, `@FormatNumber`, `@FormatPercent`, and `@FormatDuration`
- **AND** each builtin appears with its supported arguments and a description of its display purpose

#### Scenario: Formatting builtins are recognized as builtins in expressions
- **WHEN** the parser encounters any of the `@Format*` names in an expression
- **THEN** the name is treated as a builtin rather than as a component reference
- **AND** runtime evaluation resolves the call to a display string

#### Scenario: Java SDK 词表从契约派生且含 FormatNumber/FormatPercent
- **WHEN** Java SDK 从默认契约加载校验词表
- **THEN** 五个 `@Format*` 函数全部在列，`FormatNumber`/`FormatPercent` 条目带结构化 params 与 returnType
- **AND** 使用这两个函数的 valid 语料不报 unknown
