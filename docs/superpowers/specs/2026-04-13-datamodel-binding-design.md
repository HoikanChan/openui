# DataModel Binding Design

**Date:** 2026-04-13  
**Status:** Proposed

---

## Overview

Add a host-provided `dataModel` binding to OpenUI Lang so generated UI can reference application data through a fixed `data.*` root.

This fills the current gap between:

| Source | Syntax | Owner | Purpose |
|---|---|---|---|
| Local reactive state | `$var` | LLM/runtime | UI state, filters, form bindings |
| Tool-backed async data | `Query("tool")` | LLM/runtime | Fetching live backend data |
| Host-provided static data | `data.*` | Host app | Rendering already-available business data |

The core rule is simple: the host owns the data payload, and the LLM only decides how to present it.

---

## Goals

- Let host apps pass pre-fetched data into `Renderer`.
- Let generated code read that data through normal expression syntax: `data.user.name`, `data.rows[0]`, `data.rows.total`.
- Reuse existing evaluator behavior for `Member`, `Index`, array pluck, conditionals, and `@Each`.
- Keep the feature additive: no behavior change when `dataModel` is omitted.
- Teach the model about the available data shape via prompt generation.

## Non-Goals

- Two-way binding into host data.
- A reactive data store inside `lang-core`.
- Multiple external roots such as `ctx.*` or `model.*`.
- Inline declaration or reassignment of `data` inside openui-lang.
- Framework parity beyond `react-lang` in this iteration.

---

## Public API

### Renderer

```tsx
<Renderer
  response={llmOutput}
  library={library}
  dataModel={{
    sales: [
      { quarter: "Q1", revenue: 100 },
      { quarter: "Q2", revenue: 120 },
    ],
    user: { name: "Alice", role: "admin" },
    totalRevenue: 220,
  }}
/>
```

Add `dataModel?: Record<string, unknown>` to `RendererProps` and thread it into `useOpenUIState`.

### Prompt generation

`PromptSpec` gains an optional `dataModel` description:

```ts
export interface PromptSpec {
  // existing fields
  dataModel?: DataModelSpec;
}

export interface DataModelSpec {
  description?: string;
  fields: Record<
    string,
    {
      type: "array" | "object" | "scalar";
      description?: string;
    }
  >;
}
```

This is prompt metadata only. It describes the available root fields but does not carry runtime values.

---

## Language Semantics

`data` is a reserved external reference root. When enabled, it behaves like a runtime reference, not like a normal statement-local identifier.

### Allowed usage

```txt
Label(data.user.name)
Metric(data.totalRevenue, "Total Revenue")
Label(data.sales[0].quarter)
Each(data.sales, item, Card(item.quarter, item.revenue))
BarChart(data.sales.revenue, data.sales.quarter, "Revenue by Quarter")
```

### Semantics

- `data.<field>` reads a top-level field from the host object.
- `data.rows.field` uses the existing array-pluck evaluator behavior when `rows` is an array.
- `data.rows[0]` uses the existing `Index` evaluator behavior.
- `data` is read-only.
- If `dataModel` is not provided, `data` is treated as an unresolved reference exactly like today.
- `data = ...` is out of scope and should not be supported as a valid redeclaration pattern.

### Interaction with existing features

- `$state` continues to represent mutable runtime state only.
- `Query()` and `Mutation()` continue to use `RuntimeRef` as they do today.
- `data` can be combined freely with expressions, conditionals, builtins, and local state:

```txt
$selected = "Q1"

root = Root(
  Label(data.user.name),
  Buttons([
    Button("Q1", Action([@Set($selected, "Q1")])),
    Button("Q2", Action([@Set($selected, "Q2")])),
  ]),
  Label($selected == "Q1" ? data.sales[0].revenue : data.sales[1].revenue)
)
```

---

## Runtime Design

### 1. Parser support for external refs

The parser already distinguishes:

- normal refs resolved from the symbol table
- unresolved refs lowered to `Ph`
- query/mutation declarations lowered to `RuntimeRef`

Extend that model with parser options:

```ts
export interface ParserOptions {
  externalRefs?: string[];
}
```

`createParser()` and `createStreamingParser()` accept `options?: ParserOptions`. The only external ref in scope for this feature is `"data"`.

### 2. Materialization

`MaterializeCtx` gains:

```ts
externalRefs?: Set<string>;
```

In `resolveRef()` inside [materialize.ts](/C:/workspace/openui/packages/lang-core/src/parser/materialize.ts:39):

- If the name exists in `syms`, preserve current behavior.
- If the name does not exist in `syms` but is present in `externalRefs`, emit `RuntimeRef`.
- Otherwise preserve the current unresolved-path behavior (`Ph` in expression mode, `null` in value mode, plus `meta.unresolved`).

New AST shape in [ast.ts](/C:/workspace/openui/packages/lang-core/src/parser/ast.ts:41):

```ts
| { k: "RuntimeRef"; n: string; refType: "query" | "mutation" | "data" }
```

No new AST node kind is needed.

### 3. Evaluation

The evaluator already resolves `RuntimeRef` through `context.resolveRef(node.n)`. That means no evaluator changes are required.

In [useOpenUIState.ts](/C:/workspace/openui/packages/react-lang/src/hooks/useOpenUIState.ts:134), extend `resolveRef`:

```ts
resolveRef: (name: string) => {
  if (name === "data" && dataModel) return dataModel;
  const mutResult = queryManager.getMutationResult(name);
  if (mutResult) return mutResult;
  return queryManager.getResult(name);
}
```

This keeps runtime resolution centralized in the existing evaluation context.

### 4. Parser lifecycle in `react-lang`

`useOpenUIState` creates its streaming parser once per library today. That memoization must also account for whether the `data` external ref is enabled:

- when `dataModel` is present: create parser with `externalRefs: ["data"]`
- when `dataModel` is absent: create parser with `externalRefs: []` or `undefined`

The parser instance should be recreated when the feature toggles on or off so parse-time lowering matches runtime capabilities.

The evaluation context should include `dataModel` in its memo dependencies. If the host passes a new object identity, re-evaluation is correct and expected.

---

## Prompt Design

When `PromptSpec.dataModel` is present, `generatePrompt()` appends a `## Data Model` section after the syntax/component guidance:

```txt
## Data Model

The following host data is available via `data.<field>`:

- `data.sales` (array): List of quarterly sales records.
- `data.user` (object): Current user.
- `data.totalRevenue` (scalar): Total revenue number.

Use `data.<field>` to read host data.
Use `Each(...)` to iterate arrays.
Array pluck works on arrays: `data.sales.revenue`.
```

Requirements:

- Only include this section when `dataModel` metadata is provided.
- Keep the root identifier fixed as `data`.
- Explain array pluck explicitly, because it is not obvious from generic dot-access syntax.

---

## Data Flow

```txt
Host app
  -> Renderer(dataModel={...})
  -> useOpenUIState(...)
  -> createStreamingParser(schema, root, { externalRefs: ["data"] })
  -> parser lowers `data` to RuntimeRef("data", "data")
  -> evaluator calls resolveRef("data")
  -> evaluationContext returns the host dataModel object
  -> existing Member/Index evaluation resolves concrete values
```

---

## Error Behavior

- Missing `dataModel` + use of `data.*` should behave like any other unresolved reference.
- Providing `dataModel` does not suppress normal validation errors for unknown components, missing required props, or malformed expressions.
- No special parser error is required for `data` access itself.
- If the host provides a shape that does not match what the LLM expects, runtime behavior follows existing evaluator semantics for missing object fields and out-of-range indexes.

---

## Affected Files

| File | Change |
|---|---|
| `packages/lang-core/src/parser/ast.ts` | Add `"data"` to `RuntimeRef.refType` |
| `packages/lang-core/src/parser/materialize.ts` | Add `externalRefs` support and lower `data` to `RuntimeRef` |
| `packages/lang-core/src/parser/parser.ts` | Add `ParserOptions`; thread options into parser/materializer creation |
| `packages/lang-core/src/parser/prompt.ts` | Add `DataModelSpec`; render `## Data Model` section |
| `packages/lang-core/src/parser/index.ts` | Re-export new prompt/parser types |
| `packages/lang-core/src/index.ts` | Re-export `DataModelSpec` and `ParserOptions` |
| `packages/react-lang/src/hooks/useOpenUIState.ts` | Accept `dataModel`; configure parser and `resolveRef` |
| `packages/react-lang/src/Renderer.tsx` | Add `dataModel` prop and pass through |
| `packages/react-lang/src/index.ts` | Re-export prompt types if needed for adapter consumers |

---

## Testing

### `lang-core`

- parser without `externalRefs` still lowers unknown refs to unresolved placeholders
- parser with `externalRefs: ["data"]` lowers `data` to `RuntimeRef`
- `data.user.name` materializes as `Member(Member(RuntimeRef("data"), "user"), "name")`
- `data.rows[0]` and `data.rows.field` preserve existing `Index` and `Member` behavior
- unresolved refs other than `data` still populate `meta.unresolved`
- prompt generation includes `## Data Model` only when configured

### `react-lang`

- `Renderer` accepts `dataModel` and passes it into evaluation
- changing `dataModel` identity re-evaluates rendered props
- omitting `dataModel` keeps current behavior unchanged
- mixed expressions such as `$selected == "Q1" ? data.sales[0].revenue : 0` evaluate correctly

---

## Rollout Notes

- Scope this change to `react-lang` first.
- Keep the external ref mechanism generic (`externalRefs`) so future host-provided roots can reuse the same parser path if that ever becomes necessary.
- Do not expose multi-root support yet; the public contract for this feature is a single fixed `data` root.
