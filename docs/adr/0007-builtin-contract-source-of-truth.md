---
status: accepted
---

# Builtin 函数契约化：base-contract.json 是唯一真实来源

GenUI Java SDK 的 builtin 函数输入输出规则曾双轨并行：base-contract.json 的 builtins 数组只有 signature 自由文本（仅喂提示词），校验链走 Java 硬编码——Builtins.java 的 20+2 函数名词表 + BuiltinTypeRegistry 的 22 组 arity/类型规则。两边已经漂移（契约缺 FormatNumber/FormatPercent，代码侧自认 known-stale）。本决定把全部结构化规则搬进契约，校验链从契约派生，函数的"是什么、怎么用"只剩一个可信来源。

## 契约 builtins 结构

每条 builtin 条目：`name`（查找键）、`signature`（纯展示，只喂提示词）、`description`、`templateBuiltin`（提示词分节标记，校验不消费）、`params`（`{name, type, optional?, variadic?, binder?}`）、`returnType`。arity 从 params 机械推导：min = 非 optional 数（variadic 计 0），max = 参数总数（variadic 不封顶）。

## TS 风格类型串文法（封闭）

```
expr    := term ("|" term)*
term    := factor ("[]")*
factor  := base | "Record<string, expr>" | "{" fields "}" | "T" | "param(n)"
base    := any | number | string | boolean | null | object | array
fields  := (ident ":" expr ("," ident ":" expr)*)? ","?
```

加载期全量 fail-fast 解析：坏语法、未绑定 T（returnType 引用 T 而 params 无 T）、param(n) 越界、name 重复、optional 排必填前（binder 参数豁免——Render 的 2/3 参双形式）、variadic 不在末位 → 启动失败，错误信息带条目名与原文。

## 泛型 T 的三个组合子

`T` 只出现在单一参数位置，一次结构匹配即解出绑定：`First(array: T[])` 的 T 解为数组元素类型；`ObjectEntries(obj: Record<string, T>)` 的 T 解为字段类型并集（空对象按 null）；`Sort(array: T[])` 的 returnType `T[]` 即与入参同型。`param(n)` 引用第 n 实参类型（越界按 null——Switch 无 default 分支时的现状语义）。

## 求值器语义约定（防快照漂移）

1. **param 越界按 null**：Switch 无 default 时并入 null 类型
2. **空对象字段并集按 null**
3. **union 全有或全无**：某分支推断不出来 → 整个 union 无类型，不部分并入（partial 信息会触发现在不报的下游 mismatch）
4. **错误消息用契约原文**：期望类型串直接用契约条目里的 type 原文渲染
5. **幻觉语义**：契约里没有的函数名即 unknown（不设代码↔契约对账机制）

## Each/Render 特例（点名）

binder 作用域求值（varName 引入嵌套 scope、Render 多 binder、TemplateType 返回）超出类型语法表达能力，returnType 推断保留 Java 兜底方法；二者的 arity 与 `binder: true` 标记仍从契约读，ProgramAnalyzer 的 binder 索引从契约 params 派生。惰性（lazy）判定同样从契约派生——params 带 binder 标记即 lazy。

Action 步骤名（Run/ToAssistant/OpenUrl/Set/Reset/Action）与 Query/Mutation 是 DSL 语句语法保留字，留在代码侧（Builtins.java），不进契约。

## 防漂移哲学

契约里没有的函数就是幻觉。不设代码↔契约对账 CI——词表、类型规则、arity 全部从契约单一来源派生后，没有第二份东西可漂移；契约演进靠 contractVersion 版本号控制（本变更 bump 至 1.1.6）。signature 降级为纯展示字段：`"asc"|"desc"` 取值说明、自然语言返回、多变体写法这些信息量 params 推不出，删了会改变提示词。

## 已接受的语义偏移

迁移使 corpus 期望码发生一处偏移：`String(...)` 裸名（无 @ 前缀）原报 unknown-component，现报 unresolved-ref——String 是契约 builtin（genui 扩展函数集），校验词表从契约派生后裸名解析为标识符引用。拦截方向不变（仍 INVALID），根因码更精准。manifest 期望已随迁移更新。
