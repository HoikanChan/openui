## ADDED Requirements

### Requirement: @Render SHALL be a lazy builtin in lang-core
`@Render` SHALL be registered in `LAZY_BUILTINS` (not `BUILTINS`) so it is never eagerly evaluated. It SHALL also appear in `LAZY_BUILTIN_DEFS` with a prompt-visible signature and description. Binder names SHALL be string literals, consistent with `@Each`'s `"varName"` convention.

#### Scenario: Render is recognized as a lazy builtin
- **WHEN** the parser encounters `@Render("v", expr)` in DSL source
- **THEN** `isBuiltin("Render")` returns `true`
- **AND** `LAZY_BUILTINS.has("Render")` returns `true`

#### Scenario: Render appears in prompt docs
- **WHEN** the system prompt is generated from `LAZY_BUILTIN_DEFS`
- **THEN** `@Render` appears with its signature and description alongside `@Each`

### Requirement: materializeLazyBuiltin SHALL scope @Render variable declarations
During materialization, `@Render("v", expr)` SHALL extract the string value from each binder arg (all but the last), add those names to `scopedRefs`, then recurse into the body. This prevents the materializer from reporting bare references used inside the body (e.g., `v`) as unresolved.

#### Scenario: Single-variable form materializes without errors
- **WHEN** `@Render("v", @Switch(v, cases))` is materialized
- **THEN** `v` is treated as in-scope inside the body
- **AND** no "unresolved reference" error is produced for `v`

#### Scenario: Two-variable form materializes without errors
- **WHEN** `@Render("v", "row", @Switch(v, cases))` is materialized
- **THEN** both `v` and `row` are treated as in-scope inside the body
- **AND** the body is materialized with both variables available

#### Scenario: Body is materialized, not passed raw
- **WHEN** `@Render("v", Tag("Status"))` is materialized
- **THEN** the body `Tag("Status")` is processed through `materializeValue`
- **AND** the resulting ASTNode contains the materialized body as its last arg

### Requirement: evaluateLazyBuiltin SHALL pass @Render nodes through unchanged
When the runtime evaluator encounters `@Render` during prop evaluation, it SHALL return the ASTNode as-is rather than evaluating it to `null` (the default for unknown lazy builtins).

#### Scenario: Render node survives prop evaluation
- **WHEN** a prop value is a `@Render` ASTNode
- **THEN** `evaluateLazyBuiltin` returns the same ASTNode object
- **AND** the node is not `null`, not `undefined`, and not partially evaluated

#### Scenario: @Each is unaffected
- **WHEN** a prop value is a `@Each` ASTNode
- **THEN** `evaluateLazyBuiltin` continues to evaluate `@Each` as before with no behavioral change

#### Scenario: @Render outside a prop context evaluates to null at render time
- **WHEN** `@Render("v", expr)` appears in a non-prop position (e.g., as a top-level statement value)
- **THEN** `evaluateLazyBuiltin` still returns the ASTNode as-is
- **AND** `renderNode` produces null for the unrenderable ASTNode (same as any unrenderable value)

### Requirement: react-lang Renderer SHALL lift @Render ASTNodes to JS render functions via hydrateSlots
After prop evaluation and before passing props to the component, the Renderer SHALL walk props recursively and replace any `@Render` ASTNode with a plain JS function `(...args) => ReactNode`. The function SHALL capture `renderNode` and `evaluationContext` from context and call `evaluate(body, { resolveRef })` where `resolveRef` injects the declared variables from args.

The walker SHALL recurse into:
- Plain objects (e.g., `props.options`)
- ElementNode props (e.g., Col ElementNodes inside `props.columns`) — required because sub-component ElementNodes like Col are never rendered independently by RenderNode; Table extracts their props directly
- Arrays

The walker SHALL stop at ActionPlans (objects with a `steps` array).

#### Scenario: Single-variable @Render becomes a one-arg function
- **WHEN** `options.cell` is `@Render("v", expr)` and the component receives props
- **THEN** `props.options.cell` is a JS function of arity 1
- **AND** calling `props.options.cell(someValue)` evaluates `expr` with `v = someValue` in scope
- **AND** the return value is a ReactNode

#### Scenario: Two-variable @Render becomes a two-arg function
- **WHEN** `options.cell` is `@Render("v", "row", expr)` and the component receives props
- **THEN** `props.options.cell` is a JS function of arity 2
- **AND** calling `props.options.cell(cellValue, rowRecord)` evaluates `expr` with `v = cellValue` and `row = rowRecord` in scope

#### Scenario: hydrateSlots recurses into plain objects
- **WHEN** a `@Render` node is nested inside a plain object prop (e.g., `props.options.cell`)
- **THEN** `hydrateSlots` finds and lifts it even at depth > 1

#### Scenario: hydrateSlots recurses into ElementNode props
- **WHEN** `props.columns` contains Col ElementNodes and a Col has `options.cell = @Render("v", expr)`
- **THEN** `hydrateSlots` walks into the Col ElementNode's props and lifts the `@Render` node
- **AND** the Col ElementNode returned has `props.options.cell` as a JS function

#### Scenario: hydrateSlots is idempotent — already-lifted slots are unchanged
- **WHEN** `hydrateSlots` is called on props where a slot is already a JS function (double-hydration)
- **THEN** the function is returned unchanged
- **AND** no new function wrapper is created

#### Scenario: hydrateSlots skips ActionPlan objects
- **WHEN** a prop value is an ActionPlan (has a `steps` array)
- **THEN** `hydrateSlots` returns it unchanged

#### Scenario: static ElementNode cell value passes through unchanged
- **WHEN** `options.cell` is a static ElementNode (e.g., `cell: Tag("Badge")`)
- **THEN** `hydrateSlots` returns it unchanged
- **AND** no render function is created for it

#### Scenario: render function instances are memoized per props identity
- **WHEN** `el.props` reference is stable between renders
- **THEN** `hydrateSlots` does not produce new function instances
- **AND** component re-renders are not triggered by function identity changes

### Requirement: @Render SHALL be documented in the LLM system prompt unconditionally
The prompt documentation for `@Render` SHALL appear regardless of whether `toolCalls` or `bindings` flags are enabled. `@Render` is a template primitive with no dependency on Query or reactive state; omitting it from static-UI prompts would silently break the feature for bare-library configurations.

`generatePrompt` SHALL separate builtins into two groups at the call site:
- **Template builtins** (`@Each`, `@Render`, `@Switch`): always included
- **Data builtins** (all others): only when `supportsExpressions`

#### Scenario: Prompt includes @Render for static UI library (no toolCalls, no bindings)
- **WHEN** `generatePrompt` is called with `toolCalls: false` and `bindings: false`
- **THEN** `@Render` documentation appears in the generated prompt
- **AND** data builtins (`@Count`, `@Filter`, etc.) do NOT appear

#### Scenario: Prompt includes @Render when toolCalls is enabled
- **WHEN** `generatePrompt` is called with `toolCalls: true`
- **THEN** `@Render` documentation appears alongside data builtins

#### Scenario: Prompt includes @Render signature with string-literal binders
- **WHEN** the system prompt is generated
- **THEN** `@Render("v", expr)` and `@Render("v", "row", expr)` forms appear in the builtins section
- **AND** the binder arguments are documented as string literals, matching the @Each `"varName"` convention

#### Scenario: Prompt recommends @Render as a prop value pattern
- **WHEN** the prompt describes @Render
- **THEN** it recommends @Render for use as a prop value (e.g., `options.cell`)
- **AND** it notes that @Render outside a prop context renders as null
