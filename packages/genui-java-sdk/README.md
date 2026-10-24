# OpenUI GenUI Java SDK

Java 21 SDK for assembling GenUI system prompts from:

- the built-in DSLEngine base contract resource
- one registered `GenUIExtension` selected by `extensionId`
- one `GenUIPromptRequest` carrying request data, prompt additions, and generation flags

It also provides `GenUiGenerator` for services that want the SDK to assemble the
prompt, invoke an LLM, parse sync or streaming responses, and extract OpenUI Lang.

## Repair budget and Markdown fallback

`generate` and `generateStream` measure a 20-second monotonic budget from the start of
DSL generation, including prompt assembly, model calls and validation. The initial
generation and first repair are always allowed. Before starting each subsequent
incremental repair, the SDK checks the total elapsed time: at or beyond 20 seconds,
a still-invalid program falls back to Markdown with `TIMEOUT`. A request already
started can finish and pass validation even after the budget expires. There is no
budget cancellation timer; individual request timeouts belong to the transport.
`withTiming(GenerationTiming)` permits deterministic clock tests.

The current transport is JDK `HttpClient`; pass a complete HTTP(S) endpoint:

```java
GenUiGenerator generator = GenUiGenerator.create(GenUiLlmConfig.builder()
    .endpoint("https://your-gateway.example/chat/completions")
    .putHeader("Authorization", "Bearer " + apiKey)
    .build());
```

The former BSP-relative default endpoint is not usable with HttpClient. BSP support
is explicitly deferred to [#62](https://github.com/HoikanChan/openui/issues/62).
Custom transports must implement `LlmTransport.newCall(body, stream)` and the
`LlmCall` cancellation contract. The handle must exist before network I/O; cancel
must abort both headers and body reads, release resources and be idempotent.
The default unsupported method is intentional: legacy transports must not silently
claim cancellation support. Existing direct `post`/`postStream` remain available.

On timeout or model failure the SDK stops DSL output and hands off exactly once.
The reference host (`GenerateController`) supplies the public `MarkdownFallback`
callback and calls `generateStream(original.asMarkdown(), sink)`. A host with its
own independent Markdown flow can bind that flow instead. The two-argument SDK
method uses the same explicit Markdown path by default. Markdown generation is
outside the DSL budget, uses the original user input, request context, response
data, locale and suggestions, and does not recursively fall back on failure.
`MarkdownRenderer` provides the existing escaping and Canvas/Preview wrapping.

Stream consumers must handle `replace` before Markdown DSL fragments:

```json
{"type":"replace","seq":2,"content":{"scope":"page","format":"Markdown","reason":"TIMEOUT"}}
```

Discard the entire previous DSL, parser state and rendered page, retain the original
data model, then render the new fragments. This is not a same-name DSL patch or an
append operation. Only one `done` ends the whole stream. If Markdown fails too,
`error(MARKDOWN_REQUEST_FAILED)` precedes `done`. The returned `endReason` retains
the DSL outcome (`CHECK_PASSED`, `TIMEOUT`, `MODEL_REQUEST_FAILED`, or a distinct
unchecked/validation-failed status), even after successful fallback.

This ticket preserves existing optional validation/repair strategies; the incremental
repair loop from #54 is implemented in #58 (see "Incremental repair" below). Actual
BSP/company-host adaptation and frontend whole-page replacement verification remain
tracked in #62.

For a user-facing integration walkthrough, see [INTEGRATION.md](INTEGRATION.md).

OpenUI generation expects the model content to be openui-lang text, so the
default request body does not force `response_format.type=json_object`. If an
older gateway still requires JSON-object mode, opt in explicitly with
`GenUiLlmConfig.builder().jsonObjectResponse(true).build()`.

The default resource is checked in at
`src/main/resources/openui/base-contract.json`. The primary entrypoint,
`GenerationSdk.create()`, loads this resource automatically. Tests can override
it with `GenerationSdk.builder().baseContract(...).build()`.

## Base Supplement (host global additions)

A **GenUI Base Supplement** lets a host add its own components to the base
contract — globally, for every request, without an `extensionId` — and replace
the prompt docs of existing base components. The supplement is a single JSON
document merged once at `GenerationSdk` build time; the merge result (the
*Effective Base Contract*) is the only base that prompt assembly and
`GenUIExtension` collision checks ever see. Without a supplement, behavior is
byte-identical to before.

```java
GenerationSdk sdk = GenerationSdk.builder()
    .baseSupplement(GenUIBaseSupplementLoader.fromResource("genui/company-supplement.json"))
    .build();
```

JSON format — a subset of `base-contract.json` with the same component entry
shapes (`description`+`propsSchema` or `signature`+`description`):

```jsonc
{
  "supplementVersion": "2026.07",            // optional, informational only
  "components": {
    "TopoChart": {
      "description": "Network topology chart. Bind nodes/links to host data.",
      "propsSchema": {
        "type": "object",
        "properties": {
          "nodes": { "type": "array", "items": { "type": "any" } },
          "links": { "type": "array", "items": { "type": "any" } }
        },
        "required": ["nodes", "links"]
      }
    }
  },
  "componentGroups": [
    { "name": "Charts", "components": ["TopoChart"], "notes": ["Prefer TopoChart for topology data."] }
  ],
  "examples": ["root = Stack([topo])\ntopo = TopoChart(data.nodes, data.links)"],
  "additionalRules": ["Prefer TopoChart over Table for network element relations."]
}
```

Merge rules:

| Section | Rule |
|---|---|
| `components` | Same name: **wholesale replacement** of the base spec (description and propsSchema together, never field-by-field), keeping the base position. New name: appended after base components. No deletion. |
| `componentGroups` | Same name: **union** — order-preserving de-duplicated member union (base members first), notes appended. New group: appended. |
| `examples` / `additionalRules` | Appended after the base entries. |
| `capabilityGaps` | Same `id`: **wholesale replacement** of the base entry, keeping the base position. New `id`: appended after base entries. Finally, any remaining entry whose `id` matches a newly added supplement component name is automatically evicted. |
| `contractVersion` / `root` / `tools` / `builtins` | Not allowed in the supplement; the base values always win. |

Loading is strict: any top-level key outside
`supplementVersion` / `components` / `componentGroups` / `examples` /
`additionalRules` / `capabilityGaps` (including `tools`, `root`, `builtins`, `contractVersion`,
or a typo like `additionalRule`) fails `fromJson` with an error listing the
illegal keys. Supplement component schemas and capability gaps are validated at build time with
errors attributed to the supplement. At most one supplement per SDK instance;
merging multiple team packages is the host's preprocessing job.

Host responsibilities:

- **Every supplement component must already be registered in the host's
  frontend renderer.** The SDK only shapes the prompt; a component the model
  can name but the frontend cannot render fails at render time. Preferably
  generate the supplement JSON from the host's frontend component registry.
- **Review same-name replacement entries when upgrading the SDK.** A replaced
  component keeps the host's spec wholesale, so upstream improvements to that
  component's docs are shadowed until the supplement entry is refreshed.

## Prompt assembly authority & golden tests

`PromptAssembler` is the authoritative GenUI prompt assembler (design D9) and is a
byte-for-byte port of `packages/lang-core/src/parser/prompt.ts`'s `generatePrompt`.
Builtin docs come from the contract's top-level `builtins` manifest (exported by the
frontend); all other sections are transcribed from `prompt.ts`.

Cross-language prompt alignment was previously pinned by `PromptGoldenTest`; it was
removed on the validation-iteration branch (2026-10-14) because the contract's
builtins manifest is now internal truth while lang-core's manifest lags — the
byte-for-byte premise no longer holds. See
`docs/adr/0003-builtin-vocabulary-is-contract-data.md`.

## Generated DSL validation

`GenUiGenerator` can validate generated openui-lang before services cache or
render it. The validation data model lives under `core.validation`:

- `ValidationStatus` (`VALID`, `PARTIAL`, `INVALID`)
- `ValidationMode` (`FINAL`, `STREAMING`)
- `ValidationSeverity` (`ERROR`, `WARNING`, `INFO`)
- `ValidationIssue`, `ValidationResult`, `ValidationMetadata`, `ValidationRequest`
- `ValidationConfigMode` (`FINAL_ONLY`, `DISABLED`)
- `RepairPolicyKind` (`NONE`, `FINAL_REPAIR`)
- `GenUiValidationConfig`, the top-level generator config record

`GenUiValidationConfig` exposes three axes: when to validate, what coarse repair
policy to apply, and an optional cap on incremental repair rounds. Use the
presets for the common modes:

```java
GenUiValidationConfig cfg = GenUiValidationConfig.incremental(); // default: elapsed-time repair budget
GenUiValidationConfig capped = GenUiValidationConfig.incremental(3); // at most 3 repair rounds
GenUiValidationConfig strict = GenUiValidationConfig.finalOnly(); // no repair; sync throws on INVALID
GenUiValidationConfig off = GenUiValidationConfig.disabled();
```

`GenUiGenerator.generate()` runs one end-of-round unified check after extracting
openui-lang from the LLM response — over the current effective program
(last-wins by statement id), so superseded definitions no longer produce
errors. With `DISABLED`, it skips validation and returns a backward-compatible
`GenUiGenerationResult(dsl, dataModel)`, leaving `validationStatus()` and
`validationResult()` as `null`. With `FINAL_ONLY`, a valid result returns the
current effective DSL plus the populated `validationStatus()` and
`validationResult()`. An `INVALID` result with `FINAL_ONLY` throws
`GenerationValidationException`; callers can inspect
`e.validationResult().actionableIssues()` for the low-noise blocking diagnostics.
The complete parser-parity view remains available through `issues()`.
With the default `INCREMENTAL_REPAIR`, an unrecoverable `INVALID` instead ends
the DSL session and hands off to whole-page Markdown (see "Incremental repair"
below).

Final validation is data-aware. The generator forwards the concrete response
data through initial validation and every repair attempt, so the validator can
check generated `data.*` paths and proven expression types against the same
model the renderer receives. Lower-level callers enable this pass explicitly:

```java
ValidationResult result = DefaultOpenuiLangValidator.INSTANCE.validate(
    ValidationRequest.builder()
        .dsl(generatedDsl)
        .contract(contract)
        .dataModel(renderDataModel)
        .mode(ValidationMode.FINAL)
        .build());
```

A missing `dataModel` keeps the legacy syntax/contract-only behavior. Passing
`Map.of()` means a concrete empty model and therefore makes every `data.*` path
missing. The MVP reports stable issue families for observable errors including
missing/invalid data paths, component prop and slot type mismatches, builtin
arity/type mismatches, obvious operator mismatches, and missing Table/Col
fields or non-flat row shapes. It also checks proven chart cardinality (label/value
lengths and single-point trend lines) and duplicate derived category expressions.
`@Each`/`@Render` binders remain in scope through separately declared template
statements reached from the builtin body; the same binder is still unresolved when
that template is referenced outside the declaring builtin call.
Empty, heterogeneous, or unsupported evidence is handled
conservatively instead of guessing a type.

`ValidationResult` deliberately exposes two issue views:

- `issues()` is the immutable raw diagnostic stream for debugging, telemetry, and TS parity.
- `actionableIssues()` preserves independent errors but removes exact duplicates and diagnostics
  dominated by syntax, Table row-shape, or root-cause issues. Repair prompts use this view.

```java
try {
  GenUiGenerationResult result = generator.generate(request);
  if (result.validationStatus() == ValidationStatus.VALID) {
    cache(result.dsl());
  }
} catch (GenerationValidationException e) {
  ValidationResult result = e.validationResult();
  log.warn("Generated DSL failed validation: {}", result.actionableIssues());
}
```

For sync repair, configure the coarse policy as `FINAL_REPAIR`. The generator
maps that kind to `RepairPolicy.from(...)` and `RepairCoordinator.repairFull(...)`
tries reflection repair before throwing. Repaired output is re-validated in
`FINAL` mode and returned as `VALID`; the result metadata includes
`extra["repaired"] = "true"`.

### Incremental repair (default, #58)

Normal generation defaults to `GenUiValidationConfig.incremental()`:
`FINAL_ONLY` validation plus `INCREMENTAL_REPAIR`. After a round ends with blocking
errors, the model is asked for **only overriding or new statements** (pure
openui-lang, no full page/JSON/deletion). The SDK merges the patch into the same
current program (last-wins by statement id), forwards the patch live on the stream,
and re-checks the **whole merged program** after the round. The first repair always
runs. Further repairs start only while total elapsed time is below 20 seconds;
duplicate errors, empty patches, and lack of progress are not separate early-exit
conditions. Any repair already in progress is allowed to finish.

All rounds share the original contract, system prompt, and one characterized data
snapshot; each round's scanner is reset, so half statements never splice across
model requests. Completed and interrupted rounds remain on `rounds()`.

If a completed repair is still invalid and the budget has ended, the DSL session ends
unsuccessfully and the public entry performs exactly one whole-page Markdown
handoff (`replace` + `done` on the stream; Markdown result on sync). Explicit
`GenUiValidationConfig.finalOnly()` keeps the no-repair behavior (sync throws
`GenerationValidationException`), and `disabled()` bypasses validation/repair
entirely.

```java
GenUiValidationConfig cfg =
    new GenUiValidationConfig(
        ValidationConfigMode.FINAL_ONLY, RepairPolicyKind.FINAL_REPAIR);

GenUiGenerator generator =
    GenUiGenerator.withTransport(llmConfig, transport, cfg, null);
```

The lower-level `RepairPolicy` value object carries advanced attempt and timeout
knobs for FINAL_REPAIR (whole-page DSL repair):

```java
RepairPolicy policy = RepairPolicy.of(RepairPolicyKind.FINAL_REPAIR, 2);
```

Incremental repair does not read `RepairPolicy.maxAttempts`: by default it runs
with the shared elapsed-time check before the second and later repair attempts.
Repeated errors, empty patches, or lack of progress do not stop it early. Pass
`GenUiValidationConfig.incremental(n)` when a host needs a hard round cap instead.
`RepairPolicy.from(...)` only governs the legacy FINAL_REPAIR path.

### Streaming: live forwarding + end-of-round unified check (#56)

`GenUiGenerator.generateStream(request, sink)` always starts by sending a
`RenderStreamEnvelope.dataModel(...)` frame with `seq=0`. Generation itself is
**never gated**: every model delta is forwarded live as a `dsl` envelope —
nothing is withheld for validation, no per-statement repair runs, and no extra
pre-generation model request is issued. The remaining behavior depends on
`ValidationConfigMode`:

| Mode | Stream behavior |
|---|---|
| `DISABLED` | Raw LLM deltas are emitted as `dsl` envelopes. No validation result is attached. |
| `FINAL_ONLY` | Raw LLM deltas are emitted as `dsl` envelopes. When the model's output round ends cleanly, one unified validation runs over the **current effective program**; the status is carried by the returned `GenUiGenerationResult`, with no late `error` frame. |

The end-of-round check is maintained by `ProgramRoundSession` (the Program
State & Validation module): complete statements are parsed as they arrive and
kept in a **last-wins-by-name** map, so a re-issued `id = ...` statement
overrides the previous definition and historical definitions stop producing
current errors. The round-end validation runs once, in `FINAL` mode, over the
current effective definitions — covering syntax, references, component and
builtin contracts, params, data paths, static types, and page completeness
using the request's effective contract and the full Render Data Model. Forward
references defined later in the same round resolve fine; references still
missing at round end are errors. Types that cannot be proven are not reported
as definite mismatches.

Each round is recorded on the result (`GenUiGenerationResult.rounds()`): raw
output, current definitions, diagnostics, duration, contract version, and end
reason. A normally completed round has a `null` end reason (its verdict is in
`validationResult`); an interrupted round records the session terminal reason
(e.g. `TIMEOUT` / `MODEL_REQUEST_FAILED`) because it has no validation verdict.
The same records feed the incremental repair loop (#58) and evaluation;
completed and interrupted rounds survive a timeout, model failure, or Markdown
handoff.

If the stream fails mid-flight, the SDK emits `error("LLM_STREAM_FAILED")` then
`done` and returns the best-effort current program without running a round-end
check (the content is not trustworthy).

Validation and repair internals do not enter the public stream: issue lists,
repair prompts, and repair attempts remain SDK-internal. Public stream
consumers see only `dataModel`, `dsl` (raw deltas, live), terminal `error`,
and `done` envelopes.

Known limitation: sync repair `timeout` bounds the attempt count, not a single
in-flight LLM call; it is inert at the default `Duration.ZERO`. Proper
enforcement needs transport read-timeout cooperation.

### Rollback

To roll back validation quickly, use `GenUiValidationConfig.disabled()` for
bare-delta behavior. To keep raw streaming while still checking the final result,
use `GenUiValidationConfig.finalOnly()`. A service can also revert its endpoint
implementation to raw delta streaming if clients cannot yet consume
`RenderStreamEnvelope` SSE frames.

## Migrating from bare LLM delta streaming

Older integrations often treated streaming generation as plain text: read each
HTTP chunk, append it to a string, and pass the concatenated text to the
renderer. With generated DSL validation enabled, migrate stream consumers to
the SDK contract instead:

1. Server-side generation should call `GenUiGenerator.generateStream(request, sink)`
   (or feed a `ProgramRoundSession` for the end-of-round check), then serialize each
   `RenderStreamEnvelope` as one SSE `data:` frame.
2. Client-side consumers should parse `text/event-stream`, JSON-decode each
   `data:` payload, and append only frames whose `type` is `"dsl"`.
3. Treat `type="dataModel"` with `seq=0` as the render data model, `type="error"`
   as terminal failure detail (`content.code`, `content.message`,
   `content.retryable`), and `type="done"` as stream closure.

Breaking change for the reference service: `examples/genui-service` `/generate`
now returns `text/event-stream` `RenderStreamEnvelope` SSE frames:
`dataModel` with `seq=0`, then accepted `dsl` frames, optional `error`
frames with `{code, message, retryable}`, and `done`. Existing consumers that
concatenate raw response body chunks, including
`examples/react-ui-dsl-demo/src/useGenerate.ts`, will break and need an SSE
envelope parsing follow-up. This docs change does not fix that frontend.

## Characterization (large data models)

Host data can be large enough that embedding it verbatim in the system prompt
blows the token budget — a 10k-row table or a 10k-point numeric series easily
runs into hundreds of KB of JSON. **Characterization** is a deterministic,
LLM-free, single-pass transform that keeps the prompt small without losing the
information the model needs to render correctly.

The dual-path invariant is the key guarantee: the same `request.response()`
feeds two decoupled paths that share only a read-only reference.

- **Render Data Model** — the full host data, sent to the runtime as the
  seq=0 `dataModel` envelope and returned by `GenUiGenerationResult.dataModel()`.
  The UI binds against this. It is **never** sampled or reduced.
- **Prompt Data Model** — the prompt's copy of the same data. When it is
  large, Characterization replaces it with a same-shape "sample tree" (the
  first K rows/elements, long strings truncated) plus a TypeScript-type
  **sidecar** that carries the complete schema, the complete enum domain for
  every low-cardinality string field (rendered as a `"a" | "b" | "c"` union —
  isomorphic to `@Switch` cases), and true array/object counts. The sidecar is
  appended after the JSON sample block in the `## Data Model` section, e.g.:

  ```ts
  data: {
    rows: {
      id: number
      name: string
      status: "closed" | "open" | "pending"
      revenue: number
      note?: string | null
    }[]  // 10000 items (showing 3)
  }
  ```

Characterization only ever touches the Prompt Data Model. It is applied at
`GenerationSdk.assemblePrompt`, the single prompt-convergence point, so it
covers both the `GenUiGenerator` path and direct `assemblePrompt` callers.
When the raw serialized data is small (`≤ triggerBytes`), the gate passes
through unchanged — small data and the cross-language golden tests stay
byte-identical to before.

### Config (`CharacterizationConfig`)

| Option | Default | Meaning |
|---|---|---|
| `enabled` | `true` | master switch; `false` restores full-data behavior everywhere |
| `triggerBytes` | `2048` | raw serialized size above which characterization activates |
| `sampleRows` (K) | `3` | number of elements kept verbatim in the sample tree |
| `maxStringLen` | `80` | string values longer than this are truncated in the sample |
| `enumMaxDistinct` | `50` | absolute cap on distinct values for a string column to qualify as an enum |
| `enumMaxRatio` | `0.5` | cap on `distinct / total` for a string column to qualify as an enum |
| `deepScanLimit` | `10000` | element cap for deep schema inference on nested object/array columns (does not limit top-level enum/count completeness) |

A string column becomes an `EnumShape` (complete domain) iff
`distinct ≤ enumMaxDistinct && distinct/total ≤ enumMaxRatio`; otherwise it
stays a free-text `string`. The enum/count pass scans the full array, not the
sample, so the domain is always complete when it qualifies.

### Configuring it

```java
GenerationSdk sdk = GenerationSdk.builder()
    .characterization(CharacterizationConfig.builder().sampleRows(5).build())
    .build();
```

or via `GenUiGenerator`:

```java
GenUiGenerator generator =
    GenUiGenerator.create(GenUiLlmConfig.defaults(), characterizationConfig);
// or: GenUiGenerator.withTransport(config, characterizationConfig, transport)
```

To roll back to full-data prompt behavior immediately:

```java
CharacterizationConfig.builder().enabled(false).build()
```

### Effect validation

`CharacterizationEffectTest` measures the compression effect on representative
fixtures and writes `target/characterization-effect-metrics.md`. On a 10k-row
table the prompt copy shrinks to ~0.05% of its uncompressed size, a 10k-point
numeric series to ~0.06%, and a deep nested object to ~0.77% — all while
keeping enum domains complete and array counts true.

## Running the tests

Run the Java SDK tests with Maven:

```bash
cd packages/genui-java-sdk
mvn test
```
