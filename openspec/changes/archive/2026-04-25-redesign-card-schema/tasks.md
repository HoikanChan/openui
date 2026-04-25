## 1. Schema

- [x] 1.1 Rewrite `Card/schema.ts`: remove `FlexPropsSchema` merge, add `width: z.enum(["standard", "full"]).optional()`, change children to `z.array(z.any()).optional()`
- [x] 1.2 Delete `Card/flexPropsSchema.ts`

## 2. Component

- [x] 2.1 Update `Card/index.tsx`: remove inline `gapMap`, `alignMap`, `justifyMap`; remove flex prop handling from the style object; pass `width` to `CardView`
- [x] 2.2 Update Card `description` string to reflect new props and recommend VLayout/HLayout for internal layout

## 3. View

- [x] 3.1 Update `Card/view/index.tsx` (`CardView`): add `width` prop and apply the appropriate width CSS class

## 4. Styles

- [x] 4.1 Verify `card.module.css` has width variant classes (`standard`, `full`); add them if missing

## 5. Tests & Snapshots

- [x] 5.1 Update `Card/schema.ts` unit tests (if any) to reflect removed flex props and added width
- [x] 5.2 Check E2E snapshots for any fixture using Card flex props; regenerate affected snapshots via `pnpm test:e2e:regen`
- [x] 5.3 Run `pnpm test` and confirm all tests pass
