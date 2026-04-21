## 1. Virtual Components

- [ ] 1.1 Create `genui-lib/Charts/Series/index.tsx` — virtual component with `{ category: string, values: number[] }`
- [ ] 1.2 Create `genui-lib/Charts/Point/index.tsx` — virtual component with `{ x: number, y: number, z?: number }`
- [ ] 1.3 Create `genui-lib/Charts/ScatterSeries/index.tsx` — virtual component with `{ name: string, points: PointSchema[] }`

## 2. Shared View Utilities

- [ ] 2.1 Add `buildDataset(labels, series)` helper to `genui-lib/Charts/view-utils.ts` — converts `labels + SeriesSchema[]` to ECharts dataset source (header row + data rows)
- [ ] 2.2 Add `buildScatterSeries(datasets)` helper — converts `ScatterSeriesSchema[]` to ECharts scatter series array

## 3. Fix Existing Chart Schemas and Views

- [ ] 3.1 Replace `genui-lib/Charts/BarChart/schema.ts` with `{ labels, series, variant?, xLabel?, yLabel? }`
- [ ] 3.2 Rewrite `genui-lib/Charts/BarChart/view/index.tsx` — use `buildDataset`, map `variant` to ECharts `stack: "total"` or grouped, swap axes for stacked
- [ ] 3.3 Update `genui-lib/Charts/BarChart/index.tsx` description to "Vertical bars; use for comparing values across categories or devices"
- [ ] 3.4 Update `genui-lib/Charts/BarChart/stories/index.stories.tsx` with 网管 sample data
- [ ] 3.5 Replace `genui-lib/Charts/LineChart/schema.ts` with `{ labels, series, variant?, xLabel?, yLabel? }`
- [ ] 3.6 Rewrite `genui-lib/Charts/LineChart/view/index.tsx` — map `variant` to `smooth`/`step`/default
- [ ] 3.7 Update `genui-lib/Charts/LineChart/index.tsx` description to "Lines over time; use for latency, throughput, or any time-series metric"
- [ ] 3.8 Update `genui-lib/Charts/LineChart/stories/index.stories.tsx` with 网管 sample data
- [ ] 3.9 Replace `genui-lib/Charts/PieChart/schema.ts` with `{ labels, values, variant? }`
- [ ] 3.10 Rewrite `genui-lib/Charts/PieChart/view/index.tsx` — map `labels+values` to ECharts pie data, `variant: "donut"` sets `radius: ["40%","70%"]`
- [ ] 3.11 Update `genui-lib/Charts/PieChart/index.tsx` description to "Circular slices; use for protocol distribution or traffic breakdown by source"
- [ ] 3.12 Update `genui-lib/Charts/PieChart/stories/index.stories.tsx` with 网管 sample data
- [ ] 3.13 Replace `genui-lib/Charts/GaugeChart/schema.ts` with `{ readings: { name, value }[], min?, max? }`
- [ ] 3.14 Rewrite `genui-lib/Charts/GaugeChart/view/index.tsx` — map `readings` to ECharts gauge `data` array
- [ ] 3.15 Update `genui-lib/Charts/GaugeChart/index.tsx` description to "Gauge dials; use for KPI status, utilization %, or health score — supports multiple needles"
- [ ] 3.16 Update `genui-lib/Charts/GaugeChart/stories/index.stories.tsx` with multi-reading example

## 4. New Chart Types

- [ ] 4.1 Create `genui-lib/Charts/HorizontalBarChart/` — schema same as BarChart, view swaps axes (`xAxis: type:"value"`, `yAxis: type:"category"`), description targets long labels / ranked lists
- [ ] 4.2 Create `genui-lib/Charts/AreaChart/` — schema same as LineChart, view adds `areaStyle: {}` to each series, description targets bandwidth utilization / cumulative trends
- [ ] 4.3 Create `genui-lib/Charts/RadarChart/` — schema `{ labels, series }`, view builds `radar.indicator` from labels and `type:"radar"` series, description targets multi-metric device health comparison
- [ ] 4.4 Create `genui-lib/Charts/HeatmapChart/` — schema `{ xLabels, yLabels, values[][] }`, view flattens to `[x, y, value]` tuples, adds `visualMap: { calculable: true }`, description targets traffic patterns by hour/day
- [ ] 4.5 Create `genui-lib/Charts/TreeMapChart/` — schema `{ data: { name, value, group? }[] }`, view groups by `group` to build ECharts children nesting, description targets bandwidth breakdown by subnet/device group
- [ ] 4.6 Create `genui-lib/Charts/ScatterChart/` — schema `{ datasets: ScatterSeriesSchema[], xLabel?, yLabel? }`, view maps each dataset to a `type:"scatter"` series, description targets latency vs packet loss correlation
- [ ] 4.7 Add Storybook stories for each new chart type with realistic 网管 sample data

## 5. Exports and Registry

- [ ] 5.1 Update `genui-lib/Charts/index.ts` — export all 10 charts + Series + ScatterSeries + Point
- [ ] 5.2 Verify all new/updated components are registered in `dslLibrary`

## 6. Tests and Snapshots

- [ ] 6.1 Update E2E fixtures in `__tests__/e2e/fixtures.ts` — replace old `{ source: number[][] }` dataModels with new schema for BarChart, LineChart, PieChart, GaugeChart
- [ ] 6.2 Add E2E fixtures for HorizontalBarChart, AreaChart, RadarChart, HeatmapChart, TreeMapChart, ScatterChart
- [ ] 6.3 Delete stale DSL snapshots in `__tests__/e2e/snapshots/` and regenerate by running the E2E test suite
- [ ] 6.4 Run `vitest` in `packages/react-ui-dsl` and confirm all tests pass
