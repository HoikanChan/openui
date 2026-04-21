## Context

`builtins.ts` is the single source of truth for all data functions in `lang-core`. It drives runtime evaluation (evaluator.ts), prompt generation (prompt.ts), and parser classification (BUILTIN_NAMES). Adding a new eager builtin requires only one entry in the `BUILTINS` record — all downstream consumers pick it up automatically.

The existing `Filter` builtin provides a reference pattern: a pure `fn(...args) => unknown` with no AST access needed. `Switch` follows the same pattern.

## Goals / Non-Goals

**Goals:**
- Add `@Switch(value, cases, default?)` as an eager builtin for enum-to-display mapping
- Support both integer enums (numeric keys coerced to strings) and string enums
- Auto-propagate to the LLM prompt via `builtinFunctionsSection()`

**Non-Goals:**
- Lazy/AST-level evaluation (not needed; all branches are plain values or pre-evaluated components)
- Array-indexed shorthand (discussed and rejected — adds dual-mode complexity for minimal gain)
- Pattern matching beyond exact key equality

## Decisions

**Eager builtin, not lazy**

`Switch` goes in `BUILTINS`, not `LAZY_BUILTINS`. Lazy builtins (only `Each` today) receive raw AST nodes and manage their own evaluation context — necessary for loop variable scoping. `Switch` has no such requirement: all case values are fully evaluated before `fn` is called, which is the correct behavior for a lookup table.

**Object literal as case map, string-coerced key lookup**

Object keys in openui-lang are always strings at the AST level (`{0: "x"}` stores key `"0"`). `String(value ?? "")` coercion means integer values `0`, `1`, `2` and their string equivalents `"0"`, `"1"`, `"2"` both match correctly — no special handling needed for numeric enums.

Alternative considered: two-array form `@Switch(val, [k1,k2], [v1,v2])` — rejected, more verbose. Array-index shorthand `@Switch(val, ["a","b","c"])` — rejected, dual-mode is confusing.

## Risks / Trade-offs

- **All branches eagerly evaluated**: component-valued cases (e.g. `Badge(...)`) are all instantiated before the lookup. Acceptable — there are no side effects in this DSL and the number of cases is small.
- **String coercion for keys**: `true` and `"true"` match the same key. Unlikely to cause issues in practice given typical enum shapes.

## Migration Plan

Purely additive. No existing behavior changes. Ships as a minor version bump.
