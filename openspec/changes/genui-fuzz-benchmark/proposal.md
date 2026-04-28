## Why

当前 fuzz 数据集（20 个纯数字命名的 JSON 文件）覆盖重复、无展示意图，导致 prompt 优化很容易对已有样本过拟合，而真正暴露模型保守退化的非标准 data shape 没有覆盖。在调 prompt 之前，需要一套有维度设计的 benchmark 数据集作为客观基线。

## What Changes

- 在 `packages/react-ui-dsl/src/__tests__/e2e/fuzz-data/benchmark/` 下新建约 49 个 benchmark case 文件
- 每个文件采用统一 envelope：`{ meta: { prompt, taxonomy, evalHints }, data: <any> }`
- 新建 `OUTLINE.md` 作为 taxonomy 维度定义和 case 矩阵的长期维护文档
- 新建 benchmark loader，支持读取 `meta.prompt`（替代弱 ID 前缀推断）和 `meta.evalHints`（注入 judge）
- 现有 `fuzz-data/0.json`–`19.json` 保持不变，benchmark 作为独立子集

## Capabilities

### New Capabilities

- `fuzz-benchmark-dataset`: 49 个人工策展的 benchmark case，覆盖 6 个正交维度（Shape / Record Structure / Value Encoding / Temporal / Completeness / Aggregation），包含列式数据、动态 key 字典、图关系、对比 KPI、标准 API envelope、原始值数组等当前零覆盖场景
- `benchmark-loader`: 读取 benchmark case 的 loader，从 `meta.prompt` 获取展示意图，从 `meta.evalHints` 向 judge 注入 case 级评估条件

### Modified Capabilities

- `semantic-regression-testing`: eval 流程扩展支持 benchmark case 的 `evalHints` 作为 per-case judge context，补充现有全局 rubric

## Impact

- 新增文件：`fuzz-data/benchmark/*.json`（~49 个）、`fuzz-data/benchmark/OUTLINE.md`
- 新增文件：benchmark loader（`fuzz-data/benchmark-loader.ts` 或集成进现有 `fuzz-loader.ts`）
- 修改范围：`eval/judge-runner.ts`（注入 evalHints）、`eval/rubric.ts`（支持 per-case rubric overlay）
- 不影响现有 `fixtures.ts` 和 `fuzz-data/0–19.json` 的运行
