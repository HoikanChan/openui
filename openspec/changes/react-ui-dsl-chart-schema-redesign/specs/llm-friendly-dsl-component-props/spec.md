## MODIFIED Requirements

### Requirement: Affected React UI DSL components SHALL expose semantic props at the top level
The React UI DSL library SHALL define affected component schemas so semantic component inputs are authored as top-level props instead of being wrapped in a `properties` object. This applies to the affected public component set in `packages/react-ui-dsl`, including the currently nested components such as Button, Charts, Form, HLayout, Image, Link, List, Select, Text, TimeLine, and VLayout, and to any remaining public components whose exported schema still exposes the same wrapper pattern.

Chart components specifically SHALL use domain-semantic field names (`labels`, `series`, `values`, `readings`, `datasets`) as top-level props — not a passthrough ECharts config object.

#### Scenario: Prompt and schema surface use flattened component arguments
- **WHEN** the library exports prompt signatures or JSON Schema for an affected component
- **THEN** the component's semantic fields appear at the top level
- **AND** the exported shape does not require a `properties` wrapper object

#### Scenario: Chart schemas expose domain fields not ECharts config
- **WHEN** a consumer inspects the exported JSON Schema for any chart component
- **THEN** the top-level properties are domain fields (`labels`, `series`, `values`, `readings`, `datasets`, `xLabels`, `yLabels`) and NOT ECharts-internal fields (`dataset`, `xAxis`, `yAxis`, `series` as ECharts series objects)
