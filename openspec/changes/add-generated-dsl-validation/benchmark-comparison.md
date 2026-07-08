# Generated DSL Validation — Benchmark A/B 对比报告

- **日期**：2026-07-04
- **生成模型**：qwen3-30b-a3b-instruct-2507（temperature=0，enable_thinking=false）
- **Judge**：claude-haiku-4-5-20251001（EVAL_JUDGE_RUNNER=claude-code，两臂同一 judge，自洽可比）
- **样本**：`fuzz-data/benchmark` 44 个 fixture，其中 **39 个**经 Java 管线双臂生成；5 个顶层为数组的 fixture（items-with-number-arrays、items-with-tag-arrays、polymorphic-records、schema-inconsistent、timeseries-tuple-pairs）超出 Java SDK `Map` 数据模型的表达范围（公司内部响应恒为对象信封，SDK 设计使然），两轮均保留原快照，仅用作 judge 噪声参照。

## 实验设计

两臂**均通过 `GenUiGenerator.generateStream()`** 走同一 Java SDK、同一 prompt 组装、同一 transport（自研 OpenAI 兼容 `LlmTransport`，测试专用），唯一变量是校验配置：

| 臂 | 校验配置 | 语义 |
|---|---|---|
| OFF（baseline） | `GenUiValidationConfig.disabled()` | raw delta 直通，LLM 输出原样落盘 |
| ON | `streamingGateWithReask()` | statement gate 按语句校验放行 + Fail-Fast Reask 修复 |

打分：将各臂 39 个 `.dsl` 换入 `benchmark-snapshots/`，跑 `pnpm eval start --suite benchmark`（渲染 → Playwright 截图 → LLM judge 四维评分）。驱动器：`packages/genui-java-sdk/src/test/java/com/huawei/cloudsop/genui/bench/ValidationBenchmarkDriver.java`；原始产物与逐次 LLM 请求体（含 reask 现场）在 `packages/genui-java-sdk/target/benchmark-out/full/{off,on}/`。

- Report A（OFF）：eval run `20260704_152834_2lb5`
- Report B（ON）：eval run `20260704_154735_skw8`

## 总览

| 指标 | OFF 臂 | ON 臂 | 差异 |
|---|---|---|---|
| Judge overall（44 个） | **7.1/10** | **7.2/10** | +0.1 |
| Java 39 个均分 | 6.77 | 6.82 | +0.05（≈持平） |
| **渲染/parse 失败（坏 UI 交付）** | **1（named-list-homogeneous，计 0 分）** | **0** | **✅ 消除** |
| 单遍 gate 通过率 | — | 38/39（97.4%） | — |
| Reask 触发 | — | 1 次（sparse-nullable，修复未成功→INVALID 扣留） | — |
| 平均生成耗时 | 4113ms | 4329ms | +216ms（+5.3%） |
| P50 耗时 | 4011ms | 3995ms | ≈持平 |
| P95 耗时 | 6397ms | 8756ms | +2359ms（reask 尾部） |
| 首个 DSL 输出（流式体感） | 2370ms | 2896ms | +526ms（语句缓冲代价） |

四维均分（0–3）：component_fit 2.64→2.70，data_completeness 2.16→2.20，format_quality 2.30→2.20，layout_coherence 2.61→2.52 —— 均在噪声范围内。

## 判读

1. **校验的核心收益是结构性的，不是美学性的。** OFF 臂有 1/39 直接把 parse 不过的 DSL 交付到了渲染端（named-list-homogeneous，judge 计 0 分）；ON 臂 0 个坏 DSL 流出。这正是 gate 的承诺：**语法非法的语句永远不会到达前端**。对应分差 0→8，是全表最大正向贡献之一。
2. **对已经合法的 DSL，校验不改变（也不应改变）质量分。** qwen3-30b 在 temp=0 下单遍合法率 97.4%，两臂均分 6.77 vs 6.82 持平符合预期——gate 只拦坏语句，不重写好语句。
3. **判分波动主要来自两处非确定性，需要如实声明：**
   - **生成采样方差**：两臂是各自独立调用 LLM（即便 temp=0，服务端输出仍不稳定，冒烟阶段同一 fixture 三跑三样）。current-vs-previous-kpi +8、integer-enum-status +6 与 aggregated-only −6、timeseries-multi-entity-interleaved −7 这类大幅摆动，主体是采样差异而非校验因果。
   - **Judge 噪声**：5 个两轮 DSL 完全相同的对照 fixture 中，2 个分数漂移 ±1（截图字节差异导致缓存未命中后重判）。因此 |Δ|≤1 一律视为噪声。
4. **sparse-nullable（10→2）是 ON 臂唯一被扣留的 fixture，值得单独说明。** 该次生成中模型在 `@Each` 内发明了"块语句"语法（真语法错误），gate 以 `syntax-unexpected-token` 拦截，reask 一次后仍无效 → 最终 INVALID，只保留已接受前缀。本次评测把这个部分前缀当快照打了 2 分；**生产语义下它是 `error` envelope（前端展示失败态），而非把碎 UI 静默交付**。OFF 臂同 fixture 恰好抽到一次合法输出得 10 分——这是采样运气，不是 OFF 管线的能力。完整 reask 现场（错误 DSL、结构化 issues、修复指令、全量 prompt）见 `target/benchmark-out/full/on/sparse-nullable.llm-calls.json`。
5. **延迟代价可接受且集中在两处**：首输出 +0.5s（gate 按完整语句缓冲后才放行）、触发 reask 时的尾延迟（P95 +2.4s）。常态路径（gate 一次通过）P50 持平。

## 结论

以 39 fixture 单轮对比：**Streaming Gate + Fail-Fast Reask 以约 +5% 平均延迟和 +0.5s 首输出延迟为代价，把"坏 DSL 交付率"从 2.6%（1/39）降到 0，并对无法修复的输出给出确定性的失败语义**；对合法输出的视觉/语义质量无损（均分持平）。质量均分要出现可测的提升，需要更大的样本或多轮重复实验以压低采样方差；就本 change 的验收目标（服务端可信校验、坏语句不流出 SDK）而言，行为已按规格达成。

## 实验期间的事故与环境修复记录

| 事项 | 说明 | 处置 |
|---|---|---|
| benchmark-snapshots 被覆盖 | 该目录实为 gitignored（此前误判为已提交），换入 OFF 臂时覆盖了 39 个原快照且无恢复源 | 快照本为 regen 脚本的可再生产物；当前目录内容 = ON 臂输出（44/44 测试通过）。建议在方便时跑 `pnpm eval start --suite benchmark --regen` 重建规范 TS 基线 |
| vitest/vite 版本错配 | 主仓 vitest 4.1.9 与 vite ^5.0.0 配对，`vite/module-runner` 缺失，react-ui-dsl 的 vitest 完全不可运行 | `packages/react-ui-dsl/package.json` vite `^5.0.0 → ^6.0.0`（与 examples/* 先例一致），已随 lockfile 更新 |
| eval-loop 构建回归 | `report-app/main.tsx` 引入 `virtual:react-ui-dsl-view-styles` 后，`eval-loop.ts` 的 vite build 未挂对应插件（此前靠 report-app 缓存掩盖） | 导出 `report-cli.mjs` 的 `reactUiDslViewTargetPlugin` 并挂入 `eval-loop.ts` build |
| codex judge 撞用量上限 | 判分中途 codex（gpt-5.4-mini）耗尽配额，失败点漂移 | 两臂统一切换 claude-haiku-4-5 judge（缓存 key 含模型名，无脏缓存混入） |
| Java SDK reask 拼接 bug | 冒烟阶段发现 reask 成功后 acceptedDsl 出现整程序重复 | 已由另一 agent 修复（worktree 未提交改动），修复后冒烟 4/4 干净通过 |
