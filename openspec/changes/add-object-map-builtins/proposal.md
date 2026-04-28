## Why

The React UI DSL can already project named fields from arrays, but it has no builtin for iterating dynamic-key object maps such as `{ "dev-001": {...}, "dev-002": {...} }`. In benchmark fixtures this forces the LLM to hardcode keys from sample data, which produces brittle DSL and low scores on object-map-shaped inputs.

## What Changes

- Add `@ObjectEntries(obj)` as a new eager builtin that converts a record map into `[{ key, value }, ...]`.
- Add `@ObjectKeys(obj)` as a new eager builtin that converts a record map into `string[]`.
- Define stable semantics for both builtins: plain record-map input only, original property enumeration order preserved, unsupported inputs handled safely.
- Update React UI DSL prompt guidance and examples so the LLM prefers `@ObjectEntries` / `@ObjectKeys` over hardcoding dynamic object keys.

## Capabilities

### New Capabilities
- `object-map-builtins`: Builtins and prompt guidance for converting dynamic-key object maps into iterable row/key arrays in OpenUI Lang and React UI DSL.

### Modified Capabilities
- None.

## Impact

- `packages/lang-core` builtin registry, runtime semantics, and prompt builtin documentation
- `packages/react-ui-dsl` default prompt rules, examples, and prompt-surface tests
- GenUI eval / benchmark coverage for dynamic-key object fixtures
