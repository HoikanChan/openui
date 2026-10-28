# 0004 — Capability boundary and degradation (`capabilityGaps`)

## Status

Accepted (2026-10-21 issue #71 / #74 / #75; amended 2026-10-22 review)

## Context

In production corpora, the LLM repeatedly hallucinates unsupported builtin
aggregation/grouping/slicing/joining functions (`@GroupBy`, `@Group`,
`@ObjectFromEntries`, `@Slice`, `@Take`, `@Top`, `@Join`, `@Lookup`, `@Merge`)
or unsupported components. Without an explicit capability boundary and
degradation protocol:

1. The initial system prompt only lists what is supported, leaving the model to
   guess familiar SQL/Lodash/JS names when user requests exceed the contract.
2. When validation fails with `unknown-component`, generic did-you-mean hints
   cannot guide the model to a valid structural fallback, causing multi-round
   repair loops.

## Decision

### D1 — Contract-driven `capabilityGaps` declaration (`kind: "data" | "component"`)

`base-contract.json` and `GenUIBaseSupplement` carry an optional top-level
`capabilityGaps` array (`List<CapabilityGapSpec>`). Each entry declares:
- `id`: unique identifier (e.g., `group-by`, `top-n`, `join`)
- `kind`: `"data"` or `"component"` (strictly validated at load time; currently
  only `"data"` entries are defined in `base-contract.json`, while `"component"`
  is reserved for future component-gap routing without a separate runtime branch
  today)
- `name`: human-readable capability name (e.g., `"分组展示"`)
- `aliases`: hallucinated function/component names (normalized without leading
  `@` via `CapabilityGapSpec.normalizeAlias`, matched case-insensitively)
- `phrases`: typical user-intent phrases
- `fallback`: concrete fallback instruction using existing components/builtins

### D2 — Initial prompt `## Capability Boundary & Degradation` section

When `contract.capabilityGaps()` is non-empty, `PromptAssembler` emits a
`## Capability Boundary & Degradation` section immediately before
`## Critical Rules`. It lists each gap's name, aliases, phrases, and fallback,
and defines the single-source-of-truth
`CapabilityGapSpec.NOTICE_STATEMENT_TEMPLATE`:
`capabilityNotice = TextCallout("info", "部分需求暂不支持", "暂不支持<name>，以下按原始数据展示")`
mounted as the first child of `root = Stack([capabilityNotice, ...])` (keeping
the notice in the same language as the user's request).

### D3 — Base supplement merge and automatic eviction

`GenUIBaseSupplement.applyTo` merges `capabilityGaps` by `id`:
- Same `id`: wholesale replacement of the base entry at its original position.
- New `id`: appended after base entries.
- Automatic eviction: after merging, any capability gap whose `id` matches a
  newly added supplement component name is automatically evicted.

### D4 — Validation invariants and error attribution

At contract load and supplement merge time:
- `id`, `kind`, `name`, `fallback` must be non-blank; `kind` must be `"data"`
  or `"component"`.
- `aliases` must be non-empty, contain no null/blank elements, and have no
  case-insensitive duplicates within or across gaps.
- `phrases` (when provided) must contain no null/blank elements.
- No alias may collide with any registered component or builtin name in the
  contract.
- `GenerationSdk` validates `baseContract` first (attributed to
  `"base contract"`), then validates `baseSupplement` and the post-`applyTo`
  merged contract (attributed to `"base supplement"`).

### D5 — Three-tier `RepairHints.unknownComponentHint` routing

For `unknown-component` errors, `RepairHints.unknownComponentHint` routes in
three tiers:
1. **Tier 1 (Known builtin missing `@`)**: `name` matches a builtin in the
   contract (`BuiltinCatalog.fromContract(contract)`) or fallback set →
   instruct the model to prefix `@`.
2. **Tier 2 (Matched `CapabilityGapSpec` alias)**: `name` (normalized via
   `CapabilityGapSpec.normalizeAlias`, case-insensitive) matches a gap alias →
   emit a `CAPABILITY GAP` hint stating the pre-aggregated data premise (`if
   the input data already contains pre-aggregated/grouped rows, display that
   existing data directly; otherwise...`), the gap's `fallback`, and
   `gap.noticeStatement()` in the user's request language.
3. **Tier 3 (Unmatched unknown component)**: emit a `Degradation Rule`
   forbidding inventing undocumented names and requiring replacement with
   documented components plus `capabilityNotice = TextCallout(...)`.

### D6 — Incremental repair `## Capability boundary (overrides the original request)` section

When `RepairPromptBuilder` detects any `unknown-component` error
matching a `CapabilityGapSpec` alias, it injects a top-level
`## Capability boundary (overrides the original request)` section before
`## Incremental repair rules (fixed)`, and marks the original user request in
the user message as `(for context only — see capability boundary in system
prompt)`. When multiple distinct gaps match in the same round, each gap is
rendered under a numbered `### <n>. <name> (<id>)` sub-heading.

### D7 — Two-round circuit breaker on unmatched `unknown-component`

In `GenUiGenerator`, if two consecutive incremental repair rounds fail with
unmatched `unknown-component` errors (errors that do not match any
`CapabilityGapSpec` alias and are not known builtins missing `@`), the
generator aborts further incremental repair rounds early instead of exhausting
`maxRepairRounds`.

### D8 — Unreached (dead-code) statements do not block FINAL validation

Type validation only checks statements reachable from `root`. Unreached helper
statements left behind after an incremental repair splice do not affect frontend
rendering and must not trigger validation failure or extra repair rounds.
Repair bench derives the reachable statement set from the entry statement's
references and reports capability-gap aliases only from reachable statements.

## Consequences

- `CapabilityGapSpec.NOTICE_STATEMENT_TEMPLATE` and
  `CapabilityGapSpec#noticeStatement()` are the single source of truth for the
  `capabilityNotice = TextCallout(...)` snippet across `PromptAssembler`,
  `RepairHints`, and `RepairPromptBuilder`.
- `repair-bench` reports per-case `capabilityNoticePresent`,
  `remainingCapabilityGapAliases` and
  `consecutiveUnmatchedUnknownComponents`, plus a top-level
  `summary.capabilityBoundary` aggregate.
