## ADDED Requirements

### Requirement: Chart DSL components SHALL expose semantic top-level props
The React UI DSL chart capability SHALL define chart schemas so chart inputs are authored as semantic top-level props instead of passthrough ECharts configuration objects. Chart components specifically SHALL use domain-semantic field names such as `labels`, `series`, `values`, `readings`, `datasets`, `xLabels`, `yLabels`, and `data`.

#### Scenario: Chart schema surface is semantic and flattened
- **WHEN** a consumer inspects exported prompt signatures or JSON Schema for any chart component
- **THEN** the component's semantic chart fields appear at the top level
- **AND** the exported shape does not require a `properties` wrapper object
- **AND** the exported shape does not expose ECharts-internal fields such as `dataset`, `xAxis`, `yAxis`, or raw series config objects

### Requirement: Chart schemas SHALL use precise semantic data shapes
All multi-series charts (BarChart, HorizontalBarChart, LineChart, AreaChart, RadarChart) SHALL accept `labels: string[]` and `series: SeriesSchema[]` as their primary data props. PieChart SHALL accept `labels: string[]` and `values: number[]`. GaugeChart SHALL accept `readings: { name: string, value: number }[]` and optional `min` and `max`. HeatmapChart SHALL accept `xLabels: string[]`, `yLabels: string[]`, and `values: number[][]`. TreeMapChart SHALL accept `data: { name: string, value: number, group?: string }[]`. ScatterChart SHALL accept `datasets: ScatterSeriesSchema[]` and optional `xLabel` and `yLabel`.

#### Scenario: BarChart accepts labels and series
- **WHEN** a BarChart is rendered with `labels: ["Q1","Q2","Q3"]` and `series: [{ category: "Revenue", values: [100, 120, 90] }]`
- **THEN** the chart renders vertical bars grouped by label with one bar per series entry

#### Scenario: LineChart supports smooth and step variants
- **WHEN** a LineChart is rendered with `variant: "smooth"`
- **THEN** the lines render with bezier curves using ECharts `smooth: true`
- **AND WHEN** a LineChart is rendered with `variant: "step"`
- **THEN** the lines render as step lines using ECharts `step: "middle"`

#### Scenario: PieChart renders from parallel arrays
- **WHEN** a PieChart is rendered with `labels: ["TCP","UDP","HTTP"]` and `values: [45, 30, 25]`
- **THEN** three slices are rendered with sizes proportional to the values

#### Scenario: GaugeChart renders from readings
- **WHEN** a GaugeChart is rendered with `readings: [{ name: "CPU", value: 76 }, { name: "Memory", value: 54 }]`
- **THEN** the gauge renders multiple needles on the same dial

#### Scenario: HeatmapChart uses x/y labels and matrix values
- **WHEN** a HeatmapChart is rendered with x labels, y labels, and `values[yIndex][xIndex]`
- **THEN** the chart renders a grid with one cell per matrix entry
- **AND** the ECharts option includes a continuous `visualMap` with range derived from the min/max values

#### Scenario: TreeMapChart groups flat items by group
- **WHEN** TreeMapChart data items share a `group` value
- **THEN** those items are nested under a parent rectangle labeled with that group

#### Scenario: ScatterChart supports z dimension
- **WHEN** a ScatterChart receives datasets whose points include `z`
- **THEN** the chart renders those points with size proportional to `z`

### Requirement: Chart virtual components SHALL be available as schema carriers
The chart capability SHALL define virtual DSL components `Series`, `ScatterSeries`, and `Point` using `defineComponent` with `component: () => null`. `Series` SHALL use schema `{ category: string, values: number[] }`. `ScatterSeries` SHALL use schema `{ name: string, points: PointSchema[] }`. `Point` SHALL use schema `{ x: number, y: number, z?: number }`.

#### Scenario: Virtual components render nothing
- **WHEN** Series, ScatterSeries, or Point are rendered standalone
- **THEN** they return null and exist only to carry schema information in the DSL

#### Scenario: Virtual components appear in the DSL library spec
- **WHEN** `dslLibrary.toSpec()` is called
- **THEN** Series, ScatterSeries, and Point appear as component entries with their signatures

### Requirement: Additional chart types SHALL be registered and exported
The chart capability SHALL register and export HorizontalBarChart, AreaChart, RadarChart, HeatmapChart, TreeMapChart, and ScatterChart alongside BarChart, LineChart, PieChart, and GaugeChart.

#### Scenario: HorizontalBarChart swaps axes
- **WHEN** a HorizontalBarChart is rendered with valid labels and series
- **THEN** ECharts uses `xAxis.type = "value"` and `yAxis.type = "category"`

#### Scenario: AreaChart fills under the line
- **WHEN** an AreaChart is rendered with valid labels and series
- **THEN** each series has `areaStyle: {}` set in the ECharts option

#### Scenario: RadarChart builds indicators from labels
- **WHEN** a RadarChart is rendered with labels and series
- **THEN** ECharts uses `type: "radar"` and `radar.indicator` built from `labels`

#### Scenario: New chart components appear in the DSL library
- **WHEN** `dslLibrary.toSpec()` is called
- **THEN** all six new chart component names appear in the returned spec

### Requirement: Chart schemas SHALL be fully typed
Every chart schema SHALL use precise Zod types. No chart schema SHALL use `z.any()`, `z.unknown()`, or `catchall(z.any())`.

#### Scenario: Chart JSON Schema contains concrete properties
- **WHEN** the Zod schema for any chart is serialized to JSON Schema
- **THEN** no property has type `{}` and no expected semantic property is omitted from the schema definition
