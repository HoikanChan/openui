## Context

The DSL Card component currently merges `FlexPropsSchema` (direction, gap, align, justify, wrap) directly into its schema, giving it the full layout capabilities of VLayout/HLayout. This was likely done for convenience — a Card often needs internal spacing — but it creates semantic ambiguity for the LLM and breaks parity with the underlying React UI `Card` component which only exposes `variant` and `width`.

Additionally, `Card/flexPropsSchema.ts` is a local duplicate of `genui-lib/flexPropsSchema.ts`, and `Card/index.tsx` re-declares the same value maps (`gapMap`, `alignMap`, `justifyMap`) that already live in the shared schema file.

## Goals / Non-Goals

**Goals:**
- Card schema reflects only visual container props (`variant`, `width`)
- `width` prop is present and matches React UI Card's `"standard" | "full"` values
- `children` is `z.array(z.any())` with a description that names the accepted child types
- Dead code removed: local `Card/flexPropsSchema.ts` and inline value maps in `index.tsx`
- LLM prompt signature for Card no longer suggests it is a layout tool

**Non-Goals:**
- Changing Card's visual appearance or CSS
- Modifying `CardView` (the independent implementation stays as-is)
- Changing `CardHeader` schema
- Adding padding/spacing props to Card (use VLayout/HLayout inside Card for layout)

## Decisions

### Decision: Remove all flex props, not just `direction`

Keeping `gap` alone on Card would be a partial measure — LLMs would still conflate Card with a layout primitive. A clean break is clearer: Card is a visual shell, VLayout/HLayout are layout tools. If content inside a Card needs spacing, the author wraps with VLayout/HLayout.

**Alternative considered:** Keep `gap` as a convenience prop on Card since cards commonly need internal spacing. Rejected because it still blurs the boundary and adds a second place where gap semantics must be maintained.

### Decision: Add `width` as `"standard" | "full"`

The React UI Card already has this prop and defaults to `"standard"` (not full-width). The DSL was silently hardcoding full-width behavior. Exposing `width` restores fidelity with the underlying component.

### Decision: `children: z.array(z.any())` — no named union

There is no mechanism in the current schema/prompt system to auto-enumerate "all registered components." A manual union of all component refs would create import cycles and require constant maintenance. `z.any()` is the established pattern (used by VLayout, HLayout, Tabs, etc.) and the component `description` field is where LLM guidance on accepted children belongs.

### Decision: Delete `Card/flexPropsSchema.ts`

It is a subset of `genui-lib/flexPropsSchema.ts` with no additions. Once Card no longer uses flex props, this file has no purpose.

## Risks / Trade-offs

- **Existing LLM-generated DSL using Card flex props will fail validation** → Mitigation: this is an intentional breaking change; affected E2E snapshots must be regenerated via `pnpm test:e2e:regen`
- **Authors who relied on `<Card gap="m">` for spacing must now use `<VLayout gap="m"><Card>...</Card></VLayout>`** → Mitigation: update Card description to suggest VLayout/HLayout for layout needs

## Open Questions

- Should Card's default `width` in the DSL match React UI's default (`"standard"`)? Assume yes — keep parity.
