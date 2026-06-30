## ADDED Requirements

### Requirement: 仅刻画 prompt 副本,渲染数据保持全量

Characterization SHALL 只作用于进入 system prompt 的 data model 副本,MUST NOT 改动作为 seq=0 `dataModel` envelope 下发给运行时的 Render Data Model。变换 MUST 是无 LLM、确定性、单次遍历的纯函数。

#### Scenario: 全量数据仍然下发给运行时
- **WHEN** 一个 10000 行的 host data 经过生成流程
- **THEN** seq=0 的 `dataModel` envelope 仍包含全部 10000 行
- **AND** system prompt 中的副本被替换为采样后的形态

#### Scenario: 相同输入产出相同结果
- **WHEN** 对同一份 host data 连续两次执行 Characterization
- **THEN** 两次产出的 `DataModelSpec`(采样树与 sidecar)逐字节相同

### Requirement: 保持引用路径有效

采样后的数据树 SHALL 与原数据保持同构:键名、嵌套对象结构、数组/对象的字段位置 MUST 不变,使得模型生成的 `data.<field>`、数组 pluck `data.rows.revenue`、`@Each(data.rows, ...)` 等引用在运行时对全量数据依然有效。Characterization MUST NOT 向数据树内注入任何非原始字段(如 `__count__`、`__sample__`)。

#### Scenario: 数组字段保持为数组
- **WHEN** 原 `data.rows` 是对象数组
- **THEN** 采样后的 `data.rows` 仍是对象数组(仅元素更少)
- **AND** 每个保留元素的键集合与原元素一致

#### Scenario: 不污染字段命名空间
- **WHEN** 检视采样树的任意节点
- **THEN** 不存在原数据中没有的注入键

### Requirement: 大数组采样到 K 行样本

当一个数组元素数超过保留阈值时,Characterization SHALL 仅保留前 K 个元素(默认 K=3)作为 Sample Rows,并对保留元素递归施加同样的字符串截断与嵌套采样规则。

#### Scenario: 截断超长对象数组
- **WHEN** `data.rows` 含 10000 个对象且 K=3
- **THEN** 采样树中的 `data.rows` 仅含前 3 个对象

#### Scenario: 小数组原样保留
- **WHEN** 某数组元素数不超过 K
- **THEN** 该数组原样保留,不做采样

### Requirement: 完整枚举域提取

对每个低基数字符串字段(`distinct ≤ 50` 且 `distinct/total ≤ 0.5`),Characterization SHALL 全量扫描所有元素收集其**完整** distinct 值集合,并在 sidecar 中以 union 字面量形式给出。超过基数上限的字符串字段 MUST 被当作自由文本,仅给出类型与 count,不展开取值。

#### Scenario: 枚举域完整不漏 case
- **WHEN** `status` 列在全量数据中取值为 `open`/`closed`/`pending`,而前 3 行样本只出现了 `open`/`closed`
- **THEN** sidecar 中 `status` 的类型为 `"open" | "closed" | "pending"`(包含样本未出现的 `pending`)

#### Scenario: 高基数字符串降级为自由文本
- **WHEN** `description` 列有 8000 个不同取值
- **THEN** sidecar 中其类型为 `string`,不展开任何具体取值

### Requirement: TS-type sidecar 承载 schema 与计数

Characterization SHALL 生成一段 TypeScript 风格的类型声明作为 Sidecar,承载完整字段 schema、枚举 union、以及数组/对象的 count(以注释给出)。Sidecar 的落点(`description` 携带或 `DataModelSpec` 新增 sidecar 字段)由实现自定,MUST NOT 把"压缩生效时仍与 TS oracle 字节一致"作为约束——该一致性仅在压缩未触发时要求。

#### Scenario: sidecar 标注全量规模
- **WHEN** `data.rows` 原有 10000 个对象、采样保留 3 个
- **THEN** sidecar 类型声明中 `rows` 的元素类型后带注释标明全量为 10000
- **AND** 原始 `description` 文本仍被保留

#### Scenario: 原有 description 不丢失
- **WHEN** Characterization 完成且输入带有 `description`
- **THEN** 产出的 `DataModelSpec` 仍保留原 `description` 文本,并附带 sidecar

### Requirement: 长字符串截断

对超过长度阈值(默认 ~80 字符)的字符串标量,Characterization SHALL 截断并以省略标记结尾;数字、布尔、null 标量 MUST 原样保留。

#### Scenario: 截断超长字符串
- **WHEN** 某字符串值长度为 5000 字符、阈值为 80
- **THEN** 采样树中该值被截断至约 80 字符并带省略标记

### Requirement: 按体积阈值触发

Characterization SHALL 仅在 `raw` 序列化体积超过触发阈值(默认 ~2KB)时执行采样;未超阈值时 MUST 原样返回输入,使小数据的 prompt 与行为保持不变。

#### Scenario: 小数据不被改动
- **WHEN** `raw` 序列化体积为 500 字节、阈值为 2KB
- **THEN** Characterization 返回与输入等价的 `DataModelSpec`,不做采样

#### Scenario: 未触发路径与 TS oracle 字节一致
- **WHEN** `prompt-golden` fixture 的数据未达触发阈值(或测试中禁用 characterization)
- **THEN** 组装出的 prompt 与 TS oracle 逐字节一致,`prompt-golden` 全部通过
- **AND** 压缩生效时的 prompt 不要求与 TS oracle 一致

#### Scenario: 大数据触发采样
- **WHEN** `raw` 序列化体积为 2MB
- **THEN** Characterization 执行采样并显著缩小 prompt 副本体积

### Requirement: 效果验证作为交付物

本能力 SHALL 附带可度量的效果验证,而非仅依赖单测通过。验证 MUST 覆盖:(a)**token/体积压缩比**——对代表性大数据集,采样后 prompt 副本体积相对原始下降到设定目标以内;(b)**形状保真度**——采样后 sidecar 所述 schema、枚举域、count 相对全量数据无损(枚举域为全集、count 为真值、字段路径齐全);(c)**端到端无回归**——采样开启后,代表性场景生成的 DSL 仍能正确引用字段且不漏枚举 case。

#### Scenario: 压缩比达标
- **WHEN** 对一组代表性大数据集(含万行表格与万点序列)运行采样
- **THEN** 每个数据集的 prompt 副本体积相对原始下降至目标阈值以内,且结果被记录为可复核的度量

#### Scenario: 形状无损
- **WHEN** 比对采样产出与全量数据
- **THEN** 每个枚举字段的 union 等于全量 distinct 集合
- **AND** 每个被截断数组/对象的 count 等于全量真值
- **AND** 全量数据中出现的每个顶层及嵌套字段路径都能在 sidecar 中找到

#### Scenario: 端到端不漏 case
- **WHEN** 采样开启,代表性场景生成 DSL 且数据含某枚举字段
- **THEN** 生成的 `@Switch` 覆盖该字段的全部枚举取值
