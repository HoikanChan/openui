# Fuzz Benchmark Cases — Outline

## 维度定义

每个 case 的 `taxonomy` 是各维度取值的组合，维度之间互相独立，可自由交叉覆盖。

---

### Dim A · Shape（顶层结构）

数据根节点长什么样。

| 取值 | 说明 | 示例 |
|---|---|---|
| `flat-object` | 单对象，所有字段均为 scalar | `{ name, status, count }` |
| `bare-array` | 裸数组，无 wrapper | `[{...}, {...}]` |
| `named-list` | 命名 key 包裹列表 | `{ devices: [...] }` |
| `paginated-envelope` | 分页 wrapper | `{ total, pageSize, pageIndex, list: [...] }` |
| `cursor-envelope` | 游标分页 | `{ hasMore, cursor, items: [...] }` |
| `multi-top-arrays` | 多个顶层数组 | `{ rows: [...], statistics: [...] }` |
| `nested-object` | 对象内含子对象和/或子数组 | `{ info: {...}, alarms: [...] }` |
| `columnar-arrays` | 并行原始值数组（列式存储） | `{ timestamps: [...], values: [...] }` |
| `map-object` | 动态 key → 对象（字典） | `{ "dev-001": {...}, "dev-002": {...} }` |
| `grouped-map` | 动态 key → 数组（分组字典） | `{ critical: [...], warning: [...] }` |
| `date-keyed-map` | 日期 key → 值（时间桶） | `{ "2024-01-01": {...}, "2024-01-02": {...} }` |
| `api-response-envelope` | 标准 API 响应 wrapper | `{ success, code, message, data: {...} }` |

---

### Dim B · Record Structure（记录内部结构）

数组里的元素长什么样。

| 取值 | 说明 | 示例 |
|---|---|---|
| `homogeneous` | 所有 record 字段完全相同 | `[{a,b,c}, {a,b,c}]` |
| `schema-inconsistent` | 部分 record 有多余/缺失字段 | `[{a,b,c}, {a,b}]` |
| `polymorphic` | 同数组内 record 类型不同，靠 type 字段区分 | `[{type:"A", ...}, {type:"B", ...}]` |
| `primitive-numbers` | 元素是纯数字 | `[45.2, 67.8, 23.1]` |
| `primitive-strings` | 元素是纯字符串 | `["active", "inactive"]` |
| `tuple-array` | 元素是定长数组（非 object） | `[[ts, val], [ts, val]]` |
| `record-with-array-field` | record 是 object，某字段值本身是数组 | `{ name, trend: [45, 67, 23] }` |
| `record-with-max-min` | record 含 max/min/avg/current 四字段 | `{ name, max, min, avg, current }` |
| `embedded-tree` | record 含递归 children 字段 | `{ id, name, children: [...] }` |
| `parentid-flat` | 扁平 record，通过 parentId 引用同数组内其他 record | `{ id, parentId, name }` |
| `graph-nodes-edges` | nodes 数组 + edges 数组，各自含 id/source/target | `nodes:[{id}], edges:[{source,target}]` |
| `adjacency-list` | 每个 record 含 neighbors/connections 子数组 | `{ id, name, neighbors: ["id2","id3"] }` |

---

### Dim C · Value Encoding（值编码挑战）

字段值本身的编码方式，**可多选**。

| 取值 | 说明 |
|---|---|
| `epoch-ms` | 时间以 epoch 毫秒整数存储 |
| `epoch-s` | 时间以 epoch 秒整数存储 |
| `integer-enum` | 状态/类型以整数编码（0/1/2/3） |
| `string-enum` | 状态/类型以短字符串编码（"active"/"up"） |
| `boolean-int` | 布尔含义字段用 0/1 整数表示 |
| `byte-values` | 流量/存储为原始字节值 |
| `percentage-decimal` | 百分比以 0.0–1.0 小数存储 |
| `ratio-values` | 值之和约为 1 或 100，具有比例语义 |
| `cross-magnitude` | 同字段在不同 record 间相差百万倍以上 |
| `min-max-band` | 时序行每个时间点含 value/upper/lower（范围带） |
| `delta-values` | 字段表示差值（current-previous、actual-target、trend） |
| `uuid-id` | id 字段为 UUID，无业务语义 |
| `composite-id` | id 字段为复合格式（"22-5635"、"NE-01"） |

---

### Dim D · Temporal Pattern（时序模式）

| 取值 | 说明 |
|---|---|
| `none` | 无时间维度 |
| `single-entity` | 单实体多时间点，时间均匀分布 |
| `single-entity-irregular` | 单实体多时间点，时间不规律（事件触发） |
| `multi-entity-interleaved` | 多实体 rows 混排在同一数组，未分组 |
| `multi-entity-unaligned` | 多实体时间点不对齐，各实体缺失点不同 |
| `unordered` | 有时间字段但数组未按时间排序 |
| `date-keyed` | 时间体现在 key 而非字段（date-keyed-map 专用） |

---

### Dim E · Completeness（完整性 / 基数）

| 取值 | 说明 |
|---|---|
| `full` | 所有字段均有值，记录数正常 |
| `sparse-nullable` | 大量字段为 null，少数字段有值 |
| `nearly-all-null` | 几乎全 null，只有 id/name 有值 |
| `empty` | 列表为空（0 条记录） |
| `singleton` | 列表只有 1 条记录 |

---

### Dim F · Aggregation Layer（聚合层次）

| 取值 | 说明 |
|---|---|
| `raw-only` | 只有原始 record，无预计算统计 |
| `stats-plus-rows` | 同时有聚合摘要和原始行 |
| `aggregated-only` | 只有摘要 scalar，无原始行 |
| `ratio-distribution` | 值表示各分类的占比/分布（和≈1 或≈100） |
| `delta-comparison` | 结构为当前值 vs 对比值（previous/target/yesterday） |

---

## Case 矩阵

| 文件名 | A·Shape | B·Record | C·Value Encoding | D·Temporal | E·Completeness | F·Aggregation |
|---|---|---|---|---|---|---|
| **顶层形态** | | | | | | |
| `flat-object-single` | flat-object | — | epoch-ms, string-enum, uuid-id | none | full | raw-only |
| `named-list-homogeneous` | named-list | homogeneous | byte-values, percentage-decimal, string-enum | none | full | raw-only |
| `paginated-list` | paginated-envelope | homogeneous | epoch-ms, integer-enum | none | sparse-nullable | raw-only |
| `cursor-paginated-items` | cursor-envelope | homogeneous | epoch-ms, string-enum | none | full | raw-only |
| `multi-top-arrays` | multi-top-arrays | homogeneous | epoch-ms, byte-values | none | full | stats-plus-rows |
| `api-response-envelope` | api-response-envelope | homogeneous | string-enum | none | full | raw-only |
| **列式 / 元组** | | | | | | |
| `timeseries-columnar` | columnar-arrays | primitive-numbers | epoch-ms | single-entity | full | raw-only |
| `timeseries-tuple-pairs` | bare-array | tuple-array | epoch-ms | single-entity | full | raw-only |
| **动态 key 字典** | | | | | | |
| `object-map-by-id` | map-object | homogeneous | string-enum, epoch-ms | none | full | raw-only |
| `grouped-object-of-arrays` | grouped-map | homogeneous | integer-enum | none | full | raw-only |
| `date-keyed-buckets` | date-keyed-map | homogeneous | — | date-keyed | full | raw-only |
| **嵌套 / 层级** | | | | | | |
| `array-with-nested-arrays` | named-list | record-with-array-field | — | none | full | raw-only |
| `nested-object-with-array` | nested-object | homogeneous | epoch-ms, string-enum | none | full | raw-only |
| `tree-embedded-children` | nested-object | embedded-tree | — | none | full | raw-only |
| `flat-parentid-reference` | bare-array | parentid-flat | — | none | full | raw-only |
| **图关系** | | | | | | |
| `nodes-edges-graph` | multi-top-arrays | graph-nodes-edges | — | none | full | raw-only |
| `adjacency-list-graph` | named-list | adjacency-list | — | none | full | raw-only |
| **时序** | | | | | | |
| `timeseries-single-entity` | named-list | homogeneous | epoch-ms | single-entity | full | raw-only |
| `timeseries-multi-entity-interleaved` | named-list | homogeneous | epoch-ms | multi-entity-interleaved | full | raw-only |
| `timeseries-unordered` | named-list | homogeneous | epoch-ms | unordered | full | raw-only |
| `timeseries-stats-plus-rows` | multi-top-arrays | homogeneous | epoch-ms, byte-values | multi-entity-interleaved | full | stats-plus-rows |
| `timeseries-min-max-band` | named-list | homogeneous | epoch-ms, min-max-band | single-entity | full | raw-only |
| `timeseries-multi-entity-unaligned` | multi-top-arrays | homogeneous | epoch-ms | multi-entity-unaligned | sparse-nullable | raw-only |
| **值编码挑战** | | | | | | |
| `integer-enum-status` | named-list | homogeneous | integer-enum, epoch-ms | none | full | raw-only |
| `byte-large-values` | named-list | homogeneous | byte-values, cross-magnitude | none | full | raw-only |
| `boolean-as-integer` | named-list | homogeneous | boolean-int | none | full | raw-only |
| `percentage-as-decimal` | named-list | homogeneous | percentage-decimal | none | full | raw-only |
| `cross-magnitude-values` | named-list | homogeneous | cross-magnitude, byte-values | none | full | raw-only |
| **原始值数组** | | | | | | |
| `primitive-number-array` | bare-array | primitive-numbers | — | none | full | raw-only |
| `primitive-string-array` | bare-array | primitive-strings | string-enum | none | full | raw-only |
| `labeled-ratio-array` | named-list | homogeneous | ratio-values | none | full | ratio-distribution |
| `unlabeled-ratio-array` | bare-array | primitive-numbers | ratio-values | none | full | ratio-distribution |
| **Record 内部挑战** | | | | | | |
| `record-with-sparkline` | named-list | record-with-array-field | — | none | full | raw-only |
| `per-record-max-min` | named-list | record-with-max-min | percentage-decimal | none | full | aggregated-only |
| `schema-inconsistent` | bare-array | schema-inconsistent | string-enum | none | sparse-nullable | raw-only |
| `polymorphic-records` | bare-array | polymorphic | integer-enum | none | full | raw-only |
| **对比 / 差值 KPI** | | | | | | |
| `current-vs-previous-kpi` | flat-object | — | delta-values | none | full | delta-comparison |
| `actual-target-gap` | named-list | homogeneous | delta-values, percentage-decimal | none | full | delta-comparison |
| **聚合** | | | | | | |
| `aggregated-only` | flat-object | — | — | none | full | aggregated-only |
| `two-arrays-key-join` | multi-top-arrays | homogeneous | byte-values | none | full | stats-plus-rows |
| **完整性 / 基数** | | | | | | |
| `empty-list` | named-list | homogeneous | — | none | empty | raw-only |
| `single-record-list` | named-list | homogeneous | epoch-ms, string-enum | none | singleton | raw-only |
| `sparse-nullable` | named-list | homogeneous | epoch-ms, string-enum | none | sparse-nullable | raw-only |
| `nearly-all-null` | flat-object | — | — | none | nearly-all-null | raw-only |
| **结构陷阱** | | | | | | |
| `bidirectional-pairs` | named-list | homogeneous | string-enum | none | full | raw-only |
| `nested-one-level-too-deep` | nested-object | homogeneous | epoch-ms | none | full | raw-only |
| `partial-success-with-errors` | nested-object | homogeneous | string-enum | none | sparse-nullable | raw-only |

---

## 覆盖空白（低优先级，待补）

- `single-entity-irregular`（不规律时间间隔）
- `nearly-all-null` × `bare-array`
- `polymorphic` × `epoch-ms`
- `grouped-map` × `sparse-nullable`

---

## 已落盘文件（全部 47 个 case）

所有矩阵中的 case 均已写入 `fuzz-data/benchmark/` 目录，文件名与矩阵一致，格式为 `{ meta, data }`。
