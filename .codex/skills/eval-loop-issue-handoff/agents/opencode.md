# opencode Instructions

Open opencode in this repo and start from a prompt like this:

---

Use `eval-loop-issue-handoff` to report GenUI eval-loop findings as Linear-ready capability fix issues.

Rules:

- File issues by reusable capability class, not one issue per fixture.
- Prioritize issues that improve benchmark score distribution or eval-loop trustworthiness.
- Each issue must carry concrete evidence:
  - run id
  - suite
  - fixture ids
  - score breakdown
  - failure reason
  - judge feedback
  - full `dataModel` or justified excerpt
  - generated DSL or failure-preserving excerpt
  - screenshot source path and Linear screenshot asset URL
  - snapshot path
  - `report-data.json` path
- Upload screenshots through Linear `fileUpload` presigned URLs and embed Markdown images.
- Use the capability issue template in `references/issue-template.md`.
- Include Required Fix Shape and Generalization Gate.
- Keep issue scope narrow and forbid fixture-specific implementation.
- If the tracker tool is unavailable, return markdown ready for manual submission.

Read first:

- `SKILL.md`
- `references/issue-template.md`
- `references/evidence-checklist.md`
- `references/triage-rules.md`
- `references/linear-screenshot-upload.md`
