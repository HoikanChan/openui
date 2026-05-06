# Linear Screenshot Upload

Use Linear's official presigned upload flow for eval screenshots. Do not upload screenshots through MCP base64 attachments for capability issues.

## Preconditions

- Codex or the plugin must already have the local screenshot file path.
- `LINEAR_API_KEY` must be available in the current shell environment.
- If `LINEAR_API_KEY` is missing, stop and report the missing key instead of silently skipping the screenshot or falling back to MCP base64.
- In normal setups the key is usually exported as a global zsh environment variable and inherited by the shell that launches Codex.

## Flow

1. Read the local screenshot file and determine `contentType`, `filename`, and byte `size`.
2. Call Linear GraphQL:

```graphql
mutation FileUpload($contentType: String!, $filename: String!, $size: Int!) {
  fileUpload(contentType: $contentType, filename: $filename, size: $size) {
    uploadUrl
    assetUrl
    headers {
      key
      value
    }
  }
}
```

3. Read `uploadUrl`, `assetUrl`, and `headers` from the response.
4. From the plugin service side or Node side, `PUT` the raw screenshot bytes directly to `uploadUrl` with all returned headers.
5. Embed the returned `assetUrl` in the issue body:

```md
![<fixture-id>](<assetUrl>)
```

## Failure Handling

- Retry upload once for transient network or 5xx failures.
- If upload still fails, do not silently omit the screenshot.
- Record the local screenshot path and upload error in the issue body.
- Prefer creating the issue with explicit upload failure evidence over creating an issue that appears screenshot-complete but is not.
