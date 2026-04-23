## 1. Descriptions component contract

- [x] 1.1 Create `packages/react-ui-dsl/src/genui-lib/Descriptions` with `Descriptions`, `DescGroup`, and `DescField` schemas matching the approved positional API
- [x] 1.2 Implement `Descriptions.items` as a mixed list of `DescField` and `DescGroup`, while restricting `DescGroup.fields` to `DescField[]`
- [x] 1.3 Implement `DescField.value` so fields can display either plain values or already-rendered component expressions
- [x] 1.4 Keep formatting behavior limited to plain values, while component values render through `renderNode`
- [x] 1.5 Keep `DescField.label` and `Descriptions.items` required, with no automatic label derivation or automatic field generation

## 2. Pure CSS runtime layout

- [x] 2.1 Add a custom descriptions-style view component under `packages/react-ui-dsl/src/genui-lib/Descriptions/view`
- [x] 2.2 Implement top-level `title` and `extra` handling on `Descriptions`
- [x] 2.3 Implement the metric-cards-inspired visual style: 3-column default grid, soft card backgrounds, rounded corners, muted labels, emphasized values
- [x] 2.4 Implement column/span layout rules, including group-level column overrides and responsive-safe grid rendering
- [x] 2.5 Implement automatic span calculation for plain-text values when `DescField.span` is omitted, while keeping explicit `span` as an override and component-valued fields on a safe default path
- [x] 2.6 Add date/time formatting support for `format: "date" | "dateTime" | "time"`

## 3. Library and prompt integration

- [x] 3.1 Register `Descriptions`, `DescGroup`, and `DescField` in `packages/react-ui-dsl/src/genui-lib/dslLibrary.tsx`
- [x] 3.2 Export the new component family from `packages/react-ui-dsl/src/index.ts`
- [x] 3.3 Add prompt guidance and a prompt example showing object details, grouping, `span`, and `Tag` rendering
- [x] 3.4 Add a story entry so the new component family is available in the DSL component catalog

## 4. Verification

- [x] 4.1 Add schema and prompt-surface tests proving the new signatures are exported as:
  - `Descriptions(items, title?, extra?, columns?)`
  - `DescGroup(title, fields, columns?)`
  - `DescField(label, value, span?, format?)`
- [x] 4.2 Add runtime tests for mixed top-level items, group rendering, plain-value rendering, and explicit `span` behavior
- [x] 4.3 Add runtime tests covering component-valued fields such as `DescField("Status", Tag(user.status))`
- [x] 4.4 Add runtime tests covering auto-span calculation when `span` is omitted, including long plain-text values and component-valued fallback behavior
- [ ] 4.5 Run the targeted `react-ui-dsl` tests and typecheck commands needed to verify the new component family
