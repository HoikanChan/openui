# React UI DSL Chart E2E Fixture Design

Date: 2026-04-21

## Context

`packages/react-ui-dsl/src/__tests__/e2e/fixtures.ts` currently defines chart fixtures that mostly assert:

- `echarts.init` was called
- a chart container with `height: 300px` exists

Those checks only prove that rendering reached `BaseChart`. They do not prove that a chart fixture produced the correct ECharts option for its chart type, data model, or variant.

At the same time, the committed DSL snapshots already express richer chart semantics such as:

- the chart component chosen
- the data paths passed into that component
- chart-specific arguments such as labels, axis names, or variants

The current chart fixture assertions therefore sit at the wrong layer. They duplicate `BaseChart` smoke coverage while leaving chart-specific integration contracts mostly untested.

## Goal

Redesign chart e2e fixture assertions so they verify the renderer integration contract:

`DSL snapshot -> parser -> Renderer -> chart view -> echarts.setOption(option)`

The tests should prove that each chart fixture results in the expected ECharts option shape for its chart type and data mapping.

## Non-Goals

- Redesign the chart DSL itself
- Replace committed DSL snapshots with new snapshot formats
- Move chart integration coverage entirely into chart view unit tests
- Assert the full ECharts option tree by exact deep equality

## Current Problems

### Repeated smoke assertions

Per-chart assertions such as `echarts.init` and fixed `300px` height validate generic `BaseChart` behavior, not chart semantics.

### Weak failure signals

When a chart fixture fails today, the error often says little more than "ECharts was initialized" or "container not found". That is not actionable for regressions in axis mapping, series type, or data shaping.

### Mixed fixture responsibilities

Some chart fixtures include unrelated layout or table content, which makes failures harder to interpret. A fixture intended to validate a chart can currently fail because of extra UI around the chart.

### Overlapping chart coverage

Fixtures for `Series`, `ScatterSeries`, and `Point` currently behave like extra chart smoke tests instead of defending a distinct integration contract.

## Design

### 1. Split generic chart smoke coverage from chart semantics

Keep one dedicated `BaseChart` smoke test that covers:

- `echarts.init`
- `setOption`
- `dispose`
- default chart sizing

Remove equivalent checks from individual chart fixtures unless a fixture truly needs an extra DOM assertion for chart-specific behavior.

### 2. Make chart fixtures assert the final ECharts option

For chart fixtures, extract the last `setOption(option, true)` call from the mocked ECharts instance after rendering.

Expose that option to fixture assertions through a chart-focused assertion API, for example:

- `verifyChart({ option, setOptionCalls })`

instead of the current generic callback shape:

- `verify(container, { echartsInit })`

This changes the fixture authoring model from "prove a chart mounted" to "prove the renderer produced the right chart configuration."

### 3. Assert only key chart contracts

Each chart fixture should assert the minimum fields that uniquely define its integration behavior. Avoid asserting the entire option tree.

Examples:

- `PieChart`: series type is `pie`; `labels + values` map to `[{ name, value }]`; legend orientation remains correct
- `LineChart`: categorical x-axis uses `labels`; series type is `line`; `smooth` or `step` reflects the DSL variant
- `BarChart`: series type is `bar`; axis names are preserved; `stack` appears only for stacked variants
- `HorizontalBarChart`: axis orientation differs from normal bar chart
- `AreaChart`: area-specific configuration is present so it is not just a plain line chart
- `GaugeChart`: gauge series exists and readings map correctly
- `RadarChart`: indicators and series values are shaped correctly
- `HeatmapChart`: x/y labels and matrix values are transformed into the expected heatmap data structure
- `TreeMapChart`: treemap series type and grouped data shape are correct
- `ScatterChart`: scatter series type, dataset mapping, and x/y labels are correct

### 4. Keep fixture scope narrow

Chart fixtures should be centered on chart contracts, not extra surrounding content.

When a fixture mixes a chart with unrelated table, card, or layout behavior, prefer one of these:

- simplify the fixture so the chart is the primary behavior under test
- split ancillary UI coverage into the component fixtures that own that behavior

This reduces ambiguous failures and keeps chart fixtures easier to maintain.

### 5. Reevaluate helper chart component fixtures

`Series`, `ScatterSeries`, and `Point` should not remain as chart e2e fixtures unless each defends a distinct renderer integration contract.

Decision rule:

- keep them in e2e only if they verify unique chart input assembly that is visible in the final option
- otherwise move their primary coverage closer to schema/view tests and let chart e2e focus on top-level chart outcomes

## Approaches Considered

### Approach A: Assert key option fields in chart e2e fixtures

Pros:

- matches the intended integration layer
- produces actionable failures
- avoids brittle full-option snapshots
- keeps maintenance cost reasonable

Cons:

- needs a helper for extracting and normalizing `setOption` input

### Approach B: Snapshot normalized ECharts options

Pros:

- broad coverage with little per-fixture code
- easy to add for new charts

Cons:

- noisier diffs
- easier to bless incorrect output into snapshots
- weaker signal when a specific semantic contract regresses

### Approach C: Move option assertions to chart view unit tests

Pros:

- smaller, more targeted tests
- lower renderer-path setup cost

Cons:

- misses the `Renderer -> chart view` integration boundary
- does not match the desired role of these e2e fixtures

## Decision

Adopt Approach A.

Chart e2e fixtures will assert key fields of the final ECharts option generated by the renderer pipeline. `BaseChart` smoke behavior will be tested separately and removed from per-chart fixture repetition.

## Expected Test Structure

The resulting test suite should have three layers:

1. Fixture coverage tests
   Ensures every top-level DSL component keeps a fixture and matching committed snapshot.

2. Chart e2e integration tests
   Ensures chart DSL snapshots and data models produce the expected ECharts option contracts.

3. BaseChart smoke tests
   Ensures chart initialization, disposal, and default sizing still work.

## Success Criteria

- A regression in chart option mapping fails with a chart-specific assertion, not a generic smoke failure
- Per-chart fixtures no longer repeat `BaseChart` default sizing checks
- Each retained chart fixture has a distinct semantic purpose
- Mixed chart-plus-non-chart fixtures are simplified or split where necessary
- The suite remains maintainable without full deep-equality snapshots of ECharts options

## Risks and Mitigations

### Risk: Option assertions become too brittle

Mitigation:

- assert only stable, high-signal fields
- avoid full deep equality
- normalize only where the rendering path adds harmless noise

### Risk: Helper component coverage becomes unclear

Mitigation:

- explicitly decide whether `Series`, `ScatterSeries`, and `Point` remain in e2e
- document the specific contract each retained fixture owns

### Risk: BaseChart behavior loses coverage during refactor

Mitigation:

- introduce or preserve a dedicated `BaseChart` smoke test before removing repeated per-fixture checks
