# GenUI Java SDK

Java 21 SDK that assembles GenUI system prompts and drives an LLM to generate openui-lang UI from host data. This context covers the prompt-side data handling (how large host data is characterized before being embedded in a prompt) and the repair-side vocabulary (how validation failures are explained back to the model).

## Language

**Builtin Vocabulary**:
The set of builtin function names that validation accepts, carried by the contract's builtins section and derived per validation request — never a hardcoded name list in code. Language keywords (Query/Mutation/Action) are not vocabulary.
_Avoid_: builtin whitelist, function registry

**Builtin Signature**:
The concise declaration on a builtin contract entry: `name`, display-only `signature`/`description`, `templateBuiltin`, ordered `params` (`name`, `type`, `optional`, `variadic`) and `returnType`. There is no rule language, no `typeRule`/`typeRulesVersion`, no `input`/`callForms`, and no per-parameter `binder`/`nameSyntax`; a residual one of those, or the removed `param(n)`/`when`/`else` type syntax, fails at load with the builtin name and the offending field. The loader validates structure only (required fields, uniqueness, `TypeExpr` grammar, named type-variable bindings, ordinary optional/variadic order) and delegates Each/Render/Switch layout preconditions to `BuiltinAnalysis`, which owns the special signatures. `SignatureInference` turns `params` into entry expectations and `returnType` into the result upper bound; `BuiltinAnalysis` adds the Java-only result semantics (Sort/Filter evidence, object merge, enumeration, format-date shape refinement, template scope and deferred templates). `any` and unknown inputs stay UNPROVEN, and a return type never fabricates array cardinality, observed values, or key/value correlation.
_Avoid_: typeRule, rule compiler, builtin type registry, signature parsing for arity

**Case Contract**:
The contract bundled with a repair-bench case, representing the contract in force when the original generation ran; it overrides the SDK default for that case's reproduction and repair, and silent fallback to the default is forbidden because it would disguise vocabulary drift as not-reproduced.
_Avoid_: historical contract, legacy contract

**Current Effective Program**:
The last-wins-by-name statement map a round maintains: a re-issued `id = ...` statement overrides the previous definition, new names append, and superseded statements stop producing current errors (they survive only in the round's raw-output record). Frontend rendering and backend validation both resolve against this same definition set.
_Avoid_: deduplicated DSL, merged statements, program snapshot

**Round-End Unified Check**:
The single validation pass that runs once after a model output round ends, over the Current Effective Program, in FINAL mode — covering syntax, references, component/builtin contracts, params, data paths, static types, and page completeness. No per-statement gating or repair happens during the round.
_Avoid_: streaming gate, final check (as a separate second policy), statement gate

**Round Record**:
The per-round audit entry (raw output, current definitions, diagnostics, duration, contract version) carried on `GenUiGenerationResult.rounds()`, feeding later incremental repair (#58) and evaluation.
_Avoid_: session log, attempt log

**Base Contract**:
The frozen, frontend-exported description of the built-in component library (components, groups, examples, rules, builtins, capabilityGaps) that ships inside the SDK; the frontend is its single source of truth and the SDK only consumes it.
_Avoid_: default contract, built-in schema

**GenUI Base Supplement**:
A host-authored, build-time addition to the Base Contract that applies globally to every request: new component specs are added, a same-name component spec **replaces** the base one wholesale (despite the "supplement" name), same-name component groups merge by union, examples/rules append, and `capabilityGaps` replace in place by `id` or append for new `id`s. The host is responsible for keeping supplemented specs in sync with what its frontend can actually render.
_Avoid_: overlay, patch, base extension, custom components

**Effective Base Contract**:
The result of merging the GenUI Base Supplement into the Base Contract when the SDK is built; everything downstream — prompt assembly and GenUI Extension collision checks — sees only this merged view, never the two layers separately.
_Avoid_: merged contract, final contract

**GenUI Extension**:
A per-request component/tool/capability-gap package selected by extensionId on top of the Effective Base Contract; strictly append-only — name/id collisions with the effective base are rejected, never overridden.
_Avoid_: plugin, supplement (reserved for the global build-time GenUI Base Supplement layer)

**Capability Gap**:
A contract-declared operation or component capability (`id`, `kind`, `name`, `aliases`, `phrases`, `fallback`) that openui-lang intentionally does not support dynamically (e.g. `group-by`, `top-n`, `join`), surfaced both in the initial system prompt (`## Capability Boundary`) and in deterministic repair routing when a model calls one of its `aliases`.
_Avoid_: unsupported feature list, hardcoded blacklist

**Capability Notice**:
The DSL convention `capabilityNotice = TextCallout("info", "部分需求暂不支持", "暂不支持<name>，以下按原始数据展示")` placed as the first child of `root` when a user request hits a **Capability Gap** and falls back to faithful raw-data display.
_Avoid_: error banner, fallback markdown

**Render Data Model**:
The full host data the SDK forwards to the runtime as the seq=0 `dataModel` envelope; the rendered UI binds against this, so it is never sampled or reduced.
_Avoid_: response, payload, raw data

**Prompt Data Model**:
A lossy, characterized view of the same host data embedded in the system prompt purely so the model understands shape and characteristics; never rendered, safe to sample aggressively.
_Avoid_: sampled data, compressed data

**Characterization**:
The deterministic, LLM-free transform that turns the Render Data Model into the Prompt Data Model.
_Avoid_: compression (implies recoverability, which is not a goal), summarization

**Enum Domain**:
The complete set of distinct values of a low-cardinality string field, surfaced so the model can generate exhaustive `@Switch` cases without dropping any.
_Avoid_: categories, sample values

**Sample Rows**:
A few deterministic, representative elements of a large array kept verbatim so the model sees realistic field shapes.
_Avoid_: examples, preview

**Sidecar**:
Characterization metadata (counts, inferred schema, Enum Domains) presented out-of-band alongside the data tree rather than inside it, so that `data.<field>` reference paths in the tree stay valid.
_Avoid_: annotations, metadata block

**Reask Prompt**:
The deterministic repair message sent back to the model after a failed generation, presenting the validation issues it must fix.
_Avoid_: retry prompt, error prompt

**Derived Issue**:
A validation issue that exists only because another failure on the same statement corrupted parsing or materialization; it describes a symptom, not a cause.
_Avoid_: cascade error, secondary error

**Cascade Suppression**:
Dropping Derived Issues from a Reask Prompt so the repair model sees causes instead of symptoms. Applies only to the prompt view — never to the validation verdict itself.
_Avoid_: dedup (different concern), filtering

**Root-cause Hint**:
A hint that names the underlying language-subset violation behind an error (e.g. a JS global that does not exist in openui-lang, or a builtin missing its `@`). Exempt from Cascade Suppression because it *is* the explanation.
_Avoid_: suggestion, tip

## Relationships

- A **Base Contract** plus at most one **GenUI Base Supplement** yields the **Effective Base Contract** (build-time, global)
- A **GenUI Extension** stacks on the **Effective Base Contract** per request (append-only); a **GenUI Base Supplement** may replace base entries, an Extension may not
- A **Render Data Model** is reduced by **Characterization** into a **Prompt Data Model**
- A **Prompt Data Model** is a same-shape data tree (**Sample Rows** in place of full arrays) plus a **Sidecar**
- A **Sidecar** carries the **Enum Domain** and counts that **Sample Rows** alone cannot guarantee
- A **Reask Prompt** presents validation issues after **Cascade Suppression**; a **Root-cause Hint** always survives suppression
- A **Derived Issue** is what **Cascade Suppression** removes
- A **Capability Gap** declared in the **Effective Base Contract** (or **GenUI Extension**) drives both the initial `## Capability Boundary` prompt table and **Root-cause Hint** routing toward a **Capability Notice** fallback

## Flagged ambiguities

- "data model" was used to mean both the full runtime data and the prompt copy — resolved: **Render Data Model** (full, rendered) vs **Prompt Data Model** (characterized, never rendered). The same `request.response()` feeds both paths but only the prompt path is characterized.
