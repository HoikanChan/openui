## Why

`packages/react-ui-dsl` 当前的 `Card` 把标题区能力建模成 `header` 对象，并暴露 `width` 这类与 `react-ui` 的 genui-lib 不一致的 DSL 接口。这让两套库的组件边界分叉，增加了 prompt/schema 维护成本，也让已经独立存在于 `react-ui` 中的 `CardHeader` 无法作为 DSL 一等组件复用。

## What Changes

- Rework `packages/react-ui-dsl/src/genui-lib/Card` so its schema and rendering model match `packages/react-ui/src/genui-lib/Card`.
- Add a new standalone `CardHeader` component to `packages/react-ui-dsl/src/genui-lib` with the same schema surface as `react-ui` (`title` and `subtitle`).
- **BREAKING** Remove the DSL `Card.header` object contract and migrate header authoring to `CardHeader` children.
- **BREAKING** Remove the DSL `Card.width` prop and make the rendered card follow the `react-ui` card behavior of always rendering full width while accepting flex layout props.
- Replace the current card view structure with CSS Modules-based styling and local utility helpers consistent with the repository direction for DSL components.
- Update DSL library exports, stories, and tests so the prompt/schema surface teaches `Card` + `CardHeader` as separate components.

## Capabilities

### New Capabilities
- `react-ui-dsl-card-layout`: Define the LLM-facing `Card` and `CardHeader` contracts for `react-ui-dsl` so cards follow the `react-ui` layout model and header content is authored as a separate child component.

### Modified Capabilities

## Impact

- Affected code: `packages/react-ui-dsl/src/genui-lib/Card/*`, new `packages/react-ui-dsl/src/genui-lib/CardHeader/*`, `packages/react-ui-dsl/src/genui-lib/dslLibrary.tsx`, and related stories/tests.
- APIs: `@openuidev/react-ui-dsl` `Card` schema changes incompatibly by removing `header` and `width`; `CardHeader` becomes a new public DSL component.
- Tests and prompt metadata: Storybook args, unit tests, `dslLibrary.toSpec()`, and `dslLibrary.toJSONSchema()` outputs will change to reflect the new component contracts.
