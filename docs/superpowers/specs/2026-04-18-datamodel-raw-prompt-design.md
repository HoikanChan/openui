# DataModel Raw Prompt Design

**Date:** 2026-04-18
**Status:** Proposed

---

## Overview

Extend `DataModelSpec` in `lang-core` to support a `raw` mode: the host passes a plain JSON object and `generatePrompt` renders it as a fenced JSON block in the `## Data Model` section. This gives the LLM the actual data shape and example values directly, without requiring a typed field spec.

The existing typed `fields` approach (with `DataModelFieldSpec`) is unused and is removed in this change.

---

## Goals

- Let callers pass raw host data into `generatePrompt` via `DataModelSpec.raw`.
- Render a `## Data Model` section containing the JSON and usage instructions.
- Remove the dead `DataModelFieldSpec` / `fields` API.
- Keep the change additive and backward-compatible for callers that do not use `dataModel` at all.

## Non-Goals

- Type inference from raw JSON (array/object/scalar classification) — deferred.
- Changes outside `lang-core` (demo server wiring is a consumer concern).

---

## API Changes

### `DataModelSpec`

```ts
// Before
export interface DataModelFieldSpec {
  type: "array" | "object" | "scalar";
  description?: string;
}

export interface DataModelSpec {
  description?: string;
  fields: Record<string, DataModelFieldSpec>;
}

// After
export interface DataModelSpec {
  description?: string;
  raw?: Record<string, unknown>;
}
```

`DataModelFieldSpec` is deleted. `fields` is replaced by `raw`.

### `PromptSpec`

No change. `dataModel?: DataModelSpec` stays as-is.

---

## Prompt Rendering

When `PromptSpec.dataModel` is present and `dataModel.raw` is non-empty, `dataModelSection()` renders:

```txt
## Data Model

The following host data is available via `data.<field>`:

```json
{
  "sales": [...],
  "user": {...},
  "totalRevenue": 220
}
```

Use `data.<field>` to read host data.
Use `Each(...)` to iterate arrays.
Array pluck works on arrays: `data.sales.revenue`.
```

When `dataModel.raw` is absent or empty, the section is omitted (same behavior as omitting `dataModel` entirely).

The JSON is rendered with `JSON.stringify(raw, null, 2)` — pretty-printed so the LLM can read the structure easily.

---

## Affected Files

| File | Change |
|---|---|
| `packages/lang-core/src/parser/prompt.ts` | Delete `DataModelFieldSpec`; update `DataModelSpec`; rewrite `dataModelSection()` to render raw JSON block |
| `packages/lang-core/src/parser/index.ts` | Remove `DataModelFieldSpec` re-export |
| `packages/lang-core/src/index.ts` | Remove `DataModelFieldSpec` re-export |
| `packages/react-lang/src/index.ts` | Remove `DataModelFieldSpec` re-export |

---

## Testing

- `generatePrompt` with `dataModel: { raw: { sales: [], user: {} } }` includes `## Data Model` with a JSON block.
- `generatePrompt` with `dataModel: { raw: {} }` omits the section.
- `generatePrompt` with no `dataModel` is unchanged.
- `DataModelFieldSpec` no longer exported from any package entry point.
