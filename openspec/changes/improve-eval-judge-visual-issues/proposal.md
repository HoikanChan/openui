## Why

The current `react-ui-dsl` eval judge can see rendered screenshots, but it still over-scores layouts that are visibly hard to read. Cases with card direction mismatches, chart label overlap, clipped content, or severe whitespace imbalance are often judged as acceptable because the rubric emphasizes component choice and formatting more than screenshot-driven visual defects.

This makes the eval loop weak at catching style regressions and weak at explaining them. We need the judge to both penalize visible layout defects and surface them in a structured way so failing patterns can point engineers toward concrete visual fixes instead of generic "layout coherence" feedback.

## What Changes

- Add a structured visual diagnostics contract to the eval judge output so runs can record concrete screenshot-derived issues such as overlap, clipping, direction mismatch, crowding, whitespace imbalance, and weak hierarchy.
- Tighten the judge rubric so visible rendering defects materially reduce `layout_coherence` and, when severe, reduce `overall`.
- Extend failing-pattern aggregation and task-bundle/report outputs to group and display structured visual issues instead of relying only on free-form feedback text.
- Preserve compatibility for older run data by treating the new diagnostics field as additive rather than replacing the existing score fields.

## Capabilities

### New Capabilities
- `eval-visual-diagnostics`: Define how the eval judge identifies, scores, labels, and reports screenshot-derived visual quality defects for generated DSL fixtures.

### Modified Capabilities
- None.

## Impact

- `packages/react-ui-dsl/src/__tests__/e2e/eval/rubric.ts`
- `packages/react-ui-dsl/src/__tests__/e2e/eval/judge.ts`
- `packages/react-ui-dsl/src/__tests__/e2e/eval/types.ts`
- `packages/react-ui-dsl/src/__tests__/e2e/eval/failing-patterns.ts`
- `packages/react-ui-dsl/src/__tests__/e2e/eval/task-bundle-writer.ts`
- `packages/react-ui-dsl/src/__tests__/e2e/report-app/`
- Eval-loop tests that cover judge parsing, aggregation, and report/task-bundle output
