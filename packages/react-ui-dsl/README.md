# `@openuidev/react-ui-dsl`

DSL component library for OpenUI built on React, Ant Design v5, and ECharts.

## Development

Run commands from `packages/react-ui-dsl`:

```bash
pnpm build
pnpm test
pnpm typecheck
pnpm ci
```

## E2E Workflow

The package keeps committed `.dsl` snapshots under `src/__tests__/e2e/snapshots` and validates them against fixture-based e2e tests in `src/__tests__/e2e`.

Common commands:

```bash
pnpm test:e2e
pnpm test:e2e:report
pnpm test:e2e:regen
pnpm test:e2e:regen:fixture -- -t table-basic
pnpm test:e2e:regen:fixture -- -t "table-basic|button-primary"
pnpm test:fuzz
pnpm test:fuzz:report
pnpm test:fuzz:regen
```

What each command does:

- `pnpm test:e2e` runs the e2e suite without generating a report.
- `pnpm test:e2e:report` runs the e2e suite and writes a timestamped HTML report to `src/__tests__/e2e/reports/<timestamp>/index.html`.
- `pnpm test:e2e:regen` regenerates all committed `.dsl` snapshots to match the current fixture set.
- `pnpm test:e2e:regen:fixture -- -t <fixture-id>` regenerates snapshots only for fixtures whose test names match the Vitest pattern.
- `pnpm test:e2e:regen:fixture -- -t "fixture-a|fixture-b"` regenerates multiple fixtures in one run by using a regex pattern.
- `pnpm test:e2e:report -- --update-snapshot <fixture-id>` is the report-oriented flow: it regenerates one fixture, runs that fixture, and opens the HTML report.
- `pnpm test:fuzz` runs only the fuzz suite and keeps fuzz out of the default `pnpm test:e2e` run.
- `pnpm test:fuzz:report` runs only the fuzz suite and writes a timestamped HTML report to `src/__tests__/e2e/reports/<timestamp>/index.html`.
- `pnpm test:fuzz:regen` regenerates the committed fuzz snapshots under `src/__tests__/e2e/fuzz-snapshots`.

## Snapshot Regeneration

Snapshot regeneration uses the configured LLM and expects the credentials to be configured in `packages/react-ui-dsl/.env`.

Start from `.env.example` and create `.env`:

```bash
cp .env.example .env
```

Example `.env`:

```bash
LLM_API_KEY=sk-...
LLM_BASE_URL=https://api.deepseek.com/v1
# LLM_MODEL=deepseek-chat
```

After `.env` is configured, run:

```bash
pnpm test:e2e:regen
pnpm test:e2e:regen:fixture -- -t table-basic
```

If `src/__tests__/e2e/fixtures.test.ts` fails with `DSL snapshot coverage is out of date`, that is a fixture/snapshot sync problem rather than a renderer regression. Regenerate the missing snapshots and commit the updated `.dsl` files.

Do not edit files in `src/__tests__/e2e/snapshots` manually. They are generated test inputs and should only be updated through the regen commands.

## Eval Loop

The eval loop measures and iterates on rendering quality. It gives each fixture a multi-dimensional score, identifies failing patterns, packages the work for any agent (Codex, Claude Code, opencode), and verifies that the agent's changes actually improved quality.

### One-time setup

```bash
# Install browser binaries (once per machine)
pnpm --filter @openuidev/react-ui-dsl exec playwright install chromium
```

The eval loop needs a multimodal VLM for judging. Pick a runner in `.env`:

**Option A — Claude Code** (if you already have CC installed, no extra keys needed):

```bash
EVAL_JUDGE_RUNNER=claude-code
# LLM_JUDGE_MODEL=claude-haiku-4-5-20251001   # default; override if needed
```

**Option B — Codex** (if you already have Codex installed):

```bash
EVAL_JUDGE_RUNNER=codex
# LLM_JUDGE_MODEL=gpt-5.4-mini   # default; override if needed
```

**Option C — direct LLM API** (any OpenAI-compatible endpoint):

```bash
LLM_API_KEY=sk-...
LLM_BASE_URL=https://api.openai.com/v1
LLM_JUDGE_MODEL=gpt-4o
```

### Typical iteration cycle

```
pnpm eval start           → baseline eval
pnpm eval status <run-id> → inspect scores and failing patterns
                          → open task-bundle/adapters/<agent>.md
                          → run the agent, let it write result-bundle/
pnpm eval verify <run-id> → full re-eval + delta report
```

#### 1. Start a run

```bash
pnpm eval start           # use existing DSL snapshots
pnpm eval start --regen   # regenerate DSL first, then eval
```

Creates a run workspace at `src/__tests__/e2e/eval/runs/<run-id>/` and prints:

```
✓ Run 20260425_181234_ab12 complete.
  Overall score: 6.4/10
  Failing patterns: 2
  Next steps:
    1. Review task bundle: ...runs/20260425_181234_ab12/task-bundle
    2. Choose an agent and follow adapters/<agent>.md
    3. Run: pnpm eval verify 20260425_181234_ab12
```

#### 2. Check status

```bash
pnpm eval status              # list all runs
pnpm eval status <run-id>     # detailed view: state, score, history, next step
```

#### 3. Pick an agent

Open the adapter doc for your preferred agent:

| Agent | Adapter file |
|---|---|
| Claude Code | `task-bundle/adapters/claude-code.md` |
| Codex | `task-bundle/adapters/codex.md` |
| opencode | `task-bundle/adapters/opencode.md` |

The adapter gives the agent a self-contained prompt. The agent edits source files and writes a `result-bundle/` when done.

#### 4. Write the result bundle

After the agent finishes, these files must exist under `result-bundle/`:

```
result.json                       ← { runId, completedAt, agentType }
change-summary.md                 ← what changed and why
touched-files.json                ← ["packages/react-ui-dsl/src/..."]
claimed-affected-fixtures.json    ← ["table-basic", ...]
```

#### 5. Verify

```bash
pnpm eval verify <run-id>
```

Runs the full fixture suite (regression gate), re-judges every fixture, computes deltas, and prints:

```
Verification complete for run 20260425_181234_ab12
  Score: 6.4 → 7.8 (+1.4)
  Outcome: SUCCESS

  Quality improved: 6.4 → 7.8 (+1.4). Review and commit:
    git add -p && git commit -m "improve: DSL quality +1.4 overall score"
```

The eval loop never commits automatically. You decide when to commit.

### Human corrections (optional)

Drop a `corrections.json` in the run directory to calibrate the judge or inject hints into the next task bundle:

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
  }
]
```

- `"target": "judge"` → processed by `pnpm eval calibrate <run-id>`
- `"target": "prompt"` → forwarded into the next task bundle as agent hints

### Run states

| State | Meaning |
|---|---|
| `created` | Workspace initialized |
| `waiting_for_agent` | Task bundle ready |
| `verifying` | Verification in progress |
| `verified` | Done (check verificationSummary in run.json) |
| `stalled` | No improvement for 3+ iterations |

### Judge dimensions

Each fixture is scored on four dimensions (0–3 each) plus an overall 0–10:

| Dimension | What it measures |
|---|---|
| `component_fit` | Is the chosen component type right for this data? |
| `data_completeness` | Are all important fields from the data model shown? |
| `format_quality` | Are dates, numbers, and values formatted correctly? |
| `layout_coherence` | Is the layout clear and logically organized? |
