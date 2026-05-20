## Context

React UI DSL eval 的 regen 路径在每个 fixture 调用 LLM 前，通过 `dslLibrary.prompt({ strictness, dataModel: { raw: dataModel } })` 生成 system prompt。这个 prompt 由 `lang-core` prompt assembly、React UI DSL component signatures、builtins、standard/strict rules、examples 和当前 fixture 的 data model 共同组成。

当前 run 目录保存了 report、DSL snapshots、screenshots 和 judge 结果，但没有保存模型实际看到的 system prompt。调优失败后，agent 往往只能从代码、retrospective 或记忆中推断 prompt contract，导致“某个 builtin/component 是否存在”“strict rule 是否说错”等基础判断发生漂移。

## Goals / Non-Goals

**Goals:**

- 每个 eval run 记录一份 canonical system prompt，用于审计本次 run 使用的 prompt contract。
- canonical prompt 使用真实 `dslLibrary.prompt()` 生成路径和本次 run 的 `strictness`。
- canonical prompt 的 data model section 使用占位符，不重复保存 fixture data。
- run/report metadata 记录 prompt path/hash，方便对比和排查。
- status 命令能直接显示 prompt artifact 信息。

**Non-Goals:**

- 不保存 per-fixture system prompts。
- 不改变实际 LLM regen 调用；fixture 生成仍使用真实 fixture data model。
- 不引入 prompt section registry、prompt IDE、contract diff 或 dynamic example selection。
- 不改变 DSL 语法、component schema、runtime rendering、judge rubric 或评分逻辑。

## Decisions

### D1: 记录 run-level canonical prompt，而不是 per-fixture prompt

Canonical prompt 的目标是审计语言、组件、builtins、rules 和 examples 的 contract。fixture-specific data 已经在 report entry 中记录，因此不需要为每个 fixture 复制一份大 prompt。

替代方案：保存每个 fixture 的完整 system prompt。拒绝原因是数据重复严重，并且会把注意力从 prompt contract 拉回 fixture data；当前问题主要是 contract 认知漂移，而不是丢失 fixture data。

### D2: data model section 使用明确占位符

写入的 canonical prompt 仍通过 `dslLibrary.prompt()` 生成，但传入固定占位 data model，例如：

```json
{
  "__EVAL_DATA_MODEL_PLACEHOLDER__": "Fixture data is recorded in report-data.json entries[].dataModel"
}
```

这样生成物保留 `## Data Model` section 的位置和格式，同时避免把任意一个 fixture 的数据误认为 run 的通用 prompt。

替代方案：调用 `dslLibrary.prompt({ strictness })` 不传 data model。拒绝原因是实际 regen prompt 会包含 `## Data Model` section；完全省略该 section 会让 canonical prompt 与实际 prompt 结构不一致。

### D3: prompt artifact 在 run 创建时生成，并在 report metadata 中引用

`pnpm eval start` 创建 run workspace 后生成 `system-prompt.txt`，计算 sha256 hash，并写入 manifest/report metadata。regen 失败时，该 prompt 仍应保留，方便调查失败前的 prompt contract。

替代方案：仅在 regen phase 成功后生成。拒绝原因是 prompt 本身是 regen 输入证据，失败时更需要保留。

### D4: prompt hash 使用完整文件内容计算

Hash 直接来自 `system-prompt.txt` 的 UTF-8 内容。这样 strictness、rules、examples、component signatures 或 builtins 的任意变化都会改变 hash。

替代方案：分别 hash component/builtin/rules sections。暂不采用，因为本 change 只需要稳定引用完整 prompt；section-level diff 可以作为后续能力扩展。

## Risks / Trade-offs

- [Canonical prompt 与真实 fixture prompt 的 data section 不完全相同] → 使用明确占位符，并在文案中说明真实 fixture data 位于 report entry。
- [后续新增 prompt options 时忘记同步 canonical generator] → 将 canonical prompt helper 复用实际 `dslLibrary.prompt()`，并测试 strictness 会改变 hash/内容。
- [report metadata 和 manifest 字段重复] → manifest 作为 run 恢复/status 的源，report metadata 作为查看报告时的源，二者记录同一 path/hash。
- [Windows path 在 report 中不可移植] → report 记录 run-relative path，status 可显示绝对路径。

## Migration Plan

1. 增加 prompt artifact helper，生成占位 data model 的 canonical system prompt 并计算 hash。
2. 在 eval run workspace 创建后写入 `system-prompt.txt`。
3. 将 prompt artifact metadata 写入 run manifest 和 report data。
4. 更新 `pnpm eval status <run-id>` 输出 prompt path/hash。
5. 增加 unit tests 覆盖 prompt 写入、hash、manifest/report metadata。

Rollback：移除 manifest/report 中新增字段并停止写入 `system-prompt.txt`。已生成的旧 run artifact 可以保留，不影响 eval 执行。

## Open Questions

- report app 是否需要在首版直接提供 clickable prompt link，还是先显示 relative path/hash 即可？
