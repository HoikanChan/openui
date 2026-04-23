## Why

OpenUI Lang has `@Each(arr, item, template)` for iteration, but no way to express "given a value injected at render time, produce a component" — the deferred lambda pattern that React render props, Vue scoped slots, and Solid function children all rely on. This blocks per-row custom rendering in Table columns, custom item templates in List/Select, and any other component that needs to delegate rendering back to the LLM.

## What Changes

- Add `@Render("v", expr)` as a new lazy builtin — a scoped template that receives named variable bindings from the consuming component at render time. Binder names are string literals (e.g., `"v"`, `"row"`), consistent with `@Each`'s `"varName"` convention.
- `@Render` materializes as an ASTNode (like `@Each`); the react-lang Renderer lifts it to a plain JS render function before props reach the component
- Component authors call `props.cell?.(value, record)` — no new hooks or imports needed
- Table `Col` `options.cell` gains full per-row render expressiveness via `@Render`
- Multi-variable form supported: `@Render("v", "row", expr)` injects both cell value and full row record

## Capabilities

### New Capabilities

- `render-builtin`: `@Render("v", expr)` / `@Render("v", "row", expr)` lazy builtin — scoped template primitive for OpenUI Lang. Covers lang-core materialization, evaluator pass-through, react-lang `hydrateSlots` lifting, and prompt documentation.

### Modified Capabilities

- `llm-friendly-table-dsl`: Table `Col` `options.cell` now accepts `@Render("v", expr)` for per-row custom rendering in addition to the existing static ElementNode form.

## Impact

- `packages/lang-core` — parser/builtins.ts, materialize.ts, evaluator.ts
- `packages/react-lang` — Renderer.tsx (hydrateSlots transform)
- `packages/react-ui-dsl` — Table schema description, view render fn
- LLM system prompt regeneration (new `@Render` builtin appears in prompt)
- No breaking changes — existing `cell: Tag(...)` static form continues to work via `isElementNode` fallback in hydrateSlots
