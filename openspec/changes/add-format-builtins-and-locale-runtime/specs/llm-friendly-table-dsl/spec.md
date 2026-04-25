## MODIFIED Requirements

### Requirement: Table SHALL expose existing table features through schema-level column capabilities
The React UI DSL table capability SHALL preserve current table features such as sorting, filtering, tooltip display, and custom cell rendering, but SHALL NOT expose a component-local `format` option for display formatting. Formatted display values SHALL come from expression builtins or render templates instead of raw anonymous config objects.

#### Scenario: Sort and filter remain available
- **WHEN** a column enables sorting or filtering in the schema
- **THEN** the renderer preserves those behaviors in the final antd table
- **AND** prompt authors do not need to hand-author the underlying antd config object shape

#### Scenario: Tooltip and custom cell remain available
- **WHEN** a column enables tooltip display or custom cell rendering
- **THEN** the runtime preserves those behaviors when converting the schema to the antd table configuration
- **AND** those behaviors are expressed through schema-level options instead of the legacy anonymous object structure

#### Scenario: Formatted table display is authored through expressions
- **WHEN** a column needs date, bytes, number, percent, or duration formatting
- **THEN** the displayed value is produced through `@Format*` expressions before or during rendering
- **AND** the column definition does not rely on a dedicated `format` option

### Requirement: Table migration SHALL be explicit for legacy JSON-style table definitions
The React UI DSL table capability SHALL define how legacy table definitions that still use removed table-formatting affordances are handled. Legacy JSON-style `properties.columns[*]` structures and legacy column `format` options SHALL fail with a clear migration-oriented error rather than being silently accepted.

#### Scenario: Legacy JSON-style schema is rejected predictably
- **WHEN** an existing table definition still uses the legacy JSON-style `properties.columns[*]` structure
- **THEN** the system returns a clear migration-oriented error
- **AND** the error is consistent with the chosen breaking-change migration strategy

#### Scenario: Removed column format option is rejected predictably
- **WHEN** a column definition still declares a `format` option
- **THEN** schema validation or parsing fails with a clear migration-oriented error
- **AND** the migration path points authors toward `@Format*` expressions or render templates
