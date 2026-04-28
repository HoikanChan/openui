## Context

`packages/react-ui-dsl` 的 fuzz 测试入口（`fuzz-loader.ts`）目前依赖文件 ID 前缀推断展示意图。现有 20 个数据文件全部使用数字命名（`0.json`–`19.json`），导致所有 case 退化为通用 prompt。数据形态高度重复，缺乏对列式时序、动态 key 字典、图关系、对比 KPI、标准 API envelope、原始值数组、空数据降级等场景的覆盖。

eval 流程（`eval-loop.ts`）已有 `EvalSuite = "e2e" | "fuzz"` 的 suite 概念，`--suite=fuzz` 路由到 `dsl-fuzz.test.tsx`。benchmark 作为第三个独立 suite 加入，不改变 fuzz 和 e2e 的运行方式。

## Goals / Non-Goals

**Goals:**
- 建立 49 个维度正交的 benchmark case，覆盖 6 个维度：Shape / Record Structure / Value Encoding / Temporal / Completeness / Aggregation
- `benchmark` 作为独立 EvalSuite，通过 `pnpm eval start --suite=benchmark` 单独运行
- 统一 case envelope 格式（`meta` + `data`），`meta.prompt` 替代 ID 前缀推断，`meta.evalHints` 在 judge 阶段注入 per-case 评判条件
- `OUTLINE.md` 作为 taxonomy 的长期维护文档，不进 spec 流程
- benchmark loader 独立于现有 `fuzz-loader.ts`，不破坏现有 fuzz/e2e 流程

**Non-Goals:**
- 不修改现有 `fuzz-data/0.json`–`19.json` 或 fuzz suite 的运行逻辑
- 不在本 change 内调整 prompt / DSL 组件 / 表达式
- 不删除或替换现有 `fixtures.ts` 的命名 fixture

## Decisions

### 1. benchmark 作为第三个 EvalSuite，复用现有 eval 流程

**选择**：在 `eval-loop.ts` 中将 `EvalSuite` 扩展为 `"e2e" | "fuzz" | "benchmark"`，`--suite=benchmark` 路由到新测试文件 `dsl-benchmark.test.tsx`，复用截图、judge、report、verify 等全部流程。

**原因**：整套 eval 基础设施（runVitest → screenshot → judge → report → verify）已经完整，benchmark 只需接入，不需要重新实现。独立 suite 意味着 fuzz 和 benchmark 可以分别运行、分别追踪历史分数。

**替代方案**：在 fuzz suite 内用 flag 区分——被否决，因为 fuzz 和 benchmark 的数据来源、prompt 逻辑、evalHints 注入都不同，混在一个 suite 里会让两套数据的分数无法独立比较。

### 2. 新测试文件 dsl-benchmark.test.tsx，对称于 dsl-fuzz.test.tsx

**选择**：新建 `src/__tests__/e2e/dsl-benchmark.test.tsx`，由 benchmark-loader 提供 case 列表（prompt + dataModel + evalHints），运行 LLM → render → snapshot 流程，并将 evalHints 写入 report-data.json 的 entry 中供 judge 阶段读取。

**原因**：与 dsl-fuzz.test.tsx 对称，职责清晰；evalHints 通过 report entry 传递，无需修改 judge-runner 的调用接口，只需在读取 entry 时取出 evalHints 并拼入 judge prompt。

**snapshots 目录**：`fuzz-snapshots/` 给 fuzz 用，benchmark 使用独立的 `benchmark-snapshots/`，互不干扰。

### 3. Envelope 格式：`{ meta, data }`

**选择**：每个文件顶层固定两个 key：`meta`（元数据）和 `data`（原始 payload，可以是任意 JSON）。

**原因**：数据可能是数组（`bare-array` shape），顶层数组内无法内嵌元数据。独立 manifest 文件导致数据和意图分离，不自包含。`{ meta, data }` 在 loader 层 `file.data` 取 payload、`file.meta` 取元数据，loader 不将 meta 传给 LLM。

### 4. evalHints 通过 report entry 传递，在 judgeFixtures 调用处注入

**选择**：`dsl-benchmark.test.tsx` 将 `evalHints` 写入每个 report entry（`E2EReportEntry` 新增可选字段）。`runEval` 在构建 judgeInputs 时从 entry 读取 evalHints，拼入 judge prompt。全局 rubric 不变，evalHints 作为附加段落追加。

**原因**：judge 调用点在 `eval-loop.ts` 的 `runEval` 函数中，已有读取 entry 的循环（line 195–205）。在此处注入 evalHints 改动最小，且 verify 阶段同样经过此路径，evalHints 在验证时也能生效。

## Risks / Trade-offs

- **[Risk] benchmark 本身成为新的过拟合目标** → 缓解：`OUTLINE.md` 长期维护，case 持续扩充；维度设计而非具体 case 内容才是防过拟合的关键
- **[Risk] evalHints 措辞质量影响 judge 准确性** → 缓解：使用断言式语言（must / must not），可通过 calibration-verifier 校准
- **[Risk] E2EReportEntry 增加 evalHints 字段影响现有 report 渲染** → 缓解：字段可选，fuzz/e2e suite 的 entry 不写该字段，report UI 无需修改
- **[Risk] 49 个 case 中部分 shape 当前 DSL 能力不足以支持（如 adjacency-list-graph）** → 缓解：benchmark 的目标是暴露能力边界，judge 分数本身就是量化结果

## Open Questions

- `dsl-benchmark.test.tsx` 是否需要 `--fixture` 过滤支持（已有 fuzz 版本支持）？建议是，复用 `fixtureFilter` 逻辑。
- benchmark-snapshots 是否纳入 git 追踪？建议同 fuzz-snapshots，gitignore 掉，按需 regen。
