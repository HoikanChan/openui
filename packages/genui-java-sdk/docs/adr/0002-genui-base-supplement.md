# 0002 — GenUI Base Supplement

## Status

Accepted

## Context

The base contract (`openui/base-contract.json`, exported by
`packages/react-ui-dsl` and frozen into the jar) is the only component library
every request sees. Hosts had two extension points, neither of which fits
"company-internal components available globally, plus the ability to correct a
base component's prompt docs":

- `GenUIExtension` is per-request (selected by `extensionId`), append-only,
  and rejects any component name that collides with base — it can never
  override base docs and never applies globally.
- `builder().baseContract(...)` replaces the whole contract; it is a test
  backdoor. A host using it must maintain a full copy of the base contract
  and inevitably drifts from it on every SDK upgrade.

Terminology (Base Contract / GenUI Base Supplement / Effective Base Contract /
GenUI Extension) is defined in `packages/genui-java-sdk/CONTEXT.md`. The hard
constraint: without a supplement, prompt output must remain byte-identical
(`PromptGoldenTest` is the cross-language anchor).

## Decision

### D1 — Same-name components are replaced wholesale, not merge-patched

A supplement component whose name exists in base replaces the base spec
entirely — description and propsSchema together — while keeping the base
position; new names are appended. Groups, by contrast, merge as an
order-preserving de-duplicated member union (base first) with notes appended;
examples and rules append; `contractVersion`/`root`/`tools`/`builtins` are
untouchable.

Rejected: JSON-merge-patch-style partial component updates. A component spec
is one prompt document; patching fields of it produces hybrids nobody
authored, and "which half came from where" is undiagnosable from a bad
generation. Wholesale replacement is predictable and reviewable — at the cost
that a replaced component shadows upstream doc improvements until the host
refreshes the entry (README tells hosts to review same-name entries on SDK
upgrades). No deletion capability on purpose: removing base components would
break the frozen contract's group references and golden expectations; if a
host truly needs that, the existing frontend re-export path is the answer.

### D2 — One JSON document, no programmatic assembly API

The only supported input is a JSON file (same shape as `base-contract.json`
restricted to `supplementVersion`/`components`/`componentGroups`/`examples`/
`additionalRules`), loaded via `GenUIBaseSupplementLoader.fromJson/fromResource`,
at most one per SDK instance.

Rejected: a builder-style programmatic API for composing supplements. JSON
keeps the artifact diffable, reviewable, and producible by the host's frontend
registry tooling; a Java assembly API would invite scattering component docs
through host code and would duplicate the loader's validation surface. Multi-
team composition is explicitly the host's preprocessing job — merging inside
the SDK would force it to own conflict-resolution policy between packages it
knows nothing about. The record constructor stays public only because Java
requires it; Javadoc pins the loader as the sole supported path.

Unknown top-level keys are rejected (listing all illegal keys), unlike the
tolerant `GenerationContractLoader`: the base contract is machine-exported, a
supplement is hand-written, and a silently ignored typo (`additionalRule`)
would masquerade as a no-op rule.

### D3 — Merge once at build time into a plain `GenerationContract`

`GenerationSdk`'s constructor validates the supplement's own components first
(errors prefixed so they attribute to the supplement), then applies
`GenUIBaseSupplement.applyTo(base)` and stores the result in the existing
`baseContract` field. Downstream — prompt assembly, group-reference
validation, `GenUIExtension` collision checks — sees only the Effective Base
Contract and is zero-diff.

Rejected: a distinct `EffectiveBaseContract` type (no consumer of the layering
exists after construction; the supplement version is deliberately not reported
in `GenUIPromptAssemblyMetadata`) and reusing `GenUIExtension` as the carrier
(its semantics are per-request, append-only, collision-rejecting — the exact
opposite of the supplement on every axis that matters).

### D4 — No frontend export tool in this repo

Producing the supplement JSON (and keeping it aligned with what the host
frontend can actually render) is the host's responsibility. This repo defines
the format and provides Java-side loading/merging/validation only. Rejected
for now: a `react-ui-dsl` helper that exports a supplement from a component
registry — the hosts' frontends are not in this repo, so any such tool would
codify assumptions about registries we cannot see. The README mitigates the
drift risk with explicit host-responsibility guidance.

## Consequences

- Hosts get global components and base doc corrections from one reviewable
  JSON file; `GenUIExtension` semantics are unchanged (collisions are now
  checked against the Effective Base Contract, so extensions also cannot
  shadow supplement components).
- The no-supplement path is byte-identical (`applyTo` is never invoked), so
  `PromptGoldenTest` keeps anchoring cross-language parity; supplement merge
  semantics are locked by Java-only unit tests
  (`GenerationSdkBaseSupplementTest`, `GenUIBaseSupplementLoaderTest`) since
  there is no TypeScript oracle for a Java-only capability.
- A host's replaced component can silently lag upstream doc improvements —
  accepted, documented, and reviewable in the host's own diff.
