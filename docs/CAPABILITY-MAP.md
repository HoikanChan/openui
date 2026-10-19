# OpenUI 能力全景图(validation-iteration 分支)

> 基准:HEAD `f8d7bebe` + 工作区未提交改动(#42 builtin 契约化进行中),2026-10-15。
> 本分支是外部迭代线,不合并回 master;前端包已删,完整形态见 master(以 `VALIDATION-ITERATION.md` 为准)。
> 阅读方式:先看 §0 一页脑图建立骨架,再按编号下钻任一分支。

---

## 0. 一页脑图

```
OpenUI(数据驱动 UI 生成,SmartCanvasService 契约的服务端实现)
│
├─ 1. 系统定位与生态位
│    ├─ 上游:AICOService / Agents-Skills(意图 + 数据 + 生成建议)
│    ├─ 本仓:SmartCanvasService 参考实现(SDK + REST 服务)
│    └─ 下游:DSLEngine(前端渲染,本分支已删,术语保留)
│
├─ 2. 语言内核 ─ packages/lang-core(TS,跨语言对齐 oracle)
│    ├─ parser:lexer / statements / expressions / merge / prompt 生成
│    ├─ runtime:evaluator / queryManager / store / toolProvider
│    └─ scripts:generate-prompt-golden / generate-validation-oracle
│
├─ 3. Java Generation SDK ─ packages/genui-java-sdk(主战场,Java 21)
│    ├─ 3.1 契约层 contract:base-contract + Extension 注册 + Supplement 合并 + TypeExpr
│    ├─ 3.2 Prompt 组装 prompt:PromptAssembler + 数据特征化采样 characterize/
│    ├─ 3.3 LLM 链路 llm:GenUiGenerator(同步/流式)+ SSE 解析 + DSL 提取
│    ├─ 3.4 校验体系 validation:parser → semantic → type → reducer(actionable)
│    │    └─ 流式 gate:StreamingValidationSession(EMIT/BUFFER/WITHHOLD/FAIL)
│    ├─ 3.5 修复体系 repair:RepairCoordinator(reask 循环)+ 策略 + 提示词
│    └─ 3.6 内部基准 bench(test scope):repair-bench + ValidateCli + 语料报告
│
├─ 4. Eval CLI ─ packages/genui-eval-cli(生成链路 CLI,交付载体预留)
├─ 5. 参考服务 ─ examples/genui-service(Spring Boot 2.7 REST 参考实现)
│
├─ 6. 语料与数据资产
│    ├─ repair-cases/(SDK:25 例 smartcanvas 真实错误语料)
│    ├─ interception-corpus(test resources:拦截语料 + 契约)
│    ├─ validation-test/(49 例真实校验结果)
│    └─ reports/(Reask 修复率根因分析)
│
├─ 7. 规格与文档体系
│    ├─ 术语:CONTEXT.md(词汇表)/ CONTEXT-MAP.md(指路)/ issues-map.md
│    ├─ docs/:Next.js 文档站 + adr/(7 篇)+ superpowers/specs(27 篇设计)
│    └─ openspec/:变更工作流(10 活跃 + 归档)
│
└─ 8. 现状基线与迭代方向
     ├─ 复现基线:18/25 复现,7 not-reproduced(缺 dataModel)
     ├─ 主口径目标:可复现案例修复成功率 ≥ 80%
     └─ 进行中:#42 builtin 注册表契约化(工作区未提交)
```

---

## 1. 系统定位与生态位

**这是什么**:面向 Agents/Skills 输出可视化的数据驱动 UI 生成能力——按用户意图、业务数据、生成建议,由大模型产出可流式渲染的 UI 中间表示(OpenUI Lang / Stream IR),前端 Renderer 流式解析渲染。

**生态位**(术语见根 `CONTEXT.md` 词汇表):

- **上游**:AICOService / 前置 Agents-Skills —— 透传或编排生成请求(意图 + dataModel + 生成建议)。
- **本仓**:SmartCanvasService 契约的参考实现。核心链路:组装生成上下文与提示词 → 约束大模型输出 Stream IR → 服务端校验/修复 → SSE 流式下发。
- **下游**:DSLEngine(前端渲染运行时,统一 portal / LUI / Canvas)。本分支已删前端代码,但契约语义(base-contract)仍由本仓 Java 侧镜像维护。

**分支形态**(`VALIDATION-ITERATION.md`):只保留 validation/repair 迭代所需——`genui-java-sdk`、`genui-eval-cli`、`lang-core`、`examples/genui-service`、`interal-code/`、`reports/`。根 README 描述的是完整上游仓库,以 `VALIDATION-ITERATION.md` 为准。

---

## 2. 语言内核 — packages/lang-core

框架无关的 OpenUI Lang 内核(TS,`@cloudsop/openui-lang-core@0.2.1`),Java parser 的**跨语言对齐 oracle 来源**。

| 分支 | 内容 |
|---|---|
| `src/parser/` | lexer / statements / expressions / ast / merge / materialize / enrich-errors;**prompt.ts** = prompt 生成(Java `PromptAssembler` 的字节对齐 oracle);builtins.ts = 函数词表 |
| `src/runtime/` | evaluator / evaluate-prop / evaluate-tree / queryManager / state-field / store / toolProvider / mcp / reactive(渲染侧运行时;Java 侧不消费,仅保留语义基线) |
| `src/library.ts` | 组件库定义(组件名 + propsSchema + 描述),Java Generation Contract 的对齐基线 |
| `scripts/generate-prompt-golden.mts` | 重生成跨语言 prompt golden 文件(Java 侧曾逐字节断言) |
| `scripts/generate-validation-oracle.mts` | 生成跨语言 validation oracle:TS parser 是 Java 校验器契约错误分类学的 oracle(支持子集,非全 AST parity) |
| `src/__tests__/` | 7 个测试文件(builtins-manifest / format / object-map / render / switch / null-coalescing / library-extension) |

术语重点(各自 CONTEXT.md):Open Template / Template Instance / Iterator Binding / Deferred Render Template(`@Render`)/ Render Binding / Unbound Template Reference。

---

## 3. Java Generation SDK — packages/genui-java-sdk(主战场)

Java 21,fastjson2 2.0.60。能力按包分六层。

### 3.1 契约层 `core/contract`

- **`GenerationSdk`**(门面):`create()` 加载 `openui/base-contract.json`;`register(GenUIExtension)` 按 extensionId 注册(重注册覆盖、同名碰撞拒绝);`mergedContract()` / `assemblePrompt()`。
- **base-contract.json**(当前 1.1.6,工作区从 1.1.5 升级):49 组件、8 componentGroups、29 builtins、root=Stack。builtin 词表唯一真实来源(ADR 0007:契约里没有的函数名即幻觉)。
- **GenUIExtension**:下游组件/工具契约,description + propsSchema 包装(非裸 schema)。
- **GenUIBaseSupplement**:宿主全局增补——build 时一次性合并进 base,合并结果即 Effective Base Contract(prompt 与碰撞检查只见它)。
- **DataModelSpec** / **ToolSpec** / **ComponentPromptSpec** / **ComponentPropsSchema**:数据模型、工具、组件规格。
- **`typeexpr/`**(工作区新增,#42):契约 builtins 条目中 TS 风格类型串的解析(`TypeExpr` sealed interface + `TypeExprParser` + `TypeExprEquals` 结构等值比较)。

### 3.2 Prompt 组装 `core/prompt`

- **`PromptAssembler`**:base + extension + request overlay → system prompt,与 lang-core `generatePrompt` 字节对齐(golden 机制)。
- **`characterize/`**(ADR 0001):数据特征化采样——host data 的 prompt 副本做激进结构化采样(ShapeWalker / Array-Enum-Object-Scalar Shape / ColumnAccumulator / TsTypeRenderer),防止大表撑爆 token;全量渲染数据仍走 seq=0 dataModel envelope 单独下发。

### 3.3 LLM 链路 `core/llm`

- **`GenUiGenerator`**:`generate()` 同步 / `generateStream()` 流式(带 gate 与 reask);产出 `GenUiGenerationResult`。
- **protocol/transport/stream**:OpenAI 兼容 ChatCompletion 协议;`RestfulLlmTransport`(单请求 10 分钟上限);`SseDeltaParser` / `SseFrames` 流式增量解析。
- **`OpenuiCodeExtractor`**:从模型回复提取 openui-lang 代码。
- **`RenderStreamEnvelope`**:SSE 单信封多 type(ir / 模板 / PIU / iframe / 完成 / 错误),DSLEngine 按 `type` 分发。
- 配置:`GenUiLlmConfig`(默认不强制 json_object 响应模式)。

### 3.4 校验体系 `core/validation`

生成结果的服务端可信校验,按管线分层:

| 层 | 关键类 | 职责 |
|---|---|---|
| parser | `OpenuiLexer` / `OpenuiPreprocessor` / `StatementSplitter` / `OpenuiParser` / `ExpressionParser` | 词法 → 语句切分 → AST;syntax-* 错误码 |
| parser | `Builtins`(语法保留字)/ `BuiltinCatalog`(工作区新增,#42:函数词表恒从 base-contract 派生) | builtin 名单来源 |
| semantic | `ProgramAnalyzer` / `ContractCatalog` / `ComponentContractValidator` | unknown-component / null-required 等;产出 `RepairHints` |
| type | `ProgramTypeValidator` / `SchemaTypeCompiler` / `BuiltinTypeRegistry` / `DataPathResolver` / `TypeAssignability` | propsSchema 编译 → 静态类型检查(type-prop-mismatch / type-data-path-missing / type-table-column-missing 等);dataModel 缺失则类型校验关闭 |
| reducer | `ValidationIssueReducer` | raw → actionable 去噪视图(修复提示词消费) |
| stream | `StreamingValidationSession` / `StatementBoundaryScanner` / `GateDecision` | 流式 gate:EMIT(渲染安全)/ BUFFER(临时未解析暂留)/ WITHHOLD(阻塞级 ERROR 永不发出,Fail-Fast 触发点)/ FAIL |

两个正交开关:`ValidationMode`(FINAL / STREAMING,单次调用的规则集)× `ValidationConfigMode`(FINAL_ONLY / STREAMING_GATE / DISABLED,生成流水线中何时触发)。详见 spec `2026-08-28-java-validation-actionable-issues-design.md` 与 `2026-08-12-openui-lang-template-scope-design.md`。

### 3.5 修复体系 `core/validation/repair`

- **`RepairCoordinator`**:`repairFull()`(完整修复,`FullRepairOutcome` 含 OutcomeKind + 每轮 AttemptRecord)/ `reaskStream()`(流式前缀保留 + 单语句重请求)。
- **`ReaskPromptBuilder`**:修复提示词(userIntent + acceptedPrefix + 校验 issue 的 actionable 视图 + 案例契约现算的 originalSystemPrompt)。
- **`RepairPolicy`**:kind + maxAttempts + timeout + statementRepairTimeout。固定温度 0、不开 thinking。

### 3.6 内部基准 `src/test/.../bench/`(test scope,永不进交付物)

- **`RepairBenchCli`**:repair-bench 入口,案例包进 → stdout JSONL 出,无状态。默认 **validation-only**(零 LLM、不读 .env);`--repair` 才烧 LLM(qwen3.8-flash 可调通)。
- 支撑:`RepairCasePack`(loader)/ `RepairBenchRunner` / `BenchEnv`(.env 解析)/ `OpenAiCompatBenchTransport` / `PromptDump`。
- 同驻先例工具:`ValidateCli`(单 DSL 校验)/ `ValidationBenchmarkDriver` / `ValidationCorpusReportGenerator`。
- 使用指南:`packages/genui-java-sdk/docs/repair-bench.md`;设计:spec `2026-10-13` + 实现设计 `2026-10-14`。

---

## 4. Eval CLI — packages/genui-eval-cli

包装 genui-java-sdk 生成能力的 Java 命令行工具(`EvalCliMain` / `JobRunner` / `OpenAiCompatibleLlmTransport`)。定位:预留为 validation 模块向内部测试服务交付的载体(形态待定,另行设计)。仓库自用工具,不是子领域,无独立 CONTEXT.md。

## 5. 参考服务 — examples/genui-service

SmartCanvasService 契约的可运行 REST 参考实现(Spring Boot 2.7 / javax,刻意对齐公司内部栈,勿建议升 Boot 3)。结构:`application/`(GenerationAppService)/ `config/`(SdkConfig)/ `llm/` / `tools/` / `web/`,附 `register-extension.http`。供内部整目录拷走仿照搭建。`interal-code/` 为内部参照代码(ChatCompletionsRsp / GenUIServiceDelegateImpl / LLMService)。

---

## 6. 语料与数据资产

| 资产 | 位置 | 规模 / 说明 |
|---|---|---|
| smartcanvas 错误语料 case 包 | `packages/genui-java-sdk/repair-cases/` | 25 案例 + 语料级 contract.json(= base-contract 快照);`dsl.txt` 逐字落盘含 CRLF;issues.json 透传拦截报错 |
| 语料转换缺口 | 同上 `CONVERSION-GAPS.md` | 7 例缺 dataModel(→ not-reproduced)、反推 intent 待核对 |
| 复现基线 | 同上 `REPRODUCE-BASELINE.md` | validation 首跑(1.1.5)与 md 根因逐案例对比 |
| 语料再生成脚本 | `packages/genui-java-sdk/scripts/md-to-repair-cases.mjs` | 从 smartcanvas 失败语料 md 重生成;`--no-llm` 模式;案例数漂移即终止 |
| smartcanvas 错误语料 case 包 | `packages/genui-java-sdk/repair-cases/` | 25 案例 + 语料级 contract.json(= base-contract 快照);`dsl.txt` 逐字落盘含 CRLF;issues.json 透传拦截报错;早期 3 例(syntax / unknown-component / missing-required)已并入 `src/test/resources/interception-corpus/`,根 `repair-cases/` 已删 |
| 拦截语料 | `src/test/resources/interception-corpus/` | dsl(bad/valid)+ contracts + manifest;`InterceptionCorpusTest` 消费 |
| 真实校验结果 | `validation-test/` | 49 例:INVALID 26 / VALID 23;raw 90 issue、actionable 60;分类统计齐全 |
| 根因分析 | `reports/reask-repair-rate-analysis.html` | Reask 修复率根因分析(迭代输入) |
| 质量 issues map | 根 `issues-map.md` | 2026-04-28 benchmark:49 fixtures,7.71/10,五维评分 + gate 失败清单 |
| eval 任务输出 | `data/tasks/` | JobRunner 运行产物(meta / stdout / stderr) |

---

## 7. 规格与文档体系

- **词汇与边界**:根 `CONTEXT.md`(系统词汇表,DSLEngine/SmartCanvasService/契约/流式校验等 ~25 条术语与关系)、`CONTEXT-MAP.md`(package 边界指路)、各 package CONTEXT.md。
- **docs/ 文档站**:Next.js + fumadocs,面向外部开发者(openui-lang 教程 / chat 集成 / api-reference / mcp)。注意:描述完整上游形态,与本分支有偏差。同级有 `SYSTEM-DESIGN.md`(内部功能规格书)。
- **ADR**:`docs/adr/` 7 篇(canvas×4 / eval / 模板作用域 / builtin 契约化)+ `packages/genui-java-sdk/docs/adr/` 3 篇(数据特征化 / base supplement / builtin 词表)。
- **superpowers**:specs/ 27 篇设计文档(时间线 2026-04 → 10,近期:repair-bench 设计与实现、generated-dsl 校验);plans/ 为对应实施计划。
- **openspec/**:变更工作流,10 个活跃 change(builtin-contract-source-of-truth、genui-service-rest 等)+ 大量归档。
- **交付文档**:`packages/genui-java-sdk/README.md` + INTEGRATION.md(集成走查)、`docs/repair-bench.md`、`LLM_MIGRATION.md`。

## 8. 工具链与工程配置

- **Java 侧**:Maven(pom.xml,根 + 三模块);spotless(Huawei 格式)+ checkstyle(config/);本机约束——commit charge 常耗尽致 `mvn test` fork OOM,用独立 javac + TestLauncher 验证。
- **JS 侧**:pnpm workspace;vitest(lang-core);eslint / prettier / publint / tsdown。
- **格式化纪律**:跑 spotless:apply 全量前必须先 fetch+同步 master。
- **LLM 配置**:仓库根 `.env`(LLM_BASE_URL / LLM_API_KEY / LLM_MODEL / HTTPS_PROXY),真实环境变量优先;仅 `--repair` 读取。
- **快照纪律**:`.dsl` 快照(snapshots/ 等)是 AI 生成资产,只能经 regen 脚本更新,禁止手编;benchmark 快照目录 gitignored,覆盖前必须备份。

---

## 9. 现状基线与迭代方向

**复现基线**(REPRODUCE-BASELINE,1.1.5 契约 + 旧 Builtins 硬编码注册表):

- 25 例中 **18 复现** / 7 not-reproduced(全部缺 dataModel,语料缺口而非契约漂移)。
- 复现 18 例与 md 根因:完全一致 5 / 部分一致 12 / 不重叠 1。
- 偏差三点归因:① builtin 注册表错位(#42,修复中)② dataModel 缺失(T4 回填)③ Stack gap 噪声(已接受)。

**迭代目标**(repair-bench 设计):基于真实错误数据做错误校验与反思修复,**可复现案例修复成功率 ≥ 80%**。主口径 = repaired / (repaired + still-invalid),剔除 transport-error / timeout 噪声。

**进行中(工作区未提交,#42 builtin 契约化)**:`Builtins.java` 收缩为纯语法保留字(Action 步骤 + Query/Mutation);新增 `BuiltinCatalog` 恒从 base-contract.json 派生函数词表;新增 `typeexpr/` 类型表达式解析;base-contract 升 1.1.6(29 builtins,补齐 Flatten/ObjectValues/Number/String/Join/MergeObject,移除 FormatNumber/FormatPercent);65 文件改动、1786 插入。

**已知债务**:

- master 侧 base-contract.json 损坏(5d6155fd 合并拼成两份 JSON 致 82 测试错误),master 侧待修。
- 根 issues-map 显示 judge 质量与 parse/render 正确性未完全对齐(2 例 10/10 仍挂 benchmark gate),质量与稳定性需分开跟踪。
- 7 例缺 dataModel 语料回填后,复现口径才完整。
