## 1. Implementation

- [x] 1.1 Add `Switch` entry to `BUILTINS` in `packages/lang-core/src/parser/builtins.ts` with signature, description, and `fn`

## 2. Tests

- [x] 2.1 Add unit tests for `@Switch` in `packages/lang-core/src/__tests__/` covering: integer enum match, string enum match, no-match with default, no-match without default, null value, non-object cases, composition inside `@Each`
