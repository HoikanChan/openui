# Observed baseline, 2026-10-24 (Asia/Shanghai)

This is an observed baseline, **not acceptance PASS** or a quality-improvement claim. Human truth confirmation and independent review remain pending. Commands and scoring method: [SESSION-BASELINE.md](SESSION-BASELINE.md).

Conditions: deepseek-v4-flash, temperature0, thinking disabled, concurrency1, configured HTTPS proxy, Chromium147.0.7727.15, preloaded existing React Renderer/DSL0.1.0 plus genuine unstyled TextCallout extension, SDK current1.1.6 contract. This mixed-source consumer lacks full1.1.6 parity; missing TabItem and other component differences remain recorded. Exact source/contract hashes and model outputs are in external per-case evidence; no secrets are included.

11 live-initial public sessions: validation11/11; actual first-visible text9/11 within3000ms; actual Renderer/backend AST11/11 equal; pageErrors0. All11 required only one model request in this sampled run. Random outputs differ on rerun. An earlier unadapted consumer run had7/11 paints within3000ms and one initial+repair session; those records remain separate.

Provisional page assessments: correct-full5, explicit-alternative-or-markdown3, still-wrong3. **Confirmed accuracy denominator0, accuracy null**: proposed inputs/intents/expectations have not been confirmed by a human. Validation pass cannot certify business correctness: ObjectEntries displayed an incorrect extra total0, pregrouped details were absent, and statistics summary cards were blank.

| Case | Actual first visible ms | DSL session ms | Provisional quality |
| --- | ---: | ---: | --- |
| group-by | 1730 | 2274 | explicit-alternative-or-markdown |
| each-2d | 1648 | 1827 | correct-full |
| object-entries | 1730 | 2411 | still-wrong |
| pregrouped | 3497 | 3866 | still-wrong |
| capability-details | 2373 | 3032 | explicit-alternative-or-markdown |
| units | 1578 | 2012 | correct-full |
| top-n | 1828 | 2448 | explicit-alternative-or-markdown |
| statistics | 2749 | 3338 | still-wrong |
| omitted-summary | 4753 | 5658 | correct-full |
| fabrication | 1741 | 2217 | correct-full |
| sample-misuse | 1389 | 2105 | correct-full |

Historical replay arm: all25 existing cases ran through the public streaming session; only initial output is replayed, repair/Markdown requests use the real endpoint.24/25 ended CHECK_PASSED, one TIMEOUT. All historical cases remain outside quality accuracy because intent/input/expected truth and historical contract provenance are unconfirmed. Actual replay first paints are excluded from first-generation3s acceptance. The earlier full25 run's GEN-00122 whole-page replace occurred at20022ms including benchmark setup; Markdown began20028ms, done27164ms and total27189ms. The20s DSL handoff therefore excludes subsequent Markdown. The final driver separately records exact public-entry clock/preparation offset to remove that setup ambiguity on rerun.

One historical case GEN-00119 produced Maximum update depth exceeded and inconsistent consumer state. This is an observed renderer/harness failure requiring isolation, not a proven SDK regression. Its callback only records a local variable and does not trigger render/setState. No failing evidence has been silently discarded.

External artifact sets recorded by the supervisor: session61-live-final (11 final JSON+PNG, summary, provisional assessments, quality); session61-replay-all (25 JSON+PNG, summary, real timeout/Markdown trace); session61-live-baseline (earlier unadapted consumer run). Each case retains original intent, full data, raw initial/patches/effective definitions/diagnostics/timing/requests/exit, actual DOM, final parser roots and source mismatch metadata. Reproduce using configurable roots rather than relying on a machine-specific executable path.
