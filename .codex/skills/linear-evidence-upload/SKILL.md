---
name: linear-evidence-upload
description: Use when a GenUI Linear workflow already decided a local image must be uploaded to Linear and embedded as Markdown.
---

# Linear Evidence Upload

This is a helper skill, not a user-facing workflow entry point.

Use it only when:

- a local image path already exists
- the caller already decided the image is required evidence
- the goal is to obtain a Linear `assetUrl` and Markdown image snippet

This skill does not decide whether an image should be uploaded. It only performs the upload contract and reports success or failure.

Do not use MCP base64 attachment helpers for this workflow. Use Linear's official `fileUpload` presigned URL flow only.

## Contract

Read [references/upload-contract.md](references/upload-contract.md) before uploading.

Return one of:

- upload success with `assetUrl`, Markdown image snippet, and upload metadata
- upload failure with local path, retry result, and failure summary

If `LINEAR_API_KEY` is not available in the current shell environment, stop and report that the key is missing. In normal setups this key is usually exported as a global zsh environment variable and inherited by the shell that launches Codex.

Never silently swallow a failed upload.
