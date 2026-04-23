## 1. Tag component contract

- [x] 1.1 Create `packages/react-ui-dsl/src/genui-lib/Tag` with a schema aligned to `packages/react-ui/src/genui-lib/Tag/schema.ts`
- [x] 1.2 Implement the Ant Design-backed Tag view and helper mapping for `variant` and `size`
- [x] 1.3 Decide and document the initial runtime handling for the optional `icon` string field

## 2. Library integration

- [x] 2.1 Export and register `Tag` in `packages/react-ui-dsl/src/genui-lib/dslLibrary.tsx`
- [x] 2.2 Add Storybook coverage for the new DSL `Tag` component and include representative size and variant cases
- [x] 2.3 Update any component-list or story-structure tests that assume the full set of exported DSL components

## 3. Verification

- [x] 3.1 Add prompt signature and JSON schema assertions proving `Tag` appears with the aligned field names and enum values
- [x] 3.2 Add view-layer tests covering variant mapping, size mapping, and the documented `icon` behavior
- [x] 3.3 Run the targeted `react-ui-dsl` test and typecheck commands needed to verify the new component
