## ADDED Requirements

### Requirement: OpenUI Lang SHALL provide expression-level formatting builtins for common display cases
The system SHALL provide `@FormatDate`, `@FormatBytes`, `@FormatNumber`, `@FormatPercent`, and `@FormatDuration` as builtin expression functions. These builtins SHALL be available anywhere other data builtins are allowed, and their signatures and descriptions SHALL appear in prompt documentation so LLMs can generate them intentionally.

#### Scenario: Prompt surface documents the formatting builtin family
- **WHEN** prompt documentation is generated from the builtin registry
- **THEN** the prompt includes `@FormatDate`, `@FormatBytes`, `@FormatNumber`, `@FormatPercent`, and `@FormatDuration`
- **AND** each builtin appears with its supported arguments and a description of its display purpose

#### Scenario: Formatting builtins are recognized as builtins in expressions
- **WHEN** the parser encounters any of the `@Format*` names in an expression
- **THEN** the name is treated as a builtin rather than as a component reference
- **AND** runtime evaluation resolves the call to a display string

### Requirement: Formatting builtins SHALL inherit a shared runtime locale default
The formatting builtin family SHALL use a shared default locale supplied by the rendering runtime. Each builtin MAY accept an explicit locale argument, and that explicit locale SHALL take precedence over the renderer default.

#### Scenario: Builtin inherits renderer locale when no locale argument is provided
- **WHEN** a host renders DSL with a renderer-level locale and evaluates `@FormatNumber`, `@FormatPercent`, `@FormatBytes`, or `@FormatDate` without an explicit locale argument
- **THEN** the builtin uses the renderer-level locale as its default formatting locale

#### Scenario: Explicit locale overrides the renderer locale
- **WHEN** the renderer has a default locale
- **AND** a formatting builtin is called with an explicit locale argument
- **THEN** the builtin uses the explicit locale for that call
- **AND** the renderer default remains unchanged for other calls

### Requirement: @FormatDate SHALL support preset date/time styles
`@FormatDate(value, style?, locale?)` SHALL format date-like values using preset styles instead of arbitrary custom patterns. Supported styles SHALL include `date`, `dateTime`, `time`, and `relative`.

#### Scenario: Date style formats a timestamp as a date-only string
- **WHEN** `@FormatDate("2026-01-02T03:04:05.000Z", "date")` is evaluated
- **THEN** the result is a locale-aware date-only display string

#### Scenario: DateTime style formats a timestamp as a date-time string
- **WHEN** `@FormatDate("2026-01-02T03:04:05.000Z", "dateTime")` is evaluated
- **THEN** the result is a locale-aware date-time display string

#### Scenario: Relative style formats a timestamp relative to now
- **WHEN** `@FormatDate(value, "relative")` is evaluated with a valid date-like value
- **THEN** the result is a relative-time display string in the resolved locale

### Requirement: @FormatBytes SHALL support SI and IEC byte systems
`@FormatBytes(value, system?, decimals?, locale?)` SHALL format byte counts into compact unit strings and SHALL support both SI (`KB`, `MB`, `GB`) and IEC (`KiB`, `MiB`, `GiB`) output systems.

#### Scenario: Default byte formatting uses the implementation default system
- **WHEN** `@FormatBytes(1536)` is evaluated without a system argument
- **THEN** the result is a compact byte display string using the implementation's fixed default system

#### Scenario: IEC output uses binary unit labels
- **WHEN** `@FormatBytes(1536, "iec")` is evaluated
- **THEN** the result uses IEC unit labels such as `KiB`

#### Scenario: SI output uses decimal unit labels
- **WHEN** `@FormatBytes(1536, "si")` is evaluated
- **THEN** the result uses SI unit labels such as `KB`

### Requirement: @FormatNumber and @FormatPercent SHALL provide locale-aware numeric display strings
`@FormatNumber(value, decimals?, locale?)` SHALL format scalar numeric values as locale-aware number strings. `@FormatPercent(value, decimals?, locale?)` SHALL treat its input as a ratio and format it as a locale-aware percentage display string.

#### Scenario: Number formatting applies locale-aware grouping
- **WHEN** `@FormatNumber(12345.67)` is evaluated
- **THEN** the result is a locale-aware grouped number string

#### Scenario: Percent formatting treats input as a ratio
- **WHEN** `@FormatPercent(0.125, 1)` is evaluated
- **THEN** the result is a locale-aware percentage display representing `12.5%`

### Requirement: @FormatDuration SHALL format elapsed time from scalar inputs
`@FormatDuration(value, unit?, locale?)` SHALL format scalar elapsed-time values into compact duration display strings. The default input unit SHALL be seconds, and the builtin SHALL support an explicit unit argument of `s` or `ms`.

#### Scenario: Duration defaults to seconds input
- **WHEN** `@FormatDuration(65)` is evaluated without a unit argument
- **THEN** the result is a compact duration display for sixty-five seconds

#### Scenario: Duration accepts explicit milliseconds input
- **WHEN** `@FormatDuration(65000, "ms")` is evaluated
- **THEN** the result is the same compact duration display as sixty-five seconds

### Requirement: Formatting builtins SHALL fail soft for nullish or invalid inputs
Formatting builtins SHALL prioritize stable rendering over strict validation. Nullish inputs SHALL produce an empty string. Inputs that cannot be interpreted for the requested formatting mode SHALL produce `String(value)` rather than throwing a runtime error.

#### Scenario: Nullish input returns empty string
- **WHEN** any `@Format*` builtin is evaluated with `null` or `undefined`
- **THEN** the result is an empty string

#### Scenario: Invalid date input falls back to the raw display value
- **WHEN** `@FormatDate("not-a-date", "date")` is evaluated
- **THEN** the result is `"not-a-date"`
- **AND** no runtime exception is raised
