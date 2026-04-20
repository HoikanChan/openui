## Context

`packages/react-ui-dsl` currently mixes two incompatible schema styles. `Table` and some newer work have already moved toward openui-lang-style component signatures, while many other components still place business fields under `properties` and expose `style` or `actions` directly in the LLM-facing schema. Because `react-lang` derives both prompt signatures and JSON Schema directly from the component Zod objects, this local modeling choice leaks straight into `dslLibrary.prompt()` and `dslLibrary.toJSONSchema()`.

The result is predictable: the model is taught to emit wrapper objects and host-control fields that do not represent the semantic intent of the UI. This change is cross-cutting because it touches component definitions, render adapters, stories, fixtures, and any validation or prompt output that depends on the exported library schema.

## Goals / Non-Goals

**Goals:**
- Make affected `react-ui-dsl` component schemas flat at the top level for semantic, LLM-authored props.
- Remove `style` and `actions` from the LLM-facing schema surface for affected components.
- Keep prompt generation, JSON Schema export, stories, fixtures, and parser behavior aligned on the same flattened shape.
- Make the breaking change explicit by rejecting legacy nested inputs instead of silently normalizing them.

**Non-Goals:**
- Redesign component semantics beyond flattening fields and removing `style` / `actions`.
- Add a compatibility adapter for legacy `properties` payloads.
- Change the underlying rendering libraries (`antd`, `echarts`) or the `react-lang` library contract.
- Replace intentional nested semantic objects such as structured component-specific objects that are still meaningful API surface.

## Decisions

### Flatten schemas at the component source

The change will be implemented by editing each component's `schema.ts` and corresponding render entry point rather than by transforming prompt output or JSON Schema after the fact. This keeps the authored schema, runtime props, prompt signatures, and parser validation in one consistent source of truth.

Alternative considered: keep existing component schemas and add an export-time or prompt-time projection layer that hides `properties`, `style`, and `actions`.

Rejected because it would leave runtime props and public prompt/schema representations out of sync, making the DSL harder to reason about and easier to regress.

### Remove non-semantic fields from the DSL contract

`style` and top-level `actions` will be removed from affected component schemas instead of being preserved as optional escape hatches. The DSL surface should describe what the model is expected to generate, not every implementation detail a host renderer could theoretically accept.

Alternative considered: flatten `properties` but keep `style` and `actions` available.

Rejected because the model would still be encouraged to generate host-specific control data that is not part of the semantic UI request.

### Treat this as a deliberate breaking change with no compatibility shim

Legacy definitions that rely on `properties`, `style`, or removed `actions` will stop validating for the affected components. The parser and schema should fail predictably rather than supporting mixed old and new shapes.

Alternative considered: accept both old and new shapes temporarily and normalize at runtime.

Rejected because dual-shape support keeps prompt guidance ambiguous and prolongs the very nesting problem this change is meant to remove.

### Update all library-facing examples and tests in the same change

Stories, e2e fixtures, and any prompt-facing descriptions must be updated together with schema changes. This avoids a partial migration where the parser accepts the new shape but examples and snapshots still reinforce the old one.

Alternative considered: change schemas first and defer examples or snapshots to follow-up work.

Rejected because prompt quality and regression coverage depend on the examples matching the exported schema immediately.

## Risks / Trade-offs

- [Breaking existing DSL authored against the nested shape] -> Make the breaking change explicit in proposal/specs/tasks and update examples/tests in the same change so the new preferred form is unambiguous.
- [Cross-cutting edits may miss one or two components or stories] -> Drive the implementation from a complete component inventory and add prompt/schema assertions to prevent `properties`, `style`, or `actions` from reappearing.
- [Chart components currently use a loose options object, so flattening may produce broad top-level schemas] -> Keep the existing loose option typing where necessary, but still move those fields to the top level so prompt output no longer wraps them in `properties`.
- [Removing `style` eliminates an escape hatch for consumer-controlled visual tweaks] -> Accept that trade-off for the DSL package; the LLM-facing schema should remain semantic-first even if it reduces host customization through generated DSL.

## Migration Plan

1. Update affected component schemas to flatten semantic fields and remove `style` / `actions`.
2. Update component render entry points and view adapters to read top-level props directly.
3. Update prompt-facing descriptions, stories, fixtures, and snapshot baselines to the new shape.
4. Add or update tests that assert prompt output and JSON Schema no longer expose `properties`, `style`, or `actions` for the affected components.
5. Land the change as a breaking schema update with no backward-compatibility layer.

Rollback strategy: revert the change set and restore the prior schema files, stories, and snapshots together. Because no compatibility layer is introduced, rollback is straightforward but must be done as a full revert rather than as selective file restoration.

## Open Questions

None. The user explicitly chose a flattened top-level schema and removal of `style` / `actions` with no compatibility period.
