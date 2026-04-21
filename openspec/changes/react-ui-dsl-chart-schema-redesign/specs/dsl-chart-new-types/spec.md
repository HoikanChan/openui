## ADDED Requirements

### Requirement: HorizontalBarChart is a registered DSL component
A `HorizontalBarChart` component SHALL be registered in `dslLibrary` with schema `{ labels: string[], series: SeriesSchema[], variant?: "grouped" | "stacked", xLabel?: string, yLabel?: string }` and a description that guides LLM usage.

#### Scenario: HorizontalBarChart description targets long labels
- **WHEN** the DSL library spec is inspected
- **THEN** HorizontalBarChart description mentions preferring it for long category labels or ranked lists

#### Scenario: HorizontalBarChart renders with swapped axes
- **WHEN** a HorizontalBarChart is rendered with valid labels and series
- **THEN** ECharts xAxis is `type: "value"` and yAxis is `type: "category"`

### Requirement: AreaChart is a registered DSL component
An `AreaChart` component SHALL be registered with schema matching LineChart (`labels`, `series`, `variant?: "linear" | "smooth" | "step"`, `xLabel?`, `yLabel?`).

#### Scenario: AreaChart fills area under series lines
- **WHEN** an AreaChart is rendered
- **THEN** each series has `areaStyle: {}` set in the ECharts option

#### Scenario: AreaChart description targets cumulative metrics
- **WHEN** the DSL library spec is inspected
- **THEN** AreaChart description references bandwidth utilization or cumulative traffic

### Requirement: RadarChart is a registered DSL component
A `RadarChart` component SHALL be registered with schema `{ labels: string[], series: SeriesSchema[] }`.

#### Scenario: RadarChart renders as spider/web chart
- **WHEN** a RadarChart is rendered with labels and series
- **THEN** ECharts uses `type: "radar"` with `radar.indicator` built from `labels`

#### Scenario: RadarChart description targets multi-metric comparison
- **WHEN** the DSL library spec is inspected
- **THEN** RadarChart description references comparing multiple metrics across devices or interfaces

### Requirement: HeatmapChart is a registered DSL component
A `HeatmapChart` component SHALL be registered with schema `{ xLabels: string[], yLabels: string[], values: number[][] }`.

#### Scenario: HeatmapChart includes a visualMap
- **WHEN** a HeatmapChart is rendered
- **THEN** the ECharts option includes a `visualMap` component with `calculable: true`

#### Scenario: HeatmapChart description targets time-pattern analysis
- **WHEN** the DSL library spec is inspected
- **THEN** HeatmapChart description references traffic patterns by hour/day or alert frequency

### Requirement: TreeMapChart is a registered DSL component
A `TreeMapChart` component SHALL be registered with schema `{ data: { name: string, value: number, group?: string }[] }`.

#### Scenario: TreeMapChart description targets resource breakdown
- **WHEN** the DSL library spec is inspected
- **THEN** TreeMapChart description references bandwidth or resource breakdown by subnet or device group

### Requirement: ScatterChart is a registered DSL component
A `ScatterChart` component SHALL be registered with schema `{ datasets: ScatterSeriesSchema[], xLabel?: string, yLabel?: string }`.

#### Scenario: ScatterChart description targets correlation analysis
- **WHEN** the DSL library spec is inspected
- **THEN** ScatterChart description references correlations such as latency vs packet loss

### Requirement: All new chart types are exported from Charts index
HorizontalBarChart, AreaChart, RadarChart, HeatmapChart, TreeMapChart, and ScatterChart SHALL all be exported from `genui-lib/Charts/index.ts`.

#### Scenario: New charts appear in DSL library
- **WHEN** `dslLibrary.toSpec()` is called
- **THEN** all six new chart component names appear in the returned spec
