# Separator DSL Component Design

**Date:** 2026-04-25
**Package:** `packages/react-ui-dsl`
**Status:** Approved

## Overview

Add a `Separator` DSL component to `packages/react-ui-dsl/src/genui-lib/` by reusing the existing `Separator` schema and visual implementation from `packages/react-ui`. The DSL package should only define the DSL-facing component contract, export it, and register it in the DSL library so generated UIs can emit visual dividers without introducing a parallel style system.

## Schema

Use the same props already exposed by `packages/react-ui/src/genui-lib/Separator/schema.ts`:

```ts
export const SeparatorSchema = z.object({
  orientation: z.enum(["horizontal", "vertical"]).optional(),
  decorative: z.boolean().optional(),
});
```

`react-ui-dsl` should mirror this shape exactly so the schema surface stays aligned across packages.

## File Structure

```
packages/react-ui-dsl/src/genui-lib/Separator/
  index.tsx        - defineComponent wrapper around @openuidev/react-ui Separator
  schema.ts        - zod schema matching react-ui
  index.test.tsx   - render-level coverage for default and vertical separator output
```

## Architecture

### Component boundary

`react-ui-dsl` should not introduce custom CSS or a separate view layer for this component. The rendered node should directly delegate to `@openuidev/react-ui`'s `Separator`, because that package already owns the Radix primitive, the `openui-separator` class, and the stylesheet registration.

### Library registration

`Separator` needs to be added to `src/genui-lib/dslLibrary.tsx` so:

- `dslLibrary.toSpec()` exposes the signature
- `dslLibrary.toJSONSchema()` includes the new definition
- the runtime renderer can parse and render `Separator(...)`

`src/index.ts` should also export `Separator` so package consumers can reference it consistently with other DSL components.

## Testing

Add targeted tests that prove:

1. `dslLibrary` exposes `Separator` in prompt/spec/schema output
2. Rendering `Separator()` through the DSL library produces the shared `openui-separator` class
3. Passing `orientation: "vertical"` reaches the rendered element via `data-orientation="vertical"`

## Constraints

- Reuse the same prop names and optionality as `packages/react-ui`
- Do not add new styling knobs in the DSL package
- Keep the implementation pattern consistent with simple wrapper components already in `react-ui-dsl`
