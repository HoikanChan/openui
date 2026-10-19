# BSP cancellable generation transport and host integration

Tracking: https://github.com/HoikanChan/openui/issues/62. Follow-up to #57 / #54. The user explicitly deferred BSP adaptation until the real company runtime is available. Current development uses JDK HttpClient; the offline BSP stub is not evidence of production cancellation.

## Public adaptation seam

Implement `LlmTransport.newCall(String body, boolean stream)` returning `LlmCall`:

- Allocation must be nonblocking and perform no network I/O.
- `execute()` starts exactly one exchange and returns its response body.
- Thread-safe, idempotent `cancel()` / `close()` must promptly abort waiting for headers, blocked body reads and pre-execution requests. Release sockets/responses and prevent any later execution.
- A handle belongs to one request, never to a shared transport instance. Concurrent sessions must not cancel each other.
- Do not substitute thread interruption, timeout flags or stopping consumption for actual transport cancellation.

## Acceptance

- [ ] Identify the real BSP request/abort API and supported artifact version; replace `RestfulLlmTransport.newCall`'s unsupported default with that adapter.
- [ ] Preserve relative gateway routing, authentication/context headers and streaming configuration.
- [ ] Run cancellation contract tests against the real BSP client: no response headers, blocked response body, cancel before execute, cancel vs completion, repeated cancel, concurrent independent requests; verify resources are released.
- [ ] Bind the real company host to `GenUiGenerator.generateStream(request, sink, MarkdownFallback)`; the reference binding is `GenerateController`.
- [ ] Bind its existing independent Markdown flow to the callback; retain original intent/data and exclude Markdown time from the 20-second DSL budget.
- [ ] The host consumer must process `replace {scope: page, format: Markdown, reason: ...}` by resetting the entire prior DSL/parser/page state before rendering Markdown fragments. Verify an already partially rendered page is fully replaced.
- [ ] Verify one handoff/terminal, no post-cancellation DSL, and distinct CHECK_PASSED/TIMEOUT/MODEL_REQUEST_FAILED logs in the real host.

Until completed, the BSP adapter is not supported by the new deadline-controlled generation entrypoints. Legacy direct `post`/`postStream` are not proof of compliance.
