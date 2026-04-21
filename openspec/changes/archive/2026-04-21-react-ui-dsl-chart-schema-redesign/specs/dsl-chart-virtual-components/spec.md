## ADDED Requirements

### Requirement: Series is a virtual DSL component
A `Series` virtual component SHALL be defined with `defineComponent`, `component: () => null`, and schema `{ category: string, values: number[] }`. It SHALL be exported from `genui-lib/Charts/index.ts`.

#### Scenario: Series renders nothing
- **WHEN** a Series component is rendered standalone
- **THEN** it returns null — it exists only as a schema carrier

#### Scenario: Series schema uses category not name
- **WHEN** the Series schema is inspected
- **THEN** the series label field is named `category` (matching react-ui convention), not `name`

### Requirement: ScatterSeries is a virtual DSL component
A `ScatterSeries` virtual component SHALL be defined with schema `{ name: string, points: PointSchema[] }` and `component: () => null`.

#### Scenario: ScatterSeries carries name and points
- **WHEN** a ScatterChart receives `datasets: [ScatterSeries({ name: "Devices", points: [...] })]`
- **THEN** the chart renders one scatter series labelled "Devices"

### Requirement: Point is a virtual DSL component
A `Point` virtual component SHALL be defined with schema `{ x: number, y: number, z?: number }` and `component: () => null`.

#### Scenario: Point with z value enables bubble mode
- **WHEN** a Point has a `z` value provided
- **THEN** the ScatterChart that contains it renders that point with proportional size

### Requirement: Virtual components are exported from Charts index
`Series`, `ScatterSeries`, and `Point` SHALL all be exported from `genui-lib/Charts/index.ts` alongside the chart components.

#### Scenario: Virtual components appear in DSL library spec
- **WHEN** `dslLibrary.toSpec()` is called
- **THEN** Series, ScatterSeries, and Point appear as component entries with their signatures
