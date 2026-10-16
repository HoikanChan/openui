# builtin 契约化：技术设计

## Context

当前双轨：base-contract.json 的 builtins 数组只有 signature 自由文本（`Count(array) -> number`），仅喂提示词（PromptAssembler）；校验链走 Java 硬编码——Builtins.java 的 20+2 函数名词表（Javadoc 自认 known-stale）+ BuiltinTypeRegistry 的 22 组 arity/类型规则 + 逐函数 Rule 方法。两边已漂移：契约缺 FormatNumber/FormatPercent（valid 语料 ~40 处调用），契约多的 6 条（Flatten/ObjectValues/Number/MergeObject/Join/String）代码不校验。

约束：
- lang-core（TS）是上游同步包，不动
- repair-bench 案例契约（repair-cases/*/contract.json）不含 builtins 键，且刻意不做 SDK 默认契约兜底
- interception-corpus 的 manifest 期望码（expectedCodes）是行为锚点，要求零漂移
- Spring Boot 2.7 / javax 栈，fastjson2 解析

## Goals / Non-Goals

**Goals:**
- 契约 builtins 条目结构化承载输入输出：name、params（类型+修饰）、returnType
- 校验词表与类型推断全部从 SDK 默认 base-contract.json 资源派生
- 行为对齐：corpus 期望码零漂移（含新增 7 条校验的边界由"粗类型不误报"性质保证）

**Non-Goals:**
- enum 取值校验（`"asc"|"desc"`）——v1 不做，留 description 文本
- TS 侧（lang-core）改造
- 代码↔契约对账/防漂移 CI——靠 contractVersion 版本号
- binder 作用域求值的契约化——Each/Render 特例留 Java

## Decisions

### D1: type 字段用 TS 风格类型串，不用 JSON 组合子

```
T := "any" | "number" | "string" | "boolean" | "null" | "object" | "array"
   | T "[]"                        // {"array": "string"} 的串形式：string[]
   | "Record<string, T>"
   | "{ 字段: T, ... }"            // 对象字面量
   | T "|" T ("|" T)*              // union，多类型出入参
   | "T"                           // 泛型变量，绑定于参数位置
```

params 条目：`{name, type, optional?, variadic?, binder?}`。arity 从 params 推导：min = 非 optional 数，max = 加 optional（variadic 不封顶）。

理由：契约是手工维护的 git 资产（生成脚本已删），可读可手改是硬需求；signature 本来就是 TS 味（`string | string[]`），视觉统一。备选 JSON 组合子（`{"array":{"object":...}}`）表达能力相同但没人愿意维护。风险——字符串解析器成为新依赖——用封闭文法 + 加载期全量 fail-fast 解析消除（坏语法/未绑定 T/未知类型 → 启动失败，错误信息带条目名与原始串）。

### D2: 泛型 T 统一 same/element/fields 三个组合子

`T` 出现在参数类型里，匹配实参时解出绑定，returnType 里的 `T` 代入求值：
- `First(array: T[])` → T 解为数组元素类型 → returnType `T` 即元素类型
- `ObjectEntries(obj: Record<string, T>)` → T 解为 object 各字段类型的并集 → returnType `{key: string, value: T}[]`
- `Sort(array: T[], ...)` → returnType `T[]` 即与入参同型

本仓库 29 条里 T 只出现在单一参数位置，求值器只需"一次结构匹配"，不用写通用约束求解。

### D3: 29 条全量映射口径

| 函数 | params | returnType | 备注 |
|---|---|---|---|
| Count | array | number | |
| First/Last | T[] | T | |
| Sum/Avg/Min/Max | number[] | number | |
| Sort | T[], string, string? | T[] | |
| Filter | T[], string, string, any | T[] | |
| Flatten | array[] | array | 新增校验 |
| ObjectEntries | Record<string, T> | {key: string, value: T}[] | 精度保留 |
| ObjectKeys | Record<string, any> | string[] | |
| ObjectValues | Record<string, any> | any[] | 新增校验 |
| Round | number, number? | number | |
| Abs/Floor/Ceil | number | number | |
| Number | any | number | 新增校验 |
| FormatBytes | any | string | |
| FormatNumber/Percent | any, number? | string | 新条目 |
| FormatDuration | any, string? | string | |
| MergeObject | object, object, ...object（variadic） | object | 新增校验 |
| Join | array, string? | string | 新增校验 |
| String | any | string | 新增校验 |
| Switch | any, Record<string, T>, any? | T \| param(2) | default 越界按 null |
| FormatDate | any, string? | string \| string[] | |
| Each | T[], binder(elementOf), any | —（Java 兜底） | 特例 |
| Render | binder, binder?, any | —（Java 兜底） | 特例 |

Each/Render 的 params/arity/binder 标记仍走契约（`binder: true`），仅 returnType 推断留 Java——binder 作用域求值（varName 引入嵌套 scope、Render 多 binder）是语言级能力，类型语法表达不了。

### D4: 求值器语义约定（对齐现状，防快照漂移）

1. **param 越界按 null**：Switch 无 default 分支时 `default`（param 2）按 null 类型并入 union，与 Java 现行为逐字节一致
2. **空对象字段并集按 null**：ObjectEntries 传空 object → `{key, value: null}[]`
3. **union 全有或全无**：某分支推断不出来 → 整个 union 返回"无类型"（不报下游错），不能改成"能推多少并多少"——partial 信息会触发 Java 现在不报的下游 mismatch
4. **错误消息对齐**：mismatch 提示里的期望类型串（"array"、"number[]"）直接用契约里的 type 原文渲染
5. **幻觉语义**：契约里没有的函数名 → unknown（Clamp/Mul/Div 等 bad 语料案例继续命中）

### D5: 词表注入与加载链

- Builtins.java 缩为语法保留字（Action 步骤名、Query/Mutation）——它们是 DSL 语句语法，不是函数库
- 校验词表（DATA/LAZY 分类）恒从 SDK 默认 base-contract.json 资源加载（GenerationContractLoader.loadDefault 的 builtins），与案例契约无关——repair-bench 案例契约不含 builtins 键也不受影响，其"冻结生成上下文"语义不破坏
- parser/analyzer 从静态调用改为构造时注入词表对象
- 提示词路径（PromptAssembler）不动：继续用合并契约的 builtins（signature/description/templateBuiltin 照旧渲染）

### D6: signature 降级、templateBuiltin 原样

signature 保留为纯展示字段——`"asc"|"desc"` 取值说明、自然语言返回、Render 多变体写法这些信息量 params 推不出，删了提示词变样影响生成质量。templateBuiltin 是提示词分节标记（template 节 vs data 节，true 的 4 条：Switch/MergeObject/Each/Render），校验不消费，原样不动。binder 语义（校验侧）由 params 的 `binder: true` 承载，与 templateBuiltin 是两个概念。

## Risks / Trade-offs

- [求值器成为唯一关键路径，bug 打击全部 29 条] → 封闭文法的解析器单测全覆盖 + corpus 全量回归钉住；Each/Render 兜底方法独立，风险隔离
- [7 条新校验（Flatten/ObjectValues/Number/MergeObject/Join/String/FormatPercent）可能让旧 valid 语料出新错] → 语料核查已确认这 7 条在 valid 语料零调用，坏输入只出现在 bad 语料（预期报错方向一致）；万一 corpus 打脸，按"该条粗化"处理而不是回滚架构
- [契约手改门槛升高（写错 T 下标之类）] → 加载期校验兜底：封闭 key 集、T 绑定检查、name 重复、optional 排序，启动即失败
- [master 侧 base-contract.json 已损坏（历史合并事故）] → 本变更基于 validation-iteration 分支的契约文件，合回时以本分支为准

## Migration Plan

一次性切换，无双读期（消费方全部在自己手里，双读 = 第二个来源，拖久必漂移）。顺序 = GitHub issues 依赖链：#49 契约回填 → #50 解析器 → #51 换源接线 → #52 清场 + ADR。回滚 = revert 整条链（四张票一组原子回滚）。

## Open Questions

无——设计树已在评审会话中走空（Q1-Q15 全部落定）。
