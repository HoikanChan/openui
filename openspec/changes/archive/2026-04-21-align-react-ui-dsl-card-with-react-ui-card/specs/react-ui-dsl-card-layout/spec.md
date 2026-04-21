## ADDED Requirements

### Requirement: React UI DSL Card SHALL expose the react-ui-aligned layout contract
The `packages/react-ui-dsl` library SHALL define `Card` as a layout container whose LLM-facing schema matches the `react-ui` genui-lib `Card` contract: `children`, optional `variant`, and the shared flex layout props used by `Stack`. The DSL-facing schema MUST NOT accept legacy `header` or `width` props.

#### Scenario: Card prompt and JSON schema expose only the aligned contract
- **WHEN** a consumer inspects `dslLibrary.toSpec()` or `dslLibrary.toJSONSchema()` for `Card`
- **THEN** the exported contract includes `children`, optional `variant`, and flex layout props such as `direction`, `gap`, `align`, `justify`, and `wrap`
- **AND** the exported contract does not include `header` or `width`

#### Scenario: Legacy card header object is rejected
- **WHEN** a DSL author provides `Card` input using a `header` object or `width` prop
- **THEN** the `Card` schema does not accept that input as valid
- **AND** the supported authoring path is to express header content through `CardHeader` in `children`

### Requirement: React UI DSL SHALL provide CardHeader as a standalone component
The `packages/react-ui-dsl` library SHALL export a standalone `CardHeader` component with `title` and `subtitle` fields so header content can be authored independently from the `Card` container.

#### Scenario: CardHeader appears as a first-class DSL component
- **WHEN** a consumer inspects the exported library components or prompt signature surface
- **THEN** `CardHeader` is available as its own component
- **AND** its LLM-facing schema contains `title` and `subtitle`

#### Scenario: Card can compose CardHeader through children
- **WHEN** a DSL author includes `CardHeader` inside `Card.children`
- **THEN** the `Card` child schema accepts that component
- **AND** the rendered result preserves `CardHeader` as content within the card container

### Requirement: React UI DSL Card SHALL render with react-ui-aligned card layout behavior
The `packages/react-ui-dsl` `Card` renderer SHALL behave as a full-width styled flex container with `card`, `clear`, and `sunk` visual variants, while applying the same default flex-direction and gap semantics as the `react-ui` genui-lib `Card`.

#### Scenario: Default card layout matches the aligned container behavior
- **WHEN** a `Card` is rendered without explicit layout overrides
- **THEN** it renders as a full-width container
- **AND** it lays out children in a column
- **AND** it uses the default gap expected by the aligned `react-ui` card contract

#### Scenario: Variant and flex props affect rendering without host-control schema fields
- **WHEN** a `Card` is rendered with a supported `variant` and flex layout props
- **THEN** the card applies the requested visual variant and flex behavior
- **AND** the implementation uses the component's supported semantic props rather than exposing host-control fields such as `style` in the DSL schema
