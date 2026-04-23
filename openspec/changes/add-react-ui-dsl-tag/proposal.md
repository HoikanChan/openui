## Why

`packages/react-ui-dsl` currently has no `Tag` component even though `packages/react-ui` already defines a `Tag` schema. That leaves the DSL without a compact status/badge primitive and creates schema drift between the two React-facing libraries.

## What Changes

- Add a new `Tag` component to `packages/react-ui-dsl/src/genui-lib/Tag`.
- Align the DSL-facing `Tag` schema with `packages/react-ui/src/genui-lib/Tag/schema.ts`, including `text`, `icon`, `size`, and `variant`.
- Render the new DSL `Tag` through Ant Design's `Tag` component, with local mapping for OpenUI size and variant semantics.
- Register `Tag` in the exported DSL library and add stories and tests covering the schema surface and runtime appearance mapping.
- Keep the optional `icon` field in the schema contract for parity, while documenting the initial runtime handling separately from the schema alignment work.

## Capabilities

### New Capabilities
- `dsl-tag-component`: React UI DSL exposes a `Tag` component whose public schema matches the existing React UI `Tag` contract and renders through Ant Design.

### Modified Capabilities
None.

## Impact

- Affected code: `packages/react-ui-dsl/src/genui-lib/Tag/*`, `packages/react-ui-dsl/src/genui-lib/dslLibrary.tsx`, and related tests and Storybook files.
- APIs: `@openuidev/react-ui-dsl` gains a new public `Tag` component in its prompt surface and JSON schema output.
- Dependencies: no new package dependency is expected because `antd` is already the rendering backend for `packages/react-ui-dsl`.
