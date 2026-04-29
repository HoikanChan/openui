## Context

`packages/react-ui-dsl` already has a standalone chart stack built on ECharts, semantic Zod schemas, and a `dslLibrary` prompt surface that favors flattened top-level props over raw chart option objects. It does not currently provide a compact sparkline-style chart primitive for dense layouts such as metric cards, table cells, or lightweight summaries.

The requested MiniChart capability must stay fully inside `react-ui-dsl`. It cannot depend on `react-ui` chart components or schemas, even though `react-ui` has separate mini-chart components at the plain React layer. The DSL-facing API should follow the existing openui-lang style, keep arguments positional, and remain intentionally low-frequency and low-complexity for LLM generation.

## Goals / Non-Goals

**Goals:**
- Add a new DSL-facing `MiniChart` component to `react-ui-dsl`.
- Keep the public schema compact and easy for LLMs to emit: required `type`, required `data`, optional shared display props only.
- Support `line`, `bar`, and `area` mini-chart modes with strong built-in defaults.
- Reuse shared normalization and sizing logic internally while preserving separate ECharts rendering implementations per chart mode.
- Extend prompt, schema, stories, and tests so MiniChart is documented and validated like existing DSL components.

**Non-Goals:**
- Do not expose `MiniLineChart`, `MiniBarChart`, or `MiniAreaChart` as separate public DSL components.
- Do not mirror `react-ui` component-level knobs such as palette themes, animation toggles, click handlers, or className hooks.
- Do not retrofit the existing full-size `LineChart`, `BarChart`, or `AreaChart` schemas with a `mini` mode.
- Do not introduce multi-series mini charts or full axis/legend configuration.

## Decisions

### 1. Public DSL surface SHALL be one `MiniChart` component

The DSL surface will expose a single `MiniChart` component instead of three public components. The motivating trade-off is that mini charts are expected to be a low-frequency capability, so a single name keeps the prompt surface smaller and reduces the number of public chart signatures LLMs need to choose between.

Alternatives considered:
- Expose three public components (`MiniLineChart`, `MiniBarChart`, `MiniAreaChart`): clearer at the React layer, but noisier in the DSL library and prompt surface.
- Add a `mini` prop to existing full-size charts: rejected because it mixes two different data contracts and encourages invalid prop combinations.

### 2. `MiniChart` SHALL use a strict positional semantic signature

`MiniChart` will use positional OpenUI-style arguments rather than an object-style options bag. The intended authoring shape is:

`MiniChart(type, data, size?, color?)`

This keeps the component aligned with the rest of the DSL and preserves a small, predictable schema. `type` is required and limited to `line | bar | area`. `data` is required and limited to a single-series mini chart data shape: `number[]` or `{ value: number, label?: string }[]`. `size` and `color` are the only optional public display props.

Alternatives considered:
- Object-style props (`MiniChart({ type, data, ... })`): more extensible, but inconsistent with the package's existing component authoring style.
- Type-specific public props such as `variant`, `radius`, or `useGradient`: rejected to avoid turning a compact capability into a branchy option matrix.

### 3. Runtime SHALL keep three separate internal view implementations

Although the DSL surface is unified, the runtime will keep separate internal view components for line, bar, and area mini charts. A thin dispatcher component will choose the correct view based on `type`. All three views SHALL be built on `react-ui-dsl`'s existing standalone ECharts runtime and shared chart helpers rather than any `react-ui` component implementation.

This keeps each renderer simple and preserves chart-type-specific defaults:
- `line`: natural line behavior by default
- `bar`: fixed mini-bar rendering defaults
- `area`: default filled area behavior with built-in gradient handling

Alternatives considered:
- A single monolithic ECharts renderer with many conditional branches: rejected because it obscures chart-type behavior and makes future debugging harder.
- Recreating a separate non-ECharts rendering path just for mini charts: rejected because it would fragment the chart runtime, duplicate lifecycle/resizing behavior, and make regression debugging inconsistent with the rest of `react-ui-dsl`.

### 4. Shared mini-chart helpers SHALL own normalization and width-aware truncation

Mini charts should behave as compact trend summaries, not full historical plots. The implementation will normalize both accepted data shapes into one internal point list and centralize width-aware truncation in shared helpers so all three views show the most recent points that fit the available container space.

This behavior is intentionally part of the MiniChart capability rather than a caller concern because it keeps authored DSL simple and makes the component robust in constrained layouts.

Alternatives considered:
- Render every point regardless of width: simpler helper logic, but visually poor for dense cards and small cells.
- Require callers to pre-trim data: rejected because it leaks layout-specific logic into authored DSL.

### 5. MiniChart prompt guidance SHALL position it as a compact single-series trend primitive

Prompt and spec guidance will explicitly steer authors toward MiniChart only when the data is already a small single-series trend or sparkline candidate. Full-size charts remain the default recommendation for multi-series, labeled axes, or richer analytical views.

This boundary reduces misuse and keeps the existing chart guidance coherent.

### 6. Verification SHALL include real rendered-output checks

MiniChart should not be considered complete with only schema or helper-level tests. The implementation must add regression coverage that renders MiniChart through the real DSL runtime and inspects the resulting output, so semantic failures in ECharts option construction or runtime dispatch are caught where users would experience them.

Alternatives considered:
- Rely only on unit tests and `dslLibrary.toSpec()` assertions: rejected because chart regressions in this repository have already shown that parse success and mount success do not guarantee meaningful rendered semantics.

## Risks / Trade-offs

- [Low-frequency feature becomes too hidden] → Mitigation: add one prompt example and at least one Storybook example that clearly shows card-summary usage.
- [Unified public surface hides chart-type differences too aggressively] → Mitigation: keep separate internal views and encode stable per-type defaults instead of trying to unify render logic completely.
- [Width-aware truncation surprises authors expecting full history] → Mitigation: document that MiniChart is a compact summary view that prefers the most recent data that fits the container.
- [Future requests for more styling options pressure the schema] → Mitigation: start with only `size` and `color`; add new public props only if multiple real use cases require them.
- [MiniChart appears correct in isolated stories but regresses in DSL runtime] → Mitigation: add e2e regression fixtures and inspect the actual rendered result in the report/runtime harness.

## Migration Plan

No migration is required because MiniChart is an additive DSL capability. Existing chart components and existing fixtures continue to work unchanged. If the implementation proves problematic, rollback is limited to removing the new MiniChart registration, stories, tests, and prompt guidance without changing any existing chart contract.

## Open Questions

- Whether `size` should stay as `number | string` at the schema layer or be narrowed to the subset that `react-ui-dsl` can document clearly.
- Whether MiniChart needs an explicit no-label prompt rule, or whether the single-series compact-data rule is sufficient to steer LLM output.
