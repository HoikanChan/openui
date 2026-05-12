---
name: react-ui-dsl-genui-eval-loop
description: Measure and iteratively improve the rendering quality of LLM-generated OpenUI DSL. Use when asked to evaluate, score, benchmark, or improve GenUI fixture quality, or when working with `pnpm eval` commands in `packages/react-ui-dsl`.
---

# React UI DSL — GenUI Quality Eval Loop

Measures rendering quality of LLM-generated DSL fixtures, produces a scored task bundle for an agent to act on, and verifies that the agent's changes actually improved quality.

## Prerequisites

```bash
# Install browser binaries (once per machine)
pnpm --filter @openuidev/react-ui-dsl exec playwright install chromium
```

Configure a judge in `packages/react-ui-dsl/.env` — pick one:

```bash
# Option A: Codex (uses your existing Codex auth)
EVAL_JUDGE_RUNNER=codex
# LLM_JUDGE_MODEL=gpt-5.4-mini   # default

# Option B: Claude Code (requires ANTHROPIC_API_KEY)
EVAL_JUDGE_RUNNER=claude-code
ANTHROPIC_API_KEY=sk-ant-...
# LLM_JUDGE_MODEL=claude-haiku-4-5-20251001   # default

# Option C: any OpenAI-compatible endpoint
LLM_API_KEY=sk-...
LLM_BASE_URL=https://api.openai.com/v1
LLM_JUDGE_MODEL=gpt-4o
```

Override the judge model at any time with `LLM_JUDGE_MODEL=<model>`.

**Judge concurrency.** Judging runs in a bounded worker pool, default 6 fixtures in parallel. Override with `EVAL_JUDGE_CONCURRENCY=<n>` — lower it (e.g. 3) when an upstream API rate-limits, raise it for local CLIs that can fan out. A 44-fixture benchmark judge step finishes in ~`(44 / concurrency) × per-fixture-latency`, so concurrency=6 keeps the full pipeline (regen + screenshot + judge) under the 10-min Bash ceiling for typical hosted judge models.

**Regen concurrency.** DSL regeneration also runs concurrent LLM calls, default 6. Override with `EVAL_REGEN_CONCURRENCY=<n>` to adjust.

## Typical Iteration Cycle

```
pnpm eval start                 → baseline eval, produces task bundle
pnpm eval status <run-id>       → inspect scores and failing patterns
                                  open task-bundle/adapters/<agent>.md
                                  agent edits source, writes result-bundle/
pnpm eval verify <run-id>       → full re-eval + delta report
```

## Commands

All commands run from `packages/react-ui-dsl`.

### Start a run

```bash
pnpm eval start                                      # all e2e fixtures
pnpm eval start --suite fuzz                         # all fuzz fixtures
pnpm eval start --suite benchmark                    # benchmark fixtures (dsl-benchmark.test.tsx)
pnpm eval start --fixture table-basic                # single fixture (vitest -t pattern)
pnpm eval start --fixture "table|card"               # regex — multiple fixtures
pnpm eval start --regen                              # regenerate DSL snapshots first
pnpm eval start --suite benchmark --regen --fixture <id>   # canonical "verify my fix" call
```

Pick the suite that owns the fixture: e2e fixtures live under `snapshots/`, fuzz under `fuzz-snapshots/`, benchmark under `benchmark-snapshots/`. Wrong `--suite` → vitest skips every test and the run dir stays empty.

### What `eval start` actually does

Each run executes this pipeline end-to-end inside `runs/<run-id>/`:

1. Spawns vitest on the matching suite file (regenerating DSL snapshots if `--regen` is set, which calls the configured LLM with the current prompt).
2. Writes `report-data.json` with the rendered fixture output.
3. Builds the React `report-app` via vite into the run dir (`index.html` + `assets/`).
4. Launches headless Chromium via Playwright, opens the built report, and **screenshots each fixture's `.preview-shell` to `task-bundle/screenshots/<fixture-id>.png`**.
5. Optionally runs the judge for scores + failing patterns.

You do **not** need to write your own Playwright/render harness. The screenshot file is the canonical visual evidence.

### Reading run output

- The vitest+vite logs are noisy with `Module level directives cause errors when bundled, "use client" in ...antd...` warnings — those are harmless. Look for the final `Run <id> complete` line and the process exit code.
- Confirm the run finished by checking that **both** `runs/<run-id>/report-data.json` and `runs/<run-id>/task-bundle/screenshots/<fixture-id>.png` exist. If only the run dir + `run.json` exist, the pipeline halted before screenshot capture — read the logs for the actual error.
- Do **not** check `screenshots/` mid-run; the dir is created early but populated only at step 4. Wait for exit before inspecting.

Creates `src/__tests__/e2e/eval/runs/<run-id>/` and prints:

```
✓ Run 20260425_181234_ab12 complete.
  Overall score: 6.4/10
  Failing patterns: 2
  Next steps:
    1. Review task bundle: ...runs/.../task-bundle
    2. Choose an agent and follow adapters/<agent>.md
    3. Run: pnpm eval verify 20260425_181234_ab12
```

### Check status

```bash
pnpm eval status              # list all runs
pnpm eval status <run-id>     # scores, state, history, next step
```

### Verify after agent edits

```bash
pnpm eval verify <run-id>
```

Runs the full fixture suite (regression gate), re-judges **only fixtures listed in `claimed-affected-fixtures.json`** (incremental judging), reuses baseline scores for unaffected fixtures, computes deltas, prints:

```
Verification complete for run 20260425_181234_ab12
  Score: 6.4 → 7.8 (+1.4)
  Outcome: SUCCESS
  Reused baseline scores: 39 fixture(s)
  Re-judged: 5 fixture(s)
```

If `claimed-affected-fixtures.json` is missing or empty, verify falls back to full re-judge and prints a warning.

### Phased commands

The eval pipeline is split into independent phases that can be run separately for recovery:

```bash
pnpm eval regen [<run-id>] [--suite=<suite>]   # Generate DSL snapshots (phase 1)
pnpm eval render <run-id>                       # Run vitest render (phase 2)
pnpm eval judge <run-id>                        # Judge fixtures (phase 4)
pnpm eval cache:clear                           # Clear judge cache and report-app cache
```

Each phase writes its status to `run.json` under `phases: { regen, render, screenshot, judge }`. Running a phase command skips phases that are already `done`.

### Fault recovery

If the eval loop is interrupted (timeout, crash, Ctrl+C), resume from the last completed phase:

**Interrupted during regen:**
```bash
pnpm eval regen <run-id>    # Continues generating missing DSL snapshots
```

**Interrupted during render:**
```bash
pnpm eval render <run-id>   # Re-runs vitest (skipped if already done)
```

**Interrupted during screenshot:**
```bash
pnpm eval judge <run-id>    # Re-captures missing screenshots, then judges
```

**Interrupted during judge:**
```bash
pnpm eval judge <run-id>    # Only judges fixtures missing scores; skips completed ones
```

Judge progress is persisted incrementally — each fixture score is written immediately after completion. If judge is interrupted, restarting only judges the remaining fixtures. Use `EVAL_FORCE_REJUDGE=1` to force full re-judge.

### Re-run judge on an existing run

```bash
pnpm eval judge <run-id>
```

Re-runs the judge step against an existing run's `report-data.json` and reuses screenshots from `task-bundle/screenshots/` when they exist; otherwise it rebuilds the report app and re-captures them. Use this when:

- `eval start --regen` finished regen + screenshot but the judge step was killed (timeout, OOM, etc.) — no need to redo regen.
- You changed the rubric / strict rules and want to re-score an old run without regenerating DSL.

### Calibrate the judge (optional)

```bash
pnpm eval calibrate <run-id>
```

Re-runs the judge with a `calibrated-rubric.md` in the run workspace and checks alignment with `corrections.json` (±1 tolerance per dimension).

## Judge Dimensions

Each fixture is scored on four 0–3 dimensions plus an overall 0–10:

| Dimension | What it measures |
|---|---|
| `component_fit` | Is the component type right for this data? |
| `data_completeness` | Are all important data model fields shown? |
| `format_quality` | Are dates, numbers, and values formatted correctly? |
| `layout_coherence` | Is the layout clear and logically organised? |

## Agent Workflow (when improving quality)

### 1. Open the task bundle

```
runs/<run-id>/task-bundle/
  summary.md              ← scores, worst fixtures, failing patterns
  constraints.md          ← what you may and may not change
  targets.json            ← per-fixture scores and feedback
  failing-patterns.json   ← aggregated root causes
  fixtures/               ← DSL snapshot copies
  screenshots/            ← Playwright screenshots
  adapters/
    claude-code.md        ← self-contained prompt for Claude Code
    codex.md              ← self-contained prompt for Codex
    opencode.md           ← self-contained prompt for opencode
```

Open the adapter doc for your agent (`adapters/claude-code.md`, etc.) — it is a self-contained prompt that tells you exactly what to do.

### 2. Edit source files

Fix the root causes identified in `summary.md`. Typical targets:

- `packages/react-ui-dsl/src/genui-lib/` — component implementations
- The DSL generation prompt (via `LLM_MODEL` and regen) — not a source file, controlled by env

### 3. Write the result bundle

When done, create these files under `runs/<run-id>/result-bundle/`:

```
result.json                    ← { runId, completedAt, agentType }
change-summary.md              ← what changed and why
touched-files.json             ← ["packages/react-ui-dsl/src/..."]
claimed-affected-fixtures.json ← ["table-basic", ...]
```

`result.json` schema:
```json
{
  "runId": "<run-id>",
  "completedAt": "2026-04-25T18:12:34Z",
  "agentType": "claude-code",
  "notes": "optional"
}
```

### 4. Run verify

```bash
pnpm eval verify <run-id>
```

The eval loop never commits. You commit when satisfied.

## Human Corrections (optional)

Drop `corrections.json` in the run workspace to calibrate the judge or inject hints:

```json
[
  {
    "id": "c1",
    "target": "judge",
    "state": "pending",
    "fixtureId": "table-basic",
    "score_corrections": { "component_fit": 3 },
    "text_feedback": "The table choice is correct here",
    "createdAt": "2026-04-25T00:00:00Z",
    "updatedAt": "2026-04-25T00:00:00Z"
  },
  {
    "id": "c2",
    "target": "prompt",
    "state": "pending",
    "text_feedback": "Always use FormatDate for date columns in tables",
    "createdAt": "2026-04-25T00:00:00Z",
    "updatedAt": "2026-04-25T00:00:00Z"
  }
]
```

- `"target": "judge"` → applied by `pnpm eval calibrate <run-id>`
- `"target": "prompt"` → forwarded as hints into the next task bundle

## Run States

| State | Meaning |
|---|---|
| `created` | Workspace initialised |
| `waiting_for_agent` | Task bundle ready |
| `verifying` | Verification in progress |
| `verified` | Done — check `verificationSummary` in `run.json` |
| `stalled` | No improvement for 3+ consecutive iterations |

## Non-Goals

- Does **not** auto-launch agents
- Does **not** auto-commit
- Does **not** replace vitest e2e/fuzz tests
- Does **not** evaluate performance or accessibility
