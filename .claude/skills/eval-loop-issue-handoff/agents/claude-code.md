# Claude Code Instructions

Run Claude Code from the repo root with a prompt like this:

---

Use `eval-loop-issue-handoff` to turn eval findings into tracker issues.

Requirements:

- Group findings by failure mechanism, not by fixture.
- Only raise issues that are high leverage for overall eval quality or eval reliability.
- Every issue must include:
  - eval run id
  - suite name
  - representative fixture ids
  - status and score breakdown
  - failure reason when present
  - judge feedback
  - full `dataModel`
  - minimal DSL excerpt
  - screenshot path
  - snapshot path
  - `report-data.json` path
- Use a GitHub-style structure:
  - `Summary`
  - `Why This Matters`
  - `Reproduction`
  - `Current Behavior`
  - `Expected Behavior`
  - `Evidence`
  - `Proposed Scope`
  - `Acceptance Criteria`
  - `References`
- If localized labels make DSL snippets hard to read, replace labels with short ASCII placeholders while preserving the buggy structure.
- Do not file broad issues like "improve prompt" or "fix benchmark".

Before submitting, read:

- `SKILL.md`
- `references/issue-template.md`
- `references/evidence-checklist.md`
- `references/triage-rules.md`

If a tracker integration is unavailable, output ready-to-paste markdown using the same structure.
