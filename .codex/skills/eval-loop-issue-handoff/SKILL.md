---
name: eval-loop-issue-handoff
description: Use when turning GenUI eval-loop, benchmark, e2e, or fuzz findings into Linear-ready capability fix issues with fixture dataModel, generated DSL, screenshots, and anti-overfit acceptance criteria.
---

# Eval-Loop Issue Handoff

Use this skill to convert GenUI eval findings into durable Linear capability fix issues. The issue must target a reusable data-shape or rendering capability, not a list of failing fixtures.

Fixtures are evidence. The fix target is the capability class.

## When To Use

- "Raise backlog issues from an eval run"
- "Turn benchmark failures into tracker issues"
- "Write GitHub-style issues for low-scoring fixtures"
- "Summarize eval findings as actionable backlog items"
- "Create Linear issues from GenUI eval failures"

## Do Not Use For

- one-off cosmetic issues with no eval impact
- broad "improve the prompt" issues without a concrete failure mechanism
- implementation progress updates on an existing issue

Use a tracker-specific handoff skill when you are updating an existing issue rather than creating new ones.

## Goal

Create issues that are:

- evidence-first
- capability-oriented
- high leverage on overall eval quality or eval reliability
- readable by humans without local repo context
- resistant to fixture-specific implementation

## Triage Rules

Only raise an issue if at least one of the following is true:

- it affects multiple fixtures
- it affects one extremely low scorer, usually `<= 4/10`
- it exposes a parser/runtime correctness gap such as `null-required` or `unknown-component`
- it creates benchmark-vs-judge contradictions
- it is likely to improve overall score distribution, not just one niche visual

Avoid filing broad catch-all issues. Split by failure mechanism, not by every fixture.

Good issue boundaries:

- primitive numeric arrays default to raw tables
- object maps and nested collections drop rows
- unlabeled or null-heavy data triggers fabrication
- tuple timestamps / bytes / ratios are formatted incorrectly
- benchmark failures and judge scores contradict each other

Poor issue boundaries:

- "improve charts"
- "fix benchmark"
- "make prompt better"

## Required Evidence

Before writing the issue, collect:

- eval run id
- suite name: `e2e`, `fuzz`, or `benchmark`
- representative fixture ids
- per-fixture status
- per-fixture score breakdown
- failure reason when present
- judge feedback
- full fixture `dataModel`
- minimal DSL excerpt showing the bug
- screenshot path from the eval run
- snapshot path for the fixture
- `report-data.json` path

Optional but often useful:

- prompt
- `evalHints`
- `summary.md`
- `failing-patterns.json`
- `run.json`

Read [references/evidence-checklist.md](references/evidence-checklist.md) before submitting.

## Snapshot And Screenshot Rules

These are mandatory unless the source artifact truly does not exist.

- Always include the screenshot from the current eval run when present.
- Always include the snapshot path for each representative fixture.
- For Linear, upload screenshots through the official `fileUpload` presigned URL flow and embed `![fixture-id](assetUrl)` in the issue body.
- Do not use MCP base64 attachments for eval screenshots.
- Be explicit about suite-specific snapshot location:
  - `src/__tests__/e2e/snapshots/<fixture>.dsl`
  - `src/__tests__/e2e/fuzz-snapshots/<fixture>.dsl`
  - `src/__tests__/e2e/benchmark-snapshots/<fixture>.dsl`

Read [references/linear-screenshot-upload.md](references/linear-screenshot-upload.md) before creating Linear issues with screenshots.

## dataModel Rules

The issue body must include the fixture `dataModel`.

- Default: include the full `dataModel` in a fenced `json` block.
- If the model is extremely large, include the most relevant excerpt inline and the full source path in the same issue.
- Do not paraphrase the `dataModel`. Quote it from source.

## DSL Excerpt Rules

Do not paste the entire generated DSL unless the entire file is necessary.

- Include the smallest snippet that reveals the bug.
- Preserve identifiers and APIs exactly: `@Each`, `@Switch`, `Tree(...)`, `@FormatDate`, etc.
- If localized labels make the snippet hard to read in the tracker, replace labels with short ASCII placeholders while preserving structure.
- Keep the failure mechanism visible in the excerpt.

## Workflow

1. Identify candidate issues from `issues-map.md`, `report-data.json`, `summary.md`, and `failing-patterns.json`.
2. Group fixtures by reusable capability class, data shape, or rendering behavior, not by visual theme or judge dimension alone.
3. Decide whether the issue is worth raising using the triage rules above.
4. Collect required evidence for 2-5 representative fixtures.
5. Load [references/issue-template.md](references/issue-template.md) and fill it in.
6. Make sure the title states the capability being generalized, not the affected fixtures.
7. Make sure the issue explains why fixing it should improve overall eval quality or eval reliability.
8. Include a Generalization Gate so the implementation agent must explain anti-overfit evidence.
9. Submit the issue to Linear, or output ready-to-paste markdown if no tracker tool is available.

## Required Issue Structure

Every issue should contain these sections:

- `Source Eval Run`
- `Capability Goal`
- `Problem Class`
- `Evidence Fixtures`
- `Required Fix Shape`
- `Generalization Gate`

Use the exact structure from [references/issue-template.md](references/issue-template.md).

## Quality Bar

Before submitting, verify all of the following:

- Another agent could reproduce the problem from the issue alone.
- The issue contains run id, fixture ids, score breakdowns, `dataModel`, generated DSL, and screenshots.
- Linear screenshots use `fileUpload` asset URLs when Linear upload is available.
- The issue is scoped to one capability class.
- Acceptance criteria require a reusable rule or behavior, not a fixture-specific patch.
- The issue forbids fixture ids, sample-specific constants, snapshot edits, and listed-fixture-only branches in source changes.
- The issue explains why it is worth doing beyond the listed fixtures.

## Tracker Notes

This skill is optimized for Linear. For other trackers, preserve the same issue structure and evidence requirements, but adapt screenshot upload mechanics.

- Codex: use the available tracker tool or raw API.
- Claude Code: use the available tracker integration or output markdown.
- opencode: use the available tracker integration or output markdown.

If there is a tracker-specific skill for state/project/workpad behavior, combine that skill with this one. This skill defines issue content quality, not tracker workflow policy.

## References

- [references/issue-template.md](references/issue-template.md)
- [references/evidence-checklist.md](references/evidence-checklist.md)
- [references/triage-rules.md](references/triage-rules.md)
- [references/linear-screenshot-upload.md](references/linear-screenshot-upload.md)
