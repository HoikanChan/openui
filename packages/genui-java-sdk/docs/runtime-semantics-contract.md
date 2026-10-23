# 契约迁移语义与验收依据（#79）

本次以 **生效 Base Contract 的接口定义**为依据，不以 TS 执行结果定义语言。默认 29 项均已逐项核对；契约明确的规则可进入迁移验收，契约没有定义的边界不能补成猜测值。本文件不宣称完成生产运行时验证，也不修改现有校验政策。

范围：[#79](https://github.com/HoikanChan/openui/issues/79)，父需求 [#77](https://github.com/HoikanChan/openui/issues/77)。依据为[完整设计](https://github.com/HoikanChan/openui/issues/77#issuecomment-6340992323)第 7、8、10、12、13、14 节；已阅读[多系统调研](https://github.com/HoikanChan/openui/issues/77#issuecomment-6340983889)和 [Go 调研](https://github.com/HoikanChan/openui/issues/77#issuecomment-6340985384)。调研支持类型表示、关系与局部求解分层，不提供本项目 builtin 的具体返回值。

## 证据版本与复现

核对基点：`bfc9c760f3b3feaa09a2ad8b23d5c357193b0574`。默认资源：`src/main/resources/openui/base-contract.json`，`contractVersion = @cloudsop/openui-react-ui-dsl@1.1.6`，SHA-256 `fcacf88ca74858ba653975aa05889bf839686972afe8b0cd82dc086d2e0c9fc2`，Git blob `f4f70c7c5747acfae078db0ad3b994eb6aebd134`。行号均指此版本。#83 资源新增 binder 布局字段（`input`/`nameSyntax`/`callForms`）；Sum 的可执行规则保持 number[] 输入与 number 输出。当前 SHA-256 为 `4b78c6d26d542d32347ea3a0f6ca214ec066f86ab75df822c30ff59aa8d42d97`（contractVersion 未变，仍为 1.1.6 词汇）。

从仓库根目录运行：

```powershell
mvn -f packages/genui-java-sdk/pom.xml -DskipTests compile
$evidenceClasspath = "packages/genui-java-sdk/target/classes;$env:USERPROFILE/.m2/repository/com/alibaba/fastjson2/fastjson2/2.0.60/fastjson2-2.0.60.jar"
java --class-path $evidenceClasspath packages/genui-java-sdk/docs/examples/ContractSemanticsEvidence.java
```

环境：Java `21.0.10`、Maven `3.9.16`；两条命令退出码均为 0。示例通过 SDK 公开 `GenerationContractLoader.fromJson` 加载默认资源，打印 29 项名称、通过 `BuiltinSpec` 推导的参数数量和完整描述，并核对关键返回声明及现存冲突。21 个案例的版本、唯一 ID 与来源检查通过，5 个未定义精确结果均显式标为 UNPROVEN 且无伪造 expectedValue。末行实测输出（JSON 键顺序无语义）：

```json
{"contractVersion":"@cloudsop/openui-react-ui-dsl@1.1.6","builtins":29,"sha256":"fcacf88ca74858ba653975aa05889bf839686972afe8b0cd82dc086d2e0c9fc2","evidence":"contract-only; no runtime execution"}
```

#83 资源更新后重跑，末行实测输出（2026-10-23，退出码 0）：

```json
{"contractVersion":"@cloudsop/openui-react-ui-dsl@1.1.6","sha256":"4b78c6d26d542d32347ea3a0f6ca214ec066f86ab75df822c30ff59aa8d42d97","builtins":29,"evidence":"contract-only; no runtime execution"}
```

`callForms` 按准确实参数量声明 `arity`、body 实参下标 `body` 与 binder 实参下标 `binders`。Each 声明 `{arity:3, body:2, binders:[1]}`；Render 分别声明两参和三参形式。加载器拒绝重复数量、缺失形式、越界或与 binder/input 冲突的位置；语义、依赖和类型分析共用编译结果，不再按最后一个实参猜 body。

`input` 对 binder builtin 是必填键：整数指向元素来源实参；显式 `null` 表示"没有元素来源"（Render）。省略键视为漏声明并在加载期失败，避免 Each 的元素级检查静默退化成 `MISSING_CONTEXT`。声明了 `T[]` 元素数组参数的 binder builtin（Each）不得用 `null` 拒绝命名来源，同样在加载期拒绝。

Each 只在源元素存在已证明时把 body 观察值包回输出数组。存在性证明要求实际观察到非空数组，或输入的**每个**可能分支长度下界都 ≥1；联合里单个非空分支不构成证明（#77 §6.5）。空源或 Filter 后未证明保留任何行的源不能产生这些观察值；body 内独立错误仍检查。本地关闭对象的缺字段由结构证明，不要求快照观察值。

这是可执行的**契约证据检查**，不运行 DSL、不证明生产返回值。配套 [验收输入](examples/contract-migration-acceptance.json)将“确定结果”“只确定类型”“尚待定义”分开；#90 起这些规则已接入公开 validator 与确定性生成校验入口，测试不构造内部分析快照。

## 阅读矩阵的约定

- “接受”指结构化 `params` 允许生成的输入；`?` 为可选，`...` 为剩余参数。数量从 params 推导，不能解析展示 signature。
- “容错”指 description 明确规定的异常输入行为。容错不自动扩大生成接受范围，例如 `Join(null)` 可以有容错结果，同时不满足 array 参数政策。
- “声明”是返回类型承诺；“未定”表示契约未给具体值或自相矛盾。不得用本地 TS、Java 推断器当前实现填空。
- `any` 作为输入允许任意值；作为返回值只说明信息未知。空数组与未知数组分开，缺字段与显式 null 分开。
- `templateBuiltin` 只决定提示词分节，不表示惰性调用。MergeObject/Switch 均不能因此被当成 binder。

## 默认 29 项矩阵

来源统一为默认资源的 `/builtins` 中同名条目（数组从第 1623 行开始，首个条目在第 1624 行）；表中保留现有接受政策，没有新增 enum 限制。

| 名称 | 接受输入；参数数量 | 返回/边界证据；迁移要求 |
| --- | --- | --- |
| Count | array；1 | number；数组长度，`[] → 0`。null/非数组不被参数接受，未定义容错。 |
| First | T[]；1 | T；非空取首。空数组无返回值定义，不能把空数组的元素 Never 当作实际返回值。 |
| Last | T[]；1 | T；非空取尾。空数组同上，不能自行补 null。 |
| Sum | number[]；1 | number；数字求和。空数组具体值未写明，非数字不因潜在转换而接受。 |
| Avg | number[]；1 | number；平均值。空数组具体值未定。 |
| Min | number[]；1 | number；最小值。空数组具体值未定。 |
| Max | number[]；1 | number；最大值。空数组具体值未定。 |
| Sort | T[], string, string?；2–3 | T[]；按 field 排序，direction 描述为 asc 默认/desc。保留元素与数量。其他 direction 的行为未定；当前 params 仅 string，不能称已有 enum 校验。 |
| Filter | T[], string, string, any；4 | T[]；筛选子集，数量下界 0。signature 列出 ==、!=、>、<、>=、<=、contains；参数未结构化为枚举。比较/转换细节未定；不声称谓词收窄或某行必保留。 |
| Flatten | array[]；1 | array；展平一层。description 定义 null/非数组容错为 []。混合元素（标量/null/数组）的策略未定，不能假定 JS flat、过滤或递归展平。 |
| ObjectEntries | Record<string,T>；1 | {key:string,value:T}[]；有序键值行，原字段只在 value 下。空对象 → []；null/数组/不支持输入容错 → []。类型迁移保留每个 key/value 对应关系。 |
| ObjectKeys | Record<string,any>；1 | string[]；有序键，空对象 → []；null/数组/不支持输入容错 → []。具体数字键排序规则未写明。 |
| ObjectValues | Record<string,any>；1 | any[]；有序值，空对象 → []；null/数组/不支持输入容错 → []。新结构规则应保留已知字段值的类型，不能将空对象推成含 null 元素。 |
| Round | number, number?；1–2 | number；小数位默认 0。负位数、非整数精度、舍入平局规则未定。 |
| Abs | number；1 | number；绝对值。无非数字转换承诺。 |
| Floor | number；1 | number；向下取整。无非数字转换承诺。 |
| Ceil | number；1 | number；向上取整。无非数字转换承诺。 |
| Number | any；1 | number；字符串解析、boolean 转 0/1、无效值 → 0。空串、特殊进制、非有限数的具体判断未定义。 |
| Switch | any, Record<string,T>, any?；2–3 | T 或 default；数字查键转字符串。未命中且省略 default → null；cases 在当前模板环境分析。显式 null default 与省略分别记录，即使结果可同为 null。 |
| FormatDate | any, string?, string?；1–3 | 标量 → string，数组 → string[]，保留数量。输入秒/毫秒自动识别，ISO 字符串；style 默认 dateTime，可用 date/time/relative。null/无效值输出和时区细节未写明；不据本地 Date 行为定规则。 |
| FormatBytes | any；1 | string；SI 单位，nullish → 空串。只声明 1 参，不能按其他实现添加 locale 参数。 |
| FormatNumber | any, number?, string?；1–3 | 声明 string，description 却称无效值返回 raw input。无效 object/boolean/null 的结果种类待裁定；不能直接扩大为任意联合或无条件证明 string。 |
| FormatPercent | any, number?, string?；1–3 | string；0.125 是比例，格式为百分比。null/无效值和精度边界未写明。 |
| FormatDuration | any, string?, string?；1–3 | string；默认秒，支持 ms。signature 未显示 locale，但 params 接受第三个 string；以 params 为调用政策。null/无效值输出未写明。 |
| MergeObject | object, object, ...object；至少 2 | object；从左到右后写覆盖。description：非对象跳过、没有对象时 {}。后两项是容错，不隐式放宽至少 2 个对象的政策；零参/一参调用仍不满足 params。每个剩余参数都须检查。 |
| Join | array, string?；1–2 | string；separator 默认 ", "，null/undefined 元素跳过，其余转字符串；null/非数组/空输入容错 → 空串。嵌套对象字符串表示未定。 |
| String | any；1 | string；number/boolean 转字符串，其他类型 → 空串（包括已是字符串、object、array、null）。不要擅自改成 JavaScript String。 |
| Each | T[], binder, body；3 | 每个元素求值 body，结果序列保持输入数量。跨语句模板由 ADR 0006 保留，见下节；空输入 → 空序列，不免除 body 中独立的名称/调用检查。 |
| Render | binder, body 或 binder,binder,body；2–3 | 延迟模板，不是立即执行后的组件。调用期提供 v/row，覆盖同名捕获绑定；不能把可选 row 当普通 optional 参数错配 body。 |

## 已有模板行为必须保留

[ADR 0006](../../../docs/adr/0006-openui-lang-lexical-template-scope.md)随 commit `1e3ffc9317e3c45b8cc6920b33cc23c15675d774` 落地，明确允许 Each 引用独立模板及其传递依赖。Java 公开 validator 已有 `StaticTypeValidationTest.acceptsEachBinderAcrossReferencedTemplateStatements`：

```openui
root = Tabs(@Each(data.groups, "group", groupTemplate))
groupTemplate = TabItem(group.model, group.model, [groupTable])
groupTable = Table([nameCol], group.devices)
nameCol = Col("Device Name", "name")
```

复核命令：`mvn -f packages/genui-java-sdk/pom.xml "-Dtest=StaticTypeValidationTest#acceptsEachBinderAcrossReferencedTemplateStatements" -DforkCount=0 test`，实测 1 test、0 failures/errors/skips，BUILD SUCCESS。使用不派生 JVM 的方式控制本机内存开销。

数据：`{"groups":[{"model":"CE12804S","devices":[{"name":"DC1-spine-01"}]}]}`。迁移验收保持 VALID，group 绑定须传到 groupTable。默认契约 Each 描述的 inline-only 文本落后于此决策，不能拿它删除合法行为。模板的不同使用位置分别检查；binder 未提供时应报告引用问题，不转 null。裸标识符 binder 的既有合法形式按 #77 第 10.1 节保留；结构化布局须表示名称语法，不能把 binder 当普通 string 值求值。

更深的捕获与遮蔽按 ADR 0006：每轮独立环境；Render 捕获外层 iterator，调用时自身 binder 遮蔽；离开内层 Each 后恢复外层；$state 仍实时读取。本任务记录验收要求，不声称这些均已在生产打包产物验证。

## 缺字段、null 与数组投影

`additionalRules` 第 1610、1615 行定义完整路径和数组字段投影；Flatten description 明确 `data.groups.items` 先产生 items[][] 再展平。可确认：投影保留数组层级，不能偷偷 flatten。

契约未定义缺字段/null 元素是保留 null、保留缺失、跳过还是失败。输入 `{"rows":[{"x":1},{},{"x":null},null]}` 的 `data.rows.x` **没有可确认的精确输出**。迁移须保留每个分支的字段存在性，不能省略缺失分支后宣称所有行有 x。已知空数组不提供字段证据；字段值为 null 与不存在不能合并成同一个字段事实。精确投影结果规则等待契约补充，不阻塞其他结构运算。

## 权威导出与结构规则传递

当前验证和提示词的权威输入是生效契约，默认由 `GenerationContractLoader.loadDefault()` 读取 classpath `openui/base-contract.json`。Case Contract 优先级及 Base Supplement/Extension 合并仍按既有 SDK 定义；不能因缺规则偷偷回退另一份默认词表。

历史前端导出入口可以精确定位：`258997028bd79cb49a3207c692cb5f16d9ad379c:packages/react-ui-dsl/scripts/generate-base-contract.mts`，运行入口为该包 `generate:base-contract`。它调用 `dslLibrary.toSpec()`，再将 `getBuiltinsManifest()` 放到顶层 builtins，同时写前端 generated JSON 和 SDK resource。`react-lang@0.2.0` 从 workspace `lang-core` 重新导出 manifest；lockfile 是 workspace link，没有独立 registry 锁定版本。该历史 manifest 仅有 signature/description/templateBuiltin，不能承载默认资源现有的 name/params/returnType，更不能承载 #77 新规则。该脚本随前端移除，当前工作树不能运行它。

因此只能确认**历史入口**，不能称它已是支持新结构的可运行导出器。后续迁移必须在维护该接口的权威导出端传递：

```text
权威接口源（29 builtin、调用形式、binder、结果规则、规则版本）
  → 导出器保留结构字段（不得退成只有展示文本的 manifest）
  → 前端导出 JSON 与 SDK resource 同一内容/版本
  → GenerationContractLoader / ContractCompiler
  → 同一生效契约的 parser、语义、类型分析、提示词
```

新字段具体 JSON schema 由契约编译任务交付，不在 #79 虚构可运行字段。加载未知规则必须失败，不能丢弃。导出验收要求：29 名称完整、结构字段往返不丢失、两端规则版本一致、未知 op 加载失败、新 Identity<T> 可只改契约组合已有运算。未提供权威新导出器及往返证据前，**结构化规则发布链路未验收**；这是接口迁移缺口，不是缺 TS 实现造成的阻塞。

## 仍需裁定的精确规则

| 缺口 | 已知事实 | 阻塞范围 |
| --- | --- | --- |
| First/Last 空数组 | params 无非空限制，return T | 空输入返回规则；不得断言 Never、null 或 undefined |
| 聚合空数组 | number[] → number | 精确空值；number 类型承诺可保留，不编造 0/NaN/异常 |
| Flatten 混合元素 | 接受 array[]，只定义一层及 null/非数组容错 | 不能为混合数组定义 keep/drop/reject 运算；严格 params 下可拒绝明确非数组元素 |
| FormatNumber 无效值 | any 输入 + string 声明 + raw input 描述 | 对冲突输入不能证明具体输出种类，需明确 raw 指原值还是原值的文本 |
| 缺字段/null 投影 | 有数组 pluck，无异常分支规则 | 精确值与数量，缺失分支不能删除 |
| MergeObject 容错是否扩展政策 | params ≥2 object，描述可跳过/无对象回 {} | 不自动改政策；若产品要接受 0/1/非对象，须显式改调用形式 |
| Each 文本冲突 | ADR 与公开测试已接受跨语句 | 文档按 ADR 保留；后续权威导出须删除 inline-only 文本 |
| 新规则导出 | 历史入口只传展示 manifest | 新规则端到端发布必须补导出器/往返证据 |

以上精确规则待接口维护方裁定。对缺失或冲突的结果保证，目标分析记录 **UNPROVEN（未证明：现有接口信息不足）** 精度缺口；不猜返回值，不以这个缺口单独产生修复错误，也不把它计成完整 MATCH。独立、明确的 params 违规仍可报错，例如 Flatten 混合数组中的已知 number 元素；聚合函数的 number 类型声明仍可使用，只对未给出的精确空值保持未证明。迁移内核继续实现已确认的结构语义；缺 TS 实现不是 Java 开发阻塞。

TS 后续工作集中跟踪于 [#91：权威契约导出与模板接口同步](https://github.com/HoikanChan/openui/issues/91)，关联 #77/#79/[#88](https://github.com/HoikanChan/openui/issues/88)。已去重：[#73](https://github.com/HoikanChan/openui/issues/73)完整覆盖 capabilityGaps 导出，本次复用该票；已关闭 #49 是 Java 资源回填，未覆盖权威 TS 导出。#91 只跟踪接口/导出缺口，本任务不修改 TS runtime/export 源码。

## #79 验收映射

| issue 要求 | 本次交付 |
| --- | --- |
| 默认 29 项及重点边界 | 上述逐项矩阵、可执行契约证据脚本、验收 JSON |
| 运行时容错与校验政策分开 | 矩阵独立记录 params 与 description；不改 params |
| Each 独立模板 binder | ADR 0006、公开 validator 既有测试和完整 DSL/数据 |
| 权威导出与类型规则路径 | 历史 commit/入口/manifest 限制、目标传递链与发布验收 |
| 可复现输入输出或契约证据 | 脚本 29 项输出/摘要指纹、JSON 样例；明确非运行时测试 |
| 必要规范决定与未决项 | [ADR 0005](adr/0005-contract-semantics-evidence.md)，本文件未决表 |

## #83 验收映射：分析缓存（设计 13.2）

设计 13.2 要求给出"缓存开/关结果一致"的对比验收。本实现**没有分析结果记忆化**：每次 `analyze()` 新建 `AnalysisSession`，类型合成、证据与反例检查都在单次会话内现算，跨调用不读写任何分析结果缓存。因此"缓存开关对比"对本实现不适用（N/A）——不存在可切换的缓存，也就无从产生开关两态的结果差。

可重复性另行锚定，与缓存无关：同一输入独立运行两次结论必须一致，由 `EachSwitchScopeValidationTest.conclusionsAreRepeatableAcrossIndependentRuns`（以及内核既有重复性用例）固定。若未来引入分析缓存，需补 13.2 的开关对比验收后再启用。

## #90 终态：单引擎与单一规则源

#79 建立的语义矩阵现在由一条生产路径执行，不再有迁移期并行实现：

| 项 | 终态 | 证据 |
| --- | --- | --- |
| 类型分析路径 | `TypeAnalysis.analyze` → `type/analysis` 内核（Type/Fact/TypeOps/Checks/CoverageReport）+ `contract.RuleEvaluator` | `DefaultOpenuiLangValidator` FINAL 分支；`StaticTypeValidationTest` |
| builtin 词表 | 契约 `builtins` 派生；`Builtins.java` 只保留 Query/Mutation/Action 等语句关键字 | `BuiltinCatalog.fromContract`；`Builtins` 搜索无函数名单表 |
| 求值规则 | `typeRule`（`RuleCompiler` 加载期封闭文法 + `RuleEvaluator` 运行期） | `RuleCompilerTest`、`ContractBuiltinRulesTest` |
| 已删除 | `ValueType`/`ValueTypes`/旧 `DataPath*`、`TypeTranslator`、`returnType`+`params` 泛型求值（`genericResult`/`collectLowerBounds`/`instantiate`） | 全局搜索无引用；`DataPathResolverTest` 改用新 `Type` |
| 参数检查覆盖 | 每次规则模式检查登记 MATCH/MISMATCH/UNPROVEN；只有 MISMATCH 且无上游错误上报 | `typeChecks.*` 元数据；`StaticTypeValidationTest` |
| 修复触发 | 只有 `ValidationStatus.INVALID`（确定 ERROR）进入增量修复；仅 UNPROVEN 的 VALID 结果不触发 | `GenUiGeneratorTypeTriggerTest`（sync + streaming） |
| 语料行为 | `repair-cases/` 校验结论不变（25 例 code 集合逐例一致）；Case Contract 1.1.5 已补齐 `typeRule`，与 `base-contract.json` 同规则 | validation-only bench 对比；`RepairCorpus*RulesTest` |
| 集成行为 | 同名覆盖/遮蔽、入口不可达语句不报当前错误、取消隔离、校验不改调用方 dataModel | `StaticTypeValidationTest`、`EachSwitchScopeValidationTest`、`TypeEngineIntegrationBehaviourTest`、`CancellationIsolationTest` |

Each/Render/Switch 仍是语言模板构造：binder 布局（`callForms`/`binders`/`body`/`input`/`nameSyntax`）从契约读取，模板作用域与分支语义留在 Java 侧。它们不构成第二份 builtin 类型词表，也不产生第二条类型分析路径。

未包含在 #90：有界分析预算与截断策略（#89 已拆分跟踪为 #94）；AST 关联与展示集成由 #59 保持既有归属。
