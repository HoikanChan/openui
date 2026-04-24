## 1. Runtime contracts

- [x] 1.1 Add or extend shared chart helpers so virtual `Series` DSL nodes are normalized into plain `{ category, values }` data before multi-series chart views consume them
- [x] 1.2 Update LineChart, BarChart, AreaChart, HorizontalBarChart, and RadarChart views to rely on the shared normalized-series contract
- [x] 1.3 Add focused unit tests for `Series` element-wrapper normalization and keep existing scatter normalization coverage intact

## 2. Semantic chart assertions

- [x] 2.1 Strengthen the high-value raw-rows line-chart fixture so it asserts final ECharts option semantics, including axis labels and data-bearing series entries
- [x] 2.2 Review existing chart fixtures and promote at least the most regression-prone chart cases from mount/init checks to semantic option checks where that materially improves coverage
- [x] 2.3 Keep fixture snapshots aligned with the new assertions by regenerating only the affected chart fixtures

## 3. Verification

- [x] 3.1 Run the chart-focused unit tests covering shared helpers and chart prompt/runtime behavior
- [x] 3.2 Run the targeted React UI DSL e2e fixture for the semantic chart regression and confirm it fails without the runtime fix and passes with it
- [x] 3.3 Run the full React UI DSL e2e suite and record any unrelated residual warnings separately from pass/fail status
