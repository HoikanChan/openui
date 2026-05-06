# HOR-10 Shape-Aware Formatting Prompt Design

## Context

HOR-10 tracks GenUI DSL output that chooses broadly correct components but formats API-shaped data poorly. The issue appears in benchmark fixtures, but the fix should generalize to similar API responses rather than target fixture IDs.

The current runtime already exposes `@FormatDate`, `@FormatBytes`, `@FormatNumber`, and `@FormatPercent`. The gap is that the prompt does not strongly explain when to project positional data before formatting, how to treat byte and ratio semantics, or how to avoid invalid pseudo-reusable component definitions.

## Decision

Implement this as a prompt-only change in `packages/react-ui-dsl/src/genui-lib/dslLibrary.tsx`, with prompt-surface tests in `packages/react-ui-dsl/src/genui-lib/dslLibrary.test.ts`.

Do not add a data-shape analyzer or renderer-level auto-formatting in this change.

## Prompt Rules

Add generic rules for these data shapes:

- Positional tuple arrays such as `[[timestamp, value], ...]` must be projected by index before formatting. For timestamps, format `item[0]` or a derived timestamp array with `@FormatDate`; do not call `@FormatDate` on the entire tuple array.
- Byte-count fields such as `bytes`, `*Bytes`, `inBytes`, `outBytes`, `totalBytes`, `usedBytes`, or rows with `unit: "bytes"` should display through `@FormatBytes`.
- Ratio values stored as 0-1 fractions, or derived as `used / total`, should display through `@FormatPercent` in text, tables, and descriptions.
- Derived row-level ratio or byte displays that need multiple fields must use `@Render("v", "row", ...)` so all referenced fields are in scope.
- Generated DSL must not declare pseudo-reusable component templates that reference undeclared variables. Repeated displays should use existing components directly, table cells, descriptions, or valid data iteration.
- Avoid duplicating the same record set in both summary cards and a full table unless the data has distinct summary values. For ordinary homogeneous lists, prefer one clear table with formatted columns.

## Tests

Extend prompt tests to assert the generic rules and representative snippets are present. Tests should avoid fixture IDs such as `timeseries-tuple-pairs` and `cross-magnitude-values`; they should verify the shape-aware guidance instead.

Existing uncommitted anti-fabrication prompt tests are adjacent work and should be preserved.

## Risks

Prompt-only fixes depend on model compliance. This is acceptable for the first HOR-10 pass because it is low cost and aligns with the existing `dslLibrary.prompt()` design. If later evals show unstable compliance across API shapes, the follow-up should be a separate data-shape hinting layer rather than renderer auto-formatting.
