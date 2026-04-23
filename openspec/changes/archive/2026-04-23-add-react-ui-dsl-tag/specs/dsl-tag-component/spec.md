## ADDED Requirements

### Requirement: Tag SHALL expose the existing React UI Tag schema in React UI DSL
The React UI DSL library SHALL provide a `Tag` component whose public schema matches the existing React UI `Tag` contract, including `text`, `icon`, `size`, and `variant`.

#### Scenario: Prompt signature exposes aligned Tag fields
- **WHEN** a consumer inspects the exported DSL prompt surface
- **THEN** the `Tag` component signature includes `text`
- **AND** the signature also includes optional `icon`, `size`, and `variant` fields using the same field names as the React UI `Tag` schema

#### Scenario: JSON schema exposes aligned Tag fields
- **WHEN** a consumer inspects the exported JSON schema for the DSL library
- **THEN** the `Tag` definition includes `text`, `icon`, `size`, and `variant`
- **AND** the allowed enum values for `size` and `variant` match the React UI `Tag` schema

### Requirement: Tag SHALL render through the Ant Design backend with semantic appearance mapping
The React UI DSL `Tag` component SHALL render through Ant Design's `Tag` component while preserving the semantic `size` and `variant` contract exposed by the schema.

#### Scenario: Variant maps to semantic Ant Design appearance
- **WHEN** a DSL `Tag` is rendered with a supported `variant`
- **THEN** the runtime maps that variant to a deterministic Ant Design tag appearance
- **AND** the rendered result preserves the semantic intent of `neutral`, `info`, `success`, `warning`, and `danger`

#### Scenario: Size affects rendered Tag appearance
- **WHEN** a DSL `Tag` is rendered with `size` set to `sm`, `md`, or `lg`
- **THEN** the runtime applies a deterministic size mapping for that value
- **AND** the rendered tag appearance changes without requiring consumers to pass raw Ant Design style props

### Requirement: Tag SHALL be part of the exported React UI DSL component library
The React UI DSL library SHALL expose `Tag` as a first-class component in its exported library surface so prompts, stories, and tests can use it consistently.

#### Scenario: DSL library includes Tag
- **WHEN** the exported DSL library is created
- **THEN** `Tag` is registered in the component list
- **AND** consumers can reference `Tag` in generated prompt examples and runtime rendering flows

### Requirement: Tag icon handling SHALL remain explicit
The React UI DSL `Tag` component SHALL treat the optional `icon` schema field explicitly so aligned schema support does not imply undocumented string-to-icon rendering behavior.

#### Scenario: Icon field is accepted without implicit icon registry behavior
- **WHEN** a DSL `Tag` definition provides an `icon` string
- **THEN** the component accepts that input as part of the schema contract
- **AND** the implementation documents and tests the chosen runtime behavior instead of implicitly promising arbitrary icon resolution
