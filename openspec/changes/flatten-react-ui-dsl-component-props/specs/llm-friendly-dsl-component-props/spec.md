## ADDED Requirements

### Requirement: Affected React UI DSL components SHALL expose semantic props at the top level
The React UI DSL library SHALL define affected component schemas so semantic component inputs are authored as top-level props instead of being wrapped in a `properties` object. This applies to the affected public component set in `packages/react-ui-dsl`, including the currently nested components such as Button, Charts, Form, HLayout, Image, Link, List, Select, Text, TimeLine, and VLayout, and to any remaining public components whose exported schema still exposes the same wrapper pattern.

#### Scenario: Prompt and schema surface use flattened component arguments
- **WHEN** the library exports prompt signatures or JSON Schema for an affected component
- **THEN** the component's semantic fields appear at the top level
- **AND** the exported shape does not require a `properties` wrapper object

### Requirement: Affected React UI DSL components SHALL exclude non-semantic host-control fields
The React UI DSL library SHALL not expose `style` or top-level `actions` as LLM-facing props for affected public components. The exported component schema and prompt guidance MUST only include fields that represent semantic component intent.

#### Scenario: Non-semantic fields are absent from the exported DSL contract
- **WHEN** a consumer inspects the exported prompt signatures, stories, or JSON Schema for an affected component
- **THEN** `style` is not presented as a supported generated prop
- **AND** top-level `actions` is not presented as a supported generated prop

### Requirement: Legacy nested component inputs SHALL be rejected explicitly
The React UI DSL library SHALL treat the transition away from `properties`, `style`, and removed top-level `actions` as a breaking schema change for affected components. Legacy definitions using those removed fields MUST no longer validate as accepted input for the affected components.

#### Scenario: Legacy nested DSL fails validation
- **WHEN** an affected component is authored using a removed `properties`, `style`, or top-level `actions` field
- **THEN** parser validation does not accept that component definition under the new schema
- **AND** the accepted authoring path is the flattened top-level schema
