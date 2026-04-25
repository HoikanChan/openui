## ADDED Requirements

### Requirement: Eval pipeline writes a single report-data.json per run
The eval pipeline SHALL write its quality evaluation output into the current run workspace as a single `report-data.json` file. This file SHALL be the sole source of truth for per-fixture judge scores and aggregated failure patterns for that run.

#### Scenario: report-data.json written for a run
- **WHEN** the eval pipeline completes a run
- **THEN** it writes `report-data.json` under `src/__tests__/e2e/eval/runs/<run-id>/`
- **THEN** that file contains fixture entries, judge scores, and top-level failing patterns for the same run

### Requirement: Screenshot capture per fixture
The eval pipeline SHALL capture a screenshot of each fixture's rendered output using Playwright against a renderable report view produced by the existing report pipeline. Screenshots SHALL be saved into the current run workspace.

#### Scenario: Screenshot captured after render stabilizes
- **WHEN** the eval pipeline runs a fixture
- **THEN** Playwright waits for `networkidle` plus 500ms before capturing the screenshot
- **THEN** the screenshot file is written under `task-bundle/screenshots/` or an equivalent run-scoped screenshots directory

#### Scenario: Screenshot failure marks a degraded run
- **WHEN** the pipeline cannot capture screenshots for a run
- **THEN** the run is marked as degraded in run metadata
- **THEN** judge results from that run are not treated as directly comparable to a normal screenshot-backed run without an explicit warning

### Requirement: VLM judge scoring per fixture
The eval pipeline SHALL call a multimodal VLM judge for each fixture, passing the screenshot when available, the DSL output, and the data model. The judge SHALL return a structured `JudgeScore` object.

#### Scenario: Judge returns structured scores
- **WHEN** the judge is called with a fixture's screenshot, DSL, and data model
- **THEN** the judge returns integer scores (0–3) for `component_fit`, `data_completeness`, `format_quality`, and `layout_coherence`
- **THEN** the judge returns an `overall` score (0–10) and a `feedback` string

#### Scenario: Judge called at temperature zero
- **WHEN** the VLM judge API is called
- **THEN** the request uses `temperature: 0`

### Requirement: failing_patterns aggregated at the run level
After all fixtures are judged, the eval pipeline SHALL aggregate cross-fixture failure patterns into the same `report-data.json`.

#### Scenario: failing_patterns written to report-data.json
- **WHEN** eval pipeline finishes all fixtures
- **THEN** `report-data.json` contains a `failing_patterns` array
- **THEN** each failing pattern includes `pattern`, `affected_fixtures`, `avg_score_impact`, `likely_cause`, and `agent_hint`

### Requirement: Eval pipeline runnable as an explicit CLI stage
The eval pipeline SHALL be runnable as part of an explicit CLI stage such as `node eval-loop.mjs start` or an equivalent eval CLI without replacing the existing `report-cli.mjs` workflow.

#### Scenario: Baseline eval run reuses committed snapshots by default
- **WHEN** the user starts an eval run without `--regen`
- **THEN** the pipeline reuses existing DSL snapshots
- **THEN** it produces run-scoped `report-data.json`, screenshots, and task-bundle inputs

#### Scenario: Baseline eval run regenerates DSL when requested
- **WHEN** the user starts an eval run with `--regen`
- **THEN** the pipeline regenerates DSL snapshots before judging
