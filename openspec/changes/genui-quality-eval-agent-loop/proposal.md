## Why

`react-ui-dsl` 是一个 LLM 驱动的生成式 UI 库，但目前缺乏对生成效果的系统性评测：无法量化“呈现质量”、无法自动发现质量回归，也缺少一套可重复的开发者优化闭环。现有 e2e/fuzz 测试主要验证正确性（能不能跑），不评估质量（组件选型是否合适、数据是否完整展示、格式化是否正确、布局是否合理）。

同时，这个优化闭环需要兼容多种开发者 agent 工作流，包括 Codex、Claude Code 和 opencode。把核心流程绑定到某一家的 skill、runtime 或自动拉起机制，会让后续维护和扩展变复杂。

## What Changes

- **新增** eval pipeline：对每个 fixture 渲染结果截图，调用 VLM judge 按 rubric 打分，并把结果写入结构化 `report-data.json`
- **新增** run workspace 与标准 handoff contract：由 orchestrator 为每轮评测生成 `task-bundle/`，供开发者选择任意 agent 执行；agent 完成后回写 `result-bundle/`
- **新增** agent-neutral verifier：读取 `result-bundle/` 后重跑评测、计算 delta、检测回归、记录 history，并生成下一步建议
- **新增** human-in-loop 可选介入：`corrections.json` 支持人工修正 judge 分数或给 prompt 优化建议，分别流入 calibration 或 optimizer
- **新增** 只读诊断视图：report-app 可显示 judge score、failing patterns、delta 摘要等评测结果，但不承担流程控制
- 不替换现有 vitest e2e/fuzz 测试；eval loop 作为并列 CLI 工作流存在
- 不自动拉起 Codex / Claude Code / opencode；由开发者手动选择 agent
- 默认不自动 commit；先输出验证结果和提交建议

## Capabilities

### New Capabilities

- `eval-pipeline`: 截图捕获 + VLM judge + rubric 打分，输出 `report-data.json`
- `agent-handoff`: 生成标准 `task-bundle/`，兼容 Codex、Claude Code、opencode 的手动交接
- `agent-verification`: 读取标准 `result-bundle/`，重跑评测并验证质量增益与回归
- `judge-calibration`: 处理 `corrections.json`，校准 judge rubric，使 judge 与人工判断对齐
- `eval-loop-orchestrator`: 管理 run workspace、状态、history 和多轮开发者驱动优化流程

## Impact

- `packages/react-ui-dsl/src/__tests__/e2e/` — 扩展 report pipeline，并新增 eval run workspace、handoff contract、verifier、history 管理
- `packages/react-ui-dsl/src/__tests__/e2e/report-data.json` — schema 扩展，加入 `judge_scores`、`failing_patterns` 和 delta 相关信息
- 新增 Playwright 依赖（devDependency）
- 新增 VLM 调用（复用现有 `LLM_API_KEY` / `LLM_BASE_URL` 配置，支持 multimodal model）
- 新增 `src/__tests__/e2e/eval/` 目录，存放 eval pipeline、bundle writer/reader、verifier、orchestrator 模块
- report-app 扩展为只读诊断界面，展示 judge / delta 数据但不负责控制流程
