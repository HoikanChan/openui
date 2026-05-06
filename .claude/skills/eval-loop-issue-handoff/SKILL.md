---
name: eval-loop-issue-handoff
description: Use when turning eval-loop, benchmark, e2e, or fuzz findings into tracker issues. Produces issue bodies with concrete evidence: run id, fixture ids, score breakdowns, failure reasons, screenshot and snapshot paths, full dataModel, DSL excerpts, and measurable acceptance criteria. Works for Codex, Claude Code, and opencode.
---

# Eval-Loop Issue Handoff

Use this skill to report eval-loop problems as durable tracker issues. The output must be concrete enough that another agent can reproduce the problem without re-reading the whole run workspace.

This skill is tracker-neutral. It can be used to:

- create Linear issues
- create GitHub issues
- create Jira tickets
- draft markdown for manual submission

Agent-specific starter prompts are available in:

- `agents/claude-code.md`
- `agents/opencode.md`

## When To Use

- "Raise backlog issues from an eval run"
- "Turn benchmark failures into tracker issues"
- "Write GitHub-style issues for low-scoring fixtures"
- "Summarize eval findings as actionable backlog items"

## Do Not Use For

- one-off cosmetic issues with no eval impact
- broad "improve the prompt" issues without a concrete failure mechanism
- implementation progress updates on an existing issue

Use a tracker-specific handoff skill when you are updating an existing issue rather than creating new ones.

## Goal

Create issues that are:

- evidence-first
- mechanism-specific
- high leverage on overall eval quality or eval reliability
- readable by humans without local repo context

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

- Always include the screenshot path from the current eval run when present.
- Always include the snapshot path for each representative fixture.
- If the tracker supports file upload, attach the screenshot in addition to keeping the path in the body.
- Be explicit about suite-specific snapshot location:
  - `src/__tests__/e2e/snapshots/<fixture>.dsl`
  - `src/__tests__/e2e/fuzz-snapshots/<fixture>.dsl`
  - `src/__tests__/e2e/benchmark-snapshots/<fixture>.dsl`

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
2. Group fixtures by failure mechanism, not by visual theme.
3. Decide whether the issue is worth raising using the triage rules above.
4. Collect required evidence for 1-3 representative fixtures.
5. Load [references/issue-template.md](references/issue-template.md) and fill it in.
6. Make sure the issue explains why fixing it should improve overall eval quality or eval reliability.
7. Submit the issue to the tracker, or output ready-to-paste markdown if no tracker tool is available.

## Required Issue Structure

Every issue should contain these sections:

- `Summary`
- `Why This Matters`
- `Reproduction`
- `Current Behavior`
- `Expected Behavior`
- `Evidence`
- `Proposed Scope`
- `Acceptance Criteria`
- `References`

Use the exact structure from [references/issue-template.md](references/issue-template.md).

## Quality Bar

Before submitting, verify all of the following:

- Another agent could reproduce the problem from the issue alone.
- The issue contains run id, fixture id, screenshot path, snapshot path, and `dataModel`.
- The issue is scoped to one failure mechanism.
- Acceptance criteria are measurable against a future eval run.
- The issue explains why it is worth doing beyond a single fixture.

## Tracker Notes

This skill is agent-neutral and tracker-neutral.

- Codex: use the available tracker tool or raw API.
- Claude Code: use the available tracker integration or output markdown.
- opencode: use the available tracker integration or output markdown.

If there is a tracker-specific skill for state/project/workpad behavior, combine that skill with this one. This skill defines issue content quality, not tracker workflow policy.

## References

- [references/issue-template.md](references/issue-template.md)
- [references/evidence-checklist.md](references/evidence-checklist.md)
- [references/triage-rules.md](references/triage-rules.md)
