## MODIFIED Requirements

### Requirement: React UI DSL SHALL expose a dedicated descriptions component family for object detail layouts

The React UI DSL library SHALL provide a first-class descriptions component family for rendering one object as labeled detail rows without requiring authors to manually compose the layout from generic layout containers or `Table` primitives.

#### Scenario: Prompt and schema surface expose the approved descriptions signatures

- **WHEN** a consumer inspects the exported prompt surface or JSON schema for the React UI DSL library
- **THEN** the library includes `Descriptions`, `DescGroup`, and `DescField`
- **AND** `Descriptions` is authored as `Descriptions(items, title?, extra?, columns?)`
- **AND** `DescGroup` is authored as `DescGroup(title, fields, columns?)`
- **AND** `DescField` is authored as `DescField(label, value, span?)`

### Requirement: DescField SHALL accept direct value expressions

Each descriptions field SHALL accept its display content directly as the `value` argument rather than relying on component-local path lookup or a descriptions-specific formatting prop.

#### Scenario: Plain values can be passed directly to a field

- **WHEN** a `DescField` is authored with a plain value expression such as `user.name` or `user.createdAt`
- **THEN** the runtime uses that expression result as the displayed field value

#### Scenario: Formatted values can be passed directly to a field

- **WHEN** a `DescField` is authored with an expression such as `@FormatDate(user.createdAt, "dateTime")`
- **THEN** the runtime uses the formatted string result as the displayed field value
- **AND** authors do not need a separate `format` argument on `DescField`

#### Scenario: Component values can be passed directly to a field

- **WHEN** a `DescField` is authored with a component expression such as `Tag(user.status)`
- **THEN** the runtime renders that component as the field value
- **AND** authors do not need a separate descriptions-specific render callback API to customize field display
