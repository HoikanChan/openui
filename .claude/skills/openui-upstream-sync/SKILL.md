---
name: openui-upstream-sync
description: Execute upstream code sync for OpenUI's lang-core and react-headless packages — file-level cherry-pick from thesysdev/openui via path-filtered diff + 3-way apply, theme-based markdown review, conventional-commit trailers for baseline tracking, and bounded auto-repair (Level 1-2). Use when user says 同步开源, 同步 lang-core, 同步 react-headless, sync upstream, pull upstream, or asks to bring upstream changes into packages/lang-core or packages/react-headless. Do NOT use for other packages (react-lang, react-ui, react-ui-dsl, openui-cli) — they are out of scope.
---

# OpenUI Upstream Sync

## Overview

Cherry-pick code from `thesysdev/openui` into local `packages/lang-core/` and `packages/react-headless/` only. The local fork has **no shared git history** with upstream — sync is content-level via path-filtered diff + 3-way apply, not `git merge` / `git cherry-pick`. Human decides themes; agent executes patches and reports.

Trigger phrases: `同步开源`, `同步 lang-core`, `同步 react-headless`, `sync upstream`.

## Scope (hard rule)

Pathspec is locked to `packages/lang-core packages/react-headless`. Every `git log`, `git diff`, and patch operation MUST carry this pathspec. Any hunk touching other packages (react-lang, react-ui, react-ui-dsl, openui-cli, docs, root configs) is dropped at patch generation, not later.

## Workflow

For each target package (process them independently — separate report, separate commit), run phases 1–5.

### Phase 1: Discover

```bash
.claude/skills/openui-upstream-sync/scripts/sync-status.sh <lang-core|react-headless>
```

Outputs: baseline SHA (from last `Synced-From:` trailer) + scope-filtered candidate commits.

### Phase 2: Report

Read candidate commit messages + diffs. Cluster into semantic themes. Write `.sync/reports/<YYYY-MM-DD>-<pkg>.md` using the report template in [EXAMPLES.md](EXAMPLES.md). For each theme:

- List source commit SHAs + 1-line summaries.
- Mark `⚠ N files filtered (out of scope)` when a source commit was only partially in scope.
- Pre-tick `[ ] verify-e2e` when touched files include `src/parser*`, `src/evaluator*`, `src/builtins*`, or `src/runtime*` (DSL-behavior surface).

**Stop and wait** for the human to check `[x] take` / `[x] skip` / `[x] defer` boxes.

### Phase 3: Apply

For each `[x] take` theme, for each source commit:

```bash
git diff <sha>^..<sha> -- packages/lang-core packages/react-headless \
  > .sync/staging/<sha>.patch
git apply --3way .sync/staging/<sha>.patch
```

Auto-repair (see [REFERENCE.md](REFERENCE.md#repair-levels)):

- **Level 1 (default on)** — A4 retry with `--ignore-whitespace`; A3 strip hunks for files deleted locally.
- **Level 2 (one attempt only)** — A1 conflict → write ONE merge proposal to `.sync/staging/<sha>.merged.<ext>`, do NOT auto-commit, request human review.
- **Level 3/4** — rejected. Never modify `react-ui-dsl/` etc. to "fix" sync.

### Phase 4: Verify

Default (β):

```bash
pnpm --filter @cloudsop/openui-lang-core build && pnpm --filter @cloudsop/openui-lang-core test
pnpm --filter @openuidev/react-headless build && pnpm --filter @openuidev/react-headless test
pnpm --filter @cloudsop/openui-react-ui-dsl build
```

If any theme has `[x] verify-e2e`, additionally:

```bash
pnpm --filter @cloudsop/openui-react-ui-dsl test:e2e
```

**NEVER run `:regen` during sync verification.** Committed `.dsl` snapshots are the frozen baseline — `regen` would launder real regressions into new baselines. If e2e fails, treat as a sync regression signal, not a snapshot staleness signal.

### Phase 5: Commit or pending

On success: one `chore(sync): <summary>` commit with `Synced-From:` trailers (one per successfully taken upstream commit). See template in [EXAMPLES.md](EXAMPLES.md#sync-commit-message).

On exit-gate trigger (see [REFERENCE.md](REFERENCE.md#exit-gates)): commit what succeeded (partial — trailer only lists actually-taken SHAs), then notify on **all three** channels:

1. Echo blocked-themes summary in chat.
2. Write `.sync/PENDING_REVIEW.md`.
3. Leave untracked `SYNC_NEEDS_REVIEW.md` at repo root (so `git status` flags it on every shell session).

## Hard rules (do not violate)

1. **Pathspec mandatory** on every git command reading upstream.
2. **Never run `pnpm test:e2e:regen*`** during sync.
3. **Never modify** files outside `packages/lang-core/` or `packages/react-headless/`. C1/D1 verify failures (downstream API break) are reported, not auto-fixed.
4. **Level 2 = 1 attempt.** Failure of the merged proposal triggers immediate exit, no retry.
5. **Hard exit** if total runtime > 10 minutes or any patch triggers ≥ 2 failure classes.
6. **Baseline = trailer.** Never invent a baseline file; always reparse `Synced-From:` trailers. Initial sync (no prior trailer) uses upstream/main root.

## References

- [REFERENCE.md](REFERENCE.md) — failure classification, repair-level table, exit gates, divergence registry
- [EXAMPLES.md](EXAMPLES.md) — report / commit / repair-report / divergence templates
- [scripts/sync-status.sh](scripts/sync-status.sh) — Phase 1 helper
