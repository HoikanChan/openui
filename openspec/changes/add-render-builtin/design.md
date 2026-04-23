## Context

OpenUI Lang has `@Each(arr, item, template)` as its only lambda-like primitive — a lazy builtin that evaluates a template per array item. Components like Table need a parallel mechanism: "given a single value injected at render time, produce a component." All major frontend frameworks solve this (React render props, Vue scoped slots, Solid function children) but OpenUI Lang has no equivalent.

The current workaround is `options.cell: ElementNode` — a static component applied identically to every row, ignoring the actual cell value. This means custom rendering (status badges, conditional tags, computed links) is impossible with `dataModel`-backed rows.

## Goals / Non-Goals

**Goals:**
- `@Render("v", expr)` lazy builtin: a deferred scoped template the consuming component evaluates at render time with injected variable bindings
- Multi-variable form: `@Render("v", "row", expr)` for access to both cell value and full row record
- Slot ASTNodes lifted to plain JS render functions by react-lang before props reach component code — component authors call `props.cell?.(value, record)`, no hooks needed
- Static ElementNode fallback preserved — `cell: Tag("Fixed")` continues to work
- Table `Col` `options.cell` as the first consumer; pattern is reusable by List, Select, and any future component

**Non-Goals:**
- `@Map` data transformation builtin (deferred to a separate change)
- Multi-argument `@Render` beyond two variables (value + row) in this iteration
- Changes to `@Each` semantics
- Slot support in non-React adapters

## Decisions

### `@Render` joins `LAZY_BUILTINS`, not `BUILTINS`

`@Render` must NOT be eagerly evaluated — its body contains references to variables that only exist at component render time (e.g., `"v"`). Adding it to `LAZY_BUILTINS` ensures the materializer and evaluator treat it as a deferred node, exactly like `@Each`.

**Variable binder convention — string literals, not bare identifiers.** `@Render` follows the same convention as `@Each`: binder names are string literals (`"v"`, `"row"`), not bare identifier references. This is consistent with the DSL's existing lazy-builtin syntax (`@Each(arr, "t", template)`) and prevents the parser from interpreting the binder as a reference to an existing variable. All examples use `@Render("v", expr)` and `@Render("v", "row", expr)`.

Alternatives considered: a new ASTNode kind (`k: "RenderFn"`) — rejected because LAZY_BUILTINS already provides the correct pass-through semantics with minimal new infrastructure.

### `materializeLazyBuiltin` scopes var declarations

During materialization, `@Render("v", @Switch(v, cases))` must not report `v` as unresolved. The materializer reads the string value from each binder arg (all but the last), adds those names to `scopedRefs`, then recurses into the body. This mirrors exactly how `@Each` scopes its loop variable — the string literal `"v"` declares the name, the bare reference `v` inside the body resolves against it.

### `evaluateLazyBuiltin` returns the Slot node as-is

When the runtime evaluator encounters `@Render` during prop evaluation, it must NOT evaluate it to `null` (the current fallback for unhandled lazy builtins). Instead it returns the ASTNode unchanged so the react-lang layer can detect and lift it.

### `hydrateSlots` in Renderer.tsx lifts `@Render` to render functions

After prop evaluation (evaluate-tree), before `<Comp props={...}>`, Renderer walks props recursively and replaces any `@Render` ASTNode with a plain JS `(...args) => ReactNode` closure. The closure captures `renderNode` and `evaluationContext` and calls `evaluate(body, { resolveRef: name => scope[name] ?? ctx.resolveRef(name) })`.

This keeps all DSL-awareness in react-lang. Component code never sees ASTNodes — it receives standard React render functions.

Alternative considered: `useRenderSlot()` hook that component authors call explicitly — rejected because it pushes DSL knowledge into every component, requiring imports from lang-core and a non-obvious call pattern.

Alternative considered: `renderSlot` added to `ComponentRenderProps` alongside `renderNode` — rejected for same reason; component authors would still need a special call pattern rather than just `cell?.(value, record)`.

### `hydrateSlots` recurses into plain objects AND ElementNode props; stops only at ActionPlans

`options.cell` is nested inside `Col.props.options` (a plain object), and Col itself arrives at the Table component as an ElementNode inside `props.columns`. The walker must recurse into:
- **Plain objects** — handles `props.options.cell` and similar nested structures
- **ElementNode props** — required for the Table → Col case: Col is never rendered by `RenderNode` (its component is `() => null`); instead, Table extracts `col.props` and passes them directly to `mapColumnsToAntd`. If the walker stops at ElementNodes, `@Render` inside `Col.props.options.cell` is never lifted.
- **Arrays** — handles `props.columns` (array of Col ElementNodes)

The walker stops at ActionPlans (objects with a `steps` array — handled by `triggerAction`) and non-object values.

**Double-hydration safety**: if a sub-component ElementNode is both walked by an outer `hydrateSlots` call and later rendered by `RenderNode` → `RenderNodeInner` → inner `hydrateSlots`, the second pass sees functions (not `@Render` nodes) and leaves them unchanged. Double-hydration is a no-op.

### Backward compatibility via `typeof cell === "function"` check in Table view

Since `hydrateSlots` now recurses into ElementNode props, when `cell` is a static ElementNode it is walked but left unchanged (no `@Render` ASTNode found inside it). The component's render fn in `mapColumnsToAntd` checks `typeof cell === "function"` first, then `isElementNode(cell)` as fallback. No existing DSL output breaks.

### `@Render` prompt inclusion is unconditional — template builtins split from data builtins

Currently `generatePrompt` gates the entire `builtinFunctionsSection()` on `supportsExpressions` (`toolCalls || bindings`). This means a static-UI library (no tools, no `$variables`) receives no builtin docs at all — including `@Render` and `@Each`. But `@Render` is a template primitive with zero dependency on Query or reactive state; omitting it from static prompts silently breaks the feature for the most common bare-library use case.

The fix: split `builtinFunctionsSection` into two categories at the `generatePrompt` call site:

- **Template builtins** (`@Each`, `@Render`, `@Switch`): always included. These are structural/rendering primitives available in every prompt configuration.
- **Data builtins** (`@Count`, `@Filter`, `@Sort`, `@Sum`, `@Avg`, `@Min`, `@Max`, `@Round`, `@Abs`, `@Floor`, `@Ceil`): included only when `supportsExpressions`. These operate on Query results and reactive state that may not exist.

Implementation: add a `templateOnly: boolean` flag to `LAZY_BUILTIN_DEFS` entries and a `templateOnly: boolean` field to `BuiltinDef`; or simply enumerate the split in `generatePrompt` by extracting the two subsets by name. The simpler approach (enumerate in `generatePrompt`) avoids touching every `BuiltinDef` entry.

The existing `IMPORTANT @Each rule` guidance in the builtin section also moves unconditionally (it's template guidance, not data guidance).

## Risks / Trade-offs

- **New function instances on every render** → `useMemo` in Renderer wrapping `hydrateSlots` call, keyed on `el.props`; acceptable given parse results already recreate props objects
- **`@Render` in non-prop positions** — `@Render` is a valid expression everywhere in the language (no parser-level position restriction exists in v0.5; adding one would introduce a novel "position-restricted expression" concept inconsistent with the spec). When `@Render` appears outside a prop context (e.g., as a top-level statement value), `evaluateLazyBuiltin` returns the node as-is; `renderNode` produces null for unrenderable values, which is existing behavior. The LLM prompt recommends `@Render` as a prop value pattern without forbidding other positions.
- **Variable name collision** with top-level statement names (e.g., `v = Something()`) → the `hydrateSlots` closure uses a local scope that takes priority via `resolveRef` override; no global state is mutated
- **Deep nesting** (slot inside slot) → not a goal for this iteration; hydrateSlots only lifts top-level Slot nodes in each object, nested Render bodies are evaluated lazily at call time

## Open Questions

None — the hydrateSlots traversal strategy is resolved. The walker recurses into ElementNode props and arrays, so `@Render` inside `Col.props.options.cell` is correctly lifted when the outer Table element is hydrated.
