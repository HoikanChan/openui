## ADDED Requirements

### Requirement: Regen 阶段的 DSL 生成 SHALL 经由 Eval Generation CLI

Regen 阶段对 fixture 的 DSL 生成 MUST 以子进程调用 Eval Generation CLI（Java Generation SDK 链路）完成：Node 侧进程内从当前 `dslLibrary` 导出 base contract 与用例清单写入 run 工作区，CLI 逐行返回生成结果。Node 侧 MUST NOT 保留任何 TS 拼 prompt 或直连 LLM 的生成路径。既有的并发上限（`EVAL_REGEN_CONCURRENCY`，缺省 6）MUST 经 CLI `--concurrency` 参数透传生效；staging 原子替换与断点续跑行为 MUST 保持不变。

#### Scenario: regen 经 CLI 生成

- **WHEN** 执行 `pnpm eval regen`
- **THEN** 所有 fixture 的 DSL MUST 由 Eval Generation CLI 子进程生成
- **AND** 快照写入仍 MUST 走 staging 目录原子替换，任一失败不污染既有快照

#### Scenario: 并发上限透传

- **WHEN** 设置 `EVAL_REGEN_CONCURRENCY=3` 执行 regen
- **THEN** CLI MUST 以 `--concurrency=3` 启动，同一时刻活跃 LLM 调用数不超过 3

### Requirement: Run manifest SHALL 记录生成链路标识

`run.json` SHALL 包含 `generator` 字段，标记本次 run 的 DSL 生成链路与 base contract 版本（形如 `genui-eval-cli (<contractVersion>)`）。读取旧 run manifest 时 MUST 容忍 `generator` 缺失与已废弃的 `strictness` 字段存在；MUST NOT 迁移或改写历史 run 数据。

#### Scenario: 新 run 写入 generator

- **WHEN** 切换后创建新的 eval run
- **THEN** `run.json` MUST 含 `generator` 字段且包含 base contract 的 `contractVersion`

#### Scenario: 旧 run 可读

- **WHEN** eval status / history 读取一个切换前的旧 run（含 `strictness`、无 `generator`）
- **THEN** 读取 MUST 成功，不报错、不改写该 run 的文件

## REMOVED Requirements

### Requirement: Eval CLI 的 `--strictness` 参数

**Reason**: prompt strictness（standard/strict）概念随 TS 生成路径一并废除；Java Generation SDK 无此概念，strict 模式实际无人使用。

**Migration**: `pnpm eval start` / `pnpm eval regen` 不再接受 `--strictness`；既有调用移除该参数即可（原缺省即 standard，行为无损）。历史 run manifest 中的 `strictness` 字段被容忍读取但不再写入。
