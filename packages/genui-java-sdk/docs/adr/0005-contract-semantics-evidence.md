# 0005 — 契约语义依据与迁移验收

## Status

Accepted for #79 evidence and acceptance scope (2026-10-22); the migration target in item 4 was completed by #90 (2026-10-23). Concrete rules marked unresolved remain undecided; this record does not accept an implementation of #77.

**Item 4 is superseded by [ADR 0006](0006-concise-builtin-signature-contract.md) (2026-10-31).** The `typeRule` single-evaluation-target design and contract-carried binder layout were replaced by concise signatures plus `BuiltinAnalysis`. **Item 6 is likewise superseded by ADR 0006**: "authoritative export must preserve the new structural rules and version" no longer applies, because the exported shape is the concise signature (name/signature/description/templateBuiltin/params/returnType) rather than a versioned rule language. The semantic evidence matrix below remains the behavioural record.

## Context

默认 Base Contract 已定义 29 个 builtin 的 params、返回声明与文字行为。lang-core 可以落后（ADR 0003 D4），不能以它作为迁移的唯一语义依据。用户本次明确要求以 base-contract 接口定义为准，无需关注 TS 实现。若用某个本地求值器填补契约空白，后续类型运算可能对错误的运行语义作出证明。

同时，description 中的异常输入容错与 params 中的生成接受范围不同。MergeObject 的“跳过非对象”不能自动把两个必需 object 参数改成任意零参调用。Each 的 inline-only 文本则与已接受的 ADR 0006 及公开 validator 测试冲突。

## Decision

1. 生效契约是本次语义与验收依据。记录结构化输入政策、文字容错和返回保证时分别注明，不以运行时容错自动放宽生成政策。遇到内部矛盾，明确记录受影响的精确规则，不从 TS、Java 当前实现或其他语言猜值。
2. 保留 ADR 0006 的独立 Each 模板、传递依赖与 binder 环境。默认契约 inline-only 描述应由后续权威导出修正；本次不通过改生成资源伪装已完成同步。
3. #77 的目标为有限结构类型和事实证据组成的一个深 Module。测试通过公开 validator 和确定性的生成校验 Interface；不暴露内部快照构造器给测试。局部类型运算性质测试只采用已确认的参考语义。
4. #77 契约迁移以版本化结构规则取代 ADR 0003 D3 的 signature 解析和按 builtin 名称处理 binder 的方案。名称、调用形式、binder 布局和结果规则来自同一生效契约；Java 按有限运算种类执行。**#90 已完成该迁移**：生产类型分析只有一条路径（`type/analysis` 内核 + `contract.RuleEvaluator`），`typeRule` 是唯一求值规则；ADR 0007 描述的 `returnType`/`params` 求值器、`ValueType`/`ValueTypes` 类型代数与 `TypeTranslator` 桥接已删除，Java 侧不再持有 builtin 类型词表。Each/Render/Switch 仍是语言模板构造，其 binder 布局从契约读取，语义留在 Java 侧——这不是第二套词汇表。ADR 0007 已同步更新为终态。
5. 新引擎不沿用 ADR 0007 的“联合有未知则全无类型”和“空对象值集合按 null”作为目标语义。未知字段保留、空对象产生空 Keys/Values/Entries、空数组与未知数组分开；未证明不冒充通过或错误。First/Last 空结果等未定义值另列，不由此条补出。
6. 权威导出必须保留新结构规则及版本，落入同一份前端/SDK 契约；历史只有展示 manifest 的脚本不能直接作为新结构的发布验收。未知规则加载失败，不忽略、不回退成 any。具体 schema 和实现由后续契约任务交付。

## Consequences

可核实部分立即形成 [29 项语义矩阵](../runtime-semantics-contract.md)和[验收输入](../examples/contract-migration-acceptance.json)。未定义的 First/Last 空数组结果、聚合精确空值、FormatNumber 无效输入种类、Flatten 混合元素和缺失/null 投影，作为 UNPROVEN 精度缺口保留，不猜值、不计完整 MATCH、不单独触发修复；独立的已知参数违规仍正常报告。不因为没有 TS 实现停止整体迁移工作。TS 接口源和结构规则导出跟踪 [#91](https://github.com/HoikanChan/openui/issues/91)，capabilityGaps 导出复用 [#73](https://github.com/HoikanChan/openui/issues/73)；Java 消费及往返验收仍由 #88 负责。

没有新增 builtin、修改生产运行时或批量改默认/历史契约。对维护中的旧规则和 fixture 的实际替换由后续实施负责。本 ADR 不覆盖 ADR 0003 D1/D2/D4 的生效词表、诊断名称和允许 lang-core 落后的决定。

## Alternatives

直接把运行时容错当成允许生成的输入，会悄悄扩大接口；直接采用本地 TS 结果，会把落后实现当权威；为消除冲突立即重写默认资源，会超出 #79 的文档/样例范围。选择逐项证据和明确未决规则，使后续实现可以审查每个承诺而不混入这些隐式决策。
