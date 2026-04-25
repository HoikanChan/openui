## Why

The DSL `Card` schema conflates two unrelated concerns — visual container styling and flex layout control — resulting in a schema that duplicates VLayout/HLayout capabilities while omitting the `width` prop that the underlying React UI Card actually exposes. This makes the LLM-facing API semantically ambiguous and incomplete.

## What Changes

- **BREAKING** Remove `direction`, `gap`, `align`, `justify`, and `wrap` flex props from `Card` schema
- Add `width` prop (`"standard" | "full"`) to match the React UI Card component
- Change `children` from `z.union([CardHeader.ref, z.any()])` to `z.array(z.any())` with a description that makes accepted children explicit
- Remove the local `Card/flexPropsSchema.ts` duplicate (Card no longer needs it)
- Remove duplicated `gapMap`, `alignMap`, `justifyMap` inline maps from `Card/index.tsx` (already exist in shared `flexPropsSchema.ts`)
- Update Card `description` to reflect the simplified, accurate props

## Capabilities

### New Capabilities

- `dsl-card-schema`: DSL Card component schema — defines the props contract, accepted children, and width behavior for the Card container in the DSL library

### Modified Capabilities

<!-- none — no existing spec covers Card schema -->

## Impact

- `packages/react-ui-dsl/src/genui-lib/Card/schema.ts` — schema rewritten
- `packages/react-ui-dsl/src/genui-lib/Card/index.tsx` — description updated, inline maps removed
- `packages/react-ui-dsl/src/genui-lib/Card/flexPropsSchema.ts` — deleted
- E2E snapshots under `src/__tests__/e2e/snapshots/` may need regeneration if any fixture uses Card flex props
- LLM prompt output changes (Card signature will no longer list flex props)
