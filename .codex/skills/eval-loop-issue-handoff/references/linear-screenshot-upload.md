# Linear Screenshot Upload

Use Linear's official presigned upload flow for eval screenshots. Do not upload screenshots through MCP base64 attachments for capability issues.

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

3. `PUT` the raw screenshot bytes to `uploadUrl` with all returned headers.
4. Embed the returned `assetUrl` in the issue body:

```md
![<fixture-id>](<assetUrl>)
```

## Failure Handling

- Retry upload once for transient network or 5xx failures.
- If upload still fails, do not silently omit the screenshot.
- Record the local screenshot path and upload error in the issue body.
- Prefer creating the issue with explicit upload failure evidence over creating an issue that appears screenshot-complete but is not.
