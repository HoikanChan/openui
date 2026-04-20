## Why

`@openuidev/react-ui-dsl` has no test that covers the full `openui-lang DSL → parse → render` pipeline with realistic input. The existing Table unit test validates `mapColumnsToAntd` logic in isolation; it does not exercise the parser, the Renderer, or the component tree together. As a result, both parse errors (LLM output fails schema validation) and render errors (components crash or produce wrong output) go undetected until the demo or a downstream consumer breaks.

## What Changes

- Add a VCR-style e2e test suite under `packages/react-ui-dsl/src/__tests__/e2e/` that drives tests with real LLM-generated DSL snapshots.
- Introduce a fixtures config (`fixtures.ts`) that defines test scenarios per component, each with a plain-English prompt, a realistic API-shaped `dataModel`, and expected render assertions.
- Implement a `loadOrGenerate()` helper that reads a `.dsl` snapshot file when it exists and calls the OpenAI API to generate and save one when it does not.
- Add `test:e2e` and `test:e2e:regen` scripts to `packages/react-ui-dsl/package.json`.
- Install `@testing-library/react` and `openai` as dev dependencies in the package.

## Capabilities

### New Capabilities

- `dsl-e2e-snapshot-tests`: Full-pipeline integration tests for Table, PieChart, LineChart, BarChart, and GaugeChart, driven by LLM-generated DSL snapshots stored as committed `.dsl` files.

### Modified Capabilities

None.

## Impact

- Affected code: `packages/react-ui-dsl` (test infrastructure, `package.json`, `vitest.config.ts`).
- Dependencies: adds `@testing-library/react` and `openai` as dev dependencies.
- Systems: CI will run `test:e2e` against committed snapshots; `test:e2e:regen` is a developer-only command that requires `OPENAI_API_KEY`.
