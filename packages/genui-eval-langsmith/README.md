# GenUI LangSmith Evaluation Runner

Unified evaluation CLI for GenUI generation, static validation, incremental repair, and frontend visual feedback on LangSmith Cloud with local or intranet OpenAI-compatible models.

## Build

```bash
mvn -pl packages/genui-eval-langsmith -am package -DskipTests
```

This produces `packages/genui-eval-langsmith/target/genui-eval-langsmith.jar`.

## Environment variables

System environment variables take precedence over a repo-root `.env` file.

```dotenv
# LangSmith Cloud (optional; omit or pass --offline to run offline only)
LANGSMITH_API_KEY=lsv2_pt_...
LANGSMITH_ENDPOINT=https://api.smith.langchain.com
LANGSMITH_WORKSPACE_ID=
LANGSMITH_FAILED_TRACES_DIR=target/langsmith-failed-batches

# Local or intranet OpenAI-compatible LLM (LLM_API_KEY is optional for localhost/127.0.0.1)
LLM_BASE_URL=http://localhost:11434/v1
LLM_API_KEY=
LLM_MODEL=qwen3.8-flash

# Optional proxy (applied to LangSmith Cloud and remote LLMs; bypassed for localhost/127.0.0.1)
HTTPS_PROXY=
```

## Subcommands

### 1. Idempotent dataset sync

```bash
java -jar packages/genui-eval-langsmith/target/genui-eval-langsmith.jar sync-dataset \
	--type=repair-cases \
	--source=packages/genui-java-sdk/repair-cases \
	--dataset=genui-repair-cases

java -jar packages/genui-eval-langsmith/target/genui-eval-langsmith.jar sync-dataset \
	--type=validation-test \
	--source=validation-test \
	--dataset=genui-validation-test
```

### 2. Repair benchmark (LangSmith + offline HTML)

```bash
# Zero-LLM validation-only baseline
java -jar packages/genui-eval-langsmith/target/genui-eval-langsmith.jar eval-repair \
	--cases=packages/genui-java-sdk/repair-cases \
	--offline-report=target/repair-validation.html

# Streaming incremental repair against local model + LangSmith Cloud
java -jar packages/genui-eval-langsmith/target/genui-eval-langsmith.jar eval-repair \
	--cases=packages/genui-java-sdk/repair-cases \
	--repair \
	--max-attempts=3 \
	--include-usage \
	--out=target/repair-run.jsonl \
	--offline-report=target/repair-run.html
```

LangSmith receives nested per-round traces (exact prompts, raw outputs, optional token usage, validator diagnostics), row-level feedback (`status`, `reproduced`, `repaired`, `attempt_count`, `capability_notice_present`), and session-level summary feedback (`main_repair_rate`, `end_to_end_repair_rate`, `secondary_repair_rate`, `not_reproduced_rate`, `bucket::<issueCode>::repair_rate`).

### 3. End-to-end A/B generation comparison

```bash
java -jar packages/genui-eval-langsmith/target/genui-eval-langsmith.jar eval-ab \
	--fixtures=<fixtures-dir> \
	--arm=off \
	--out-dir=target/ab-off \
	--offline-report=target/ab-off.html

java -jar packages/genui-eval-langsmith/target/genui-eval-langsmith.jar eval-ab \
	--fixtures=<fixtures-dir> \
	--arm=on \
	--out-dir=target/ab-on \
	--offline-report=target/ab-on.html
```

### 4. Validation corpus coverage (`validation-test/`)

```bash
java -jar packages/genui-eval-langsmith/target/genui-eval-langsmith.jar eval-corpus \
	--dir=validation-test \
	--offline-report=target/validation-corpus.html
```

### 5. Rendered screenshots + multimodal visual judge (LangSmith attachments + scores)

For `eval-repair` or `eval-ab` runs, render the generated/repaired DSL in the real React UI, capture Playwright Chromium screenshots, score them with a multimodal judge, and attach both the PNG screenshots (`visual_render_and_judge` child run attachment `rendered_page.png`) and visual scores back onto the original LangSmith experiment runs:

```bash
# Attach rendered screenshots + VLM visual scores onto an eval-repair experiment
pnpm --filter @cloudsop/openui-react-ui-dsl eval:visual-bridge \
	--repair-jsonl="$PWD/target/repair-run.jsonl" \
	--cases="$PWD/packages/genui-java-sdk/repair-cases" \
	--out-dir="$PWD/target/repair-run-visual"

# Attach rendered screenshots + VLM visual scores onto an eval-ab arm
pnpm --filter @cloudsop/openui-react-ui-dsl eval:visual-bridge \
	--ab-metrics="$PWD/target/ab-on/metrics-on.json" \
	--fixtures=<fixtures-dir> \
	--out-dir="$PWD/target/ab-on-visual"
```

When `LANGSMITH_API_KEY` is set during `packages/react-ui-dsl` `pnpm eval start --regen`, `genui-eval-cli` also records `.eval-runs/<runId>/langsmith-runs.json` and `eval-loop.ts` automatically attaches rendered PNG screenshots (`visual_render_and_judge` child run), `render_passed`, `visual_component_fit`, `visual_data_completeness`, `visual_format_quality`, `visual_layout_coherence`, `visual_overall` (with judge feedback comment), `visual_issues`, and session summaries (`visual_overall_avg`, `render_pass_rate`).
