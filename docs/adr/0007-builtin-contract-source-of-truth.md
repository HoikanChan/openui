---
status: accepted
---

# Builtin 函数契约化：base-contract.json 是唯一真实来源

GenUI Java SDK 的 builtin 函数输入输出规则曾双轨并行：base-contract.json 的 builtins 数组只有 signature 自由文本（仅喂提示词），校验链走 Java 硬编码——Builtins.java 的 20+2 函数名词表 + BuiltinTypeRegistry 的 22 组 arity/类型规则。两边已经漂移（契约缺 FormatNumber/FormatPercent，代码侧自认 known-stale）。本决定把全部结构化规则搬进契约，校验链从契约派生，函数的"是什么、怎么用"只剩一个可信来源。

**终态（#90，2026-10-23）**：本决定经历两段实现。第一段把 `params`/`returnType` 从契约读入，但仍用 Java 的类型串解析器和泛型组合子求值；第二段（#77/#80/#81/#85/#86/#88 到 #90）用版本化结构规则 `typeRule` 取代了该求值器。现在生产类型分析只有一条路径：`TypeAnalysis` 内核 + `contract.RuleEvaluator`，`typeRule` 是唯一求值规则。旧的 `ValueType`/`ValueTypes` 类型代数、`TypeTranslator` 桥接、`returnType`+`params` 求值代码（含 `genericResult`/`collectLowerBounds` 及旧求值器上的 `instantiate`）已删除；现存同名方法 `RuleEvaluator.instantiate` 是契约规则代入，不是旧路径。`returnType` 仅作为展示字段保留，不参与结果推断（加载期仍会解析其语法并校验类型变量绑定与 `param(n)` 下标）；`params` 保留 binder/variadic 布局，以及模板构造（Each/Render/Switch）非 binder 参数检查所用的 `TypeExpr`——两者都不再参与 builtin 结果推断。加载期**没有** `params` 与 `typeRule.arity` 的自动交叉核对，默认 29 条的一致性靠人工核对与测试验证。下文保留第一段的历史说明，并在“求值器语义约定”一节改写为终态。

## 契约 builtins 结构

每条 builtin 条目：`name`（查找键）、`signature`（纯展示，只喂提示词）、`description`、`templateBuiltin`（提示词分节标记，校验不消费）、`params`（`{name, type, optional?, variadic?, binder?}`）、`returnType`（展示字段，加载期仅解析语法并校验类型变量绑定与 `param(n)` 下标）、`typeRule`（唯一求值规则，版本 1）。有 `typeRule` 时 arity 以 `typeRule.arity` 为准；模板构造（Each/Render/Switch）没有 `typeRule`，arity 仍从 `params`/`callForms` 派生。加载器只编译规则，不比较 `typeRule.arity` 与 `params` 推导结果。

## 默认契约只维护一份

生产、修复语料和日常测试共用 SDK resource `packages/genui-java-sdk/src/main/resources/openui/base-contract.json`。删除 `repair-cases/contract.json` 和未被引用的 eval-cli `sample-base-contract.json`，不再通过复制契约并测试两份相等来保持同步。

外部案例包仍可显式指定自定义契约；测试特殊语义时使用最小测试契约或在默认契约上声明差异。历史评测用 Git commit、契约版本和 SHA-256 固定依据，通过 Git 恢复历史版本，不另维护默认契约副本。

## typeRule 结构规则（终态）

`typeRule` 是封闭文法：`typeRulesVersion`、`arity`、`parameters[{name, rest, accept}]`、`result`。`accept` 与 `result` 只能使用有限模式（`unknown`/`any`/scalar/`object`/`array`/`sequence`/`recordOf`/`variable`）和有限运算（`SEQUENCE_PRESERVE`/`SUBSET`/`ELEMENT`/`FLATTEN_ONE`/`SELECT`、`RECORD_OVERLAY`/`KEYS`/`VALUES`/`ENTRIES`、`SHAPE_MAP_SCALAR_OR_ARRAY`）。`RuleCompiler` 在加载期拒绝未知 op/tag、越界实参、非法 arity、非末位 `rest`；未知规则加载失败，不静默回退成 `any`。**未绑定的结果变量不在加载期拒绝**：这类规则编译通过，调用时该结果返回 `Unknown` 并记 `UNBOUND_TYPE_VARIABLE`（见下节）。

### 无 `typeRule` 的遗留 custom 契约

非模板 builtin 若只有 `params`/`returnType` 而没有 `typeRule`，结果记为 `UNPROVEN`，原因为 `UNSUPPORTED_RULE`——不再按 `returnType` 推断，也不再据此报类型 mismatch。这类条目当前是**不受支持**的：迁移旧契约时必须补 `typeRule` 才能获得等价的参数与结果检查。`params`/`returnType` 单独不再推出任何结果类型。

## 泛型求解（终态）

类型变量只按单次调用求解：每个变量每出现一次收集一条下界，重复出现取并集；未知输入（`Unknown` 类型或带精度损失的事实）并入 `Unknown` 下界，已知实参不能把未知输入收窄成虚假的具体类型。未绑定的结果变量返回 `Unknown` 并记 `UNBOUND_TYPE_VARIABLE`，对覆盖统计可见，不伪装成精确类型。

## 求值器语义约定（终态）

1. **未知不冒充确定**：`Unknown`/精度损失参与结果时，结果为 `Unknown` 并记 loss；不产生虚假 mismatch，覆盖报告记 UNPROVEN。
2. **空集合与未知集合分开**：空对象产生空 Keys/Values/Entries；空数组不证明元素类型，也不作为 `object` 参数通过的理由。
3. **联合保留分支信息**：不再“全有或全无”——逐分支求值，未知分支并入 Union，不丢掉已知结构。
4. **错误消息由规则统一渲染**：`RuleEvaluator.displayPattern` 渲染期望模式；调用方不重复实现第二份文本。
5. **幻觉语义**：契约里没有的函数名即 unknown（不设代码↔契约对账机制）。
6. **覆盖可审计**：每条参数模式检查都登记 MATCH/MISMATCH/UNPROVEN；只有 MISMATCH 且无上游错误时上报诊断。

## Each/Render 特例（点名）

binder 作用域求值（varName 引入嵌套 scope、Render 多 binder、TemplateType 返回）是语言模板语义，留在 Java 侧；其 binder 布局（`callForms`/`binders`/`body`/`input`/`nameSyntax`）从契约读取，`typeRule` 之外没有第二份 builtin 类型词表。Switch 同理：调用形式与参数检查来自契约，分支求值是模板语义。惰性（lazy）判定从 `params` 的 binder 标记派生；arity 以 `typeRule.arity` 为准。

Action 步骤名（Run/ToAssistant/OpenUrl/Set/Reset/Action）与 Query/Mutation 是 DSL 语句语法保留字，留在代码侧（Builtins.java），不进契约。

## 防漂移哲学

契约里没有的函数就是幻觉。不设代码↔契约对账 CI——词表、类型规则、arity 全部从契约单一来源派生后，没有第二份东西可漂移；契约演进靠 contractVersion 版本号控制（本变更 bump 至 1.1.6）。signature 降级为纯展示字段：`"asc"|"desc"` 取值说明、自然语言返回、多变体写法这些信息量 params 推不出，删了会改变提示词。

## 已接受的语义偏移

迁移使 corpus 期望码发生一处偏移：`String(...)` 裸名（无 @ 前缀）原报 unknown-component，现报 unresolved-ref——String 是契约 builtin（genui 扩展函数集），校验词表从契约派生后裸名解析为标识符引用。拦截方向不变（仍 INVALID），根因码更精准。manifest 期望已随迁移更新。
