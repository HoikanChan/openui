# Observed baseline, 2026-10-24 (Asia/Shanghai)

This is an observed baseline, **not ticket acceptance PASS** or a quality-improvement claim. Code and measurement methodology passed independent review at `4566b42600e8b5e07d20011a6c5ef1dd26fce696`; human truth confirmation, the3second criterion and a genuine SDK1.1.6 consumption environment remain unmet. The issue remains OPEN; this is not “implemented, pending merge.” Commands and scoring method: [SESSION-BASELINE.md](SESSION-BASELINE.md).

Conditions held fixed across the two adapted-consumer runs below: deepseek-v4-flash, temperature0, thinking disabled, concurrency1, configured HTTPS proxy, Chromium147.0.7727.15, preloaded existing React Renderer/DSL0.1.0 plus genuine unstyled TextCallout extension, current SDK1.1.6 contract. This mixed-source consumer does not establish full1.1.6 parity. Its39 native names overlap the49 target names in29;20 target components are missing, and10 consumer names are extra. In particular, genuine TabItem and its parent Tabs use incompatible child/property shapes, so merely extending another native definition cannot certify target behavior. Full audit: external `consumer-compatibility61.md`.

## Latest complete run: corrected callback evidence at4566b426

The complete new run is `session61-live-guarded-4566b426`, with all earlier runs preserved. No best-run selection is used. The corrected driver requires an actual Renderer callback whose captured response equals the final response; missing/stale callbacks yield unavailable consistency, never a shadow-parser PASS.

11 live-initial public sessions: validation11/11; actual final callback observed and captured response matched11/11; actual Renderer/backend AST11/11 equal; pageErrors0. Actual first-visible text within3000ms: **6/11**. All11 used one real model request in this sampled run. Random outputs and latency differ on rerun.

Provisional page assessments: correct-full6, explicit-alternative-or-markdown3, still-wrong2. **Confirmed accuracy denominator0, accuracy null**: proposed inputs/intents/expectations have not been confirmed by a human. ObjectEntries still displayed a false extra total0 despite rows3+2; pregrouped details were absent. In this new run, requested statistics total100 and mean25 appeared correctly, with four-device scope stated in the footer; an extra unrequested count card was blank and remains recorded. This is a provisional assessment of the actual new page, not a correction of the older model output.

| Case | First SDK DSL ms | Actual first visible ms | Fragment to paint ms | DSL session ms | Provisional quality |
| --- | ---: | ---: | ---: | ---: | --- |
| group-by | 3119 | 3210 | 91 | 3756 | explicit-alternative-or-markdown |
| each-2d | 1467 | 1576 | 109 | 1902 | correct-full |
| object-entries | 1810 | 1953 | 143 | 2332 | still-wrong |
| pregrouped | 5931 | 6048 | 117 | 6388 | still-wrong |
| capability-details | 1294 | 1384 | 90 | 1893 | explicit-alternative-or-markdown |
| units | 1527 | 1628 | 101 | 1982 | correct-full |
| top-n | 3525 | 3622 | 97 | 4156 | explicit-alternative-or-markdown |
| statistics | 3224 | 3428 | 204 | 4026 | correct-full |
| omitted-summary | 4433 | 4602 | 169 | 5458 | correct-full |
| fabrication | 1818 | 1933 | 115 | 2323 | correct-full |
| sample-misuse | 2602 | 2762 | 160 | 3074 | correct-full |

All five3second misses had already exceeded3000ms before the first SDK DSL fragment: GroupBy3119→3210ms, pregrouped5931→6048ms, TopN3525→3622ms, statistics3224→3428ms and omitted-summary4433→4602ms (first SDK fragment→actual paint). Fragment-to-paint in this complete run ranged90–204ms. The wait before SDK output includes prompt preparation, network and provider processing; provider telemetry is unavailable, so these measurements cannot isolate pure model-first-token latency. They do not substitute fragment time for first-visible rendering, justify excluding the slow cases, or demonstrate3second acceptance.

Exact code provenance:

- Code/method reviewed commit: `4566b42600e8b5e07d20011a6c5ef1dd26fce696`.
- Driver SHA-256: `68437200122070213916b4482cc828223bf056e3c33d409ab6e6e713c87b1eb0`.
- Observation comparison module SHA-256: `31977f0ef28ed146cfda64080795c580cf3822321041156471f001cbc7a7967a`.
- Local evidence directory: `/Users/chenxie/.codex/task-records/openui-spec54/session61-live-guarded-4566b426/`.
- Evidence includes all11 JSON+PNG, `summary.json`, `quality.json`, hash-bound provisional assessments, `timing-breakdown.json` and `code-provenance.json`; each case also retains contract/Renderer/library/source hashes, original intent/full input, raw rounds/patches/current definitions/diagnostics/requests/end reason and final DOM/callback response. Local files are not remote issue attachments.

## Preserved older run: pre-guard driver at62cf250f

The directory named `session61-live-final` is an **older pre-guard run**, not the latest final result. Its driver at `62cf250f548f9e91bebd3f35655be2a5c9a345ea` could fall back to shadow parsing when an actual callback was missing and did not record callback response revisions. Its original records are preserved; the new guard is not retroactively certified against them.

That older11-session run reported validation11/11, paint9/11 within3000ms, root equality11/11 and pageErrors0; provisional5full/3alternative/3wrong, confirmed denominator0. Its statistics cards were blank. The table below is retained as that run's original observation, not selected as the current performance result:

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


An even earlier unadapted consumer run, `session61-live-baseline`, observed7/11 paints within3000ms and one initial+repair session. It remains a separate configuration/measurement record.

## Historical replay and bounded consumer isolation

All25 existing historical cases ran through the public streaming session; only initial output was replayed, repair/Markdown requests used the real endpoint.24/25 ended CHECK_PASSED, one TIMEOUT. All historical cases remain outside quality accuracy because intent/input/expected truth and historical contract provenance are unconfirmed. Replay first paints are excluded from first-generation3second acceptance.

The preserved full25 run in `session61-replay-all` recorded GEN-00122 whole-page replace at20022ms including benchmark setup, Markdown request at20028ms, done at27164ms and total27189ms. It demonstrates an observed approximately20second DSL handoff with independent subsequent Markdown, with scheduling/preparation overhead disclosed. The corrected driver separately records public-entry clock and preparation offset; this evidence is not proof that every timing boundary case passes.

GEN-00119's Maximum update depth error was isolated offline using its recorded output and input, without further model calls. The complete final effective DSL renders normally. The captured1912-character prefix renders normally; adding the next five characters produces a1917-character prefix ending `rxValues = data`, which reproduces the error by itself. Rendering the previous/current prefix pair also reproduces it. These checks reproduced with **both original2edd baseline lang-core and the ticket/integration lang-core**, with `onParseResult` **disabled**, so the failure is not callback measurement feedback or attributable solely to the new parser changes. It concerns the existing real consumer's handling of that intermediate value; the precise internal cause and behavior in a genuine target1.1.6 runtime remain unknown. No broad consumer fix or target-runtime acceptance is claimed.

The external `isolate61/` directory preserves the original record hash, failing/prior DSL prefix hashes, minimal pair script, data, pageErrors/stacks, screenshots and `isolation-provenance.json`. Bounded comparison results are in `pair-result.json`; normal completed-output/no-callback versus failing recorded-stream/no-callback comparisons are in `result.json`. No failing evidence has been discarded.

All local evidence sets are under `/Users/chenxie/.codex/task-records/openui-spec54/`; runnable commands accept configurable source/dependency/output paths rather than depending on that directory. Remaining external prerequisites are actual human truth confirmation and a genuine1.1.6 consumption environment; the observed3second misses also remain unmet. No code-method review result or provisional assessment overrides those acceptance conditions.
