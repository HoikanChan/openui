## 1. lang-core: Register @Render as a lazy builtin

- [ ] 1.1 Add `"Render"` to `LAZY_BUILTINS` set in `packages/lang-core/src/parser/builtins.ts`
- [ ] 1.2 Add `Render` entry to `LAZY_BUILTIN_DEFS` with signature `@Render("v", expr)` / `@Render("v", "row", expr)` and description; mark it as a template builtin so it is included in prompts unconditionally

## 2. lang-core: Materializer support for @Render

- [ ] 2.1 In `packages/lang-core/src/parser/materialize.ts`, add a `Render` branch in `materializeLazyBuiltin` that scopes all variable-declaration args (all but the last) into `scopedRefs` before materializing the body
- [ ] 2.2 Write unit tests in `packages/lang-core/src/__tests__/` verifying `@Render("v", expr)` and `@Render("v", "row", expr)` materialize without unresolved-reference errors

## 3. lang-core: Evaluator pass-through for @Render

- [ ] 3.1 In `packages/lang-core/src/runtime/evaluator.ts`, add a `Render` branch in `evaluateLazyBuiltin` that returns the ASTNode unchanged instead of falling through to `null`
- [ ] 3.2 Add a unit test verifying a `@Render` ASTNode prop survives `evaluateLazyBuiltin` as the same object

## 4. react-lang: hydrateSlots transform in Renderer

- [ ] 4.1 Implement `hydrateSlots(props, renderNode, evaluationContext)` in `packages/react-lang/src/` — walks props recursively (plain objects, arrays, and ElementNode props; stops only at ActionPlans), replaces `@Render` ASTNodes with `(...args) => ReactNode` closures
- [ ] 4.2 Update `RenderNodeInner` in `Renderer.tsx` to call `hydrateSlots` (wrapped in `useMemo` keyed on `el.props`) and pass the hydrated props to `<Comp>`
- [ ] 4.3 Write unit tests for `hydrateSlots`: single-variable form, two-variable form, static ElementNode passthrough, plain-object recursion (nested `options.cell`), ElementNode-prop recursion (Col inside columns array), ActionPlan skip, idempotency (function input unchanged)

## 5. react-ui-dsl: Table Col cell render function call

- [ ] 5.1 In `packages/react-ui-dsl/src/genui-lib/Table/view/index.tsx`, update `mapColumnsToAntd` render fn: call `options.cell?.(value, record)` when `options.cell` is a function, fall back to `renderNode(options.cell)` when it is an ElementNode, else apply format/raw value
- [ ] 5.2 Add or update TypeScript types for `ColSchema.options.cell` to accept `((value: unknown, record: unknown) => ReactNode) | ElementNode | undefined`

## 6. lang-core: Split template builtins from data builtins in prompt generation

- [ ] 6.1 In `packages/lang-core/src/parser/prompt.ts`, refactor `generatePrompt` to always include template builtins (`@Each`, `@Switch`, and the new `@Render`) and to include data builtins (`@Count`, `@Filter`, `@Sort`, `@Sum`, `@Avg`, `@Min`, `@Max`, `@Round`, `@Abs`, `@Floor`, `@Ceil`) only when `supportsExpressions` is true
- [ ] 6.2 Move the `IMPORTANT @Each rule` guidance and `@Render` rule to the always-included template section
- [ ] 6.3 Add a unit test verifying that `generatePrompt({ toolCalls: false, bindings: false, ... })` produces a prompt containing `@Render` but not `@Count`

## 7. Prompt regeneration and E2E

- [ ] 7.1 Regenerate the LLM system prompt to include the new `@Render` builtin docs (run the prompt regen script from `packages/react-ui-dsl`)
- [ ] 7.2 Update or add an e2e fixture that uses `@Render("v", @Switch(v, cases))` in a Table Col cell and verify the snapshot renders correctly
