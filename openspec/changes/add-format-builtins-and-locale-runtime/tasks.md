## 1. lang-core formatting runtime

- [ ] 1.1 Extend the lang-core builtin runtime contract so builtin evaluation can receive renderer-supplied formatting defaults such as `locale`
- [ ] 1.2 Add `@FormatDate`, `@FormatBytes`, `@FormatNumber`, `@FormatPercent`, and `@FormatDuration` to the shared builtin registry with the agreed prompt-visible signatures and descriptions
- [ ] 1.3 Implement the five formatting builtins with fail-soft behavior, locale override support, SI/IEC byte handling, ratio-based percent formatting, and seconds-by-default duration formatting
- [ ] 1.4 Add unit tests covering valid formatting behavior, locale inheritance/override, and nullish or invalid-input fallback behavior

## 2. react-lang locale plumbing

- [ ] 2.1 Add `locale?: string` to `react-lang` `Renderer` props and thread it through `useOpenUIState` into the evaluation context used during prop resolution
- [ ] 2.2 Extend runtime/context typings so formatting defaults are available through `OpenUIContext` without introducing a DSL-level locale prop
- [ ] 2.3 Add renderer-level tests proving `@Format*` expressions pick up the renderer default locale and honor explicit locale arguments when present

## 3. react-ui-dsl schema and prompt migration

- [ ] 3.1 Remove `format` from `Table` column schema/types/view logic and from `Descriptions` `DescField` schema/types/view logic
- [ ] 3.2 Update `dslLibrary` prompt rules and examples to use `@Format*` expressions instead of `format: "date"` or `DescField(..., format)`
- [ ] 3.3 Ensure schema validation or parsing fails clearly when legacy `format` props are still present so the breaking migration is explicit

## 4. Verification and regression coverage

- [ ] 4.1 Update affected unit tests, stories, and snapshots in `react-ui-dsl` and `lang-core` to reflect the new builtin-based formatting model
- [ ] 4.2 Add end-to-end coverage demonstrating formatted table and descriptions output authored through `@Format*` expressions
- [ ] 4.3 Run the relevant package test suites and prompt/snapshot verification for `lang-core`, `react-lang`, and `react-ui-dsl`
