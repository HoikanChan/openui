# DataModel Binding Design

**Date:** 2026-04-13  
**Status:** Draft

---

## Overview

Add a `dataModel` data binding layer to openui-lang. Host applications pass a static data object; the LLM references it via `data.xxx` path expressions in its UI output. This separates "what data to render" (host responsibility) from "how to render it" (LLM responsibility).

---

## Motivation

Current data layers in openui-lang:

| Layer | Syntax | Who provides | Purpose |
|---|---|---|---|
| Local reactive state | `$var` | LLM declares | Form inputs, toggles, counters |
| Async tool calls | `Query("tool")` | LLM declares | Fetched backend data |
| **DataModel (new)** | `data.xxx` | **Host App** | Fixed business data to render |

The missing layer: host app holds data (e.g. from its own API call), wants the LLM to generate UI that renders it, without the LLM needing to embed the data as literals or call a tool.

This mirrors the data/structure separation used by A2UI (Google) and json-render — both use a static data context that the UI spec references by path — adapted to openui-lang's code DSL syntax (dot notation instead of JSON Pointer).

---

## API

### Renderer prop

```tsx
<Renderer
  response={llmOutput}
  library={library}
  dataModel={{
    sales: [{ quarter: "Q1", revenue: 100 }, { quarter: "Q2", revenue: 120 }],
    user: { name: "Alice", role: "admin" },
    totalRevenue: 220,
  }}
/>
```

`dataModel` is `Record<string, unknown>`. It is:
- **Read-only** from the LLM's perspective — no mutation mechanism
- **Not reactive** — changing `dataModel` between renders re-evaluates props (same as `response` changing)
- **Flat top-level keys** — `data.sales`, `data.user`, etc.; deep nesting is accessed via path expressions

### Framework packages

All framework renderers (`react-lang`, `vue-lang`, `svelte-lang`) expose `dataModel` as an equivalent prop/option.

---

## LLM Syntax

The root binding name is the fixed identifier `data`. All dataModel fields are accessed as `data.<key>` paths.

### Object access
```
Label(data.user.name)
Badge(data.user.role)
```

### Array — pass whole array
```
BarChart(data.sales.revenue, data.sales.quarter, "Revenue by Quarter")
```
`data.sales.revenue` uses the existing **array pluck** behavior: when `.field` is accessed on an array, it maps each element to extract that field, returning `[100, 120]`.

### Array — iterate with Each
```
Each(data.sales, item, Card(item.quarter, item.revenue))
```

### Array — single element
```
Label(data.sales[0].quarter)
```
Use bracket `[n]` for index access. `data.sales.0` (dot with number) triggers array pluck, not index access.

### Scalar
```
Metric(data.totalRevenue, "Total Revenue")
```

### Mixed with local state and queries
```
$selected = "Q1"

root = Root(
  Label(data.user.name),
  BarChart(data.sales.revenue, data.sales.quarter),
  Buttons([
    Button("Q1", Action([Set($selected, "Q1")])),
    Button("Q2", Action([Set($selected, "Q2")])),
  ]),
  Label($selected == "Q1" ? data.sales[0].revenue : data.sales[1].revenue)
)
```

---

## Implementation

### 1. `lang-core` — Parser: `externalRefs` option

**File:** `packages/lang-core/src/parser/parser.ts` (and `materialize.ts`)

The streaming parser receives an `externalRefs?: string[]` option. Internally, `MaterializeCtx` gains `externalRefs?: Set<string>`.

In `materialize.ts`, the `resolveRef` function currently turns unknown names into `Ph` (null placeholder). With `externalRefs`, it instead emits a `RuntimeRef` with a new `refType: "data"`:

```typescript
// Before (existing):
if (!ctx.syms.has(name)) {
  ctx.unres.push(name);
  return mode === "expr" ? { k: "Ph", n: name } : null;
}

// After:
if (!ctx.syms.has(name)) {
  if (ctx.externalRefs?.has(name)) {
    // Preserve for runtime resolution — do NOT add to unres
    const rtNode = { k: "RuntimeRef" as const, n: name, refType: "data" as const };
    return mode === "expr" ? rtNode : /* value mode: */ null;
  }
  ctx.unres.push(name);
  return mode === "expr" ? { k: "Ph", n: name } : null;
}
```

In value mode (standalone statement `x = data`), `null` is returned — this is an unusual usage and acceptable. In expr mode (inline in component args, which is the normal case), the `RuntimeRef` is preserved.

### 2. `lang-core` — AST: extend `RuntimeRef` refType

**File:** `packages/lang-core/src/parser/ast.ts`

```typescript
// Before:
| { k: "RuntimeRef"; n: string; refType: "query" | "mutation" }

// After:
| { k: "RuntimeRef"; n: string; refType: "query" | "mutation" | "data" }
```

The evaluator already routes all `RuntimeRef` through `context.resolveRef(node.n)` — no evaluator changes needed.

### 3. `lang-core` — Parser API: pass `externalRefs` through

**File:** `packages/lang-core/src/parser/parser.ts`

`createStreamingParser` and `createParser` accept a new optional third argument:

```typescript
export interface ParserOptions {
  externalRefs?: string[];
}

export function createStreamingParser(
  schema: LibraryJSONSchema,
  root: string,
  options?: ParserOptions,
): StreamParser
```

When `dataModel` is provided by the host, `["data"]` is passed here. When not provided, `externalRefs` is empty and behavior is unchanged.

### 4. `react-lang` — `useOpenUIState`: wire up dataModel

**File:** `packages/react-lang/src/hooks/useOpenUIState.ts`

- Add `dataModel?: Record<string, unknown>` to `UseOpenUIStateOptions`
- Pass `externalRefs: dataModel ? ["data"] : []` to `createStreamingParser`
- Extend `resolveRef` in `evaluationContext`:

```typescript
resolveRef: (name: string) => {
  if (name === "data" && dataModel) return dataModel;
  const mutResult = queryManager.getMutationResult(name);
  if (mutResult) return mutResult;
  return queryManager.getResult(name);
},
```

The `dataModel` reference must be stable in the `useMemo` dependency array (same as `store`, `queryManager`). If `dataModel` changes identity between renders, the evaluation context rebuilds — which is correct behavior (new data → re-evaluate props).

The streaming parser instance is memoized on `[library, dataModel != null]`. If the host starts passing `dataModel` mid-session (or stops), the parser is recreated to include/exclude the `"data"` external ref.

### 5. `react-lang` — `Renderer`: expose prop

**File:** `packages/react-lang/src/Renderer.tsx`

Add `dataModel?: Record<string, unknown>` to `RendererProps` and pass through to `useOpenUIState`.

### 6. Prompt generation: dataModel schema description

**File:** `packages/lang-core/src/parser/prompt.ts`

`PromptSpec` gains an optional `dataModel` field:

```typescript
export interface PromptSpec {
  // ... existing fields
  dataModel?: DataModelSpec;
}

export interface DataModelSpec {
  /** Description shown to LLM: what this data represents */
  description?: string;
  /** Field descriptions — tells LLM whether each field is array, object, or scalar */
  fields: Record<string, { type: "array" | "object" | "scalar"; description?: string }>;
}
```

`generatePrompt` includes a `## Data Model` section in the system prompt when `dataModel` is present:

```
## Data Model

The following data is available via `data.<field>`:

- `data.sales` (array): List of quarterly sales records. Each item has `quarter` (string) and `revenue` (number).
- `data.user` (object): Current user. Has `name` (string) and `role` (string).
- `data.totalRevenue` (scalar): Total revenue number.

Use `data.<field>` to reference this data. Use Each(...) to iterate arrays.
Array pluck: `data.sales.revenue` extracts the revenue field from every item.
```

---

## Data Flow

```
Host App
  │
  ├─ dataModel: { sales: [...], user: {...} }    ← provided at render time
  │
  └─ Renderer
       │
       ├─ createStreamingParser(..., { externalRefs: ["data"] })
       │     └─ parser sees `data` in LLM output → emits RuntimeRef("data", "data")
       │         (not Ph/null)
       │
       ├─ evaluationContext.resolveRef("data") → returns dataModel object
       │
       └─ Member evaluation: data.sales → dataModel.sales (array)
                             data.sales.revenue → [100, 120, ...]
                             data.sales[0] → { quarter: "Q1", revenue: 100 }
```

---

## What Does NOT Change

- The evaluator (`evaluator.ts`, `evaluate-tree.ts`, `evaluate-prop.ts`) — no changes
- The store (`store.ts`) — dataModel is not stored reactively
- `$state` reactive bindings — unaffected
- `Query()` / `Mutation()` tool calls — unaffected
- Array pluck, `Index`, `Member` evaluation — already correct

---

## Non-Goals

- **Two-way binding**: `dataModel` is read-only. Mutations go through `Mutation()` + `onAction`.
- **Reactive updates**: dataModel is not a reactive store. If it changes, the host re-renders with a new prop (same as React).
- **Dynamic keys**: the `data` root name is fixed. Multi-namespace (`model.xxx`, `ctx.xxx`) is not in scope.
- **Nested declaration**: LLM cannot redeclare `data = ...` in openui-lang (it would conflict with the external ref).

---

## Affected Files Summary

| File | Change |
|---|---|
| `lang-core/src/parser/ast.ts` | Add `"data"` to `RuntimeRef.refType` union |
| `lang-core/src/parser/materialize.ts` | Add `externalRefs` to `MaterializeCtx`; emit `RuntimeRef` instead of `Ph` |
| `lang-core/src/parser/parser.ts` | Add `ParserOptions.externalRefs`; pass to materialize context |
| `lang-core/src/parser/prompt.ts` | Add `DataModelSpec` to `PromptSpec`; generate `## Data Model` section |
| `lang-core/src/index.ts` | Export `DataModelSpec`, `ParserOptions` |
| `react-lang/src/hooks/useOpenUIState.ts` | Accept `dataModel`; wire parser option + resolveRef |
| `react-lang/src/Renderer.tsx` | Add `dataModel` to `RendererProps` |
| `react-lang/src/index.ts` | Re-export `DataModelSpec` |
| `vue-lang` / `svelte-lang` | Mirror the `dataModel` prop addition |
