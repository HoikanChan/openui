## 1. 契约回填（GitHub #49）

- [x] 1.1 base-contract.json 29 条 builtins 全量补 name/params/returnType（TS 风格类型串，口径见 design.md D3 映射表），补录 FormatNumber/FormatPercent 两条新条目，contractVersion bump 1.1.6
- [x] 1.2 BuiltinSpec 扩展为 name/params/returnType 结构（fastjson2 反序列化），signature 保留为展示字段
- [x] 1.3 GenerationContractLoader 加载期结构校验：name 重复、optional 排必填前、variadic 不在末位、类型串全量解析（坏语法/未绑定 T/未知类型 → 抛异常，错误信息带条目名与原文）
- [x] 1.4 扩展 BaseContractFixtureTest：29 条全量加载通过、arity 推导与既有 22 组硬编码一致、每类结构错误至少一个用例

## 2. 类型表达式解析器（GitHub #50）

- [x] 2.1 实现 TS 风格类型串解析器（词法 + 递归下降：7 基础 token、`T[]` 嵌套、`Record<string, T>`、对象字面量、union、泛型 T）
- [x] 2.2 解析结果实现 record 语义等值比较（供求值器做类型对照）
- [x] 2.3 解析器单测：每条文法构造至少一合法一非法用例；错误信息带条目名与原始串

## 3. 校验链换源（GitHub #51）

- [x] 3.1 通用求值器：按序推断实参类型 → 按类型串检查 → 解 T 绑定 → 代入 returnType；语义对齐 design.md D4（param 越界按 null、空对象并集按 null、union 全有或全无、错误消息用契约 type 原文）
- [x] 3.2 Each/Render 兜底方法：binder 作用域推断（Each 嵌套 scope、Render 多 binder、TemplateType 返回），arity/binder 标记从契约读
- [x] 3.3 词表注入改造：Builtins.java 缩为语法保留字（Action 步骤名、Query/Mutation）；DATA/LAZY 词表从默认契约 builtins 派生；parser/analyzer/ExpressionParser/OpenuiParser/ProgramAnalyzer 构造注入
- [x] 3.4 ProgramAnalyzer 的 lazyBinderIndexes 改从 params 的 binder 标记派生
- [x] 3.5 删除 BuiltinTypeRegistry 逐函数规则方法与 Builtins.java 硬编码函数名集合
- [x] 3.6 迁移 BuiltinTypeRegistryTest 等既有单测到契约派生实现
- [x] 3.7 interception-corpus 全量回归零漂移（expectedCodes 逐一比对）；修复中发现的偏差按 D4 语义约定对齐，不回滚架构

## 4. 清场 + ADR（GitHub #52）

- [x] 4.1 ADR 落盘（沿用 docs/adr 编号）：契约 builtins 结构、TS 类型串文法、Each/Render 特例点名、防漂移哲学（契约外即幻觉、版本号控制）、signature 降级定位
- [x] 4.2 全库搜索确认无残留死代码（旧词表、逐函数规则、死 import）
- [x] 4.3 mvn test 全绿
