## Why

`packages/react-ui-dsl` currently exposes full-size chart components with semantic top-level props, but it does not provide a compact sparkline-style chart surface for lightweight trend summaries inside cards, tables, or dense status layouts. Adding a MiniChart capability now fills that gap with a low-friction DSL primitive that matches the package's standalone ECharts-based runtime instead of depending on `react-ui`.

## What Changes

- Add a new DSL-facing `MiniChart` component to `packages/react-ui-dsl`.
- Define a compact semantic schema for `MiniChart` using positional OpenUI-style arguments rather than object-style options.
- Support three visual modes through a required `type` argument: `line`, `bar`, and `area`.
- Limit the public DSL surface to low-complexity mini-chart props: `type`, `data`, and optional shared display props such as `size` and `color`.
- Implement MiniChart entirely on top of the existing standalone ECharts runtime in `react-ui-dsl`, with internal ECharts-backed mini chart views for line, bar, and area rendering and no reuse of `react-ui` runtime code.
- Register the new component in the DSL library, prompt surface, stories, and validation/test coverage.
- Add regression coverage that exercises MiniChart through actual DSL rendering, not just isolated schema checks.

## Capabilities

### New Capabilities
- None.

### Modified Capabilities
- `chart-schema-redesign`: extend the React UI DSL chart capability with a compact single-series mini chart surface that uses semantic top-level props and registers a new MiniChart component.

## Impact

- Affected code: `packages/react-ui-dsl/src/genui-lib/Charts`, `packages/react-ui-dsl/src/genui-lib/dslLibrary.tsx`, chart stories/tests, e2e regression fixtures, and the shared ECharts runtime helpers under `packages/react-ui-dsl/src/components/chart`.
- API surface: adds a new public DSL component, `MiniChart`, with a deliberately smaller schema than existing full-size charts.
- Prompting/LLM behavior: prompt guidance and JSON Schema will gain a low-frequency compact chart primitive for single-series trend rendering.
