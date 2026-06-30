# 0001 — Prompt data characterization

## Status

Accepted

## Context

GenUI prompts embed the host data (the "Render Data Model" — see
`packages/genui-java-sdk/CONTEXT.md` for the full terminology) so the model
understands the shape of `data.<field>` it can bind to. For small payloads
this is fine: `PromptAssembler.dataModelSection` (Java) and `prompt.ts`'s
`dataModelSection` (TS oracle) both pretty-print the full JSON straight into
the `## Data Model` section, and the two are pinned byte-for-byte by the
`prompt-golden` fixtures.

For large payloads — a 10k-row table, a 10k-point numeric series, a deeply
nested dashboard object — embedding the full JSON blows the prompt's token
budget, while still needing to preserve two things the model cannot do
without: the complete domain of any low-cardinality string field (so
`@Switch`-style rendering doesn't silently drop a case), and true array/object
counts (so the model doesn't under- or over-estimate scale).

The same `request.response()` already feeds two paths in `GenUiGenerator`:
it is forwarded untouched to the runtime as the seq=0 `dataModel` envelope
and returned by `GenUiGenerationResult.dataModel()` (this is what the UI
actually renders against), and it is also wrapped into a `DataModelSpec` and
assembled into the prompt text (this is only for the model to understand
shape). These two paths share only a read-only reference to the same response
object and never write back into it.

## Decision

### D1 — Characterize at the single prompt-convergence point

Characterization is injected as a pre-assembly pure transform inside
`GenerationSdk.assemblePrompt`, immediately before `effectiveRequest.dataModel()`
is handed to `PromptAssembler.PromptInput`. It is **not** injected at
`GenUiGenerator.toPromptRequest`.

Rationale: `assemblePrompt` is the only method both `GenUiGenerator.generate`
/ `generateStream` and any SDK consumer calling `assemblePrompt` directly are
guaranteed to pass through. Hooking `toPromptRequest` instead would cover the
generator path but miss direct `assemblePrompt` callers, requiring a second
integration point (and a second place to keep in sync) for no benefit.

A consequence of this placement is the parity rule for cross-language
alignment: byte-for-byte parity between the Java `dataModelSection` and the TS
oracle's `dataModelSection` is required **only** on the uncompressed
(pass-through) path — i.e. when raw serialized size is `≤ triggerBytes` or
characterization is disabled. The `prompt-golden` fixtures are kept below the
trigger threshold (or run with characterization disabled) so they continue to
assert this required parity. When characterization triggers, the Java prompt
is explicitly allowed to diverge from `prompt.ts`, because the TypeScript side
has no equivalent reduction mechanism — there is nothing to be at parity
with.

### D2 — The dual-path invariant, and an out-of-band sidecar

The same `request.response()` keeps feeding two decoupled paths that share
only a read-only reference:

- the full **Render Data Model**, sent as the seq=0 `dataModel` envelope and
  returned by `GenUiGenerationResult.dataModel()` — never reduced, because the
  rendered UI binds against it; and
- the characterized **Prompt Data Model**, built by replacing the data tree
  with a same-shape sample (first K elements, long strings truncated) and
  attaching a TypeScript-type **sidecar** describing the complete inferred
  schema, complete enum domains, and true counts.

Characterization touches only the latter path. The transform never mutates or
re-derives the value handed to the former path.

The sidecar is carried out-of-band: `DataModelSpec` gained a `shapeSidecar`
field, and `PromptAssembler.dataModelSection` appends it (fenced as a ```` ```ts ````
block, after a `Data shape (full dataset):` heading) immediately following the
existing JSON sample block, only when `shapeSidecar` is non-empty. The
alternative — injecting shape/count metadata directly into the sampled data
tree (e.g. a synthetic `__count__` key) — was rejected because it risks the
model echoing the synthetic key back in a `data.<field>` reference path and
breaking the binding. Keeping the sidecar out-of-band means `data.<field>`
paths in the sample tree stay exactly as valid as they were for the full data,
and the pass-through (no-sidecar) case is textually unchanged from before
this feature existed.

## Consequences

- Small/disabled-characterization prompts are byte-identical to pre-feature
  output; the `prompt-golden` cross-language fixtures require no changes.
- Large-data prompts shrink dramatically (measured ~99.9% reduction on wide
  tables and numeric series, ~99% on deep nested objects — see
  `CharacterizationEffectTest` and `target/characterization-effect-metrics.md`)
  while keeping enum domains complete and counts true.
- Cross-language byte parity is a property of the uncompressed path only; the
  TS oracle is not expected to ever implement an equivalent reduction, and
  Java's compressed-path prompt text is intentionally unconstrained by it.
- A single integration point (`GenerationSdk.assemblePrompt`) means any future
  caller of `assemblePrompt` — direct or via `GenUiGenerator` — gets
  characterization for free, and `enabled=false` is a one-line rollback.
- `DataModelSpec` carries an additional field (`shapeSidecar`); any code
  constructing `DataModelSpec` by hand needs to either supply it or accept the
  existing convenience constructor's default.

See `packages/genui-java-sdk/CONTEXT.md` for the Render Data Model / Prompt
Data Model / Characterization / Enum Domain / Sample Rows / Sidecar
terminology used throughout this ADR.
