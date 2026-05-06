# Claude Code Instructions

Run Claude Code from the repo root with a prompt like this:

---

Use `genui-eval-to-linear-issue` to turn GenUI eval findings into new Linear capability issues.

Requirements:

- Group findings by reusable capability class, not by fixture or judge dimension alone.
- Title each issue as a capability, such as "Generalize semantic value formatting for date/number/byte/percent fields".
- Include 2-5 representative evidence fixtures when available.
- Every evidence fixture must include:
  - eval run id and suite
  - fixture id
  - score breakdown
  - failure reason when present
  - judge feedback
  - full `dataModel` or justified excerpt
  - generated DSL or failure-preserving excerpt
  - screenshot as `![fixture-id](assetUrl)` when Linear upload is available
  - screenshot source path, snapshot path, and `report-data.json` path
- Use Linear `fileUpload` presigned URL upload for screenshots. Do not use MCP base64 attachments for eval screenshots.
- Include Required Fix Shape and Generalization Gate sections.
- Do not file broad issues like "improve prompt" or fixture-targeted issues like "fix object-map-by-id".

Read first:

- `SKILL.md`
- `references/issue-template.md`
- `references/evidence-checklist.md`
- `references/triage-rules.md`
- `references/linear-screenshot-upload.md`

If a tracker integration is unavailable, output ready-to-paste markdown using the same structure.
