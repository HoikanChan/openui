# React UI DSL EView Switch Design

**Date:** 2026-05-13
**Package:** `packages/react-ui-dsl`
**Status:** Draft for implementation handoff

## Overview

This document describes how to let `packages/react-ui-dsl` keep the same package name and public API while using different rendering dependencies in different environments:

- External/open-source build: keep the current `antd`-based rendering
- Internal/intranet build: replace the `antd`-based rendering with `eview`

The key constraint is that `eview` and `antd` do not expose identical component APIs. Because of that, this work should not be implemented as a fake `antd` compatibility layer. Instead, each DSL component that currently binds directly to `antd` should define its own small, stable view contract and provide two implementations under the existing component folder structure.

This is intentionally a minimal-structure change. The goal is to adapt the current codebase without moving the whole package to a new global backend architecture.

## Current Project Context

### Package build and entry

- Package root: `packages/react-ui-dsl`
- Build tool: `tsdown`
- Main entry: `src/index.ts`
- Current tsdown entry config: `tsdown.config.ts` only builds `src/index.ts`
- Public runtime library assembly: `src/genui-lib/dslLibrary.tsx`

### Important development commands

Run from `packages/react-ui-dsl`:

```bash
pnpm build
pnpm test
pnpm typecheck
pnpm ci
pnpm storybook
pnpm test:e2e
pnpm test:e2e:report
```

### Existing developer guidance already in the repo

- `packages/react-ui-dsl/AGENTS.md`
- `packages/react-ui-dsl/README.md`

Important existing rule: never manually edit committed e2e `.dsl` snapshots under `src/__tests__/e2e/snapshots/`.

### Current Storybook and test assumptions

- Storybook preview currently imports `antd/dist/reset.css` in `.storybook/preview.tsx`
- Storybook Vite config currently optimizes `antd` as a dependency in `.storybook/main.ts`
- Unit tests use `vitest.config.ts`
- Package tests already cover render behavior, DSL library structure, and e2e fixture output

### Current runtime components directly tied to `antd`

The current view layer directly imports `antd` in these runtime component views:

- `src/genui-lib/Button/view/index.tsx`
- `src/genui-lib/Form/view/index.tsx`
- `src/genui-lib/HLayout/view/index.tsx`
- `src/genui-lib/Link/view/index.tsx`
- `src/genui-lib/Select/view/index.tsx`
- `src/genui-lib/Table/view/index.tsx`
- `src/genui-lib/Tabs/view/index.tsx`
- `src/genui-lib/Tag/view/index.tsx`
- `src/genui-lib/TimeLine/view/index.tsx`
- `src/genui-lib/VLayout/view/index.tsx`

Some Storybook stories also import `antd` directly, but those are development-only and can be handled separately after the runtime path is stable.

### Current runtime components that are already independent of `antd`

These should not be part of the first migration wave unless a real internal requirement appears:

- `Card` already uses custom local markup/CSS instead of `antd/Card`
- `Separator` already delegates to `@openuidev/react-ui`
- `Charts/*` are ECharts-based, not `antd`-based
- `Descriptions` is already implemented internally in `react-ui-dsl`

## Goals

- Keep the package name unchanged: `@cloudsop/openui-react-ui-dsl`
- Keep the public DSL component API unchanged for consumers
- Let intranet builds replace `antd` runtime views with `eview`
- Keep external builds on the current `antd` behavior
- Minimize directory churn and avoid a repo-wide backend refactor
- Make the implementation understandable to engineers working inside the existing `genui-lib/*` structure

## Non-Goals

- Do not replace all components in one pass if only a subset is currently used internally
- Do not introduce a runtime provider or runtime environment switch
- Do not implement a fake general-purpose `antd` wrapper package on top of `eview`
- Do not rewrite DSL schemas or `defineComponent` contracts unless required by the view split
- Do not change e2e snapshot generation workflow as part of this effort

## Recommended Design

### 1. Keep the current component folder structure

Do not move all views into a new top-level `backends/` directory. Instead, keep each component under its existing `src/genui-lib/<Component>/` folder and split only the view implementation.

Recommended structure for each migrated component:

```text
src/genui-lib/<Component>/
  index.tsx
  schema.ts
  view/
    index.tsx
    types.ts
    antd.tsx
    eview.tsx
    shared.ts        # optional, only if the component has shared helpers
```

This keeps the existing ownership model intact:

- `index.tsx`: DSL schema parsing, normalization, `defineComponent`
- `view/types.ts`: stable local view contract for this component
- `view/antd.tsx`: current external implementation
- `view/eview.tsx`: internal implementation
- `view/index.tsx`: stable import surface for the rest of the component

### 2. Add a per-component stable view contract

Each migrated component should define a small local view contract that expresses what the DSL layer actually needs, not what `antd` or `eview` happen to expose.

Examples:

- `ButtonViewProps`: `text`, `status`, `type`, `disabled`, `style`
- `TabsViewProps`: `items`, `activeTab`, `onTabChange`, `style`
- `TableViewProps`: normalized rows, normalized columns, tooltip behavior, expand-row behavior
- `FormViewProps`: normalized fields, initial values, layout, label alignment

This is the most important rule in the design:

`genui-lib/<Component>/index.tsx` must only know its local view contract, never raw `antd` props and never raw `eview` props.

### 3. Keep `view/index.tsx` as the only local import target

Existing component code often imports from `./view`. Preserve that import pattern. The rest of the component should continue importing from `./view/index.tsx`.

`view/index.tsx` becomes a very thin stable barrel. It should not contain logic. It should only re-export the selected implementation.

Example intent:

```ts
export * from "./types";
export { ButtonView, resolveButtonAppearance } from "./antd";
```

For the internal build target, the same stable file should resolve to `./eview` instead.

### 4. Use build-time switching, not runtime switching

Because the internal and external builds must keep the same package name but have different dependencies, the switch must happen during build/test/storybook resolution.

Recommended implementation model:

- Keep source files for both implementations in repo
- Keep public import paths unchanged
- Use a build/test/storybook resolver override so `view/index.tsx` resolves to `antd` or `eview` at build time

There are multiple valid technical ways to do this. The project does not need to be dogmatic about aliasing. The important part is the behavior:

- Open-source build must not import `eview`
- Internal build must not require `antd`

Two practical options:

1. Alias-based resolution
   - Use Vite/tsdown/Vitest aliases to point a stable backend module to `antd` or `eview`
2. Generated barrel files before build
   - A small script rewrites or generates `view/index.tsx` re-exports before build/test/storybook

Given the existing toolchain, alias-based resolution is cleaner if it stays maintainable. Generated barrel files are acceptable if internal build tooling already uses that pattern.

### 5. Migrate only the components that matter first

First-wave runtime components:

- `Table`
- `Tabs`
- `Select`
- `Form`
- `Button`
- `Tag`
- `Link`
- `HLayout`
- `VLayout`
- `TimeLine`

Priority order:

1. `Table`
2. `Tabs`
3. `Form`
4. `Select`
5. Layout primitives: `HLayout`, `VLayout`
6. Simpler leaf components: `Button`, `Tag`, `Link`, `TimeLine`

The reason for this order:

- `Table`, `Tabs`, `Form`, and `Select` are the highest-risk components because `eview`/`antd` API mismatch is most likely there
- Layout primitives affect many stories and composite render paths
- Simple leaf components usually have the least semantic mismatch and are easier to finish later

## File-Level Implementation Plan

### Phase 1: Prepare contracts without changing behavior

For each first-wave component:

1. Rename current `view/index.tsx` implementation to `view/antd.tsx`
2. Add `view/types.ts`
3. Add new `view/index.tsx` that re-exports `types` and the current `antd` implementation
4. Update local imports if needed so the component compiles with no behavior change

At the end of Phase 1, open-source behavior should remain exactly the same.

### Phase 2: Implement `eview` views component by component

For each component:

1. Create `view/eview.tsx`
2. Map the stable local view contract to `eview`
3. Keep any DSL-side normalization in `index.tsx`, not in the backend view
4. Add local helper functions in `view/shared.ts` only when used by both backends

### Phase 3: Add build-target switching

The package needs an explicit build target concept, even if the final implementation uses aliases.

Recommended target names:

- `antd`
- `eview`

Recommended environment variable:

```bash
REACT_UI_DSL_VIEW_TARGET=antd
REACT_UI_DSL_VIEW_TARGET=eview
```

Use that variable consistently in:

- package build
- internal build pipeline
- Storybook
- Vitest

Do not let Storybook point at one backend while `pnpm build` points at another.

### Phase 4: Update package dependency behavior per environment

External build expectations:

- `antd` remains available
- current `peerDependencies` remain valid

Internal build expectations:

- `eview` must be resolvable
- internal pipeline must ensure `antd` is not required at runtime

If the intranet pipeline still installs `antd` temporarily during migration, that is acceptable for a short transitional window, but the final target should remove `antd` as a hard runtime dependency for internal use.

## Developer Notes Per Area

### `Table`

This is the highest-risk component. Current behavior includes:

- nested `dataIndex` support via dot-path splitting
- optional filtering and sorting
- optional tooltip cell rendering
- optional `@Render`/custom cell nodes
- expandable row support via schema helpers

Requirements for `Table` migration:

- keep DSL-to-column normalization outside the backend-specific view
- isolate backend-only column mapping in the view implementation
- do not leak `antd` `ColumnType` into `index.tsx` or schema code

If `eview` does not support feature parity for all current table options, implement the missing features explicitly in the `eview` view instead of weakening the DSL contract silently.

### `Tabs`

Current `Tabs` uses:

- controlled active tab
- `onChange`
- per-item `label`
- loading-state content fallback via skeleton

For `eview`, decide early whether loading skeleton belongs inside `Tabs` or should remain a plain fallback wrapper in the DSL view contract. Keep that choice consistent in both backends.

### `Form`

Current `Form` assumes:

- field list already normalized
- layout and label alignment are DSL-facing
- required validation is represented at the field level

Do not push DSL field-shape concerns into `eview`. `eview` should receive already-normalized fields.

### Layout: `HLayout` and `VLayout`

These are likely currently leaning on `antd/Flex`. For `eview`, define the minimal behavior needed:

- direction
- gap
- style
- children

If `eview` does not have a direct flex primitive, implement the behavior with local wrappers rather than leaking CSS policy into the DSL layer.

## Storybook Strategy

Storybook is part of the developer workflow, so it must be kept usable during migration.

Recommendations:

1. Keep default Storybook pointing to `antd` until the `eview` path is stable
2. Add an internal Storybook mode or command for `eview`
3. Remove or conditionalize `antd/dist/reset.css` in `.storybook/preview.tsx` for internal mode
4. Review stories that import `antd` directly and either:
   - keep them external-only, or
   - replace them with backend-neutral fixtures where useful

Suggested commands:

```bash
pnpm storybook
REACT_UI_DSL_VIEW_TARGET=eview pnpm storybook
```

If Storybook cannot be dual-mode immediately, the internal team can temporarily prioritize `build`, `typecheck`, and targeted render tests over story coverage.

## Testing and Verification

### Required verification for every migrated component

Run from `packages/react-ui-dsl`:

```bash
pnpm typecheck
pnpm test
pnpm ci
```

For components with stories or rich render behavior, also run:

```bash
pnpm storybook
```

For higher-risk changes that touch DSL render output:

```bash
pnpm test:e2e
```

### Recommended test additions

For each migrated component, keep or add tests in one of these buckets:

- view-level mapping tests
- component render tests
- DSL library exposure tests where relevant

Examples:

- `Button`: status/type mapping tests for both backends
- `Tabs`: loading and active-tab behavior
- `Table`: sorting/filtering/tooltip/render-node behavior
- `Form`: required field rendering and layout propagation

### Internal build smoke test

The internal team should add a dedicated smoke command once target switching exists, for example:

```bash
REACT_UI_DSL_VIEW_TARGET=eview pnpm build
REACT_UI_DSL_VIEW_TARGET=eview pnpm typecheck
REACT_UI_DSL_VIEW_TARGET=eview pnpm test
```

This is important because a default build that still points to `antd` can hide broken `eview` wiring for a long time.

## Debugging Guide

### Symptom: build still pulls in `antd` on internal mode

Check:

- whether any `view/eview.tsx` or shared helper still imports `antd`
- whether Storybook/test aliases differ from build aliases
- whether a barrel file still re-exports `./antd`
- whether `devDependencies` or an internal lockfile is masking incorrect runtime resolution

### Symptom: types compile externally but fail internally

Check:

- whether `view/types.ts` still references `antd` types
- whether backend-specific types leaked into shared helpers
- whether `eview` prop shapes were modeled too early in `index.tsx`

### Symptom: table behavior regresses only in internal mode

Check:

- path resolution for nested fields
- custom cell rendering
- tooltip fallback
- sorting/filtering semantics
- expand-row handling

Do not assume `eview` has a direct table API equivalent to `antd`. Reconstruct behavior from the DSL contract.

### Symptom: Storybook works but package build fails

Check:

- Storybook aliases vs tsdown aliases
- `optimizeDeps.include` and `resolve.alias` drift
- environment variable not being forwarded into the package build

### Symptom: unit tests pass but e2e snapshots change unexpectedly

Check:

- whether the new backend changed actual rendered semantics or only DOM shape
- whether prompt-facing behavior changed indirectly
- whether a DSL contract was weakened during the migration

Do not manually edit committed snapshot files. Regenerate them only through the documented regen commands if the change is intentional.

## Risks

- Hidden `antd` assumptions in helpers or tests may survive the first pass
- `Table` feature parity may be more expensive than initially expected
- Storybook may lag behind the package build if alias handling is inconsistent
- Engineers may accidentally model `view/types.ts` after `antd` because the old implementation is the current reference

## Recommended Team Rules During Implementation

1. Every migrated component must own a backend-neutral `view/types.ts`
2. No `antd` import is allowed in `index.tsx`, `schema.ts`, or shared DSL normalization code
3. No `eview` import is allowed outside `view/eview.tsx` and backend-local helpers
4. If behavior differs across backends, document it explicitly in the component tests
5. Migrate high-risk components one by one; do not batch `Table`, `Tabs`, and `Form` into one unreviewable change

## Deliverables

The implementation should be considered complete when all of the following are true:

- first-wave components have split `antd` and `eview` views
- internal build path no longer depends on `antd` at runtime
- external build path still works unchanged
- Storybook/test/build all have a consistent target-selection mechanism
- regression coverage exists for the highest-risk components
- the package still exports the same top-level public DSL surface

## Recommendation

Proceed with a minimal-change migration inside the existing `genui-lib/*/view` folders. Avoid a large architectural refactor. The stable seam should be the per-component `view/types.ts` contract, and the environment choice should happen at build resolution time, not at runtime.

This keeps the package understandable for both the open-source side and the internal team replacing `antd` with `eview`.
