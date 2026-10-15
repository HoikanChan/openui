## ADDED Requirements

### Requirement: 校验词表 SHALL 恒从 SDK 默认契约派生
校验链的 builtin 函数词表 SHALL 从 SDK 默认 base-contract.json 资源加载的 builtins 数组派生，MUST NOT 存在第二份硬编码函数名集合。案例契约（如 repair-bench 案例包）不掺和校验词表。Action 步骤名（Run/ToAssistant/OpenUrl/Set/Reset/Action）与 Query/Mutation 属于 DSL 语法保留字，SHALL 留在代码侧，不进契约。

#### Scenario: 词表随契约走
- **WHEN** SDK 从默认资源加载契约
- **THEN** 校验词表等于契约 builtins 数组的 name 集合
- **AND** 代码中不存在与契约平行的函数名硬编码集合

#### Scenario: 案例契约不影响词表
- **WHEN** repair-bench 加载不含 builtins 键的案例契约
- **THEN** 校验词表仍为 SDK 默认契约的完整 builtin 集合
- **AND** `@Count(...)` 等调用不报 unknown

#### Scenario: 语法保留字不进契约
- **WHEN** 契约 builtins 数组被加载
- **THEN** Action 步骤名与 Query/Mutation 不在其中，其识别逻辑留在代码侧

### Requirement: 契约外函数名 SHALL 按幻觉处理
函数名不在契约 builtins 中时，校验 SHALL 按 unknown/幻觉处理。不设代码↔契约对账机制，漂移防线仅为 contractVersion 版本号控制。

#### Scenario: 发明函数继续报 unknown
- **WHEN** 校验 `@Clamp(...)`、`@Mul(...)`、`@Div(...)`（均不在契约中）
- **THEN** 报 unknown，与迁移前行为一致

### Requirement: 类型推断 SHALL 由通用求值器按契约执行
builtin 调用的参数类型检查与返回类型推断 SHALL 由通用求值器执行：按序推断各实参类型 → 按参数类型串检查 → 解出 T 绑定 → 代入 returnType 求值。求值器语义 MUST 对齐既有行为：param 越界按 null 类型处理；空对象的字段并集按 null；union 分支推断不出来时整个 union 返回无类型（全有或全无），MUST NOT 部分并入。类型不匹配的错误提示 SHALL 使用契约 type 原文渲染期望类型。

#### Scenario: param 越界按 null
- **WHEN** 校验无 default 参数的 `@Switch(value, cases)` 调用
- **THEN** 返回类型为 cases 值类型与 null 的并集，与既有行为一致

#### Scenario: union 全有或全无
- **WHEN** union 某分支的实参类型推断不出来
- **THEN** 整个 returnType 判为无类型，不触发下游 mismatch 报错

#### Scenario: 错误消息用契约原文
- **WHEN** 实参类型不匹配参数声明
- **THEN** 错误提示中的期望类型为契约条目里的 type 原文（如 `number[]`）

### Requirement: Each 与 Render 的 returnType 推断 SHALL 由代码侧兜底方法承载
Each/Render 涉及 binder 作用域求值（绑定名引入嵌套作用域、Render 多 binder），超出类型表达式文法的表达能力，其 returnType 推断 SHALL 保留代码侧兜底方法。二者的 params、arity 与 `binder: true` 标记 SHALL 仍从契约读取；ProgramAnalyzer 的 binder 索引 SHALL 从契约 params 的 binder 标记派生。此特例 MUST 在 ADR 中文档点名。

#### Scenario: Each 的作用域推断
- **WHEN** 校验 `@Each(data.items, "item", @Concat(item.name))` 且 item.name 为 string
- **THEN** binder `item` 在模板内按元素类型参与推断
- **AND** arity 与 binder 位置来自契约条目

#### Scenario: Render 多 binder
- **WHEN** 校验 `@Render("v", "row", @Concat(v, row.id))`
- **THEN** 两个 binder 均在模板作用域内，返回 TemplateType

### Requirement: 既有校验行为 SHALL 零漂移
迁移后 interception-corpus 全量测试（含 bad 语料的 expectedCodes）SHALL 与迁移前一致。新增参与校验的 7 条（Flatten/ObjectValues/Number/MergeObject/Join/String/FormatPercent）MUST NOT 使既有 valid 语料出新错。

#### Scenario: corpus 期望码全绿
- **WHEN** 运行 interception-corpus 全量测试
- **THEN** 每个案例的 actualCodes 与 manifest 的 expectedCodes 完全一致

#### Scenario: 新校验只影响新调用面
- **WHEN** valid 语料中出现契约新覆盖的 builtin 调用
- **THEN** 类型匹配时不报错（粗类型不误报）
- **AND** 坏输入的报错方向与 bad 语料期望一致
