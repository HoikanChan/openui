## Context

`issues-map.md` identifies `L1-A` as a language-layer gap rather than a component gap: the DSL has no primitive for iterating object maps with dynamic keys. Current prompt examples bias toward named arrays and chart-ready row sets, so when the model sees data shaped like `{ "dev-001": {...}, "dev-002": {...} }` it falls back to hardcoding keys from sample data instead of producing a general solution.

This change crosses two layers:
- `packages/lang-core` must expose stable runtime builtins and prompt-visible signatures.
- `packages/react-ui-dsl` must teach the model to use those builtins in its default prompt examples.

## Goals / Non-Goals

**Goals:**
- Add `@ObjectEntries(obj)` and `@ObjectKeys(obj)` as eager builtins in `lang-core`
- Fix their semantics now: `@ObjectEntries` returns `[{ key, value }, ...]`, `@ObjectKeys` returns `string[]`
- Preserve the original object property enumeration order rather than introducing hidden sorting
- Restrict the intended data shape to plain record maps and avoid expanding this change into a broader object-helper family
- Update React UI DSL prompt guidance with a dynamic-key-object example that routes the LLM toward row-based rendering instead of hardcoded keys

**Non-Goals:**
- Adding `@ObjectValues`, `@Map`, or any broader object/transform helper family
- Changing expression grammar or lazy builtin semantics
- Adding new UI components or modifying existing table/chart rendering behavior
- Solving `L1-B`, `L1-C`, or `L1-D` in the same change

## Decisions

### Add both helpers as eager builtins in `BUILTINS`

`@ObjectEntries` and `@ObjectKeys` are pure data transforms with no deferred scope, so they belong in `packages/lang-core/src/parser/builtins.ts` alongside `@Filter`, `@Sort`, and the formatting helpers. This gives one registration point for parser classification, evaluator dispatch, and prompt docs.

Alternative considered: implement them as React UI DSL-only helpers. Rejected because the capability is part of the language layer and should be available to any library built on `lang-core`.

### Fix `@ObjectEntries` output to `[{ key, value }, ...]`

The entries output will use literal `key` and `value` field names. This matches the issues map proposal, reads naturally in tables and lists, and avoids overfitting UI semantics such as `{ name, data }`.

Alternative considered: tuple output `[[key, value], ...]`. Rejected because it would immediately recreate the positional-data problem seen in `L1-B`.

### Preserve original property enumeration order

Both builtins will follow standard JavaScript property enumeration order as exposed by `Object.keys()` / `Object.entries()`. This keeps the language primitive unsurprising and avoids silently imposing a sort policy.

Alternative considered: auto-sort keys alphabetically. Rejected because sorting is a presentation decision and should not be hidden inside a low-level builtin.

### Treat plain record maps as the supported input shape

The builtins are intended for non-null, non-array record-like objects. Runtime implementations should explicitly exclude arrays and null. For unsupported inputs, the implementation should fail soft by returning an empty array rather than throwing, which stays consistent with existing builtin behavior while keeping the documented shape narrow.

Alternative considered: accept any object-like value. Rejected because that would blur the capability into a generic JavaScript interop layer and make prompt guidance less precise.

### Add React UI DSL prompt guidance as a first-class part of the change

Builtin availability alone will not improve eval results if the default prompt still demonstrates hardcoded object access patterns. The default prompt should add one explicit rule and one example for dynamic-key object maps so the LLM learns the intended authoring path immediately.

Alternative considered: rely on builtin docs alone from `lang-core` prompt generation. Rejected because `react-ui-dsl` already adds domain-specific rules and examples, and this issue is benchmark-driven inside that library.

## Risks / Trade-offs

- [Prompt guidance improves but model still prefers old hardcoded patterns in some fixtures] -> Add an example that directly mirrors the object-map failure shape and validate with targeted eval runs.
- [Returning empty arrays for unsupported input hides misuse] -> Keep docs and examples strict about record-map inputs, and cover unsupported-input behavior with unit tests so the fallback is intentional rather than accidental.
- [Future object helpers want slightly different semantics] -> Keep the implementation small and centralized so a later `@ObjectValues` or `@BuildTree` change can reuse the same plain-record helper without changing this API.
- [Prompt additions overfit table rendering] -> Phrase the example in row-conversion terms and keep `@ObjectKeys` documented for key-only list/chart cases.

## Migration Plan

No data migration or rollout coordination is required. This is an additive language feature:
1. Add builtins and tests in `lang-core`
2. Update React UI DSL prompt guidance and tests
3. Re-run targeted object-map benchmark fixtures and verify the generated DSL uses the new builtins

Rollback is straightforward: remove the builtin registrations and prompt guidance if the capability causes regressions.

## Open Questions

None for this iteration. The API surface, entry shape, ordering, and scope are all fixed for `L1-A`.
