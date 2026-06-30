## 1. 脚手架与配置

- [x] 1.1 新建包 `core.prompt.characterize`;定义入口 `Characterizer.characterize(DataModelSpec in, CharacterizationConfig cfg)`
- [x] 1.2 定义 `CharacterizationConfig`(record + builder):`enabled`(默认 true)、`triggerBytes`(2048)、`sampleRows K`(3)、`maxStringLen`(80)、`enumMaxDistinct`(50)、`enumMaxRatio`(0.5)、`deepScanLimit`(10000),全部带默认
- [x] 1.3 定义 `ShapeNode` sealed 体系(`ObjectShape`/`ArrayShape`/`ScalarShape`/`EnumShape` + `FieldShape{node,optional,nullable}`)与 `ScalarType` 枚举

## 2. 核心 walker(同构采样树)

- [x] 2.1 体积闸门:`in` 空 / `Json.stringify(raw).length() ≤ triggerBytes` / `!enabled` → 原样返回(spec:按体积阈值触发)
- [x] 2.2 `walk(value,cfg,depth)` 类型分派:`Map`/`List`/`String`/`Long`/`Double`/`Boolean`/null,返回 `{Object sample, ShapeNode shape}`
- [x] 2.3 标量规则:字符串超 `maxStringLen` 截断加省略标记;数字/bool/null 原样(spec:长字符串截断)
- [x] 2.4 `walkObject`:有序保留全部键,递归子值,产出同构 `LinkedHashMap` 样本 + `ObjectShape`(spec:保持引用路径有效)
- [x] 2.5 `walkArray` 趟 A(样本):仅取前 K 元素递归;`truncated = n>K`,不向树注入任何非原始字段

## 3. 特征扫描(sidecar 数据,walkArray 趟 B)

- [x] 3.1 对象数组:`ColumnAccumulator` 逐列累积类型集合、出现数、null 数;嵌套对象列深层 schema 仅对前 `deepScanLimit` 元素递归
- [x] 3.2 枚举域:容量上限 `HashSet` **全量**扫描收集 distinct,`distinct ≤ enumMaxDistinct 且 distinct/total ≤ enumMaxRatio` → `EnumShape`(字典序);越界即停降级 `string`(spec:完整枚举域提取)
- [x] 3.3 列定形:`null 数>0 → nullable`;`出现数<n → optional`;混合标量 → `unknown`
- [x] 3.4 数字数组 → 元素 `ScalarShape(NUMBER)` + count(不降采样);字符串数组按单列做枚举判定

## 4. TS-type sidecar 渲染

- [x] 4.1 `TsTypeRenderer`:`ShapeNode → TS 类型`,根名 `data`;枚举 union `"a"|"b"|"c"`;截断数组注释 `// <count> items (showing <K>)`;`optional→field?`、`nullable→| null`(spec:TS-type sidecar 承载 schema 与计数)
- [x] 4.2 sidecar 落点(推荐方案 a):给 `DataModelSpec` 加可空 `shapeSidecar` 字段;`dataModelSection` 加强分支在 JSON 样本块后追加 `Data shape (full dataset):` + fenced ```ts 块(样本在前、schema 在后)
- [x] 4.3 该加强分支**仅在 characterization 生效时**进入;未生效时方法体逐字节不变(parity 仅约束未压缩路径,压缩生效允许与 prompt.ts 发散)

## 5. 接入生成流程(收敛点 = assemblePrompt)

- [x] 5.1 `GenerationSdk` 持有 `CharacterizationConfig`(`Builder.characterization(cfg)`,默认 `defaults()`);`assemblePrompt` 构造 `PromptInput` 前调 `Characterizer.characterize` 取代 `effectiveRequest.dataModel()`
- [x] 5.2 配置贯通:新增 `GenUiGenerator.create(GenUiLlmConfig, CharacterizationConfig)` 重载,内部 `GenerationSdk.builder().characterization(cfg).build()`
- [x] 5.3 验证 seq=0 envelope 与 `GenUiGenerationResult.dataModel()` 仍等于全量 `response()`,未被 characterize 触及(spec:仅刻画 prompt 副本)

## 6. 单元测试

- [x] 6.1 同构性:数组保持为数组、键集合一致、无注入键
- [x] 6.2 采样:超长对象数组截断到 K;小数组原样
- [x] 6.3 枚举:样本未覆盖的取值仍出现在 union 中;高基数列降级为 `string`
- [x] 6.4 字符串截断;阈值触发(小数据原样、大数据采样)
- [x] 6.5 确定性:同输入两次产出逐字节相同
- [x] 6.6 回归:确保 `prompt-golden` fixture 不触发阈值(或测试禁用 characterization),`mvn -Dtest=PromptGoldenTest test` 全部通过——仅校验"未压缩 == TS oracle"路径,压缩生效的发散不进 golden

## 7. 效果验证(一等交付物)

- [x] 7.1 构建代表性大数据集夹具:万行对象表格(含枚举列)、万点数字序列、深层嵌套对象
- [x] 7.2 压缩比度量:对每个夹具记录采样前后 prompt 副本体积/约略 token,断言下降至设定目标以内,输出可复核的度量产物
- [x] 7.3 形状保真断言:枚举 union == 全量 distinct 集合;被截断 count == 真值;全量出现的每个字段路径都能在 sidecar 中找到(spec:形状无损)
- [x] 7.4 端到端无回归:采样开启,代表性场景生成 DSL,断言字段引用有效且 `@Switch` 覆盖全部枚举取值(spec:端到端不漏 case)
- [x] 7.5 依度量结果回填/微调阈值(`triggerBytes`、K、压缩比目标),并在 design.md 的 Open Questions 处更新结论

## 8. 收尾

- [x] 8.1 更新 `packages/genui-java-sdk/README.md` 说明 Characterization 行为、配置项与默认值
- [x] 8.2 视情形将 D1/D2 决策落为 ADR(`packages/genui-java-sdk/docs/adr/`):prompt-only 采样 + 双路径不变量
- [x] 8.3 `mvn test` 全绿(注意本机内存受限,必要时用独立 java 验证而非杀 IDE LSP)
