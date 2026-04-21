## Why

API data commonly contains integer or string enum fields (e.g. `status: 0|1|2|3`, `role: "admin"|"user"`) that need to be mapped to display labels or components. The current language only has nested ternary chains for this, which become unreadable past two cases and are error-prone for LLMs to generate correctly.

## What Changes

- Add `@Switch(value, {case: result, ...}, default?)` to the `BUILTINS` registry in `lang-core`
- Single-file change: `packages/lang-core/src/parser/builtins.ts`
- Prompt auto-updates via the existing `builtinFunctionsSection()` generator — no manual prompt edits needed
- Add unit tests for the new builtin

## Capabilities

### New Capabilities

- `switch-builtin`: Enum-value-to-display mapping via `@Switch(value, cases, default?)` — covers integer enums (0/1/2/3), string enums ("admin"/"user"), and mapping to components (Badge, Label)

### Modified Capabilities

## Impact

- `packages/lang-core/src/parser/builtins.ts` — add `Switch` entry to `BUILTINS`
- `packages/lang-core/src/__tests__/` — new test cases
- LLM prompt auto-updated (no manual change) via `builtinFunctionsSection()`
- No breaking changes; purely additive
