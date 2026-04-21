## Context

`packages/react-ui-dsl` already has a snapshot-backed e2e suite under `src/__tests__/e2e/` that validates the `DSL -> parse -> render` path with committed `.dsl` files. That suite is useful for regression detection, but it does not give contributors a fast way to inspect what all fixtures actually render without manually wiring a browser preview.

The requested change adds an explicit developer workflow for browsing fixture output while keeping the default verification path unchanged. The existing `test:e2e` command must remain focused on pass/fail verification; report generation is opt-in. The report should still be useful when some fixtures fail, but failure details can stay lightweight.

## Goals / Non-Goals

**Goals:**
- Add a dedicated command that runs the existing e2e fixtures and produces a timestamped HTML report.
- Keep `pnpm test:e2e` unchanged so normal test runs do not generate report artifacts.
- Show fixture metadata and browser-rendered previews in the report, including prompt, expected description, DSL, pass/fail status, and an optional failure reason.
- Make report output best-effort so a partially failing run still leaves behind a useful report directory.
- Minimize churn to the existing e2e fixture model and test assertions.

**Non-Goals:**
- Introduce pixel diffing, screenshot baselines, or browser automation tooling such as Playwright.
- Replace the existing vitest e2e suite with Storybook or another browsing workflow.
- Turn the report into a general-purpose test runner UI.
- Capture exhaustive stack traces or low-level DOM snapshots for every failure.

## Decisions

### Dedicated command over default `test:e2e` output

Report generation will live behind a separate package script, expected to be named alongside `test:e2e`, rather than being folded into the default verification command. This keeps CI and routine local test runs free of extra filesystem output and browser-launch side effects.

Alternative considered: always generate a report from `test:e2e`.
Rejected because the user explicitly wants reporting to be an intentional workflow, not the default behavior.

### Shared vitest entry point with a report-mode environment flag

The dedicated report script will reuse the existing e2e vitest entry point and enable report collection through an explicit environment flag. This avoids maintaining a second test harness while still letting the default command run without report generation.

Alternative considered: a standalone wrapper that orchestrates vitest and report generation as separate steps.
Rejected because the report data is produced naturally inside the test process, and a second orchestration layer would add complexity without clear benefit for this scope.

### Collect report entries during vitest execution

The e2e test harness will register each fixture execution with a small report collector. Each test case records its base metadata up front, then updates the entry with DSL, pass/fail status, and a lightweight failure message if an assertion or earlier step fails. The test still rethrows errors so vitest preserves the true exit status.

Alternative considered: parse vitest output after the run to reconstruct results.
Rejected because reconstructing fixture-level metadata from console output is fragile and loses access to structured test inputs such as prompt, data model, and expected description.

### Re-render in the report page instead of embedding test-time DOM

The generated report will contain structured data plus a browser page that re-renders fixtures with `Renderer` and `dslLibrary` when opened. This gives a more faithful preview than dumping `container.innerHTML`, and it avoids coupling the report to jsdom-specific markup.

Alternative considered: serialize the rendered HTML from the test process into a static report.
Rejected because static HTML is weaker for inspection, less representative of the browser environment, and less extensible for future filtering or richer previews.

### Timestamped report directory with separated data payload

Each run will write to a new timestamped directory such as `src/__tests__/e2e/reports/2026-04-20_19-30-00/`, containing at least `index.html` and `report-data.json`. Keeping the data in a separate JSON file simplifies debugging, keeps HTML generation simpler, and makes it easier to inspect or reuse the raw report payload later.

Alternative considered: generate a single self-contained HTML file.
Rejected because an external JSON payload keeps the report generator simpler and easier to inspect when debugging.

### Best-effort browser opening

The dedicated report command will print the generated report path and attempt to open it automatically on the local machine. Automatic opening is convenience only; failure to open the browser must not change the test outcome or suppress the report output.

Alternative considered: only print the path and never attempt to open the report.
Rejected because the requested workflow explicitly prefers generating and opening the report in one command.

## Risks / Trade-offs

- [Report page must execute browser-side rendering code] -> Keep the page runtime thin and reuse the existing renderer/library entry points instead of introducing a second rendering stack.
- [Best-effort report generation can hide partial collector bugs] -> Keep the report JSON schema explicit and add focused tests for collector behavior and report writing.
- [Timestamped directories can accumulate locally] -> Write reports into a dedicated `reports/` subtree so cleanup is straightforward and isolated from committed snapshots.
- [Automatic browser opening is platform-sensitive] -> Treat opening as optional convenience and preserve a printed path fallback.
- [Fixture preview rendering may fail in the report page even when the test already captured failure] -> Surface per-entry status and failure reason in the report so broken previews remain diagnosable.

## Migration Plan

1. Extend the e2e fixture metadata to include an expected description field suitable for report display.
2. Add report collection utilities that the existing vitest e2e tests can call while preserving their current assertions and exit behavior.
3. Add report generation output under `src/__tests__/e2e/reports/<timestamp>/`, keeping fixtures in their declared order in the report.
4. Add a dedicated package script that reuses the same vitest test entry point with report mode enabled, without changing `test:e2e`.
5. Verify the default e2e command still produces no report output and the report command does.
