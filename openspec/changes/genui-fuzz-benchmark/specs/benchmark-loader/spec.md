## ADDED Requirements

### Requirement: Benchmark loader SHALL read prompt from meta.prompt
benchmark loader SHALL 从 `meta.prompt` 字段获取展示意图，而不使用文件名前缀推断。当 `meta.prompt` 存在时，它的值就是直接传给 LLM 的 prompt string。

#### Scenario: 有效的 meta.prompt
- **WHEN** benchmark loader 读取一个包含 `meta.prompt` 的 case 文件
- **THEN** loader 返回的 prompt 值等于 `meta.prompt` 的内容
- **AND** 不触发 `inferFuzzPrompt` 逻辑

#### Scenario: 缺失 meta.prompt 时报错
- **WHEN** benchmark loader 读取一个 `meta.prompt` 为空或缺失的 case 文件
- **THEN** loader SHALL 抛出明确错误，说明该 benchmark case 缺少 prompt 字段

### Requirement: Benchmark loader SHALL expose meta.evalHints for judge injection
benchmark loader SHALL 在返回值中包含 `evalHints` 字段，供 eval 流程传给 judge-runner。

#### Scenario: evalHints 透传
- **WHEN** benchmark loader 读取一个包含 `meta.evalHints` 的 case 文件
- **THEN** loader 返回对象的 `evalHints` 属性等于 `meta.evalHints` 数组
- **AND** 若 `meta.evalHints` 为空数组，返回空数组而非 undefined

### Requirement: Benchmark loader SHALL operate independently of existing fuzz-loader
benchmark loader SHALL 作为独立模块（`benchmark-loader.ts`）存在，不修改现有 `fuzz-loader.ts` 的导出接口，不影响使用 0–19.json 的现有测试。

#### Scenario: 现有 fuzz-loader 测试不受影响
- **WHEN** 运行 `fuzz-loader.test.ts` 中的所有测试
- **THEN** 所有测试通过，与引入 benchmark-loader 之前结果相同

#### Scenario: benchmark-loader 加载 benchmark 目录
- **WHEN** 调用 benchmark loader 并指定 `fuzz-data/benchmark/` 目录
- **THEN** loader 返回该目录下所有 `.json` 文件（排除 `OUTLINE.md`）的解析结果列表

### Requirement: Benchmark loader SHALL pass data field as the LLM data model
传给 LLM 的数据 SHALL 是 `data` 字段的内容，不包含 `meta` 字段。

#### Scenario: meta 字段不出现在 LLM 输入中
- **WHEN** benchmark loader 准备一个 case 的 LLM 调用参数
- **THEN** data model 参数等于 `file.data`
- **AND** `file.meta` 的任何内容不出现在 data model 中
