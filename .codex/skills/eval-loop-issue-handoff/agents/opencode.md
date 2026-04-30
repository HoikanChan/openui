# opencode Instructions

Open opencode in this repo and start from a prompt like this:

---

Use `eval-loop-issue-handoff` to report eval-loop findings as durable tracker issues.

Rules:

- File issues by mechanism, not one issue per fixture.
- Prioritize issues that improve benchmark score distribution or eval-loop trustworthiness.
- Each issue must carry concrete evidence:
  - run id
  - suite
  - fixture ids
  - status
  - score breakdown
  - failure reason
  - judge feedback
  - full `dataModel`
  - minimal DSL excerpt
  - screenshot path
  - snapshot path
  - `report-data.json` path
- Use the repository template in `references/issue-template.md`.
- Keep issue scope narrow and acceptance criteria measurable against a future eval run.
- If the tracker does not support uploads, keep file paths in the issue body.
- If the tracker tool is unavailable, return markdown ready for manual submission.

Read first:

- `SKILL.md`
- `references/issue-template.md`
- `references/evidence-checklist.md`
- `references/triage-rules.md`
