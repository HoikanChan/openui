## MODIFIED Requirements

### Requirement: Table SHALL expose existing table features through schema-level column capabilities
The React UI DSL table capability SHALL preserve current table features such as sorting, filtering, formatting, tooltip display, and custom cell rendering, but expose them through schema-level column capabilities instead of raw anonymous config objects.

#### Scenario: Sort and filter remain available
- **WHEN** a column enables sorting or filtering in the new schema
- **THEN** the renderer preserves those behaviors in the final antd table
- **AND** prompt authors do not need to hand-author the underlying antd config object shape

#### Scenario: Format and tooltip remain available
- **WHEN** a column enables formatting or tooltip display
- **THEN** the runtime preserves those behaviors when converting the schema to the antd table configuration
- **AND** those behaviors are expressed through the new schema instead of the legacy anonymous object structure

#### Scenario: Static ElementNode cell rendering is preserved
- **WHEN** `options.cell` is a static ElementNode (e.g., `cell: Tag("Badge")`)
- **THEN** the column renders that ElementNode for every row
- **AND** no breaking change occurs for existing DSL output using this form

#### Scenario: @Render cell renders per-row using injected value
- **WHEN** `options.cell` is `@Render("v", expr)` for a column
- **THEN** `hydrateSlots` lifts `options.cell` to a JS render function before the Table component receives props
- **AND** the column's antd `render` function calls `options.cell(cellValue)` for each row
- **AND** the result of the expression with `v` bound to `cellValue` is rendered as a ReactNode

#### Scenario: @Render two-variable cell renders per-row using value and record
- **WHEN** `options.cell` is `@Render("v", "row", expr)` for a column
- **THEN** `hydrateSlots` lifts `options.cell` to a two-arg JS render function
- **AND** the column's antd `render` function calls `options.cell(cellValue, rowRecord)` for each row
- **AND** the result of the expression with `v = cellValue` and `row = rowRecord` is rendered as a ReactNode

#### Scenario: cell with @Switch maps enum values to components per row
- **WHEN** `options.cell` is `@Render("v", @Switch(v, {active: Badge(...), inactive: Label(...)}))` for a column
- **THEN** each row cell renders the component corresponding to that row's field value
- **AND** rows with unmatched values render the Switch default (null or explicit fallback)

#### Scenario: cell is absent — default text rendering applies
- **WHEN** no `options.cell` is set on a column
- **THEN** the column falls back to format-based or raw-value text rendering
- **AND** neither ElementNode nor function rendering is attempted
