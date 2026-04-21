## 1. Fixture Metadata And Report Collection

- [x] 1.1 Extend `packages/react-ui-dsl/src/__tests__/e2e/fixtures.ts` so each fixture includes an explicit expected description for report display
- [x] 1.2 Add e2e report entry types and a collector utility under `packages/react-ui-dsl/src/__tests__/e2e/` that can register fixture metadata, status, DSL, and lightweight failure reasons
- [x] 1.3 Update `packages/react-ui-dsl/src/__tests__/e2e/dsl-e2e.test.tsx` to enable report collection only when the dedicated report mode flag is set, while preserving the existing vitest pass/fail behavior

## 2. Report Output And Browser Preview

- [x] 2.1 Implement timestamped report directory creation under `packages/react-ui-dsl/src/__tests__/e2e/reports/` and write the structured `report-data.json` payload for each report run
- [x] 2.2 Implement the generated `index.html` report entry point so it renders fixture metadata and replays each fixture through the `react-ui-dsl` rendering path in the browser
- [x] 2.3 Add best-effort report finalization and browser-opening behavior that prints the generated report path and does not change the underlying test result if opening fails

## 3. Command Wiring And Verification

- [x] 3.1 Add a dedicated `packages/react-ui-dsl` package script for the e2e report workflow using the existing vitest entry point with report mode enabled
- [x] 3.2 Add focused tests for report collector/report writer behavior and the default-vs-report command split where practical
- [x] 3.3 Verify the workflow end to end by confirming `pnpm test:e2e` generates no report output and the dedicated report command generates a timestamped report directory with fixture entries
