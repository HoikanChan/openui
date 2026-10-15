## ADDED Requirements

### Requirement: builtin 契约条目 SHALL 结构化承载 name、params 与 returnType
base-contract.json 的 builtins 数组中，每一条目 SHALL 包含 `name`（查找键）、`params`（参数列表）与 `returnType`（返回类型表达式）。`signature` 字段 SHALL 保留为纯展示字段，仅用于提示词渲染，MUST NOT 承载任何校验规则。`description` 与 `templateBuiltin` 字段语义保持不变（提示词分节标记，校验不消费）。

#### Scenario: 契约条目完整可加载
- **WHEN** SDK 从默认资源加载 base-contract.json
- **THEN** 全部 builtin 条目均带 name/params/returnType，可反序列化为契约模型
- **AND** name 可直接用作函数查找键，无需从 signature 前缀解析

#### Scenario: signature 仅用于提示词
- **WHEN** PromptAssembler 渲染 builtin 文档节
- **THEN** 渲染继续使用 signature/description/templateBuiltin 原文
- **AND** 校验链不读取 signature

### Requirement: 类型表达式 SHALL 使用封闭文法的 TS 风格类型串
`params[].type` 与 `returnType` SHALL 使用 TS 风格类型串，文法封闭为：基础 token（`any`、`number`、`string`、`boolean`、`null`、`object`、`array`）、数组后缀 `T[]`（可嵌套）、`Record<string, T>`、对象字面量 `{字段: T, ...}`、union `A | B`、泛型变量 `T`。文法之外的构造 MUST NOT 出现。

#### Scenario: 合法类型串解析成功
- **WHEN** 契约加载期解析 `T[]`、`Record<string, T>`、`{key: string, value: T}[]`、`string | string[]`、`number[]`
- **THEN** 解析为对应的 Java 类型对象，语义与文法定义一致

#### Scenario: 非法类型串加载即失败
- **WHEN** 契约中出现未知 token、括号不配对、或 returnType 引用了无法从 params 解出的 `T`
- **THEN** 加载期抛出异常（启动失败），错误信息包含条目 name 与原始类型串

### Requirement: 参数修饰 SHALL 表达 optional、variadic 与 binder 语义
params 条目 SHALL 支持 `optional: true`（可省略）、`variadic: true`（不定长，必须在末位）、`binder: true`（须为字符串字面量，绑定名引入后续参数的作用域）。arity MUST 从 params 机械推导：min = 非 optional 参数数，max = 加 optional 数，variadic 不封顶。optional 参数 MUST NOT 排在必填参数之前。

#### Scenario: arity 从契约推导
- **WHEN** 契约条目声明 params 为 `[T[], string, string?]`
- **THEN** 推导 arity 为 min=2、max=3
- **AND** 实参数少于 2 或多于 3 时报 argument-count-mismatch

#### Scenario: 结构错误加载即失败
- **WHEN** 契约中 name 重复、optional 参数排在必填前、或 variadic 参数不在末位
- **THEN** 加载期抛出异常

### Requirement: 泛型 T SHALL 统一承载同型传递、元素提取与字段并集语义
泛型变量 `T` SHALL 出现在参数类型中，求值时与实参类型做一次结构匹配解出绑定；returnType 中的 `T` SHALL 代入求值。`First(array: T[])` 的 returnType `T` 即元素类型；`ObjectEntries(obj: Record<string, T>)` 中 `T` 解为 object 各字段类型的并集；`Sort(array: T[])` 的 returnType `T[]` 即与入参同型。

#### Scenario: 同型传递
- **WHEN** 校验 `@Sort(data.items, "name")` 且 data.items 类型为 `Array<T0>`
- **THEN** 推断返回类型为 `Array<T0>`，下游按该类型继续校验

#### Scenario: 字段并集
- **WHEN** 校验 `@ObjectEntries(data.devicesById)` 且实参为字段类型不同的 ObjectType
- **THEN** `T` 解为各字段类型的并集，返回类型为 `{key: string, value: <并集>}[]`

### Requirement: 契约 MUST 补录 FormatNumber 与 FormatPercent 条目
base-contract.json 的 builtins 数组 SHALL 包含 `FormatNumber(value, decimals?)` 与 `FormatPercent(value, decimals?)` 两条条目（当前代码词表有而契约缺）。contractVersion SHALL bump 至 1.1.6。

#### Scenario: 补录后词表对齐
- **WHEN** 校验链从契约派生词表
- **THEN** `FormatNumber`、`FormatPercent` 与其余 27 条一起在列
- **AND** 使用这两个函数的 valid 语料不再报 unknown
