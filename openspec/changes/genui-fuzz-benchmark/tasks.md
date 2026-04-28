## 1. Suite 接入（eval-loop.ts）

- [ ] 1.1 将 `eval-loop.ts:39` 的 `EvalSuite` 类型扩展为 `"e2e" | "fuzz" | "benchmark"`
- [ ] 1.2 在 `runVitest` 中添加 `suite === "benchmark"` 分支，路由到 `dsl-benchmark.test.tsx`
- [ ] 1.3 在 `snapshotsDirForSuite` 中添加 `benchmark` → `benchmark-snapshots` 目录映射
- [ ] 1.4 在 `cmdStart` 的 suite 解析逻辑中允许 `"benchmark"` 值通过
- [ ] 1.5 在 `runEval` 的 judgeInputs 构建循环（eval-loop.ts:195–205）中，从 entry 读取 `evalHints` 并传给 `judgeFixtures`

## 2. evalHints 注入 judge

- [ ] 2.1 在 `E2EReportEntry` 类型（`report.ts`）中添加可选字段 `evalHints?: string[]`
- [ ] 2.2 修改 `eval/judge.ts` 的 `buildJudgeSystemPrompt`，增加可选 `evalHints?: string[]` 参数；非空时在全局 rubric 末尾追加 "## Case-specific hints\n" + hints
- [ ] 2.3 修改 `judgeFixtures` 调用签名，透传 `evalHints` 到 `buildJudgeSystemPrompt`
- [ ] 2.4 验证无 evalHints 时 judge prompt 与现有输出完全相同（回归保护）

## 3. Benchmark Loader

- [ ] 3.1 新建 `src/__tests__/e2e/fuzz-data/benchmark-loader.ts`，导出 `loadBenchmarkCases(dir: string): BenchmarkCase[]`
- [ ] 3.2 实现：读取目录下所有 `.json` 文件（排除非 JSON），解析 envelope，`meta.prompt` 缺失时抛出明确错误
- [ ] 3.3 返回类型 `BenchmarkCase = { id, prompt, dataModel, evalHints, taxonomy }`，`dataModel` 为 `file.data`，不含 `meta`
- [ ] 3.4 新建 `benchmark-loader.test.ts`，覆盖：正常加载、缺失 prompt 报错、空 evalHints 返回空数组、data 为数组时正常解析

## 4. 测试入口 dsl-benchmark.test.tsx

- [ ] 4.1 新建 `src/__tests__/e2e/dsl-benchmark.test.tsx`，对称于 `dsl-fuzz.test.tsx`
- [ ] 4.2 用 benchmark-loader 加载 `fuzz-data/benchmark/` 目录，生成 vitest test case 列表
- [ ] 4.3 每个 case：调用 LLM（使用 `meta.prompt`）→ render → snapshot（写入 `benchmark-snapshots/`）
- [ ] 4.4 将 `evalHints` 写入对应 `E2EReportEntry`，供 judge 阶段读取
- [ ] 4.5 支持 `--fixture` 过滤（环境变量 `REACT_UI_DSL_E2E_SUITE=benchmark` + fixture filter）
- [ ] 4.6 验证 `pnpm eval start --suite=benchmark` 端到端可运行（即使 case 数量少）

## 5. Benchmark Case 数据文件

- [ ] 5.1 清理已有草稿（flat-object-single.json、named-list.json、paginated-list.json），与 OUTLINE 矩阵对齐后重新生成
- [ ] 5.2 写入 Shape 类 case：flat-object-single、named-list-homogeneous、paginated-list、cursor-paginated-items、multi-top-arrays、api-response-envelope、meta-plus-data-envelope
- [ ] 5.3 写入列式/元组 case：timeseries-columnar、timeseries-tuple-pairs、matrix-row-col-values
- [ ] 5.4 写入动态 key 字典 case：object-map-by-id、grouped-object-of-arrays、date-keyed-buckets
- [ ] 5.5 写入嵌套/层级 case：array-with-nested-arrays、nested-object-with-array、tree-embedded-children、flat-parentid-reference
- [ ] 5.6 写入图关系 case：nodes-edges-graph、adjacency-list-graph
- [ ] 5.7 写入时序 case：timeseries-single-entity、timeseries-multi-entity-interleaved、timeseries-unordered、timeseries-stats-plus-rows、timeseries-min-max-band、timeseries-multi-entity-unaligned
- [ ] 5.8 写入值编码挑战 case：integer-enum-status、byte-large-values、boolean-as-integer、percentage-as-decimal、cross-magnitude-values
- [ ] 5.9 写入原始值数组 case：primitive-number-array、primitive-string-array、labeled-ratio-array、unlabeled-ratio-array
- [ ] 5.10 写入 Record 内部挑战 case：record-with-sparkline、per-record-max-min、schema-inconsistent、polymorphic-records
- [ ] 5.11 写入对比/差值 KPI case：current-vs-previous-kpi、actual-target-gap
- [ ] 5.12 写入聚合 case：aggregated-only、two-arrays-key-join
- [ ] 5.13 写入完整性/基数 case：empty-list、single-record-list、sparse-nullable、nearly-all-null
- [ ] 5.14 写入结构陷阱 case：bidirectional-pairs、nested-one-level-too-deep、partial-success-with-errors
- [ ] 5.15 验证所有文件可被 JSON.parse 解析，meta.prompt / taxonomy / evalHints 均非空

## 6. OUTLINE 维护与收尾

- [ ] 6.1 将 OUTLINE.md 中"已写入数据的文件（草稿）"替换为最终状态
- [ ] 6.2 将"覆盖空白"更新，移除已补充条目
- [ ] 6.3 将 `benchmark-snapshots/` 加入 `.gitignore`
