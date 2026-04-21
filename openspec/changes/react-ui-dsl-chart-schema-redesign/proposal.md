## Why

The current chart schemas in `react-ui-dsl` expose raw ECharts config (`data: { source: number[][] }`, all visual props as `z.any()`), which forces the LLM to understand ECharts internals to generate charts. This produces unreadable DSL like `BarChart(data.x, undefined, undefined, {type:"category"}, [{type:"bar"}])` and frequent generation failures. The project is a network management system (网管系统) where charts are critical for displaying time-series metrics, device health, and traffic analysis.

## What Changes

- **BREAKING** Replace all 4 existing chart schemas (BarChart, LineChart, PieChart, GaugeChart) with domain-friendly `labels/series/values/readings` props — no more raw ECharts config
- Add 6 new chart types: HorizontalBarChart, AreaChart, RadarChart, HeatmapChart, TreeMapChart, ScatterChart
- Add 3 virtual components: `Series`, `ScatterSeries`, `Point` (mirrors react-ui genui-lib pattern)
- Update component `description` strings to guide LLM on when to use each chart type
- Update view layer to convert domain props → ECharts options internally
- Update Storybook stories and E2E fixtures for all changed/new charts

## Capabilities

### New Capabilities

- `dsl-chart-schemas`: Domain-friendly Zod schemas for all 10 chart types — `labels/series` for multi-series, `labels/values` for 1D, `readings` for gauge, `xLabels/yLabels/values` for heatmap, `data` for treemap, `datasets` for scatter
- `dsl-chart-virtual-components`: Three virtual components (`Series`, `ScatterSeries`, `Point`) used as building blocks for chart series data
- `dsl-chart-new-types`: Six new chart components — HorizontalBarChart, AreaChart, RadarChart, HeatmapChart, TreeMapChart, ScatterChart — wired up to ECharts

### Modified Capabilities

- `llm-friendly-dsl-component-props`: Chart components now expose flat, named props instead of a passthrough ECharts config object — consistent with the existing flattening work on other DSL components

## Impact

- `packages/react-ui-dsl/src/genui-lib/Charts/` — all schema, view, and index files
- `packages/react-ui-dsl/src/genui-lib/Charts/view-utils.ts` — new `buildDataset` helper
- `packages/react-ui-dsl/src/genui-lib/Charts/index.ts` — expanded exports
- `packages/react-ui-dsl/src/__tests__/e2e/fixtures.ts` — updated prompts and dataModels
- `packages/react-ui-dsl/src/__tests__/e2e/snapshots/*.dsl` — regenerated snapshots
- No changes to `packages/react-ui-dsl/src/components/chart/` (BaseChart, ECharts wrappers, theme)
