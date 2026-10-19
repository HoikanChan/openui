# Issue #57 implementation plan

Scope: a single 20-second budget at the public Java generation entrypoints, cancellable HTTP requests, and whole-page Markdown fallback. BSP adaptation is explicitly deferred by the user to a follow-up issue.

Design: each invocation owns its timer, active request and terminal result. A request handle exists before network I/O; cancellation must abort headers/body waits and release resources. A monotonic clock and scheduler are injectable. The existing generation/validation strategies run inside this budget; this ticket does not implement the incremental repair work in #54.

Markdown: an independent request using the original user input, request context and response data. Reuse MarkdownRenderer for escaping/scenario wrapping. The host supplies the fallback callback; the reference service binds it to the explicit Markdown generation path. Emit a whole-page replacement instruction before Markdown fragments, never append fallback to a partial DSL page. No recursive fallback; no inherited DSL deadline.

Testing seams, as specified by #57: public GenUiGenerator APIs, HTTP transport over a local server, and the actual reference-service HTTP endpoint. Verify cancellation before headers/during reads, exact-deadline completion, late output rejection, one terminal/handoff, and Markdown request isolation.

- [x] Restore compilation after the supplied request/escape changes.
- [x] Add cancellable call contract and JDK HttpClient implementation; test actual blocked sockets.
- [x] Add invocation budget and public-entrypoint tests with a manual clock/scheduler.
- [x] Bind independent Markdown requests and whole-page replacement at the host endpoint.
- [x] Update documentation and create the BSP adaptation issue.
- [x] Run targeted tests, all Java tests, and two-axis code review; commit on the current branch.

Review: Standards found no hard violation; fixed dropped host fields and explicitly rejected unsupported component filters. Spec found cancellation blocked by output callbacks; split output/state locks, installed the timer before the initial write, and verified regressions red then green. Both reviewers subsequently confirmed the fixes.

The cross-language validator oracle was missing from the checkout. Regenerated using the repository's `pnpm --dir packages/lang-core run generate:validation-oracle` script so the full Java suite can run reproducibly.

Final verification: `mvn test -q` passed across all three Java modules: SDK 616 tests, eval CLI 9, reference service 27 (652 total, zero failures/errors/skips). Spotless checks passed for changed SDK/service Java files; `git diff --check` passed. The stale service test now compares its contract-version metadata with the SDK's actual base contract rather than the old literal version.
