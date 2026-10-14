# 0003 — Builtin vocabulary is contract data

## Status

Accepted (2026-10-13 grill session; amended 2026-10-14 — D2 reversed, lang-core sync descoped)

## Context

The validation layers (parser classification, semantic materialization, type
checking) recognized builtin functions through hardcoded name sets in Java
(`Builtins.java`) and TS (`builtins.ts`). The internal corpus proved this had
drifted from internal truth in both directions: `@MergeObject` / `@Flatten` /
`@Number` / `@String` / `@ObjectValues` — legal internally — were rejected as
`unknown-component`, while `@FormatNumber` — removed internally — was accepted.
The refreshed `base-contract.json` (commit `c6bee2a8`) now carries the internal
truth.

## Decision

### D1 — The validation vocabulary derives from the contract in scope

Parser, semantic, and type-checking layers resolve "is this a builtin" against
the contract in scope (case contract > corpus-level contract > SDK default),
mirroring how `ContractCatalog` already resolves components. The parser needs
the vocabulary for token classification (bare PascalCase vs `@`-prefixed
call), so it receives the derived vocabulary from the validator rather than
reading static sets. Language keywords (`Query` / `Mutation` / `Action`
steps) are language, not vocabulary — they stay in code. `RepairHints`
deliberately keeps its hardcoded name set (user decision 2026-10-13): its
did-you-mean text is known-stale and accepted as cosmetic.

### D2 — No new error code: unknown `@`-functions stay `unknown-component`

An unknown name called with a leading `@` keeps emitting `unknown-component`,
same as an unknown bare PascalCase call — no `unknown-builtin` split, no
embedded replacement hints in the message (user decision 2026-10-14). The
rejected alternative (a separate code per error class for cleaner repair-bench
bucketing) was not worth the reducer / parity-oracle / baseline churn.

### D3 — Two-layer type checking; `templateBuiltin` is not laziness

Layer 1: arity and return-type rules are parsed mechanically from the
contract's `signature` strings, so a new builtin gets baseline type checking
with zero code. Layer 2: semantic rules (binder scoping, `@Each` template
typing, `@ObjectEntries`-on-array) stay in code and are added per error class
as real corpus arrives. The contract's `templateBuiltin` flag controls prompt
section placement only — it must never drive lazy/binder classification;
`Each`/`Render` binder semantics remain code keyed by name (the refreshed
contract marks `Switch` and `MergeObject` `templateBuiltin: true`, and neither
takes a binder).

### D4 — lang-core deliberately lags the contract

The contract JSON is the truth for validation and prompts; lang-core's
`builtins.ts` is NOT kept in sync (user decision 2026-10-14). Consequence:
the TS prompt oracle and the parity oracle encode the old vocabulary until
someone chooses to realign them. Cross-language byte parity holds only where
both sides agree; drift is visible in the goldens, not hidden.

## Consequences

- `PromptGoldenTest` (cross-language byte-for-byte prompt alignment) is
  REMOVED on this branch (user decision 2026-10-14): the contract's builtins
  manifest is internal truth while lang-core's manifest lags, so the
  byte-parity premise no longer holds. ADRs 0001/0002 reference it
  historically. `SdkPromptMergeTest` still locks merge wiring/ordering
  Java-side.
- `@FormatNumber` calls in old corpus become INVALID `unknown-component` —
  correct against current internal truth; repair-bench cases carry their own
  case contracts, so historical runs stay faithful to "the contract at the
  time".
- `RepairHints` may suggest adding `@` for a name its stale set doesn't know
  (the no-`@` five-piece case); accepted until revisited.
