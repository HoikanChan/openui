# Evidence Checklist

Collect these fields before filing the issue.

## Mandatory

- `runId`
- suite: `e2e`, `fuzz`, or `benchmark`
- representative fixture ids
- fixture `status`
- fixture `judgeScore.overall`
- fixture score breakdown:
  - `component_fit`
  - `data_completeness`
  - `format_quality`
  - `layout_coherence`
- `failureReason` when present
- `judgeScore.feedback`
- full fixture `dataModel`
- minimal DSL excerpt
- screenshot path
- snapshot path
- report path

## Strongly Recommended

- `prompt`
- `evalHints`
- `summary.md`
- `failing-patterns.json`

## Typical Sources

From `report-data.json`:

- `id`
- `prompt`
- `dataModel`
- `evalHints`
- `dsl`
- `status`
- `failureReason`
- `judgeScore.*`

From the eval run workspace:

- `task-bundle/screenshots/<fixture>.png`
- `task-bundle/summary.md`
- `task-bundle/failing-patterns.json`
- `run.json`

From suite snapshot directories:

- `src/__tests__/e2e/snapshots/<fixture>.dsl`
- `src/__tests__/e2e/fuzz-snapshots/<fixture>.dsl`
- `src/__tests__/e2e/benchmark-snapshots/<fixture>.dsl`

## Submission Check

Do not submit if any of these are missing without explanation:

- run id
- fixture id
- screenshot path
- snapshot path
- `dataModel`
- acceptance criteria
