## Context

`packages/react-ui` already defines a small semantic `Tag` contract:

- `text: string`
- `icon?: string`
- `size?: "sm" | "md" | "lg"`
- `variant?: "neutral" | "info" | "success" | "warning" | "danger"`

`packages/react-ui-dsl` does not expose an equivalent component today, even though it already uses Ant Design as the runtime backend for other primitives such as `Button`, `Tabs`, and `TimeLine`. The requested change is therefore additive but cross-cutting: it introduces a new DSL component, exposes it through prompt/schema generation, and translates its semantic props into Ant Design runtime props and local styling.

One important constraint is that the existing React UI schema includes `icon?: string`, while Ant Design `Tag` expects a rendered `ReactNode` icon. There is no shared icon-token registry in `packages/react-ui-dsl` today, so schema parity and visual icon rendering cannot be treated as the same decision.

## Goals / Non-Goals

**Goals:**

- Add a `Tag` component to `packages/react-ui-dsl`.
- Keep the exported DSL schema aligned with the existing React UI `Tag` schema field names and enum values.
- Render the component through Ant Design's `Tag` backend instead of introducing a custom badge implementation.
- Expose `Tag` through `dslLibrary`, Storybook, and automated tests.

**Non-Goals:**

- Changing the existing `packages/react-ui` `Tag` schema.
- Introducing a cross-library icon registry or generic string-to-icon resolver.
- Re-theming Ant Design globally to exactly replicate the SCSS palette from `packages/react-ui`.

## Decisions

### Implement the new component in `packages/react-ui-dsl` and keep `packages/react-ui` as the schema source of truth

The request is specifically about adding a DSL component backed by Ant Design, so the new work belongs in `packages/react-ui-dsl/src/genui-lib/Tag`. The React UI package remains the contract reference for field names and enum values.

Alternative considered: adding a second custom Tag implementation in `packages/react-ui` and reusing it from the DSL package. Rejected because `react-ui-dsl` already standardizes on Ant Design as its runtime layer.

### Preserve full schema parity, including `icon`, but treat icon rendering as a separate runtime concern

The new DSL schema should mirror the existing React UI schema exactly so prompt generation and JSON schema stay aligned across packages. That means keeping the optional `icon: string` field even though Ant Design expects a `ReactNode`.

For this change, the runtime should accept the field without breaking validation, but it should not invent a new implicit icon resolution system. The implementation can either ignore `icon` at render time or support only a tightly documented local fallback if one already exists by implementation time. The design and tests should make this explicit so consumers do not assume arbitrary string icons will render automatically.

Alternative considered: removing `icon` from the DSL schema to match current runtime capabilities. Rejected because it would violate the user's requirement to align with the React UI schema.

### Map semantic variants to Ant Design status colors and map size through local view styling

Ant Design `Tag` already supports semantic color states that map cleanly to the OpenUI variants:

- `neutral` -> default styling
- `info` -> `processing`
- `success` -> `success`
- `warning` -> `warning`
- `danger` -> `error`

Ant Design does not expose the same `sm` / `md` / `lg` size API as the React UI package, so size should be implemented through a small local view-layer mapping, such as inline style presets or component-scoped classes applied around the antd tag.

Alternative considered: dropping `size` from the runtime behavior and only validating it in the schema. Rejected because size is part of the aligned public contract and should still affect rendering.

### Treat the change as additive library surface work and verify it through prompt/schema and view-layer tests

The main integration points are:

- adding the new component directory with `schema`, `index`, `view`, and `stories`
- exporting and registering `Tag` in `dslLibrary`
- extending prompt/schema tests so `Tag` appears in signatures and JSON Schema
- adding a view-layer helper test for variant and size mapping

Alternative considered: relying only on Storybook coverage. Rejected because the package already uses tests to protect prompt and runtime helper behavior.

## Risks / Trade-offs

- [Schema parity may imply more runtime support than actually exists for `icon`] -> Document the icon limitation in the design and cover it with explicit tests.
- [Ant Design colors will not match `packages/react-ui` SCSS values exactly] -> Preserve semantic meaning rather than pixel-perfect parity and keep the mapping local to the Tag view.
- [Local size styling could drift from the React UI package over time] -> Centralize size presets in one Tag view helper and test the mapping directly.
- [Prompt examples may continue omitting `Tag` if the library registration is incomplete] -> Add prompt/schema assertions that fail if `Tag` is missing from `dslLibrary`.

## Migration Plan

1. Add the new `Tag` component schema and Ant Design-backed view in `packages/react-ui-dsl/src/genui-lib/Tag`.
2. Register the component in `dslLibrary` and any story/test enumerations that expect a complete component list.
3. Add or update tests for prompt signatures, JSON schema output, and view-layer mapping.
4. Verify the package still typechecks and the targeted tests pass.

Rollback strategy: revert the new `Tag` component directory and remove the `dslLibrary` registration. The change is additive, so no data migration or compatibility layer is required.

## Open Questions

- Should a future change introduce a shared icon token registry so `icon: string` can render consistently across DSL components instead of remaining schema-only for now?
