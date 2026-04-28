## ADDED Requirements

### Requirement: Judge SHALL accept per-case evalHints as supplementary scoring context
eval judge SHALL 在收到 `evalHints` 数组时，将其内容作为附加评分条件注入 judge prompt，补充全局 rubric 的 4 个维度评分，不替换全局 rubric。

#### Scenario: evalHints 存在时注入 judge prompt
- **WHEN** judge-runner 调用 judge 时传入非空 `evalHints` 数组
- **THEN** judge 的 system prompt SHALL 在全局 rubric 之后附加一个 "Case-specific hints" 段落，列出所有 evalHint 条目
- **AND** judge 返回的评分结构与无 evalHints 时相同（component_fit / data_completeness / format_quality / layout_coherence / overall / feedback）

#### Scenario: evalHints 为空时行为不变
- **WHEN** judge-runner 调用 judge 时传入空数组或不传 evalHints
- **THEN** judge prompt 与现有全局 rubric 完全相同，无附加段落

### Requirement: Judge-runner SHALL pass evalHints from benchmark loader result to judge
`judge-runner.ts` SHALL 在处理 benchmark case 时，从 benchmark loader 返回的 `evalHints` 字段读取内容并转发给 judge。

#### Scenario: benchmark case 的 evalHints 流转到 judge
- **WHEN** eval-loop 运行一个 benchmark case
- **THEN** judge-runner 从该 case 的 loader 结果中读取 `evalHints`
- **AND** 调用 judge 时将 evalHints 作为参数传入
- **AND** judge 调用记录中可验证 evalHints 已被包含在 judge prompt 中
