# Upload Contract

1. Read the local file.
2. Determine `contentType`, `filename`, and byte `size`.
3. Request Linear `fileUpload(contentType, filename, size)`.
4. `PUT` the raw bytes to the returned `uploadUrl` with the returned headers.
5. Return:
   - `assetUrl`
   - `![alt](assetUrl)` Markdown
   - upload metadata

If upload fails:

- retry once for transient failure
- return local path, retry result, and failure summary
- never silently swallow the failure
