## Why

React UI DSL eval 调优时，agent、retrospective 和代码对“模型实际看到的 system prompt”产生了认知漂移：有些组件、builtins 或规则被误判为不存在或不可用。现在需要让每次 eval run 都留下可审计的 canonical system prompt，作为判断 prompt contract 的唯一证据。

## What Changes

- 在 eval run 目录中记录一份完整展开后的 canonical system prompt。
- 该 prompt 使用本次 run 的 `strictness`，但将 fixture-specific data model 替换为明确占位符，避免重复保存已在 report 中记录的数据。
- 在 run/report metadata 中记录 system prompt artifact 路径和 hash，便于排查 prompt contract 变化。
- `pnpm eval status <run-id>` 显示 system prompt 记录信息，方便调优时快速定位。
- 不保存 per-fixture prompts，不改变 LLM 调用时实际传入 fixture data 的行为。

## Capabilities

### New Capabilities

- `eval-system-prompt-recording`: React UI DSL eval run 能记录一份 canonical system prompt，并通过 metadata/hash 将报告结果与实际 prompt contract 关联起来。

### Modified Capabilities

- `semantic-regression-testing`: eval/report 的 run 级元数据增加 system prompt artifact 和 hash，使语义回归对比可以引用本次 run 使用的 prompt contract。

## Impact

- `packages/react-ui-dsl/src/__tests__/e2e/eval-loop.ts`: 在 run 初始化或 regen 前写入 canonical system prompt，并在 status 输出中展示路径/hash。
- `packages/react-ui-dsl/src/__tests__/e2e/eval/run-manifest.ts` 与 `types.ts`: 增加 prompt artifact metadata。
- `packages/react-ui-dsl/src/__tests__/e2e/report.ts` / report data 类型：暴露 system prompt hash/path。
- `packages/react-ui-dsl/src/__tests__/e2e/llm.ts` 或新增 helper：复用真实 `dslLibrary.prompt()` 生成占位数据版本的 canonical prompt。
- 不新增第三方依赖；不改变 DSL parser、component schema、runtime rendering 或 judge rubric。
