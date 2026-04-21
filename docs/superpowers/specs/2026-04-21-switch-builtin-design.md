# Switch Builtin Design

**Date:** 2026-04-21  
**Status:** Proposed

---

## Overview

Add a `@Switch` builtin to `builtins.ts` for mapping enum values to display values. This fills the gap between ternary chains (verbose, error-prone past 2 cases) and the need to map integer or string enum fields from `dataModel` or `Query` results to labels, colors, or components.

---

## Motivation

API data commonly contains integer or string enum fields:

```json
{ "status": 0 }   // 0=Pending, 1=Active, 2=Inactive, 3=Closed
{ "type": "admin" }
```

Current workaround — nested ternary chains — degrades quickly:

```
item.status == 0 ? "Pending" : item.status == 1 ? "Active" : item.status == 2 ? "Inactive" : "Unknown"
```

With `@Switch`:

```
@Switch(item.status, {0: "Pending", 1: "Active", 2: "Inactive"}, "Unknown")
```

---

## Signature

```
@Switch(value, {case: result, ...}, default?) → result
```

| Argument | Type | Required | Description |
|---|---|---|---|
| `value` | any | yes | The value to match against |
| `cases` | object | yes | Key-value map of cases. Keys are always strings; value is coerced to string for lookup |
| `default` | any | no | Returned when no case matches. Defaults to `null` |

---

## Semantics

- `value` is coerced to a string via `String(value ?? "")` before lookup.
- Object keys in openui-lang are always strings, so `{0: "Pending"}` stores key `"0"`. Numeric values (`0`, `1`) and their string equivalents (`"0"`, `"1"`) both match correctly.
- If no case matches and `default` is omitted, returns `null`.
- All case results are evaluated eagerly (no lazy AST evaluation needed). This is consistent with how object literals already work in the evaluator.
- `@Switch` is not a lazy builtin — it goes into `BUILTINS`, not `LAZY_BUILTINS`.

---

## Usage Examples

**Integer enum → label:**
```
@Switch(item.status, {0: "Pending", 1: "Active", 2: "Inactive", 3: "Closed"}, "Unknown")
```

**String enum → label:**
```
@Switch(item.role, {admin: "管理员", user: "用户", guest: "访客"}, "未知")
```

**Enum → component (e.g. Badge with color):**
```
@Switch(item.severity, {1: Badge("Low", "gray"), 2: Badge("Medium", "yellow"), 3: Badge("High", "red")})
```

**Composed inside `@Each`:**
```
@Each(data.rows, "item", Row(item.name, @Switch(item.status, {0: "待处理", 1: "进行中", 2: "完成"}, "-")))
```

**Composed with `@Filter`:**
```
@Count(@Filter(data.rows, "status", "==", 1))
```
(Switch is for display mapping; filtering by raw enum value still uses `@Filter` directly.)

---

## Implementation

### `packages/lang-core/src/parser/builtins.ts`

Add to `BUILTINS`:

```ts
Switch: {
  name: "Switch",
  signature: "Switch(value, {case: result, ...}, default?) → result",
  description: "Map a value to a result using a cases object; returns default (or null) if no match",
  fn: (value, cases, fallback = null) => {
    if (cases == null || typeof cases !== "object" || Array.isArray(cases)) return fallback;
    const key = String(value ?? "");
    return key in (cases as Record<string, unknown>)
      ? (cases as Record<string, unknown>)[key]
      : fallback;
  },
},
```

No other files need changes. `BUILTINS` is the single source of truth consumed by:
- `evaluator.ts` (runtime execution)
- `prompt.ts` (auto-generates the `## Built-in Functions` section)
- `BUILTIN_NAMES` set (parser classification)

---

## Prompt Impact

`builtinFunctionsSection()` in `prompt.ts` auto-generates from `BUILTINS`, so the prompt will include:

```
@Switch(value, {case: result, ...}, default?) → result — Map a value to a result using a cases object; returns default (or null) if no match
```

No manual prompt changes needed.

---

## Testing

### `lang-core`

- `@Switch(1, {0: "A", 1: "B", 2: "C"})` → `"B"`
- `@Switch("admin", {admin: "管理员", user: "用户"})` → `"管理员"`
- `@Switch(99, {0: "A"}, "Unknown")` → `"Unknown"`
- `@Switch(null, {0: "A"}, "fallback")` → `"fallback"`
- `@Switch(0, {0: "zero"})` — numeric key `0` matches string key `"0"` → `"zero"`
- No match, no default → `null`
- Non-object cases (e.g. array, null) → `fallback`

### `react-lang`

- `@Switch` inside `@Each` resolves correctly per item
- `@Switch` result as component prop renders correctly
- `@Switch` with `dataModel` values evaluates through `resolveRef("data")`

---

## Affected Files

| File | Change |
|---|---|
| `packages/lang-core/src/parser/builtins.ts` | Add `Switch` to `BUILTINS` |

No other files require changes.
