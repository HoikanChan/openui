## Context

`react-ui-dsl` 已经有较完善的正确性测试基础设施：fixture-based vitest e2e、fuzz 测试、以及 `report-cli.mjs` 驱动的 report pipeline。当前缺的不是“能不能跑”，而是“跑出来的 UI 质量如何”以及“开发者如何在多种 agent 工具之间稳定地做质量迭代”。

这次 change 的第一用户是**开发者**，而不是评测平台运营者。开发者已经在 terminal、editor、Codex、Claude Code、opencode 等工具里工作，因此主控制面应保持在 CLI，而不是新建一个浏览器控制台。

目标是建立一条**开发者驱动的 agent-neutral eval loop**：

1. eval core 生成统一评测产物与任务上下文
2. 开发者选择一个 agent 执行优化
3. agent 回写统一结果产物
4. eval core 做验证、记录 history、给出下一步建议

## Goals / Non-Goals

**Goals**

- 对每个 fixture 的生成结果做多维度质量评分，并输出结构化报告
- 为 Codex、Claude Code、opencode 提供等价支持，而不把系统绑定到某一家 agent 的 skill 或 runtime
- 用统一 `task-bundle/` 和 `result-bundle/` 作为 handoff contract
- 保持开发者主流程在 CLI 中，report-app 仅做只读诊断
- 对 agent 改动做可重复的 delta 验证和回归检测
- 支持人工可选介入：校准 judge 或直接给优化建议

**Non-Goals**

- 不自动拉起任何 agent 进程
- 不构建浏览器里的流程控制台
- 不默认自动 commit 或无限自闭环迭代
- 不替换现有 vitest e2e/fuzz 测试
- 不评估首屏性能或 a11y
- 不做跨 fixture 的 A/B prompt 实验或 CI 平台化编排

## Architecture

### A1: Eval core 与 agent adapters 分层

系统分成三层：

```text
eval core
  -> baseline eval
  -> report-data.json
  -> task-bundle/
developer
  -> choose Codex / Claude Code / opencode
agent adapter
  -> renders agent-specific instructions from the same task bundle
agent
  -> edits code
  -> writes result-bundle/
eval core
  -> verify delta
  -> detect regression
  -> update history
  -> suggest next action
```

核心层只关心评测、handoff contract、验证和状态；适配层只负责给不同 agent 提供等价入口说明。这样可以保持系统对 agent 产品形态中立。

### A2: Run workspace 是单轮闭环的真边界

每次 `eval-loop start` 生成一个 run workspace：

```text
src/__tests__/e2e/eval/runs/<run-id>/
  run.json
  report-data.json
  eval-history.json
  task-bundle/
    summary.md
    constraints.md
    targets.json
    failing-patterns.json
    fixtures/
    screenshots/
    adapters/
      codex.md
      claude-code.md
      opencode.md
  result-bundle/
    result.json
    change-summary.md
    touched-files.json
    claimed-affected-fixtures.json
```

- `run.json`：记录 run id、阶段、状态、输入参数、关联 report 路径
- `report-data.json`：本轮评测真源
- `task-bundle/`：交给任意 agent 的统一输入
- `result-bundle/`：agent 完成后的统一输出

run workspace 是所有 CLI 命令、delta 验证、history 持久化的锚点，避免“上一轮 report 到底是哪份”的歧义。

### A3: `report-data.json` 是唯一评测真源

不再引入独立 `judge-report.json`。每轮评测的 judge 输出、failing patterns、delta 结果都挂在 run 对应的 `report-data.json` 上。

理由：

- 当前仓库已经有 `report-data.json` 及 report-app 消费链路
- 避免同一轮 fixture 运行被拆成两份 JSON 后再 join
- 后续 report-app 展示 judge / delta 时可以复用同一数据源

### A4: CLI 显式分阶段，不做“阻塞等待 agent”的魔法主循环

不把主入口设计成一个会卡住等待 agent 的黑盒循环，而是提供显式阶段命令：

- `node eval-loop.mjs start [--regen]`
- `node eval-loop.mjs status <run-id>`
- `node eval-loop.mjs verify <run-id>`
- `node eval-loop.mjs calibrate <run-id>`

这更符合“开发者自己选择 agent”的使用方式。orchestrator 负责维护状态机，而不是控制 agent 生命周期。

### A5: Skill 只属于 adapter 层

如果某个 agent 支持 skill，可以在 `task-bundle/adapters/` 中提供对应说明或 starter prompt。但 skill 不能成为系统主协议。

主协议必须是文件合同，因为：

- Codex、Claude Code、opencode 的能力和集成方式并不一致
- skill 机制无法保证跨 agent 一致性
- 文件合同更容易调试、审计和回放

## Eval Flow

### F1: Baseline eval

`eval-loop start` 做这些事：

1. 生成 run workspace
2. 复用现有 report pipeline 获取 renderable report 视图
3. 对每个 fixture 截图
4. 调用 judge 生成 per-fixture `judge_scores`
5. 聚合 `failing_patterns`
6. 写 `report-data.json`
7. 生成 `task-bundle/`
8. 将 run 状态置为 `waiting_for_agent`

### F2: Manual handoff

开发者查看 `task-bundle/adapters/<agent>.md`，选择一个 agent 执行优化。agent 根据 bundle 改代码，并回写 `result-bundle/`。

系统不要求 agent 直接写自由格式的 `agent-result.md`；而是要求写入标准结果包，便于 verifier 稳定消费。

### F3: Verification

`eval-loop verify <run-id>` 做这些事：

1. 读取 `result-bundle/`
2. 根据声明和 git diff 识别 touched files / claimed fixtures
3. 用 `--regen` 重跑相关生成路径
4. 重跑全量 fixture 评测作为最终 gate
5. 计算 per-fixture 与 overall delta
6. 若无回归则写入 success summary；若有回归则写入 review-needed summary
7. 更新 `eval-history.json` 和 `run.json`

这里最终 gate 必须是全量 fixture，不以 sample 作为提交放行依据。全局 prompt/schema 改动的影响范围天然是全局的。

## Judge Calibration

### C1: `corrections.json` 进入统一状态机

`corrections.json` 条目不再只靠 `processed: true/false` 控制。应使用显式状态：

- `pending`
- `applied`
- `failed`
- `forwarded_to_optimizer`

其中：

- `target = "judge"` 的条目进入 calibration
- `target = "prompt"` 的条目绕过 calibration，直接进入 optimizer task bundle

这样可以避免失败后直接吞掉 correction，也避免 `target=prompt` 无法被正确消费和回写状态。

### C2: Calibration 是显式子流程

`eval-loop calibrate <run-id>`：

1. 读取 `corrections.json` 的 `pending` judge 条目
2. 生成 calibration task bundle
3. 开发者选择 agent 进行 rubric 调整
4. 重跑 judge 验证是否与人工修正对齐
5. 按结果把 correction 标成 `applied` 或 `failed`

若 calibration 失败，保留失败信息并等待进一步人工处理，而不是静默吞掉。

## Report App

report-app 仍然有价值，但只承担只读诊断职责。建议展示：

- overall score
- per-fixture judge scores
- failing patterns
- delta summary
- run id / generated at
- touched files / claimed affected fixtures

不把“启动 agent”“是否提交”“是否继续下一轮”这类动作放进浏览器控制面。

## Risks / Trade-offs

- **多 agent 支持带来协议设计成本**：通过固定 bundle contract 降低耦合，适配差异仅留在 adapter docs
- **截图不稳定会污染分数**：eval pipeline 必须自己确保 report 视图可访问并等待稳定渲染；截图失败时本轮标记为 degraded，不与正常 run 直接比较
- **agent 声称影响范围不可信**：verifier 同时参考 `claimed-affected-fixtures` 与真实 touched files，并用全量评测做最终 gate
- **手动选择 agent 比全自动慢**：这是有意权衡，优先保证开发者可控性和跨 agent 一致性
- **默认不 auto-commit 减少自动化程度**：但可显著降低错误提交和黑箱行为，适合 v1

## Open Questions

- 是否在当前 change 内就给 report-app 加上最小 delta 可视化，还是先只提供 JSON/Markdown 摘要？
- `result-bundle/result.json` 的字段是否需要再细化到“改动类型、风险自评、建议后续动作”？
- 是否需要一个显式 `eval-loop finalize --commit` 命令，作为默认不自动提交下的可选收尾步骤？
