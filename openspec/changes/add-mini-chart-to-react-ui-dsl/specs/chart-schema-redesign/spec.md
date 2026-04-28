## MODIFIED Requirements

### Requirement: Chart schemas SHALL use precise semantic data shapes
All multi-series charts (BarChart, HorizontalBarChart, LineChart, AreaChart, RadarChart) SHALL accept `labels: string[]` and `series: SeriesSchema[]` as their primary data props. PieChart SHALL accept `labels: string[]` and `values: number[]`. GaugeChart SHALL accept `readings: { name: string, value: number }[]` and optional `min` and `max`. HeatmapChart SHALL accept `xLabels: string[]`, `yLabels: string[]`, and `values: number[][]`. TreeMapChart SHALL accept `data: { name: string, value: number, group?: string }[]`. ScatterChart SHALL accept `datasets: ScatterSeriesSchema[]` and optional `xLabel` and `yLabel`. MiniChart SHALL accept a required `type` (`line`, `bar`, or `area`), required single-series `data` (`number[]` or `{ value: number, label?: string }[]`), and optional shared display props only.

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

#### Scenario: MiniChart accepts compact single-series trend data
- **WHEN** a MiniChart is rendered with `type: "line"` and `data: [12, 18, 15, 21]`
- **THEN** the chart renders a compact single-series line sparkline
- **AND WHEN** a MiniChart is rendered with `type: "bar"` and `data: [{ value: 3, label: "Mon" }, { value: 5, label: "Tue" }]`
- **THEN** the chart renders a compact single-series bar sparkline using those values

### Requirement: Additional chart types SHALL be registered and exported
The chart capability SHALL register and export HorizontalBarChart, AreaChart, RadarChart, HeatmapChart, TreeMapChart, ScatterChart, and MiniChart alongside BarChart, LineChart, PieChart, and GaugeChart.

#### Scenario: HorizontalBarChart swaps axes
- **WHEN** a HorizontalBarChart is rendered with valid labels and series
- **THEN** ECharts uses `xAxis.type = "value"` and `yAxis.type = "category"`

#### Scenario: AreaChart fills under the line
- **WHEN** an AreaChart is rendered with valid labels and series
- **THEN** each series has `areaStyle: {}` set in the ECharts option

#### Scenario: RadarChart builds indicators from labels
- **WHEN** a RadarChart is rendered with labels and series
- **THEN** ECharts uses `type: "radar"` and `radar.indicator` built from `labels`

#### Scenario: MiniChart appears in the DSL library
- **WHEN** `dslLibrary.toSpec()` is called
- **THEN** `MiniChart` appears in the returned component list
- **AND** its signature is documented alongside the rest of the exported chart components

## ADDED Requirements

### Requirement: MiniChart SHALL expose a compact shared public surface
The React UI DSL MiniChart capability SHALL keep one public component name, `MiniChart`, across all mini-chart rendering modes. It SHALL use a required `type` argument to choose `line`, `bar`, or `area` rendering, and it SHALL expose only shared low-complexity display props on top of `data`. Type-specific runtime knobs such as line variants, bar radius, animation flags, palettes, gradients, and click handlers MUST remain internal implementation details.

#### Scenario: MiniChart surface stays aligned across chart modes
- **WHEN** a consumer inspects the exported prompt surface or JSON Schema for `MiniChart`
- **THEN** the same public prop set is visible for `line`, `bar`, and `area` modes
- **AND** the schema does not require authors to learn separate mini-line, mini-bar, or mini-area component names

#### Scenario: MiniChart does not expose full-chart configuration detail
- **WHEN** a consumer inspects the exported prompt surface or JSON Schema for `MiniChart`
- **THEN** the schema does not expose full-chart props such as `labels`, `series`, `xLabel`, or `yLabel`
- **AND** it does not expose raw ECharts config fields or type-specific styling knobs beyond the shared public props

### Requirement: MiniChart SHALL render through the standalone ECharts runtime
The React UI DSL MiniChart capability SHALL be implemented using the same standalone ECharts runtime family used by the rest of `packages/react-ui-dsl` chart components. MiniChart MUST NOT depend on `react-ui` chart components or a separate non-ECharts rendering path.

#### Scenario: MiniChart builds an ECharts-backed runtime view
- **WHEN** a MiniChart is rendered through the React UI DSL runtime
- **THEN** it produces an ECharts-backed chart view through `react-ui-dsl` chart runtime helpers
- **AND** the implementation does not import or wrap Mini chart components from `packages/react-ui`

#### Scenario: MiniChart is covered by runtime-level regression checks
- **WHEN** MiniChart DSL fixtures are exercised by regression coverage
- **THEN** the suite verifies meaningful rendered chart semantics from the actual runtime output
- **AND** the suite does not rely solely on schema parsing or component-registration assertions
