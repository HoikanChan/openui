# Upload Contract

Use Linear's official presigned upload flow. Do not use MCP base64 attachments for this workflow.

## Preconditions

- The plugin or caller already has a local image file path.
- `LINEAR_API_KEY` must be available in the current shell environment.
- If `LINEAR_API_KEY` is missing, stop and report the missing key instead of falling back to another upload path.
- In normal setups the key is usually exported as a global zsh environment variable and inherited by the shell that launches Codex.

## Flow

1. Read the local image file from disk.
2. Determine `contentType`, `filename`, and byte `size`.
3. Call Linear GraphQL `fileUpload(contentType, filename, size)`.
4. Read `uploadUrl`, `assetUrl`, and `headers` from the Linear response.
5. From the plugin service side or Node side, `PUT` the raw image bytes directly to `uploadUrl` with the returned headers.
5. Return:
   - `assetUrl`
   - `![alt](assetUrl)` Markdown
   - upload metadata

If upload fails:

- retry once for transient failure
- return local path, retry result, and failure summary
- never silently swallow the failure
