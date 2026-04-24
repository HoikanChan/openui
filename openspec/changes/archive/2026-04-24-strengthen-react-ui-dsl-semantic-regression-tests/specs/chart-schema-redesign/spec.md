## MODIFIED Requirements

### Requirement: Chart virtual components SHALL be available as schema carriers
The chart capability SHALL define virtual DSL components `Series`, `ScatterSeries`, and `Point` using `defineComponent` with `component: () => null`. `Series` SHALL use schema `{ category: string, values: number[] }`. `ScatterSeries` SHALL use schema `{ name: string, points: PointSchema[] }`. `Point` SHALL use schema `{ x: number, y: number, z?: number }`. Chart runtime code that consumes these virtual components SHALL interpret their parsed element-wrapper form equivalently to plain object input when building chart options.

#### Scenario: Virtual components render nothing
- **WHEN** Series, ScatterSeries, or Point are rendered standalone
- **THEN** they return null and exist only to carry schema information in the DSL

#### Scenario: Virtual components appear in the DSL library spec
- **WHEN** `dslLibrary.toSpec()` is called
- **THEN** Series, ScatterSeries, and Point appear as component entries with their signatures

#### Scenario: Multi-series charts accept Series element wrappers
- **WHEN** a LineChart, BarChart, AreaChart, HorizontalBarChart, or RadarChart receives `series` entries parsed from virtual `Series(...)` DSL nodes
- **THEN** the chart runtime MUST unwrap those entries into `{ category, values }` semantics before building the final chart option
- **AND** the resulting ECharts option MUST contain the expected series names and data arrays instead of undefined placeholders
