## 1. lang-core builtin registration and semantics

- [ ] 1.1 Add `ObjectEntries` and `ObjectKeys` entries to `packages/lang-core/src/parser/builtins.ts` with prompt-visible signatures and descriptions
- [ ] 1.2 Implement a shared plain-record helper so both builtins exclude `null` and arrays, preserve property enumeration order, and return empty arrays for unsupported inputs
- [ ] 1.3 Add or update lang-core unit tests covering builtin classification, ordered output, composition-friendly entry shape, and unsupported-input behavior

## 2. Prompt documentation and React UI DSL guidance

- [ ] 2.1 Verify generated builtin docs include `@ObjectEntries` and `@ObjectKeys` in the lang-core prompt surface
- [ ] 2.2 Update `packages/react-ui-dsl/src/genui-lib/dslLibrary.tsx` with one rule and one example showing how dynamic-key object maps should use `@ObjectEntries` / `@ObjectKeys` instead of hardcoded keys
- [ ] 2.3 Extend React UI DSL prompt tests to assert that the new guidance and example are present

## 3. Eval-oriented regression coverage

- [ ] 3.1 Add or update focused tests/fixtures that exercise object-map-shaped data and verify the new builtins can feed iterable UI patterns
- [ ] 3.2 Re-run targeted benchmark or eval-loop coverage for object-map fixtures and confirm generated DSL can express the generalized pattern without hardcoded sample keys
