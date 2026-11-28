---
name: verify
description: Verify OpenUI React DSL rendering in real Chromium. This skill should be used when the user reports or fixes missing content, component or parser errors, blank output, chart clipping, overflow, wrapping, sizing, spacing, or other rendering behavior.
---

# Verify OpenUI rendering

Drive the workspace `Renderer` and `dslLibrary` with the scenario's real DSL and host data. Read [the feature map](features/README.md) before choosing coverage. Use the example only as an input format, not as a bug fixture.

## Launch

From the repository root, install the workspace dependencies if `packages/react-ui-dsl/node_modules/playwright` is absent. Run:

```sh
node .claude/skills/verify/scripts/verify.mjs \
  --spec .claude/skills/verify/examples/scenario.json \
  --evidence .audit/verify/example
```

The helper starts Vite on an unused loopback port. It loads the package source and launches Chromium with the installed Playwright. Readiness requires the verification page to load and expose its scenario state. No server, login, database, or LLM is needed.

For interactive inspection, append `--headed`. For a known-bad checker test, create a separate input whose `expectText` is `["99"]` and append `--smoke-test`. Never use this flag to change a real failure into a pass.

## Doctor

Run the same command with `--doctor` to check startup without asserting a feature:

```sh
node .claude/skills/verify/scripts/verify.mjs \
  --spec .claude/skills/verify/examples/scenario.json \
  --evidence .audit/verify/doctor \
  --doctor
```

Require `doctor.json` to contain `ok: true` and the expected package version, source revision, input hash, URL, and Chromium version. The helper creates and owns its port and browser. Do not attach it to an existing browser or server. A failed health check is inconclusive.

## Drive

Copy [the scenario format](examples/scenario.json) into `.audit/verify/<task>/input.json`. Set `source` to a traceable issue or model run. Preserve the reported DSL and data. Do not create synthetic repair cases under `packages/genui-java-sdk/repair-cases/`.

Each scenario has:

- `id`, `dsl`, and `data`. Data defaults to `{}`.
- `width`, which is the rendered container width. Use the width from the report. Add `viewport` only for a specific viewport test.
- `expectText` and `absentText` for visible text.
- `sameRow` and `childrenFillWidth` for actual layout geometry. Each target has a DOM-index `path` and an optional `text` that identifies its contents.
- `tolerance` in CSS pixels, defaulting to `2`.
- `noHorizontalOverflow`, which defaults to `true`, and `noClipping`, which defaults to `false`.
- `readyDelayMs` for asynchronous chart layout. Increase it only when the observed rendering requires it.

The renderer root is path `0`. A child path appends `/index`. Run the scenario once with only text assertions to obtain all node paths and bounds in `report.json`. Select a target with a unique path and text. A missing or ambiguous target fails the check.

Inspect each saved screenshot. Geometry does not prove that chart labels are visible or readable. Use `noClipping` for CSS overflow evidence and inspect the image for text clipping. Add a specific browser assertion when the generic checks cannot observe the requirement.

Parser errors, `Renderer` errors, uncaught browser errors, and console errors fail by default. `allowParserErrors` and `allowRuntimeErrors` are available only when the scenario explicitly tests that error behavior. `allowedConsoleErrors` lists exact expected messages. Record any exception in the result rather than hiding it.

Run the structural checker tests after editing the helper:

```sh
node .claude/skills/verify/scripts/self-test.mjs
```

## Evidence

Keep the evidence directory after every run. It contains:

- `input.json`, the normalized input.
- `doctor.json`, the runtime identity and health check.
- `report.json`, the source, text, parse and runtime errors, browser errors, measurements, and failures.
- `<scenario-id>.png` and `page.png`, screenshots of the rendered result and full page.

Exit `0` means every declared assertion passed. Exit `1` means a failed or inconclusive check. Do not report a rendering fix as verified when the page did not load, a target was missing, or the target rendered on a different surface. For bug fixes, run the same input before and after the fix and quote the changed observation.

## Cleanup

The helper closes its own Chromium, stops its own Vite process, and removes its temporary app on success, failure, or interruption. Do not kill processes by name. Evidence remains after cleanup.

## Helpers

- [`scripts/verify.mjs`](scripts/verify.mjs) starts, drives, records, and cleans up the verification app.
- [`scripts/check-spec.mjs`](scripts/check-spec.mjs) validates input and measures pass/fail conditions.
- [`scripts/self-test.mjs`](scripts/self-test.mjs) checks accepted and rejected assertions.
- [`scripts/vite.config.mjs`](scripts/vite.config.mjs) binds the temporary app to the workspace source.
- [`assets/harness.tsx`](assets/harness.tsx) renders real scenarios and collects browser geometry.
