# OpenUI Generative UI Benchmark

End-to-end reliability benchmark combining the 46 screen briefs from [thesysdev/generative-ui-bench](https://github.com/thesysdev/generative-ui-bench) (bands B1 to B5, 2 to 18 requirements per brief) with structured host Data Models tailored for this repository's Java SDK (`packages/genui-java-sdk`) and real frontend renderer (`packages/react-ui-dsl` + `packages/react-lang` + `packages/lang-core`).

## Verification layers

| Layer | Command | What it checks |
|---|---|---|
| **L1 Static contract & Data Model gate** | `node score.ts <label>` | Runs `openui-upstream` against `@openuidev/lang-core@0.2.16` and `openui-ours` against our compiled `DefaultOpenuiLangValidator` (`packages/genui-java-sdk`), plus the upstream reachable-component coverage floor. Offline and deterministic. |
| **L2 Real Chromium render gate** | `node render-check.ts <label>` | Builds workspace frontend sources with Vite and renders `openui-ours` outputs in headless Chromium. Checks `pageerror`, `parseErrors`, `console.error`, blank roots, empty tables, blank ECharts canvases, broken images, literal token leaks (`undefined`, `null`, `[object Object]`, `NaN`), and horizontal overflow. |
| **HTML report** | `node tools/report.ts <label> [--render-report=...]` | Produces a self-contained HTML comparison report under `results/report-<label>.html`. |

## Prerequisites

- Node.js `>= 22.18` (runs TypeScript natively)
- Java 21 and Maven (`mvn -pl packages/genui-java-sdk -am package -DskipTests`)
- Workspace frontend dependencies installed at repo root (`pnpm install`) for L2 Chromium checks
- Benchmark local dependencies installed once in this folder:

```bash
cd benchmarks/generative-ui-bench
npm install
```

## Quick start

1. Verify all 46 Data Models assemble cleanly through `GenerationSdk`:

```bash
node tools/check-data-models.ts
```

2. Run model generation (idempotent; re-running skips existing non-empty files under `raw/<label>/`):

```bash
BENCH_API_URL="$OPENUI_BENCH_API_URL" \
BENCH_API_KEY="$OPENUI_BENCH_API_KEY" \
BENCH_MODEL="${OPENUI_BENCH_MODEL:-qwen3.7-plus}" \
BENCH_LABEL=qwen37plus \
BENCH_REPEATS=4 \
BENCH_CONCURRENCY=10 \
node run.ts openui-upstream openui-ours
```

Environment knobs for `run.ts`:

- `BENCH_API_URL` / `OPENUI_BENCH_API_URL` / `LLM_BASE_URL`: OpenAI-compatible base URL
- `BENCH_API_KEY` / `OPENUI_BENCH_API_KEY` / `LLM_API_KEY`: API key
- `BENCH_MODEL` / `OPENUI_BENCH_MODEL` / `LLM_MODEL`: model ID
- `BENCH_LABEL`: output folder name under `raw/` and `results/`
- `BENCH_REPEATS`: generations per brief (default `4`)
- `BENCH_TEMP`: temperature (default `0.7`)
- `BENCH_MAX_TOKENS`: output token ceiling (default `16384`)
- `BENCH_ENABLE_THINKING`: `"false"` (default), `"true"`, or `"omit"`
- `BENCH_REASONING_EFFORT`: optional `reasoning_effort` override
- `BENCH_ONLY`: comma-separated brief names for targeted runs (for example `b1-uptime,b2-sales`)

Available protocols:

- `openui-upstream`: upstream 70-component catalog, no Data Model
- `openui-ours`: Java SDK 49-component base contract with host Data Model (`briefs/data-models/b1.json`..`b5.json`)
- `openui-ours-nodata`: Java SDK 49-component base contract without Data Model (for isolating Data Model effects)

3. Run L1 offline static scoring (no API keys needed):

```bash
node score.ts qwen37plus
```

4. Run L2 deterministic Chromium rendering verification:

```bash
node render-check.ts qwen37plus
# Optional: save PNG screenshots or inspect a subset
node render-check.ts qwen37plus --limit=10 --screenshots
```

5. Generate the self-contained HTML report:

```bash
node tools/report.ts qwen37plus --render-report=results/render-qwen37plus.json
```

## Layout

- `briefs/briefs.ts`, `briefs/DESIGN.md`: frozen 46 upstream briefs
- `briefs/data-models/b1.json`..`b5.json`, `briefs/data-models.ts`: host Data Models mapped 1-to-1 to all 46 briefs
- `catalog/public-catalog.json`: upstream 70-component reference catalog
- `protocols/openui-upstream/`: upstream OpenUI prompt and `@openuidev/lang-core@0.2.16` validator
- `protocols/openui-ours/`: `JavaBridge.java` + TypeScript bridge calling `packages/genui-java-sdk` directly
- `render-harness/`: minimal Vite + React mount shell for L2 Chromium verification
- `tools/check-data-models.ts`, `tools/report.ts`: Data Model preflight and HTML report generator
