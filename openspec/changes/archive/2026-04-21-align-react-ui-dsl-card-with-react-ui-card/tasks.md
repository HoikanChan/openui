## 1. Schema And Component Contracts

- [x] 1.1 Add a new `packages/react-ui-dsl/src/genui-lib/CardHeader` component with `schema.ts`, `index.tsx`, view layer, and CSS Modules styling aligned to the `react-ui` `CardHeader` contract.
- [x] 1.2 Replace `packages/react-ui-dsl/src/genui-lib/Card/schema.ts` so `Card` exposes only `children`, optional `variant`, and merged `FlexPropsSchema`, removing legacy `header` and `width`.
- [x] 1.3 Update the `Card` child union and `packages/react-ui-dsl/src/genui-lib/dslLibrary.tsx` exports so `CardHeader` is a first-class DSL component and valid `Card` child.

## 2. Rendering And Styling

- [x] 2.1 Rewrite `packages/react-ui-dsl/src/genui-lib/Card/view/index.tsx` as a CSS Modules-based layout container that applies `card` / `clear` / `sunk` variants, full-width layout, and flex defaults matching the `react-ui` `Card` behavior.
- [x] 2.2 Replace the existing `Card` stylesheet with tokenized CSS custom properties and add a local `classNames` utility for composing module classes.
- [x] 2.3 Implement `CardHeader` rendering and module CSS so title/subtitle presentation fits inside the new card composition model without relying on `Card.header`.

## 3. Stories And Tests

- [x] 3.1 Rewrite `Card` stories and tests to cover the new `CardHeader` child composition, aligned variant behavior, and removal of legacy `header` / `width` props.
- [x] 3.2 Add or update tests that assert `dslLibrary.toSpec()` and `dslLibrary.toJSONSchema()` expose `CardHeader` and the new `Card` contract while rejecting the removed DSL fields.
- [x] 3.3 Add targeted tests for the new CSS Modules-based `Card` and `CardHeader` views so default layout and semantic variant behavior are protected.

## 4. Verification

- [x] 4.1 Run the relevant `packages/react-ui-dsl` verification commands for the changed surface and fix any regressions caused by the contract migration.
- [x] 4.2 Confirm the new OpenSpec change artifacts and implementation match the intended breaking migration from `Card.header` authoring to `CardHeader` children.
