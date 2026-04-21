## ADDED Requirements

### Requirement: Multi-series chart schemas use labels and series props
All multi-series charts (BarChart, HorizontalBarChart, LineChart, AreaChart, RadarChart) SHALL accept `labels: string[]` and `series: SeriesSchema[]` as their primary data props. No `z.any()` fields SHALL appear in these schemas.

#### Scenario: BarChart accepts labels and series
- **WHEN** a BarChart is rendered with `labels: ["Q1","Q2","Q3"]` and `series: [{ category: "Revenue", values: [100, 120, 90] }]`
- **THEN** the chart renders vertical bars grouped by label with one bar per series entry

#### Scenario: LineChart accepts variant prop
- **WHEN** a LineChart is rendered with `variant: "smooth"`
- **THEN** the lines render with bezier curves (ECharts `smooth: true`)

#### Scenario: LineChart accepts variant step
- **WHEN** a LineChart is rendered with `variant: "step"`
- **THEN** the lines render as step lines (ECharts `step: "middle"`)

#### Scenario: AreaChart fills area under lines
- **WHEN** an AreaChart is rendered with valid `labels` and `series`
- **THEN** the area under each line is filled (ECharts `areaStyle: {}` on each series)

#### Scenario: RadarChart has no variant or axis label props
- **WHEN** a RadarChart schema is inspected
- **THEN** it contains only `labels` and `series` — no `variant`, `xLabel`, or `yLabel` fields

### Requirement: PieChart schema uses labels and values arrays
PieChart SHALL accept `labels: string[]` and `values: number[]` as parallel arrays. It SHALL NOT accept `data: { source: number[][] }`.

#### Scenario: PieChart renders slices from parallel arrays
- **WHEN** a PieChart is rendered with `labels: ["TCP","UDP","HTTP"]` and `values: [45, 30, 25]`
- **THEN** three slices are rendered with sizes proportional to the values

#### Scenario: PieChart donut variant
- **WHEN** a PieChart is rendered with `variant: "donut"`
- **THEN** the chart renders with an inner radius cutout (ECharts `radius: ["40%","70%"]`)

### Requirement: GaugeChart schema uses readings array
GaugeChart SHALL accept `readings: { name: string, value: number }[]` and optional `min` and `max` number props. It SHALL NOT accept `data: { source: number[][] }`.

#### Scenario: Single gauge reading
- **WHEN** a GaugeChart is rendered with `readings: [{ name: "CPU", value: 76 }]`
- **THEN** a single needle points to 76 on the gauge dial

#### Scenario: Multiple gauge readings render multiple needles
- **WHEN** a GaugeChart is rendered with `readings: [{ name: "CPU", value: 76 }, { name: "Memory", value: 54 }]`
- **THEN** two needles are rendered on the same dial

#### Scenario: GaugeChart respects min and max
- **WHEN** a GaugeChart is rendered with `min: 0`, `max: 200`, and `readings: [{ name: "Latency", value: 120 }]`
- **THEN** the dial scale runs from 0 to 200

### Requirement: HeatmapChart schema uses xLabels, yLabels, and 2D values
HeatmapChart SHALL accept `xLabels: string[]`, `yLabels: string[]`, and `values: number[][]` where `values[yIndex][xIndex]` is the cell value.

#### Scenario: Heatmap renders correct cell count
- **WHEN** a HeatmapChart is rendered with 24 xLabels (hours) and 7 yLabels (days)
- **THEN** 168 cells are rendered in a 24×7 grid

#### Scenario: Heatmap includes a visualMap
- **WHEN** a HeatmapChart is rendered
- **THEN** a continuous visualMap component is present with range derived from the min/max of all values

### Requirement: TreeMapChart schema uses a flat data array
TreeMapChart SHALL accept `data: { name: string, value: number, group?: string }[]`. When `group` is present on items, the view layer SHALL nest items under their group as ECharts children.

#### Scenario: TreeMap renders flat data
- **WHEN** a TreeMapChart is rendered with `data: [{ name: "eth0", value: 200 }, { name: "eth1", value: 150 }]`
- **THEN** two rectangles are rendered sized by value

#### Scenario: TreeMap groups items by group field
- **WHEN** data items share a `group` value (e.g., `group: "Subnet A"`)
- **THEN** those items are nested under a parent rectangle labelled "Subnet A"

### Requirement: ScatterChart schema uses datasets array
ScatterChart SHALL accept `datasets: ScatterSeriesSchema[]` and optional `xLabel` and `yLabel` strings.

#### Scenario: ScatterChart renders one series per dataset
- **WHEN** a ScatterChart is rendered with two entries in `datasets`
- **THEN** two distinct scatter series are rendered, each with its own color

#### Scenario: ScatterChart supports z dimension
- **WHEN** a Point has a `z` value
- **THEN** the point is rendered with size proportional to `z` (bubble chart mode)

### Requirement: All chart schemas are fully typed with no z.any fields
Every chart schema SHALL use precise Zod types. No chart schema SHALL use `z.any()`, `z.unknown()`, or `catchall(z.any())`.

#### Scenario: Schema inspection shows no any types
- **WHEN** the Zod schema for any chart is serialized to JSON Schema
- **THEN** no property has type `{}` or is absent from the properties definition
