---
name: linear-issue-progress
description: Use when posting progress reports, benchmark/eval results, or local screenshots to Linear issues, especially when images must be attached through Linear fileUpload instead of base64 attachments.
---

# Linear Issue Progress

## Overview

Use this skill to leave a concise, evidence-backed Linear issue comment and attach local report images through Linear's official `fileUpload` presigned URL flow.

## Workflow

1. Gather the issue key or URL, local image paths, and the progress facts: changed files, verification commands, eval run IDs, residual risks, and next steps.
2. Put the report body in a temporary Markdown file. Keep it direct: summary first, then verification/eval evidence, then risks or remaining work.
3. Use `scripts/post_linear_issue_update.py` to resolve the issue, upload each image, and create the comment.
4. Return the Linear comment URL and a short summary of what was posted.

## Report Shape

Prefer this structure unless the user asks for another format:

```markdown
Progress update:
- Summary: ...
- Changed files: ...
- Verification: ...
- Eval runs: ...
- Notes / risks: ...
```

For eval screenshots, label each image with the fixture, run ID, or report name so the Linear comment remains useful after the local files disappear.

## Upload Rules

- Use `LINEAR_API_KEY` from the environment when available. If the user provides a key, pass it only through an ephemeral environment variable for the command; do not write it into files or repeat it in user-facing output.
- Prefer Linear GraphQL `fileUpload(contentType, filename, size, makePublic:false)` for every local image. Then `PUT` the raw bytes to the returned `uploadUrl` with Linear's returned headers plus the image `Content-Type`.
- Create the issue comment with Markdown image tags that point to the returned `assetUrl`.
- Do not use MCP/base64 attachment APIs unless the user explicitly asks for that fallback or the presigned upload path is impossible.
- Confirm success from the `commentCreate` response and report the comment URL.

## Script

Run from any working directory:

```bash
LINEAR_API_KEY=... python /Users/chenxie/.codex/skills/linear-issue-progress/scripts/post_linear_issue_update.py \
  --issue HOR-10 \
  --body-file /tmp/linear-progress.md \
  --image "timeseries tuple=/absolute/path/timeseries.png" \
  --image "byte large values=/absolute/path/bytes.png"
```

The script appends an `Eval screenshots` section to the body. It prints JSON containing the resolved issue, uploaded asset URLs, and created comment URL.
