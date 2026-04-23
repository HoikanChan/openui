## Why

`packages/react-ui-dsl` currently has no compact object-details primitive. When the model needs to present one record such as a user profile, order summary, or system metadata, it has to approximate that layout with layout containers, `Table`, and ad hoc text nodes.

That is a poor fit for both runtime semantics and LLM generation:

- `Table` is column-oriented and optimized for collections, not a single object
- `Stack`-based detail layouts require many more references and tokens
- there is no first-class place for `label`, `span`, `group`, or `extra` semantics
- custom value rendering such as `Tag(...)` is possible, but verbose and inconsistent

The requested change introduces a pure CSS `Descriptions` component family for object-style detail layouts, modeled after the strengths of Ant Design's descriptions pattern while keeping the OpenUI authoring surface compact and explicit.

## What Changes

- Add a new `Descriptions` component family to `packages/react-ui-dsl` for object-detail layouts.
- Introduce three OpenUI-facing primitives:
  - `Descriptions(items, title?, extra?, columns?)`
  - `DescGroup(title, fields, columns?)`
  - `DescField(label, value, span?, format?)`
- Keep the authoring model explicit:
  - `Descriptions` requires `items`
  - `DescField.label` is required
  - there is no automatic field inference or automatic label derivation
- Allow `Descriptions.items` to mix direct `DescField` entries with `DescGroup` entries.
- Support custom field rendering by passing components directly as the `value`, such as `Tag(user.status)`.
- Implement the runtime view inside `react-ui-dsl` with a custom card-grid view instead of routing through Ant Design's `Descriptions` widget.
- Keep the view-layer look aligned with the provided metric-cards reference: soft card background, rounded corners, compact section title, and a 3-column card grid visual language.
- When `DescField.span` is omitted, compute a best-fit span automatically from the rendered plain-text value width instead of always defaulting to `1`.
- Register the new component family in the exported `react-ui` libraries and prompt guidance so the model learns the short, preferred authoring path for object details.

## Capabilities

### New Capabilities

- `descriptions-component`: OpenUI React UI exposes a dedicated object-details component family with grouping, column/span layout, and custom field rendering.

### Modified Capabilities

None.

## Impact

- Affected code:
  - `packages/react-ui-dsl/src/genui-lib/Descriptions/*`
  - `packages/react-ui-dsl/src/genui-lib/dslLibrary.tsx`
  - `packages/react-ui-dsl/src/index.ts`
  - related tests and examples
- APIs:
  - `@openuidev/react-ui-dsl` gains `Descriptions`, `DescGroup`, and `DescField` in its LLM-facing component surface
- Prompt surface:
  - object-detail UIs can be generated with fewer component references and less custom render ceremony than equivalent `Stack`/`TextContent` compositions
- Dependencies:
  - no new UI library dependency is expected because the component is implemented with existing React + CSS infrastructure
