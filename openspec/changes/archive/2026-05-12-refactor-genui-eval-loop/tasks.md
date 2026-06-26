## 1. 准备工作

- [x] 1.1 在 `run-manifest.ts` 的 `RunManifest` 类型增加可选 `phases?: { regen?: PhaseStatus; render?: PhaseStatus; screenshot?: PhaseStatus; judge?: PhaseStatus }`，并定义 `type PhaseStatus = 'done' | 'failed'`
- [x] 1.2 在 `run-manifest.ts` 增加 `markPhaseDone(runId, phase)` 和 `getPhaseStatus(runId, phase)` 工具函数
- [x] 1.3 把 `eval/.judge-cache/`、`eval/.report-app-cache/`、`eval/.regen-staging/` 加入 `packages/react-ui-dsl/.gitignore`

## 2. Regen 阶段独立化与并发原子写

- [x] 2.1 新建 `eval/regen.ts` 模块，导出 `regenFixtures({ runId, suite, fixtureIds, concurrency })`，使用 promise worker pool（复用 `judge.ts` 模式）
- [x] 2.2 把 `llm.ts:callLLM` 抽出为可独立调用的 `generateDsl({ prompt, dataModel, strictness, model, apiKey, baseURL, httpsProxy })`，不再依赖 vitest 环境
- [x] 2.3 regen worker 写入 staging：`eval/.regen-staging/<run-id>/<fixture-id>.dsl`，全部成功才 `renameSync` 到 `snapshotsDirForSuite(suite)`
- [x] 2.4 任意 fixture 失败 → 不触发任何 rename；保留 staging 供调试，向调用方抛错
- [x] 2.5 解析 `EVAL_REGEN_CONCURRENCY` 环境变量，默认 6，下限 1，沿用 judge 的解析约定
- [x] 2.6 修改 `llm.ts:loadOrGenerate`：当 `REGEN_SNAPSHOTS=1` 且 snapshot 不存在时，**抛错**而非调用 LLM，错误信息引导用户先跑 `pnpm eval regen`
- [x] 2.7 验证：手动设 `EVAL_REGEN_CONCURRENCY=2`，跑 benchmark，确认并发上限生效；终止进程检查 snapshot 目录未变化
- [x] 2.8 在 `regen.test.ts` 添加单测：mock `generateDsl`，验证并发上限、顺序无关、全部成功才 rename、失败不写入

## 3. 流水线阶段化与 CLI 命令

- [x] 3.1 拆分 `runEval` 内联流程为四个函数：`runRegenPhase`、`runRenderPhase`、`runScreenshotPhase`、`runJudgePhase`，每个完成时调用 `markPhaseDone`
- [x] 3.2 在 `cmdStart` 入口检查现有 `run-id`（如复用）的 `phases` 状态，跳过已 `done` 的阶段
- [x] 3.3 新增 `cmdRegen(runId?)`、`cmdRender(runId?)`，分别调用单阶段函数；不传 runId 时为新 run；前置阶段未完成时报错指引
- [x] 3.4 现有 `cmdJudge` 改为基于 phases 判断而非"screenshots 是否存在"的间接信号；保留对旧 run 的兼容（无 phases 字段时回退到现有行为）
- [x] 3.5 在 `cmdStatus` 输出中追加 `Phases:` 行，列出 `regen / render / screenshot / judge` 各自状态
- [x] 3.6 更新 `main()` switch case 注册 `regen`、`render` 命令，更新 help 文本
- [x] 3.7 验证：先跑 `pnpm eval regen` → 检查 phases.regen=done；再跑 `pnpm eval render <run-id>` → 检查 phases.render=done；最后 `pnpm eval judge <run-id>` 完成全流程
- [x] 3.8 在 `eval-loop.test.ts`（如不存在则新建）添加流程测试：mock 各阶段函数，验证 `cmdStart` 在 phases 已 done 时不重复调用

## 4. Judge 结果按内容哈希缓存

- [x] 4.1 新建 `eval/judge-cache.ts`：导出 `computeJudgeCacheKey({ dsl, screenshotPath, rubricText, judgeModel })` 与 `readJudgeCache(key)` / `writeJudgeCache(key, score)`
- [x] 4.2 缓存目录 `eval/.judge-cache/<key[0:2]>/<key>.json`；并发安全：写入用 `.tmp` + rename
- [x] 4.3 在 `judge.ts:judgeFixture` 调用 `invokeRunner` 之前先查缓存，命中直接返回；未命中正常调用后写缓存
- [x] 4.4 `rubricText` 取实际 `buildJudgeSystemPrompt(rubricOverride, evalHints)` 渲染后的完整字符串，自然覆盖 rubric / hints 变化
- [x] 4.5 `judgeModel` 取 `resolveModel(runnerType)` 的返回值
- [x] 4.6 新增 `cmdCacheClear()` 命令（`pnpm eval cache:clear`）：删除整个 `.judge-cache/` 目录
- [x] 4.7 在 `judge.test.ts` 添加缓存单测：第二次相同输入不调用 runner；rubric 改动后 miss；不同 model 不串
- [x] 4.8 验证：连续跑两次相同 run（regen 已稳定），第二次 judge 阶段日志应显示"cache hit"占绝大多数

## 5. Verify 增量判分

- [x] 5.1 在 `cmdVerify` 中读取 `result-bundle/claimed-affected-fixtures.json`，未提供或为空时退化为全量重判并 stderr 提示
- [x] 5.2 受影响 fixture：调用 `runScreenshotPhase`（只对该子集）+ `runJudgePhase`（同样只对该子集）
- [x] 5.3 未受影响 fixture：从 baseline `report-data.json` 复制其 `judge_scores`，不调用 runner
- [x] 5.4 vitest 整轮仍然跑（regression gate）；任一 fixture 在 vitest 失败 → 替换其 score 为 `makeFailedFixtureScore`
- [x] 5.5 在 verify 总结输出中打印"复用 baseline 的 fixture 数量"和"重判 fixture 数量"
- [x] 5.6 验证：构造一个 5 fixture claimed 列表，跑 verify，确认日志只对这 5 个 fixture 调 runner
- [x] 5.7 在 `delta-verifier.test.ts` 或新建 `verify.test.ts` 添加增量复用测试：mock judge runner 调用次数，确认正确

## 6. Vite Build 缓存

- [x] 6.1 新建 `eval/report-app-cache.ts`：导出 `computeReportAppCacheKey()`（hash 报告 app 源码文件树 + 关键依赖版本）与 `restoreReportAppCache(targetDir, key)` / `saveReportAppCache(sourceDir, key)`
- [x] 6.2 修改 `buildReportApp`：先算 key → 缓存命中则 `cp` 资源到 run 工作区，跳过 `vite build`；未命中正常 build 完后 saveReportAppCache
- [x] 6.3 在缓存命中分支上仍调用统一的 `injectReportData(reportDir)` 注入当前 `report-data.json`
- [x] 6.4 验证：连跑两次 `pnpm eval start`，第二次 vite 不再启动（用日志或耗时观测）；修改 `report-app/main.tsx` 后第三次重新 build

## 7. Report 注入与 UI 提升

- [x] 7.1 抽出 `injectReportData(reportDir)`：读 `report-data.json`，转义 `<` 等字符，替换 `index.html` 内 `<script id="e2e-report-data" type="application/json">…</script>` 内容
- [x] 7.2 替换 `buildReportApp` 原内联代码为调用 `injectReportData`
- [x] 7.3 `runJudgePhase` 在每次更新 `report-data.json` 后调用 `injectReportData`（与决策 8 一致）
- [x] 7.4 `cmdVerify` 在写完更新版 `report-data.json` 后调用 `injectReportData`
- [x] 7.5 修改 `report-app/main.tsx:PreviewCard`：把 overall 分数提到 `entry-header` 与 status 同行展示
- [x] 7.6 修改 `report-app/main.tsx:JudgeScorePanel`：去掉外层 `<details>`，feedback 段落 + 四条 ScoreBar + issue chips 默认可见
- [x] 7.7 `degraded === true` 的判分 → 卡片顶部加 `.judge-degraded-banner` 警告
- [x] 7.8 `report-app/styles.css` 配套样式：判分区域分隔线、ScoreBar 紧凑布局、degraded banner 颜色
- [x] 7.9 验证：跑一次 bench → 打开 `runs/<id>/index.html`，确认分数和 feedback 默认可见、无需展开

## 8. 增量持久化 Judge 进度

- [x] 8.1 在 `runJudgePhase` 中用 in-process mutex 串行化 `report-data.json` 的读-改-写
- [x] 8.2 写入采用 `.tmp` + rename 原子模式，防止崩溃留下半文件
- [x] 8.3 每完成单 fixture 判分后立即合并到 `entries[].judgeScore` 与顶层 `judge_scores`，写盘，并调用 `injectReportData`
- [x] 8.4 `runJudgePhase` 入口若发现 `report-data.json` 中已有部分 fixture 的 `judgeScore`（且未通过 `EVAL_FORCE_REJUDGE=1` 重判）→ 跳过对应 fixture
- [x] 8.5 验证：在 judge 阶段中途 Ctrl+C，重新 `pnpm eval judge <run-id>`，确认只补判缺失 fixture，已判 fixture 数量被保留

## 9. 文档与 Skill 同步

- [x] 9.1 更新 `packages/react-ui-dsl/README.md`：补 `EVAL_REGEN_CONCURRENCY`、`pnpm eval regen`、`pnpm eval render`、`pnpm eval cache:clear`、verify 增量行为说明
- [x] 9.2 更新 `.claude/skills/react-ui-dsl-genui-eval-loop/SKILL.md`：同上，补阶段化命令与 phases 状态语义
- [x] 9.3 在 README 与 SKILL 中加一节"故障恢复"小章节：进程被砍后如何用阶段化命令续跑

## 10. 端到端验证

- [x] 10.1 走通完整新流程：`pnpm eval start --suite benchmark --regen` 期望 < 10 min 端到端完成
- [x] 10.2 模拟超时：在 regen 阶段 kill 进程 → 再次 `pnpm eval regen <run-id>` → 期望仅补未生成 fixture
- [x] 10.3 模拟超时：在 judge 阶段 kill → 再次 `pnpm eval judge <run-id>` → 期望只判剩余 fixture
- [x] 10.4 跑一次完整 verify（小规模 claimed 列表）→ 期望耗时显著低于 baseline 全量耗时
- [x] 10.5 浏览器打开最终 `index.html`，确认 judge feedback、四维分、issue chips、overall 分数与 delta 全部可见且无需点击
- [x] 10.6 跑现有 unit test 套件（`pnpm --filter @cloudsop/openui-react-ui-dsl test`），确认无回归
