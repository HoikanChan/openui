# Public-session quality and latency baseline (#61)

`RepairBenchCli --repair` keeps its historical initial-DSL replay arm and report format. Its case rows now explicitly say `historical-initial-replay`; `success` / repaired rates are **validation** results, never human intent accuracy.

`SessionBenchCli` runs `GenUiGenerator.generateStream` with the production 20 second session budget. `scripts/session-bench.mjs` connects those actual envelopes to the existing React `Renderer`, library, and this checkout's lang-core in Chromium. It measures visible text after painting, captures `.preview-shell` content and the final page, reads the **Renderer's actual onParseResult callback and its captured response revision**, and compares its root with the backend effective DSL parsed using the same consumer schema. Missing or stale final callbacks produce unavailable consistency with an explicit reason; no frontend shadow parse can certify a match. It does not execute DSL in Java, regenerate in TypeScript, or substitute the first received fragment for visible rendering. The separate streaming driver is necessary because the existing final-string screenshot report cannot observe request-to-paint or realtime patches.

The repository does not contain a React renderer installation. Supply an existing complete source checkout and a package directory with Vite/Playwright/React dependencies; the driver fails clearly when missing. These paths are configuration, not machine-specific source changes.

```sh
mvn -q -f packages/genui-java-sdk/pom.xml test-compile dependency:build-classpath \
  -Dmdep.outputFile=target/session-bench-classpath.txt
node packages/genui-java-sdk/scripts/session-bench.mjs \
  --renderer-root=/absolute/existing/openui-source \
  --dependency-root=/absolute/installed/react-ui-dsl-package \
  --env-file=/absolute/private/.env \
  --out=/absolute/evidence/live
# Same public session, historical initial DSL then actual live repairs; no first-generation latency claim:
node packages/genui-java-sdk/scripts/session-bench.mjs \
  --renderer-root=/absolute/existing/openui-source \
  --dependency-root=/absolute/installed/react-ui-dsl-package \
  --env-file=/absolute/private/.env \
  --cases=packages/genui-java-sdk/repair-cases \
  --mode=historical-initial-replay --out=/absolute/evidence/replay
```

Optional `--ids=id1,id2` selects existing case IDs. Optional `--text-callout=true` uses the actual source `react-ui/src/genui-lib/TextCallout` through `dslLibrary.extend`, preserving the real DSL Table/Col. This is an explicitly recorded mixed-source benchmark adapter. The original component is unstyled when its source SCSS dependencies are absent; it is not a replacement fake component or a complete 1.1.6 renderer. The unadapted consumer remains available by omitting the flag. Do not infer complete SDK/consumer parity: inspect component differences, parser errors and actual cells. A shared AST can still produce wrong business values or missing content.

Configuration reads LLM_MODEL / LLM_BASE_URL / LLM_API_KEY / HTTPS_PROXY from the environment first, then the supplied private env file in-process. No key or request header is copied to outputs. Avoid publishing raw model output if the original input is private. Each case records the actual model, temperature, thinking, concurrency, consumer/library/source hashes, contract version and hash encoding, raw rounds, patches, current definitions, diagnostics, request sources/counts, round durations, end reason, DSL handoff time and total including independent Markdown. Source/component mismatch is recorded rather than silently using a reduced contract.

Timing uses the same host clock: Java wall-clock timestamp immediately before public generateStream and browser Date.now after two animation frames showing nonempty visible text. Node client-start timestamp is separately recorded; JVM/browser warmup is excluded. Frame observation does not block later chunks. This is observed first visible text, not a guaranteed completed chart or layout. Java nanoTime measures round/session durations. `dslSessionMs` ends at whole-page replace before Markdown; `totalIncludingMarkdownMs` includes the later fallback. Model requests identify initial/repair/Markdown separately. Replay first paints are explicitly excluded from the live <=3s metric. Driver watchdog is 120 seconds including Markdown (which is outside the session budget); an infrastructure abort fails the command rather than inventing a passing record.

## Quality evidence, independent of validity

The 25 historical packs retain their inputs/intents. Their historical contract provenance is unknown unless supplied by case/package contract; current supported functions are not labeled historical hallucinations. Model-inferred intents and approximate/backfilled input are not accuracy truth merely because a non-null dataModel exists.

The 11 `session-cases` are complete, agent-authored synthetic **proposals**, covering GroupBy, Each 2D, ObjectEntries, pregrouped rows, explicit detail fallback, MiB, Top N, full statistics, omitted summary, fabrication and full-data/sample misuse. Each preserves its literal intent/input and pending expected result. These are not human-confirmed until a human actually confirms them. The `dsl.txt` seeds are only used by the replay arm; the live arm never reads them as initial model output.

Each run starts with `qualityCategory=unconfirmed`, `accuracyEligible=false`. Once a human confirms input+intent+expectations, attach `expectation.state="human-confirmed"` and `expectation.confirmationEvidence` to that run's case record, retaining the original evidence. Then a named assessor checks the actual rendered page and creates an external assessment file:

```json
[{"caseId":"session-units","category":"still-wrong","assessor":"reviewer-name","renderEvidence":"session-units.png","reason":"Actual rendered MiB cells are empty","resultSha256":"sha256-of-the-exact-case-json"}]
```

Allowed categories are `correct-full`, `explicit-alternative-or-markdown`, `still-wrong`. An explicit fallback must show true data **and a visible limitation**, and does not count as full success. A valid page omitting requested summary/units is still wrong. A classification may be provisionally recorded while truth confirmation is pending; it stays outside accuracy. Stale output hashes are rejected, so an old assessment cannot certify a rerun.

```sh
node packages/genui-java-sdk/scripts/session-quality.mjs /absolute/evidence/live /absolute/assessments.json
node --test packages/genui-java-sdk/scripts/session-quality.test.mjs packages/genui-java-sdk/scripts/session-observation.test.mjs
mvn -q -f packages/genui-java-sdk/pom.xml -Dtest=SessionBenchRunnerTest test
```

`quality.json` separates validation count, provisional categories, confirmed categories and the eligible denominator. An empty confirmed denominator is null accuracy, never 0% or 100%. No quality-improvement target is presumed. Exact baseline evidence and actual unmet 3s/rendering/truth conditions belong in the issue/PR evidence, not as fabricated green test fixtures.
