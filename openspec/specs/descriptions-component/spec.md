# descriptions-component Specification

## Purpose
Define the React UI DSL descriptions component family for object-detail layouts, including explicit field authoring, grouping, and metric-card-style rendering behavior.
## Requirements
### Requirement: React UI DSL SHALL expose a dedicated descriptions component family for object detail layouts

The React UI DSL library SHALL provide a first-class descriptions component family for rendering one object as labeled detail rows without requiring authors to manually compose the layout from generic layout containers or `Table` primitives.

#### Scenario: Prompt and schema surface expose the approved descriptions signatures

- **WHEN** a consumer inspects the exported prompt surface or JSON schema for the React UI DSL library
- **THEN** the library includes `Descriptions`, `DescGroup`, and `DescField`
- **AND** `Descriptions` is authored as `Descriptions(items, title?, extra?, columns?)`
- **AND** `DescGroup` is authored as `DescGroup(title, fields, columns?)`
- **AND** `DescField` is authored as `DescField(label, value, span?, format?)`

### Requirement: Descriptions SHALL require explicit item and label authoring

The descriptions component family SHALL keep detail authoring explicit rather than inferring fields or labels from the bound object.

#### Scenario: Descriptions does not provide implicit auto-generated fields

- **WHEN** a consumer authors a `Descriptions` node
- **THEN** the component expects explicit `items`
- **AND** the accepted authoring path does not depend on automatic field generation

#### Scenario: DescField does not derive fallback labels from the path

- **WHEN** a consumer authors a `DescField`
- **THEN** `label` is part of the required public contract
- **AND** the component does not rely on inferred display labels such as converting `user.createdAt` into `Created At`

### Requirement: Descriptions SHALL support mixed top-level fields and groups

The top-level descriptions container SHALL allow ungrouped fields and grouped fields in the same authoring list.

#### Scenario: Top-level items can mix direct fields and groups

- **WHEN** `Descriptions.items` is authored
- **THEN** the list may contain `DescField` entries
- **AND** the list may also contain `DescGroup` entries

#### Scenario: Groups remain one level deep

- **WHEN** `DescGroup.fields` is authored
- **THEN** the accepted child type is `DescField`
- **AND** nested `DescGroup` structures are not part of the first-version contract

### Requirement: DescField SHALL accept direct value expressions

Each descriptions field SHALL accept its display content directly as the `value` argument rather than relying on component-local path lookup.

#### Scenario: Plain values can be passed directly to a field

- **WHEN** a `DescField` is authored with a plain value expression such as `user.name` or `user.createdAt`
- **THEN** the runtime uses that expression result as the displayed field value
- **AND** `format`, when provided, applies to plain values rather than requiring a custom render wrapper

#### Scenario: Component values can be passed directly to a field

- **WHEN** a `DescField` is authored with a component expression such as `Tag(user.status)`
- **THEN** the runtime renders that component as the field value
- **AND** authors do not need a separate descriptions-specific render callback API to customize field display

### Requirement: Descriptions SHALL support descriptions-style layout semantics

The descriptions component family SHALL support section headings, top-level actions, column counts, and field spanning in a deterministic layout model.

#### Scenario: Top-level title and extra render above the details layout

- **WHEN** `Descriptions` is authored with `title` and `extra`
- **THEN** the runtime renders those controls as part of the top-level descriptions header
- **AND** `extra` is associated with the top-level container rather than with individual groups

#### Scenario: Group-level columns can override the inherited column count

- **WHEN** a `Descriptions` container declares `columns`
- **THEN** that value acts as the default column count for its direct items
- **AND** a `DescGroup.columns` value overrides that default for the fields inside that group

#### Scenario: Field span affects how items occupy the layout grid

- **WHEN** a `DescField` declares `span`
- **THEN** the runtime uses that span to determine how many layout slots the field occupies within the current column count
- **AND** fields wrap deterministically when the current row is full

#### Scenario: Omitted span auto-expands long plain-text values

- **WHEN** a `DescField` omits `span`
- **AND** its value is a plain text-like value rather than a component node
- **THEN** the runtime computes a best-fit span from the available column width and the displayed value width
- **AND** the field may expand beyond one column when needed to preserve the intended single-line metric-card presentation

#### Scenario: Component-valued fields do not require automatic width measurement

- **WHEN** a `DescField` omits `span`
- **AND** its value is a component expression such as `Tag(user.status)`
- **THEN** the runtime renders the component value without requiring text-width measurement
- **AND** the field follows the component-valued fallback span behavior defined by the implementation

### Requirement: Descriptions SHALL render through a custom React UI DSL implementation

The descriptions component family SHALL be implemented with a custom React UI DSL view instead of depending on Ant Design's descriptions runtime.

#### Scenario: The runtime implementation remains internal to React UI DSL

- **WHEN** the descriptions component family is rendered
- **THEN** the implementation uses React UI DSL's internal component and styling system
- **AND** the feature does not require introducing an Ant Design dependency for descriptions rendering

#### Scenario: Default visual styling follows the approved metric-cards pattern

- **WHEN** the descriptions component family renders with default styling
- **THEN** it uses a card-grid presentation consistent with the approved metric-cards reference
- **AND** that presentation includes the same overall visual direction for title spacing, grid gaps, card chrome, label tone, and value emphasis
