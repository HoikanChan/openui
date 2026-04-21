## Why

`@openuidev/react-ui-dsl` e2e tests currently tell contributors whether fixtures pass, but they do not provide a single place to inspect the rendered output of every fixture. Debugging requires hopping between test output, DSL snapshots, and ad hoc local rendering, while the default verification command should stay free of extra report artifacts.

## What Changes

- Add a dedicated e2e report command for `packages/react-ui-dsl` that generates a timestamped HTML report outside the default `test:e2e` flow.
- Capture per-fixture report data including component name, fixture id, prompt, expected description, DSL, pass/fail status, and lightweight failure reason when available.
- Render each fixture preview inside the generated report in a real browser context so contributors can inspect the final UI output without rerunning fixtures manually.
- Keep `pnpm test:e2e` focused on verification only; report generation becomes an explicit, developer-invoked workflow.
- Make report generation best-effort so failed fixtures still appear in the generated report with status information.

## Capabilities

### New Capabilities
- `react-ui-dsl-e2e-reporting`: Generate and open timestamped browser-viewable reports for `react-ui-dsl` e2e fixtures through a dedicated command, without changing the default verification command behavior.

### Modified Capabilities

## Impact

- Affected code: `packages/react-ui-dsl/src/__tests__/e2e/*`, `packages/react-ui-dsl/package.json`, and any helper modules added for report collection/generation.
- Developer workflow: contributors get a separate command for visual inspection of e2e fixtures while preserving the current `test:e2e` verification path.
- Output artifacts: local timestamped report directories containing HTML and report data files.
