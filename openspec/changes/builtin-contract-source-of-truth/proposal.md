# builtin 契约化：函数唯一真实来源迁移到 base-contract

## Why

builtin 函数的输入输出信息目前硬编码在 Java 侧（Builtins.java 词表 + BuiltinTypeRegistry 22 组逐函数规则），与 base-contract.json 的 builtins 数组（只有 signature 自由文本）双轨并行。契约里缺 FormatNumber/FormatPercent 两条，代码词表自认 known-stale，两边已经漂移。要让"函数是什么、怎么用"只有一个可信来源，必须把结构化的输入输出规则搬进契约，让校验链从契约派生。

## What Changes

- base-contract.json 的 builtins 条目增加 `name`、`params`（含 name/type/optional/variadic/binder 标记）、`returnType` 字段；type 用 TS 风格类型串表达（封闭文法：基础 token、`T[]`、`Record<string, T>`、对象字面量、`|` union、泛型 `T`）
- 补 FormatNumber/FormatPercent 两条新条目（代码词表有、契约缺）
- **BREAKING** 删除 Builtins.java 硬编码函数名词表与 BuiltinTypeRegistry 逐函数规则方法，校验词表与类型推断改为契约派生（一次性切换，无双读兼容期）
- **BREAKING** contractVersion 1.1.5 → 1.1.6
- `signature` 字段保留但降级为纯展示字段（只喂提示词，不承载任何校验规则）
- Each/Render 的 returnType 推断留 Java 兜底（binder 作用域求值超出类型语法表达能力），其 arity/binder 标记仍从契约读
- Action 步骤名（Run/ToAssistant/OpenUrl/Set/Reset/Action）与 Query/Mutation 留在 Java——语法保留字，不是函数库
- 防漂移哲学：契约里没有的函数就是幻觉（unknown），不设代码↔契约对账机制，靠 contractVersion 版本号控制

## Capabilities

### New Capabilities

- `builtin-contract-schema`: 契约 builtins 条目的结构定义——TS 风格类型串文法（封闭集）、params/returnType 字段语义、arity 推导规则、加载期 fail-fast 校验（坏语法/未绑定 T/未知类型/name 重复/optional 排序错误 → 启动失败）
- `builtin-type-inference`: 从契约派生的类型推断——通用求值器语义（解 T、代入 returnType）、Each/Render 兜底特例、求值器语义约定（param 越界按 null、union 全有或全无、错误消息对齐现状）、幻觉语义（契约外函数报 unknown）

### Modified Capabilities

- `format-builtins`: 契约补录 FormatNumber/FormatPercent 条目，其类型校验从硬编码改为契约派生（requirement 层面：来源变了，校验行为不变）
- `object-map-builtins`: ObjectEntries/ObjectKeys/ObjectValues 的类型规则从硬编码改为契约派生，ObjectEntries 返回类型精度（`{key: string, value: T}[]` + fieldsOf 语义）在契约中结构化承载

## Impact

- `packages/genui-java-sdk/src/main/resources/openui/base-contract.json` — builtins 数组全量回填，版本号 bump
- `packages/genui-java-sdk/src/main/java/.../core/contract/BuiltinSpec.java`、`GenerationContractLoader.java` — 数据模型扩展与加载期校验
- `packages/genui-java-sdk/src/main/java/.../core/validation/parser/Builtins.java` — 硬编码词表删除，缩为 Action/Query/Mutation 保留字
- `packages/genui-java-sdk/src/main/java/.../core/validation/type/BuiltinTypeRegistry.java` — 逐函数规则删除，换通用求值器 + Each/Render 兜底
- `packages/genui-java-sdk/src/main/java/.../core/validation/parser/ExpressionParser.java`、`OpenuiParser.java`、`semantic/ProgramAnalyzer.java` — 静态调用改为注入契约词表
- 测试：interception-corpus 全量期望码必须零漂移；BuiltinTypeRegistryTest 迁移
- lang-core（TS）不动；eval-cli 冻结样本 sample-base-contract.json 不动
- GitHub issues #49-#52 按此 spec 执行
