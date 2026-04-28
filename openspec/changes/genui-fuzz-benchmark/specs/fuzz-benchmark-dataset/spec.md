## ADDED Requirements

### Requirement: Benchmark cases SHALL use a two-field envelope format
每个 benchmark case 文件 SHALL 是一个 JSON 对象，顶层只有两个 key：`meta`（元数据）和 `data`（原始 payload）。`data` 的值可以是任意 JSON 类型（object、array、primitive）。

#### Scenario: data 是对象
- **WHEN** case 的 payload 是单实体对象
- **THEN** 文件结构为 `{ "meta": {...}, "data": { "field": ... } }`
- **AND** loader 通过 `file.data` 取得 payload，通过 `file.meta` 取得元数据

#### Scenario: data 是数组
- **WHEN** case 的 payload 是裸数组
- **THEN** 文件结构为 `{ "meta": {...}, "data": [{...}, {...}] }`
- **AND** `meta` 字段不出现在 `data` 内部

### Requirement: meta 对象 SHALL 包含 prompt、taxonomy、evalHints 三个字段
`meta` 对象 SHALL 包含：`prompt`（string，展示意图描述）、`taxonomy`（string[]，维度取值列表）、`evalHints`（string[]，case 级评判条件）。

#### Scenario: 字段完整的 meta
- **WHEN** loader 读取一个 benchmark case 文件
- **THEN** `meta.prompt` 是非空字符串，可直接作为 LLM prompt 使用
- **AND** `meta.taxonomy` 是包含至少一个维度取值的数组
- **AND** `meta.evalHints` 是包含至少一条断言的数组

### Requirement: taxonomy 取值 SHALL 来自 OUTLINE.md 定义的六个维度
每个 `taxonomy` 标签 SHALL 是 Dim A–F 中某个维度的合法取值，不允许使用自由文本标签。

#### Scenario: taxonomy 标签可映射到维度
- **WHEN** 解析一个 benchmark case 的 `taxonomy` 数组
- **THEN** 每个标签都能在 OUTLINE.md 的维度定义表中找到对应行
- **AND** 至少包含一个 Dim A（Shape）取值

### Requirement: benchmark case 集合 SHALL 覆盖 OUTLINE.md 矩阵中所有已定义 case
benchmark case 集合 SHALL 包含矩阵中列出的所有 49 个 case 文件，文件名与矩阵中的文件名列一致，后缀为 `.json`。

#### Scenario: 矩阵 case 全部落盘
- **WHEN** 列举 `fuzz-data/benchmark/` 目录下的所有 `.json` 文件（排除草稿）
- **THEN** 文件名集合与 OUTLINE.md 矩阵的文件名列完全对应

### Requirement: benchmark case 的 evalHints SHALL 使用断言式语言
每条 evalHint SHALL 描述一个可观测的输出属性，使用 "must" / "must not" / "should" 等断言词，不使用模糊描述。

#### Scenario: evalHint 描述格式化要求
- **WHEN** judge 收到包含 evalHint "epoch ms timestamps must be formatted as human-readable dates" 的 case
- **THEN** judge 在评分 format_quality 维度时 SHALL 将此条件作为额外扣分依据

#### Scenario: evalHint 描述派生要求
- **WHEN** judge 收到包含 evalHint "must derive per-device series using Filter on portResId" 的 case
- **THEN** judge 在评分 data_completeness 维度时 SHALL 检查输出是否对多实体进行了区分展示
