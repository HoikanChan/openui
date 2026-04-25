## Context

OpenUI Lang already has a shared builtin registry in `packages/lang-core/src/parser/builtins.ts`, and that registry drives three surfaces at once: runtime evaluation, parser builtin detection, and prompt documentation. By contrast, React UI DSL `Table` and `Descriptions` each implement their own local `format` handling inside view code with direct `Date` and `toLocale*` calls. The result is an awkward split:

- formatting works only inside two components instead of everywhere expressions are allowed
- the same value may format differently depending on whether it passes through a builtin or a component-local formatter
- locale behavior is implicit and host-dependent rather than controlled by the renderer

This change is cross-cutting because it touches `lang-core` builtin evaluation and prompt generation, `react-lang` runtime context, and `react-ui-dsl` schema and prompt examples.

## Goals / Non-Goals

**Goals:**
- Add expression-level formatting builtins for the highest-frequency AI-generated presentation cases: date, bytes, number, percent, and duration
- Provide one shared locale default at renderer/runtime scope so all formatting builtins inherit the same fallback behavior
- Remove `format` from `Table` and `Descriptions` so formatting is modeled consistently as data transformation before rendering
- Regenerate prompt guidance and examples so models learn the new preferred authoring pattern
- Preserve fail-soft formatting behavior so malformed or unexpected values do not crash rendering

**Non-Goals:**
- Add custom date pattern support in this iteration; `@FormatDate` will support only named preset styles
- Add first-wave `@FormatCurrency`, `@FormatRelativeTime`, or arbitrary unit-formatting builtins beyond the agreed five
- Preserve backward compatibility for existing `format` props on `Table` or `Descriptions`; this is a deliberate breaking cleanup
- Introduce a DSL-level locale prop on root/layout components
- Bring `lang-core-java` to feature parity in this change

## Decisions

### Locale defaults live in renderer runtime context, not in DSL component props

The host application should control locale once at the renderer boundary, and DSL should remain focused on declarative UI structure plus expression logic. The change will add a `locale?: string` prop to `react-lang`'s `Renderer`, store it in `OpenUIContext`, and pass it into the runtime evaluation context used by `lang-core`.

This keeps formatting configuration where other runtime-only concerns already live (`toolProvider`, `dataModel`, store/query evaluation) and avoids polluting prompt examples with unnecessary root-level locale plumbing.

Alternatives considered:
- Add locale as a root DSL prop: rejected because it makes LLM output noisier and leaks host config into generated code.
- Let each builtin require an explicit locale argument: rejected because AI-generated DSL would become repetitive and inconsistent.

### Formatting builtins remain centralized in the shared builtin registry

The new `@Format*` functions belong in `packages/lang-core/src/parser/builtins.ts` so they automatically participate in parser builtin detection and prompt docs. The evaluator will stop treating builtins as pure `(...args) => unknown` helpers and instead allow access to a small runtime formatting context containing the resolved default locale.

The runtime contract will stay intentionally narrow:
- `locale?: string`

This preserves the existing single-source-of-truth registry while avoiding a one-off special case in the evaluator for each formatting builtin.

Alternatives considered:
- Special-case `@Format*` inside `evaluator.ts`: rejected because it splits builtin behavior across files and weakens prompt/runtime alignment.
- Store formatting helpers only in `react-ui-dsl`: rejected because builtins must work in any expression context, not just specific components.

### The first-wave builtin API optimizes for AI generation, not maximum configurability

The builtin family will prefer a few high-signal optional parameters over highly generic formatting DSLs:

- `@FormatDate(value, style?, locale?)`
  - preset-only styles: `"date" | "dateTime" | "time" | "relative"`
- `@FormatBytes(value, system?, decimals?, locale?)`
  - `system`: `"si" | "iec"`
  - default system is fixed by implementation, with the other explicitly selectable
- `@FormatNumber(value, decimals?, locale?)`
- `@FormatPercent(value, decimals?, locale?)`
  - input is a ratio (`0.123` -> `12.3%`)
- `@FormatDuration(value, unit?, locale?)`
  - `unit`: `"s" | "ms"`
  - default input unit is `"s"`

The common theme is short, guessable signatures that are easy for the model to reach for during generation. We deliberately avoid a generic `@Format(value, kind, options)` API because it introduces branching, hidden defaults, and a larger prompt burden.

Alternatives considered:
- A single `@Format(...)` builtin: rejected because it is harder for the model to discover and easier to misuse.
- Exposing arbitrary date patterns now: rejected because preset styles are enough to replace current `format` usage and keep the first version stable.

### Formatting builtins fail soft and return display strings

All `@Format*` builtins will return strings intended for display. For nullish inputs they return an empty string. For unparseable or semantically invalid inputs they return `String(value)` rather than throwing. This mirrors the current UI behavior where invalid dates fall back to the raw source value and keeps generated UIs resilient against imperfect backend data.

This also simplifies component expectations after removing `format`: `Table` and `Descriptions` just render the value they are given.

Alternatives considered:
- Throw on invalid input: rejected because it turns minor data quality issues into render failures.
- Return mixed string/number types depending on builtin: rejected because display builtins should have a single output category.

### `Table` and `Descriptions` stop owning formatting semantics entirely

`react-ui-dsl` will remove:
- `Col.options.format`
- `DescField.format`
- all view-layer helpers that interpret those options
- all prompt rules/examples that recommend component-local formatting

Formatting then becomes an expression concern. Examples:
- `Col("Joined", "joinedAt", { cell: @Render("v", Text(@FormatDate(v, "date"))) })`
- `DescField("Joined", @FormatDate(data.user.joinedAt, "dateTime"))`

For the common plain-text case, authors may also pass a preformatted value directly if the schema already allows expression results at that position.

Alternatives considered:
- Keep `format` as deprecated compatibility sugar: rejected because the goal is to eliminate the split mental model immediately.
- Remove `format` only from one component first: rejected because it would preserve inconsistency and prompt ambiguity.

### Prompt guidance must actively steer the model away from removed props

This change is only successful if generated DSL stops using removed `format` props. The prompt layer in `dslLibrary` must therefore be updated in three places:
- builtin docs include the new `@Format*` functions
- examples use `@Format*` in `Table` and `Descriptions`
- additional rules explicitly forbid `format` on `Col` and `DescField`

Because the target is AI generation rather than hand-authored DSL, prompt steering is part of the core design rather than follow-up polish.

## Risks / Trade-offs

- `Renderer` prop surface grows with a new locale concern -> Mitigation: keep it to one optional `locale` prop and thread it through existing context/evaluation pathways instead of adding a separate provider API.
- Builtin signatures become more numerous -> Mitigation: keep the family tightly scoped to five high-frequency functions and document them consistently in prompt output.
- Existing tests/examples that rely on `format` will fail all at once -> Mitigation: treat the removal as part of the same change and update prompt fixtures, stories, and view-layer tests in one migration sweep.
- Locale-sensitive output can still vary if only the browser default is used -> Mitigation: let hosts set `Renderer.locale`; tests should pin locale explicitly when asserting formatted output.
- `@FormatPercent` semantics could be misread as expecting whole percentages -> Mitigation: document ratio input explicitly in prompt docs and spec scenarios.

## Migration Plan

1. Add the new `@Format*` builtin definitions and evaluator runtime-context plumbing in `lang-core`.
2. Add `locale` to `react-lang` `Renderer` and pass it into the evaluation context used during prop resolution and slot hydration.
3. Remove `format` from `Table` and `Descriptions` schemas and delete component-local format helpers.
4. Rewrite prompt rules/examples and regenerate affected system-prompt snapshots or fixtures.
5. Update tests and stories to use expression-level formatting.
6. Rollback strategy if needed: revert the change set as one unit so builtin additions and `format` removals do not drift apart.

## Open Questions

None. The remaining ambiguity around locale placement, builtin family scope, duration input units, and compatibility strategy has been resolved in favor of renderer-scoped locale, five explicit `@Format*` builtins, default-seconds duration input with optional explicit unit, and immediate removal of legacy `format` props.
