## Why

OpenUI Lang currently splits display formatting across two incompatible paths: expression builtins handle data transforms, while React UI DSL `Table` and `Descriptions` each carry their own ad hoc `format` options for date/time rendering. That split makes LLM generation less consistent, blocks reusable formatting outside those two components, and leaves locale behavior tied to whatever `toLocale*` defaults happen to exist in the host environment.

## What Changes

- Add a new `format-builtins` capability that introduces first-class formatting builtins for common AI-generated display cases: `@FormatDate`, `@FormatBytes`, `@FormatNumber`, `@FormatPercent`, and `@FormatDuration`.
- Add runtime-level locale configuration to the React renderer so formatting builtins can inherit a shared default locale from the host application while still allowing per-call overrides.
- Update prompt generation and examples to prefer expression-level formatting builtins instead of component-local `format` flags.
- **BREAKING**: Remove `format` from React UI DSL `Table` column options and `DescField`, requiring authors and generated DSL to format values through `@Format*` expressions before they reach the component.
- Migrate affected tests, docs, and examples so display formatting behavior is described through builtins rather than view-specific props.

## Capabilities

### New Capabilities

- `format-builtins`: Expression-level formatting builtins plus shared renderer locale defaults for date, bytes, number, percent, and duration presentation.

### Modified Capabilities

- `llm-friendly-table-dsl`: Table columns no longer expose a `format` option; formatted display values must come from expression builtins or render templates.
- `descriptions-component`: `DescField` no longer exposes a `format` argument; formatted display values must come from expression builtins before rendering.

## Impact

- `packages/lang-core`: builtin registry, evaluator/runtime context, prompt docs, parser-facing builtin signatures, builtin tests
- `packages/react-lang`: `Renderer` props/context for locale defaults passed into runtime evaluation
- `packages/react-ui-dsl`: `Table` and `Descriptions` schema/view removal of `format`, prompt examples, stories, and tests
- Generated system prompts and examples that currently recommend `format: "date"` or `DescField(..., "dateTime")`
- Breaking DSL surface for existing `Table`/`Descriptions` formatting usage, with migration aimed at AI-generated output rather than hand-authored compatibility
