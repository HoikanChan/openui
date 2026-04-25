## 1. Eval Pipeline — baseline report + screenshot + judge

- [ ] 1.1 添加 Playwright 为 devDependency，配置 chromium headless
- [ ] 1.2 新建 `src/__tests__/e2e/eval/` 目录，建立 eval 模块入口
- [ ] 1.3 实现 `run-manifest.ts`：创建 `runs/<run-id>/` 目录、写入 `run.json`、维护 run 状态
- [ ] 1.4 实现 `screenshot.ts`：基于现有 report pipeline 获取 renderable 视图，对每个 fixture 截图并写入 run-scoped 目录
- [ ] 1.5 实现 `rubric.ts`：定义 judge rubric prompt（4 维度 + overall），支持配置覆盖
- [ ] 1.6 实现 `judge.ts`：接收 `(fixtureId, dsl, dataModel, screenshotPath | null)`，调用 VLM（multimodal，temperature=0），返回 `JudgeScore`
- [ ] 1.7 实现 `failing-patterns.ts`：聚合 `JudgeScore[]`，识别共性失分模式，生成 `FailingPattern[]`
- [ ] 1.8 扩展 `report-data.json` schema：在 run-scoped `report-data.json` 中加入 `judge_scores`、`failing_patterns` 和 degraded-run 元数据
- [ ] 1.9 实现 `eval-loop.mjs start [--regen]`：完成 baseline eval，写入 `report-data.json` 与 run metadata

## 2. Agent Handoff — task bundle / result bundle contract

- [ ] 2.1 定义 `task-bundle/` 契约：`summary.md`、`constraints.md`、`targets.json`、`failing-patterns.json`、fixtures/screenshot 引用
- [ ] 2.2 实现 `task-bundle-writer.ts`：根据 run 的 `report-data.json` 生成统一 handoff bundle
- [ ] 2.3 实现 adapter docs 生成：输出 `task-bundle/adapters/codex.md`、`claude-code.md`、`opencode.md`
- [ ] 2.4 定义 `result-bundle/` 契约：`result.json`、`change-summary.md`、`touched-files.json`、`claimed-affected-fixtures.json`
- [ ] 2.5 实现 `result-bundle-reader.ts`：读取并校验标准结果包，而不是自由格式 markdown

## 3. Verification — delta / regression / history

- [ ] 3.1 实现 `delta-verifier.ts`：基于 baseline run 和当前代码状态计算 per-fixture 与 overall score delta
- [ ] 3.2 实现全量 fixture regression gate：最终验收必须重跑全量评测，不以 sample 作为放行条件
- [ ] 3.3 实现 `verification-summary.ts`：输出 success / review-needed / stalled 等结果摘要
- [ ] 3.4 实现 `eval-history.ts`：维护 verified iteration history、stall counter、last known score
- [ ] 3.5 实现 `eval-loop.mjs verify <run-id>`：消费 `result-bundle/`，执行验证并更新 run 状态与 history
- [ ] 3.6 实现默认非自动提交策略：验证通过时给出 commit recommendation，而非直接 `git commit`

## 4. Judge Calibration — optional human corrections flow

- [ ] 4.1 定义 `corrections.json` schema：`target`、`state`、`score_corrections?`、`text_feedback?`、`rubric_hash?`、时间戳等字段
- [ ] 4.2 实现 correction state machine：支持 `pending | applied | failed | forwarded_to_optimizer`
- [ ] 4.3 实现 calibration task bundle 生成：读取 pending 的 judge-targeted corrections 与当前 rubric
- [ ] 4.4 实现 `calibration-verifier.ts`：校准后重跑 judge，验证与人工修正对齐（带容差）
- [ ] 4.5 实现 prompt-targeted corrections forwarding：将 `target=prompt` 条目显式注入下一轮 optimization task bundle
- [ ] 4.6 实现 `eval-loop.mjs calibrate <run-id>`：处理 judge-targeted corrections 并回写 correction state

## 5. Orchestrator UX — explicit CLI stages

- [ ] 5.1 实现 `eval-loop.mjs status <run-id>`：显示当前 run 阶段、验证结果、待执行动作
- [ ] 5.2 在 `run.json` 中定义显式 run 状态：如 `created`、`waiting_for_agent`、`verifying`、`verified`、`stalled`
- [ ] 5.3 确保 orchestrator 不自动拉起 Codex / Claude Code / opencode，只生成 handoff 指引
- [ ] 5.4 为 report-app 加最小只读诊断字段：judge scores、failing patterns、delta summary、run id

## 6. Testing and Docs

- [ ] 6.1 为 `failing-patterns.ts` 写单元测试，验证 pattern 聚合逻辑
- [ ] 6.2 为 `task-bundle-writer.ts` / `result-bundle-reader.ts` 写单元测试，验证 contract 读写
- [ ] 6.3 为 `delta-verifier.ts` 写单元测试，验证 delta 与全量 regression gate
- [ ] 6.4 为 `calibration-verifier.ts` 写单元测试，验证 correction state 与容差判断
- [ ] 6.5 在 `packages/react-ui-dsl/CLAUDE.md` 和相关开发文档中补充 eval loop 使用说明：如何 start run、如何选择 agent、如何回写 result bundle、如何 verify/calibrate
