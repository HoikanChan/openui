## 1. Judge Contract And Scoring

- [ ] 1.1 Extend the eval judge rubric to define supported `visual_issues` tags and explicitly cap `layout_coherence` for visible overlap, clipping, crowding, direction mismatch, whitespace imbalance, and weak hierarchy.
- [ ] 1.2 Update judge response parsing and eval types so `visual_issues` is normalized against the supported allowlist and defaults to an empty array when omitted or malformed.
- [ ] 1.3 Add or update unit tests for judge parsing and rubric-facing contract behavior, including backwards compatibility when historical results do not contain `visual_issues`.

## 2. Aggregation And Output Surfacing

- [ ] 2.1 Extend failing-pattern aggregation to derive issue-specific patterns from repeated `visual_issues` tags while preserving existing dimension-based summaries.
- [ ] 2.2 Update task-bundle generation so `targets.json`, summary artifacts, and copied judge results preserve per-fixture `visual_issues`.
- [ ] 2.3 Update the report app to display per-fixture visual issue tags and aggregate issue-level patterns without breaking older run data.

## 3. Validation And Regression Coverage

- [ ] 3.1 Add or update eval-loop tests that cover visual-issue aggregation, task-bundle serialization, and report compatibility with missing `visual_issues`.
- [ ] 3.2 Run targeted eval verification against known visual-failure fixtures such as `byte-large-values` and `percentage-as-decimal` to confirm the judge now reports visual diagnostics and lowers layout scores appropriately.
- [ ] 3.3 Run the relevant package test suite or targeted eval tests and document any remaining calibration gaps before implementation is considered complete.
