## Why

`packages/react-ui-dsl` still exposes many component schemas through a nested `properties` object and mixes non-semantic fields such as `style` and `actions` into the LLM-facing input surface. That shape makes `dslLibrary.prompt()` and `toJSONSchema()` teach the model to generate unnecessary nesting and host-specific control fields instead of clean component arguments.

## What Changes

- Flatten component business fields from `properties.*` onto the top level of each affected `react-ui-dsl` component schema.
- Remove `style` and `actions` from the LLM-facing schema surface across the affected `react-ui-dsl` components.
- Update component render entry points to consume top-level props directly instead of `props.properties.*`.
- Update component descriptions, stories, fixtures, and tests so prompt output, examples, parsing, and rendering all use the flattened schema.
- **BREAKING**: existing DSL authored with `properties`, `style`, or top-level `actions` for the affected components will no longer be accepted.

## Capabilities

### New Capabilities
- `llm-friendly-dsl-component-props`: React UI DSL components expose flattened, semantic top-level props so generated DSL avoids wrapper objects and non-semantic host-control fields.

### Modified Capabilities

## Impact

- Affected code: `packages/react-ui-dsl/src/genui-lib/*`, `packages/react-ui-dsl/src/stories/*`, `packages/react-ui-dsl/src/__tests__/e2e/*`, and any prompt metadata derived from `dslLibrary`.
- APIs: the public DSL schema for affected `@openuidev/react-ui-dsl` components changes to a flattened top-level prop model with no `style` or `actions`.
- Systems: impacts prompt generation, JSON Schema export, parser validation, story examples, and e2e snapshot baselines that currently use the nested shape.
