## Context

`packages/react-ui-dsl` already exposes primitives for content, tabular data, charts, forms, and semantic tags. It does not expose a purpose-built way to render one object as labeled detail rows.

Today, a generated detail view must be approximated with generic layout primitives:

- `Stack` + `TextContent` for label/value pairs
- `Card` for grouping
- `Table` if the model reaches for a familiar grid, even though the runtime and prompt notes clearly treat `Table` as collection-oriented

That gap matters because the user goal is specifically LLM-friendly object rendering. The change is not only about visual layout. It is about defining a schema shape that:

- matches OpenUI's positional-argument authoring style
- is explicit enough to generate reliably
- is short enough that the model prefers it over verbose manual layout
- supports common descriptions features such as section grouping, `span`, `title`, `extra`, and custom rendered values like `Tag`

The component needs to be implemented in `packages/react-ui-dsl`, because the user clarified that the target package is the DSL-facing library. The view should still follow the supplied metric-cards reference instead of delegating to Ant Design's built-in descriptions widget.

## Goals / Non-Goals

**Goals:**

- Add a metric-card style object-details component family to `packages/react-ui-dsl`.
- Expose a compact OpenUI schema optimized for LLM generation:
  - `Descriptions(items, title?, extra?, columns?)`
  - `DescGroup(title, fields, columns?)`
  - `DescField(label, value, span?, format?)`
- Require explicit field definitions so generated output stays predictable.
- Allow top-level `Descriptions` content to mix standalone `DescField` and grouped `DescGroup` entries.
- Allow field values to be either plain values or already-rendered component expressions such as `Tag(user.status)`.
- Support descriptions-style multi-column layout with field spanning and optional per-group column overrides.
- Keep the default visual treatment aligned with the provided metric-cards reference implementation rather than a table-like descriptions list.
- Register the new primitives in the prompt/library surface with notes and examples that steer the model toward this component instead of ad hoc `Stack` layouts.

**Non-Goals:**

- Automatically inferring fields from the input object.
- Deriving a fallback display label from `path`.
- Supporting nested groups (`DescGroup` inside `DescGroup`).
- Reusing or depending on Ant Design's `Descriptions` runtime implementation.

## Decisions

### Add a dedicated `Descriptions` component family in `packages/react-ui-dsl`

The new feature belongs in `packages/react-ui-dsl/src/genui-lib` and should render through a dedicated custom view component under the same package.

Alternative considered: teaching the model to compose detail views from existing `Stack`, `Card`, and `Tag` primitives. Rejected because the user specifically wants a token-efficient schema for object data, and manual composition remains too verbose for common detail pages.

### Model the public API as one parent plus two declarative child nodes

The new schema surface should be:

- `Descriptions(items, title?, extra?, columns?)`
- `DescGroup(title, fields, columns?)`
- `DescField(label, value, span?, format?)`

`DescField` and `DescGroup` should behave like declarative child descriptors similar to how `Col` is used by `Table`: they exist as authoring nodes for the parent component to consume, and do not need to render meaningful standalone output on their own.

This keeps the authoring model aligned with OpenUI's current style:

- explicit child references
- shallow positional schemas
- separate nodes that stream independently

Alternative considered: a single `Descriptions` component with a deeply nested object prop for groups and fields. Rejected because nested object props cost more tokens and tend to validate less reliably in generated output.

### Keep field authoring explicit: no auto-mode and no inferred labels

`Descriptions.items` should be required, and `DescField.label` should also be required.

This is a deliberate trade-off in favor of generation quality. While an implicit `Descriptions()` auto mode would be shorter, it would also encourage the model to stop at a low-control output even when the UI needs proper labels, grouping, or custom rendering. Requiring explicit labels also avoids low-quality label inference from field expressions such as `user.createdAt`, `billing.plan_name`, or `profile.primaryEmail`.

Alternative considered: letting the component infer `"Created At"`-style labels from field expressions. Rejected because inferred labels are inconsistent across naming styles and domains, and the user explicitly rejected that behavior.

### Allow `Descriptions` to mix direct fields and grouped fields

`Descriptions.items` should accept both `DescField` and `DescGroup`.

This gives the model a compact incremental path:

- simple detail card: just a few top-level `DescField`s
- richer detail page: mix those fields with one or more `DescGroup`s

`DescGroup.fields` should only accept `DescField[]` in the first version. That keeps the hierarchy shallow and makes the layout renderer easier to reason about.

Alternative considered: forcing every field into a `DescGroup`. Rejected because it adds extra references and tokens for the common case where only one ungrouped section is needed.

### Use direct `value` expressions instead of internal object binding or path resolution

`DescField.value` should accept the value to display directly. That includes both plain scalar expressions and rendered component expressions, for example:

- `user.name`
- `user.profile.email`
- `Tag(user.status)`
- `user.createdAt`

This is a deliberate simplification for LLM authoring. The model no longer needs to learn a component-local binding abstraction such as `path` lookup or a special render callback shape. It can write the same kind of expressions it already uses elsewhere in OpenUI.

The parent `Descriptions` renderer should treat `value` as display content:

- render plain values as text
- render element values via `renderNode`
- apply `format` only to plain values, not to already-rendered component nodes

Alternative considered: `DescField(label, path, render?, span?, format?)` with component-local path lookup and deferred templates. Rejected because the extra abstraction makes generation harder even if it can save a few repeated field references.

### Keep custom rendering implicit by letting field values be components

Because `DescField.value` can already be an element expression, the component family does not need its own render-function hook in the first version.

This keeps the generated form shorter and easier to understand:

```text
DescField("Status", Tag(user.status))
```

instead of introducing a separate render binding mechanism.

### Support simple descriptions-style layout semantics

The runtime should implement the following layout model:

- `Descriptions.columns` defines the default number of detail columns and should default to `3` to match the approved metric-cards layout
- `DescGroup.columns`, when provided, overrides the inherited column count for that group
- `DescField.span`, when provided, overrides the automatic span calculation
- fields consume layout slots until the current row is full, then wrap
- each group starts its own layout section
- `title` and `extra` belong only to the top-level `Descriptions`

The pure CSS view should be responsive and degrade gracefully on narrow containers, for example by collapsing to a single-column layout via CSS rules even when the logical authoring model requests more columns.

Alternative considered: responsive column maps or breakpoint objects in the public schema. Rejected for the first version because it would make the component harder for the model to author and substantially increase token cost.

### Default omitted spans to auto-fit measurement instead of fixed span 1

When `DescField.span` is omitted, the runtime should estimate a span automatically using the same interaction model as the provided metric-cards reference:

- measure the available grid width
- derive one-column width from `columns` and `gap`
- for plain text values, estimate whether the value fits on one line in one column, then two columns, then the full row
- use the smallest span that preserves a single-line value when possible
- for component-valued fields, default the automatic span to `1` unless a later enhancement adds richer intrinsic measurement

This keeps authoring short for the common case. The model can usually omit `span`, and the runtime still produces a balanced card grid for long values such as IDs, emails, or timestamps.

The reference styling should also stay visually close to the supplied implementation:

- wrapper title with medium emphasis and compact bottom spacing
- 3-column grid with consistent gaps
- per-field cards with rounded corners, soft neutral background, and subtle shadow
- muted label text and stronger value text
- matching dark-theme overrides for background, label, and value colors

### Integrate the component into both exported React UI libraries with prompt guidance

Because the new component is a `react-ui-dsl` primitive, it should be registered in `dslLibrary`, exported from `src/index.ts`, and covered by story structure and prompt-surface tests.

Alternative considered: adding the component only as an internal view helper without library registration. Rejected because the user's primary success metric is generation quality, which depends on the LLM seeing the new schema in the DSL library surface.

## Risks / Trade-offs

- [Explicit labels cost a few more tokens than an inferred-label shortcut] -> Accept the extra tokens because they buy much better semantic accuracy and localization flexibility.
- [Direct value expressions may repeat `user.xxx` across many fields] -> Accept the repeated references because removing `path` and custom render bindings should improve generation reliability.
- [Values can be either plain text or component nodes] -> Keep the renderer rule simple: plain values stringify, element values render as children, and formatting applies only to plain values.
- [Automatic span measurement introduces runtime layout work and font-coupled heuristics] -> Keep the algorithm narrow and deterministic, matching the approved metric-cards behavior for plain text and falling back safely for component values.
- [A multi-column details layout can become visually uneven with wide `span` usage] -> Keep the layout rules deterministic and document that `span` should remain within the current column count.
- [Adding three new prompt-facing nodes slightly enlarges the library surface] -> Offset this by steering the model away from much longer `Stack`-based detail compositions.

## Migration Plan

1. Add the custom metric-card view implementation under `packages/react-ui-dsl/src/genui-lib/Descriptions/view`.
2. Add `Descriptions`, `DescGroup`, and `DescField` under `packages/react-ui-dsl/src/genui-lib/Descriptions`.
3. Register the new primitives in `dslLibrary` and `src/index.ts`.
4. Add prompt guidance, stories, and automated tests covering schema shape, mixed plain/component values, grouping, manual span overrides, and auto-span behavior when `span` is omitted.

Rollback strategy: remove the new descriptions component directory and unregister the new primitives from the exported libraries. The change is additive, so no data migration is required.

## Open Questions

- Should a later change add richer `format` helpers beyond `date`, `dateTime`, and `time`, or is direct component composition enough for non-date formatting?
