## Why

React UI DSL currently catches syntax and basic rendering regressions, but it can still accept DSL that parses successfully and mounts a chart container while rendering semantically empty output. The recent line-chart failure showed that a `Series(...)` DSL node could reach chart views as an element wrapper instead of plain `{ category, values }` data, leaving ECharts with axes but no visible lines.

## What Changes

- Add semantic regression coverage for React UI DSL so tests fail when runtime output is structurally valid but semantically empty or incorrect.
- Strengthen chart runtime contracts so multi-series chart views treat virtual `Series` DSL nodes the same as plain data objects.
- Upgrade high-value chart e2e fixtures to assert final ECharts option semantics instead of only checking that `echarts.init()` was called.
- Add reusable runtime-level tests around DSL element unwrapping so similar failures are caught before they reach full e2e.

## Capabilities

### New Capabilities
- `semantic-regression-testing`: Detect parse-success/runtime-semantic failures in React UI DSL fixtures by asserting meaningful rendered semantics rather than container creation alone.

### Modified Capabilities
- `chart-schema-redesign`: Multi-series chart components must correctly interpret virtual `Series` DSL nodes when building runtime chart options.

## Impact

- Affected code: `packages/react-ui-dsl/src/genui-lib/Charts/*`, `packages/react-ui-dsl/src/__tests__/e2e/*`, and chart-focused unit tests.
- Affected behavior: LineChart, BarChart, AreaChart, HorizontalBarChart, and RadarChart runtime handling of `Series(...)` inputs.
- Affected quality gates: chart regression fixtures will assert chart option semantics, not just mount/init side effects.
