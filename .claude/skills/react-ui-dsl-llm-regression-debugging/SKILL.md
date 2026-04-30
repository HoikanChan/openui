---
name: react-ui-dsl-llm-regression-debugging
description: Debug invalid or semantically wrong LLM-generated openui-lang in this repository, especially regressions under `packages/react-ui-dsl/src/__tests__/e2e`. Use when a fixture snapshot or generated DSL has parse errors, unstable output across regenerations, or wrong Table/Col/@Render behavior such as misused binders, incorrect `format` usage, or links rendered in the wrong column.
---

# React UI DSL LLM Regression Debugging

## Overview

Debug `react-ui-dsl` generation failures by isolating one fixture, proving the failure mode, and tightening the default prompt surface before touching runtime behavior. Favor prompt and fixture fixes when the runtime contract is already correct.

## Workflow

1. Reproduce on one fixture only.
2. Identify whether the failure is syntax, semantics, or runtime.
3. Tighten the fixture so the bad output cannot pass.
4. Fix the prompt at the library surface first.
5. Regenerate the same fixture multiple times to test convergence.
6. Run the relevant test set before stopping.

## Isolate One Fixture

Add or update a single fixture in [packages/react-ui-dsl/src/__tests__/e2e/fixtures.ts](C:/workspace/openui/packages/react-ui-dsl/src/__tests__/e2e/fixtures.ts) that matches the reported prompt and data model exactly.

Prefer:

- The exact user prompt.
- The exact host `dataModel`.
- Assertions on user-visible semantics, not just parse success.

Use the fixture-oriented regen flow from [packages/react-ui-dsl/README.md](C:/workspace/openui/packages/react-ui-dsl/README.md):

```bash
pnpm test:e2e:regen:fixture -- -t <fixture-id>
```

Do not regenerate unrelated snapshots. Do not hand-edit `.dsl` snapshots.

## Classify the Failure

Use the narrowest failing command first:

```bash
pnpm exec vitest run src/__tests__/e2e/dsl-e2e.test.tsx -t <fixture-id>
```

Sort the issue into one of these buckets:

- Parse fails: generated DSL is grammatically invalid or violates parser constraints.
- Parse passes but render fails: generated DSL is legal but semantically wrong for the component contract.
- Render passes once but changes shape across regens: prompt guidance is under-specified.

For `@Render` and Table bugs, check the contract before changing code:

- [openspec/changes/add-render-builtin/specs/render-builtin/spec.md](C:/workspace/openui/openspec/changes/add-render-builtin/specs/render-builtin/spec.md)
- [openspec/changes/add-render-builtin/specs/llm-friendly-table-dsl/spec.md](C:/workspace/openui/openspec/changes/add-render-builtin/specs/llm-friendly-table-dsl/spec.md)

If the runtime contract already matches the spec, treat the bug as prompt-surface or fixture-quality first.

## Tighten the Fixture Before Fixing Prompt

Write assertions that make the bad shape impossible to accept.

Prefer assertions like:

- The person name itself must be the link, not a separate "Details" column.
- Salary must still render as `95000`, not a date-like string.
- Raw ISO timestamps must not leak into the DOM.
- Wrong helper text such as `View Profile` or `View Details` must not appear if the design does not call for it.

Use `assert.verify` for DOM-structural checks.

## Fix the Prompt at the Library Surface

When the bug is about how the LLM chooses DSL patterns, prefer fixing the default prompt in [packages/react-ui-dsl/src/genui-lib/dslLibrary.tsx](C:/workspace/openui/packages/react-ui-dsl/src/genui-lib/dslLibrary.tsx), not only the e2e test harness.

Use:

- `additionalRules` for non-negotiable constraints.
- `examples` for one short canonical pattern that the model should copy.

Keep the rule text explicit. For Table regressions, say the contract directly:

- `@Render("v", expr)` receives the cell value.
- `@Render("v", "row", expr)` is required when the body needs the full row.
- `format` is only for ISO date/time strings, never numeric fields like `salary` or `revenue`.

Prefer fixing `dslLibrary.prompt()` instead of `packages/react-ui-dsl/src/__tests__/e2e/llm.ts` when the same prompt surface is used by demos, tests, and downstream integrations.

## Known Table Smells

Treat these as prompt bugs unless the schema/spec says otherwise:

- `salaryCol = Col(..., {format: "date"})`
- Referencing `row.*` inside `@Render("v", expr)` without declaring `"row"`
- Using `"row"` as the first binder when the cell value should be `"v"`
- Moving a link requested for the main value into a separate action/details column
- Inventing column behavior that weakens the requested semantics even if the DSL still parses

## Stability Check

After changing the prompt, regenerate the same fixture at least three times:

```bash
pnpm test:e2e:regen:fixture -- -t <fixture-id>
```

Read the snapshot after each run. Look for convergence, not just one lucky pass.

If the output still oscillates, tighten the canonical example or fixture assertions before changing runtime behavior.

## Verification

Run the smallest relevant checks first:

```bash
pnpm exec vitest run src/genui-lib/dslLibrary.test.ts
pnpm exec vitest run src/__tests__/e2e/llm.test.ts
pnpm exec vitest run src/__tests__/e2e/dsl-e2e.test.tsx -t <fixture-id>
```

Before claiming the fix is stable, run:

```bash
pnpm test:e2e
```

Ignore existing jsdom or antd warnings only if the test exits cleanly and they are unrelated to the regression being debugged.
