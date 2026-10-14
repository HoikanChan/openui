# repair-bench 实现设计（切片 1：bench CLI + 案例包 loader）

- 日期：2026-10-14（同日经维护者 grilling 修订后重写；复审追加修订：转换工具移出切片 1，见 §2.4）
- 状态：已与维护者对齐
- 上游 spec：[2026-10-13-repair-bench-design.md](2026-10-13-repair-bench-design.md)（已加 2026-10-14 修订注）。本设计遵循修订后的口径：**无双轨道**、**bench 驻 genui-java-sdk test scope**、**主口径剔除 transport-error/timeout**。
- 基线：`validation-iteration` @ `c56a37ae`，genui-java-sdk 552/552 绿（javac + JUnit launcher 验证）。

## 0. MVP 与后续架构总览

### MVP（切片 1）边界

| 做 | 不做 |
|---|---|
| repair-bench CLI（genui-java-sdk **test scope**）：案例包进 → stdout JSONL 出 | run 目录 / manifest / HTML 报告（外部脚本职责，无状态计算器原则） |
| 案例包 loader：`repair-cases/` 扫描、契约解析链、dataModel null 语义 | 案例包生成与管理——含 `validation-test/` → `repair-cases/` 转换工具（**先不需要**，2026-10-14 维护者复审决定，语料进入方式见 §2.4）；内部导出工具反向对齐格式见 §8 |
| SDK 前置微改动：`FullRepairOutcome` 增加 outcome kind + 逐 attempt 记录（§4） | ReaskPromptBuilder / 校验规则 / RepairPolicy 默认值改动（附录 A 单列） |
| 复现判定 + `repairFull` 驱动 + 假 transport 单测（语料确定性快照测试随 `repair-cases/` 就位补入，见 §7） | 流式 bench（`reaskStream`）；修复成功率进 CI（均不做的理由见上游 spec E / D） |

### 后续架构（只画边界）

- **切片 2（baseline 与分桶报告）**：组装首批 26 例语料进入 `repair-cases/`（转换工具先不需要，进入方式与入集共识见 §2.4）；MVP 跑 baseline；外部脚本消费 JSONL 生成 run 目录 + manifest（模型名、corpus 哈希、代码版本）+ 按 issue code 分桶报告。bench 本身不变。
- **切片 3+（迭代循环）**：按上游 spec D 迭代。修复率不达标的桶 → 改提示词/策略；校验规则缺口另走 validation-test 缺口分析工具链（见 §2.4），补好的规则让未来内部导出的语料更干净地进 bench——两条迭代线共用校验器代码但工具不合并。

## 1. 宿主与入口

### 1.1 宿主决策：genui-java-sdk test scope

bench 是**内部迭代工具**，与 `ValidateCli`（离线 FINAL 校验 CLI）、`ValidationCorpusReportGenerator`（语料报告）同先例，住 `packages/genui-java-sdk/src/test/java/com/huawei/cloudsop/genui/bench/`——test scope 永不进交付物。

`packages/genui-eval-cli` **不再承载 bench**：该包预留为 validation 模块向内部测试服务交付的载体（交付形态待定，另开设计）；bench 寄居其上会让测试服务的交付工作牵动迭代工具（CONTEXT-MAP 定位已同步修订）。

代价（接受）：bench 自带一份 OpenAI 兼容 transport（~100 行，从 eval-cli 的 `OpenAiCompatibleLlmTransport` 拷贝适配）+ `.env` 加载（~20 行）+ 有界线程池 runner（~50 行，照 `JobRunner` 形态）。合计 ~170 行的复制，换 bench 与交付物的物理隔离。

### 1.2 命令行接口

```
java -cp <test-classpath> com.huawei.cloudsop.genui.bench.RepairBenchCli \
  --cases=repair-cases/ [--concurrency=4] [--max-attempts=3] [--timeout-minutes=0] [--limit=N]
```

| 参数 | 默认 | 说明 |
|---|---|---|
| `--cases` | 必填 | 案例包根目录，扫描一层子目录中含 `case.json` 者 |
| `--concurrency` | 4 | 有界线程池大小（repair 单案例含多次 LLM 往返，默认低于 generate 的 6） |
| `--max-attempts` | 3 | 上游 spec C 已定；映射 `RepairPolicy.of(FINAL_REPAIR, N)` |
| `--timeout-minutes` | 0（关） | 映射 `RepairPolicy.timeout`，**尝试之间**检查的墙钟预算（§6） |
| `--limit` | 无 | 按目录名排序取前 N，冒烟用 |

- LLM 配置：`LLM_BASE_URL / LLM_API_KEY / LLM_MODEL / HTTPS_PROXY`，真实环境变量优先；其次从**仓库根** `.env` 读取（文件可选、不存在不报错、只解析 `KEY=VALUE` 行、`#` 注释与空行跳过）。首次运行前把现有 `packages/genui-eval-cli/.env` 复制为仓库根 `.env`（gitignored 本地动作；eval-cli 包内那份留给交付链路，交付不依赖 .env——另议）。
- 退出码：0 = 整批跑完；2 = 用法错误；1 = 基础设施失败（含**加载阶段任何案例解析失败——整批终止**，语料是 git 静态资产，坏一个就是全坏，不静默降级）。
- 运行配方：与 ValidateCli 相同的 test-classpath 运行方式；无 fat jar（内部工具，从 worktree 检出直接跑，javac + launcher 配方见 §7）。

## 2. 案例包格式与 loader

### 2.1 目录布局

```
repair-cases/
  contract.json          # 语料级共享契约
  case-0001/
    case.json            # 必需
    contract.json        # 可选：案例级契约，优先于语料级
    dsl.txt              # 必需：失败 DSL 原文，不包 fence
    issues.json          # 可选：内部拦截时的报错，仅透传比对，不参与判定
```

细化点（不与上游 spec B 修订后口径冲突）：

1. **契约解析链**：`<case>/contract.json` > `repair-cases/contract.json` > **报错终止**。故意不做 SDK 默认契约兜底——bench 的意义是保真"当时契约"，静默兜底会把契约漂移伪装成 not-reproduced。
2. **id 以 `case.json` 的 `id` 字段为准**，目录名只用于排序与定位；全语料 id 重复 = 语料结构性损坏，加载阶段整批报错。

### 2.2 case.json schema（无双轨道版）

| 字段 | 必填 | 类型 | 说明 |
|---|---|---|---|
| `id` | ✓ | string | 非空，全语料唯一 |
| `generationId` | | string | 内部溯源 |
| `originalIntent` | | string | 原始用户意图；缺失 → 传 `null` 给 repairFull（ReaskPromptBuilder 对 null/blank 跳过 Original request 段） |
| `dataModel` | | object | **缺失/显式 null → `null`；`{}` → 空 map（具体空模型）**。绝不能像 `GenerationJob.parseAll` 那样把缺失归一成 `Map.of()`——`ValidationRequest.dataModel` 区分 null（类型校验关闭、`data` 未解析）与空 map，这是 gen 管线 `dataModelFor()` 专门翻译过的语义陷阱 |

`reportedBy` 字段不存在（2026-10-14 修订：真实语料没有独立的 render 发现通道，双轨道取消）。

`issues.json`（可选）：`[{"code": "...", ...}]`，仅 `code` 必填，读入后原样透传到输出行（`internalIssueCodes`），**不参与复现判定、不进任何口径**——内外校验器版本天然有差，比对只作信息。

### 2.3 loader 校验规则

**任何解析失败（本批语料内任何一个案例的 case.json 非法 / dsl.txt 缺失或空 / contract.json 解析失败 / issues.json 非法 / id 重复 / 零案例目录 / --cases 不是目录）→ 整批终止、退出码 1、报错信息列出全部问题案例。** 语料完整性由 commit 保证，bench 不为坏语料提供降级跑法。

`dsl.txt` 读原文**不做 strip**（存证优先；校验器自身会预处理）。

### 2.4 首批语料进入 `repair-cases/`（转换工具先不需要）

> **2026-10-14 复审**：维护者决定 `validation-test/` → `repair-cases/` 的转换工具**先不需要**，移出切片 1（§0 已同步）。本节保留 grilling 已对齐的入集共识——语料实际进入时（届时再决定手工整理还是补工具）按此执行；不预设进入时机。

mvp 只需要几条案例跑通

## 3. 单案例执行流程

```
runCase(case):                                        # 在 worker 线程内
  sdk = GenerationSdk.builder().baseContract(case.contract).build()
  promptReq = GenUIPromptRequest(null,
               case.dataModel == null ? null : new DataModelSpec("Response data", case.dataModel),
               List.of(), null, null, null, null)     # 镜像 GenUiGenerator.toPromptRequest
  systemPrompt = sdk.assemblePrompt(promptReq).prompt()   # originalSystemPrompt 现算（决策见下）
  externalRefs = case.dataModel != null ? Set.of("data") : Set.of()   # 镜像 externalRefsFor

  # ── 步骤 1：复现判定（FINAL） ──
  v0 = DefaultOpenuiLangValidator.INSTANCE.validate(ValidationRequest.builder()
       .dsl(case.dsl).contract(case.contract).rootName(case.contract.root())
       .externalRefs(externalRefs).dataModel(case.dataModel).mode(FINAL).build())
  initialCodes = v0.actionableIssues() 的 code 集合

  if v0.status != INVALID:
      return 行{status: "not-reproduced", ...}          # 内外漂移信号（上游 spec E）

  # ── 步骤 2：repairFull ──
  coordinator = new RepairCoordinator(transport, DefaultOpenuiLangValidator.INSTANCE,
                   new RepairPolicy(FINAL_REPAIR, maxAttempts, timeout, ZERO))
                 .withBodyBuilder(repairBodyBuilder)     # §6 温度说明
                 .withExternalRefs(externalRefs)
                 .withDataModel(case.dataModel)          # 每轮复验带类型校验
  outcome = coordinator.repairFull(case.originalIntent, case.dsl, v0,
                                   case.contract, case.contract.root, systemPrompt)
  return 行{status: outcome.kind 映射（§4/§5）, attempts: [...], finalDsl: outcome.dsl, ...}
```

关键决策与理由：

1. **originalSystemPrompt 现算，不由案例包携带**。案例包契约就是"当时契约"，`assemblePrompt` 从同一契约现算的 system prompt 与原始生成自洽（7 月数据显示继承完整 system prompt 是修复率主杠杆）；携带则语料格式多一个巨型字段且两处漂移。现算入参带**案例自己的 dataModel**（不是 `assembleCanonicalPrompt` 的占位符）。
2. **直接驱动 RepairCoordinator，不走 `GenUiGenerator.generate()`**。generate 的 FINAL_REPAIR 路径把过程信息折叠成异常，bench 需要的 v0、逐 attempt codes、outcome kind 拿不到。代价是在 bench 侧复刻 generator 的装配（bodyBuilder / externalRefs / dataModel / repairConfig），用测试钉住（§7）。
3. **复现判定口径 = FINAL 校验存在 ERROR**（`status() == INVALID`，FINAL 模式下与 `hasBlockingIssues()` 等价）。
4. `repairFull` 的 `contract` 入参传案例契约本身——bench 语境无 extension 可合并，案例契约即全量契约。
5. **修复请求温度 0 + 不开 thinking**：bench 构造 `repairConfig = GenUiLlmConfig.builder().defaultModel(model).temperature(0).enableThinking(false).build()` 专用于 bodyBuilder，镜像 `GenUiGenerator.repairConfig()` 的确定性修复纪律。

## 4. SDK 前置微改动：`FullRepairOutcome` 过程可见性

现状两个缺口（读源码确认）：

1. **transport-error 与"修不动"不可区分**：`failed(...)` 与 `exhausted(...)` 产出同形 record。bench 的两种迭代动作（重跑 vs 改提示词）直接消费这个区分。
2. **无逐 attempt 记录**：spec C 输出要求 `attempts:[{issueCodes}]`，现状只有最后一次 `result`。

改动形状（工厂方法是 package-private、外部只见访问器，低风险；枚举值与 bench 输出状态 1:1）：

```java
public record FullRepairOutcome(OutcomeKind kind, String dsl, ValidationResult result,
        List<AttemptRecord> attemptLog) {

    public enum OutcomeKind { REPAIRED, STILL_INVALID, TRANSPORT_ERROR, TIMED_OUT }

    /** 单次尝试的复验记录；传输失败的尝试 issues 为 null、valid 为 false。 */
    public record AttemptRecord(int attempt, boolean valid, List<ValidationIssue> issues) {}

    // 派生访问器，保持 GenUiGenerator 现有调用点不动：
    public boolean repaired() { return kind == OutcomeKind.REPAIRED; }
    public boolean timedOut() { return kind == OutcomeKind.TIMED_OUT; }
    public int attempts() { return attemptLog.size(); }
}
```

- `repairFull` 循环内每轮把 `revalidated` 收进 `attemptLog`；transport 失败补一条 `AttemptRecord(attempt, false, null)`；超时在**尝试之间**判定，`attemptLog` 只含已完成的尝试。
- `STILL_INVALID` 取代原 `exhausted` 命名——"试满预算仍无效"说的是模型修不动，不是状态机耗尽。
- AttemptRecord 存完整 `ValidationIssue`（SDK 不替消费者做投影），CLI 层映射为 actionable codes（§5）。
- 约 40 行 + 单测，随切片 1 交付、过 552 基线回归。

## 5. 输出 schema

### 5.1 逐案例行（JSONL，完成顺序输出，`synchronized` emit——沿用 JobRunner 形态）

```json
{"type":"case","caseId":"rw-046-CPU-CPU-cpu_usage",
 "status":"repaired","success":true,"reproduced":true,
 "initialIssueCodes":["type-prop-mismatch","type-table-column-missing"],
 "internalIssueCodes":["type-prop-mismatch"],
 "attemptCount":2,
 "attempts":[{"attempt":1,"valid":false,"issueCodes":["syntax-unexpected-token"]},
             {"attempt":2,"valid":true,"issueCodes":[]}],
 "finalIssueCodes":[],"finalDsl":"root = Stack([...])","durationMs":18432}
```

| 字段 | 说明 |
|---|---|
| `status` | `repaired` \| `still-invalid` \| `transport-error` \| `timeout` \| `not-reproduced`（平铺单字段，无第二层） |
| `success` | 派生布尔（`status == repaired`），spec C 口径保留，人看汇总方便 |
| `reproduced` | v0 = INVALID（进入修复流程）；not-reproduced 行为 false |
| `initialIssueCodes` | v0 的 **actionable** codes（升序去重）；not-reproduced 恒为空 |
| `internalIssueCodes` | issues.json 存在时透传，否则字段缺省 |
| `attempts[]` | 仅 reproduced 的行有；`issueCodes` = 该轮复验 actionable codes |
| `finalIssueCodes` | 最后一次复验的 actionable codes（repaired 恒空） |
| `finalDsl` | outcome 的最后 DSL（repaired 时即有效文本）；not-reproduced 行不输出（输入已知） |

**actionable vs raw**（口径决策）：全链路（initial / attempts / final / 分桶）统一 **actionable**——修复提示词消费的就是 actionable 视图，分桶要回答"模型拿着这套问题清单修不修得动"，口径必须与提示词所见一致；raw 视图留给校验器调试（validation-test 工具链已覆盖）。

### 5.2 汇总行（批次末尾，单行 JSON）

```json
{"type":"summary","model":"qwen3.6-27b","maxAttempts":3,"concurrency":4,"timeoutMinutes":0,
 "total":26,"byStatus":{"repaired":21,"still-invalid":4,"transport-error":1,"timeout":0,"not-reproduced":0},
 "mainRate":{"success":21,"denominator":25,"rate":0.84},
 "secondaryRate":{"success":21,"denominator":26,"rate":0.8077},
 "issueBuckets":{"type-prop-mismatch":{"cases":18,"repaired":15,"still-invalid":2,"transport-error":1,"timeout":0}},
 "durationMs":412000}
```

口径公式（上游 spec D + 2026-10-14 修订）：

- **主口径**（80% 立在这上面）：`mainRate = repaired / (repaired + still-invalid)`——即"可复现且真实跑完修复流程"的案例（transport-error 与 timeout 剔除分母：基础设施噪声与自设预算不算"修不动"，可筛出重跑）。分母为 0 时 `rate` 为 null。
- **副口径**：`secondaryRate = repaired / total`（not-reproduced 计入惩罚，度量外部环境保真度）。
- `issueBuckets` 仅统计 reproduced 案例，按 `initialIssueCodes` 分桶（一案例可入多桶），每桶给 outcome 分布——迭代循环"按 code 分桶看失败"的直接数据源。
- 汇总各 map 用 TreeMap/LinkedHashMap 保输出稳定；逐案例行是完成序（外部脚本自行按 caseId 排序）。

## 6. 并发与超时

- **Runner**：`RepairBenchRunner` 照 `JobRunner` 形态（有界固定线程池、逐行 JSONL、单案例异常转 error 行不中断批次、`synchronized emit`）。不复用 eval-cli 的 JobRunner（跨模块，不值得为此建依赖）。
- **每案例一个 `RepairCoordinator` 实例**（`withDataModel` 装案例自己的模型；coordinator 是带状态的轻量对象）；validator 用无状态单例 `DefaultOpenuiLangValidator.INSTANCE`；transport 的 JDK `HttpClient` 线程安全，全批共享。
- **transport 拷贝**（§1.1）：JDK HttpClient，每请求 10 分钟 `HttpRequest.timeout`，HTTP 非 2xx 转 `LlmTransportException`。残余风险：request timeout 覆盖到响应头到达，**响应体读取**阶段理论上仍可阻塞。MVP 接受，transport-error 在输出中可见；实际出现挂死案例再加 Future + cancel 外壳（不预做）。
- **`--timeout-minutes`**：映射 `RepairPolicy.timeout`，语义是 RepairCoordinator 现有的"尝试之间"墙钟检查，默认 0 = 不启用，先靠 transport 的 10 分钟请求上限兜底。bench 不自造超时机制。

## 7. 测试策略

### 回归保护分层（上游 spec D 已定）

- **确定性部分进常规单测**：loader、参数解析、.env 加载、复现判定、假 transport 的 repairFull 流程。
- **修复成功率不进 CI**：真实 LLM 的 bench 是指标不是门禁。

### 单测清单（genui-java-sdk test tree，bench 包旁）

1. **参数**：`RepairBenchArgs.parse` 必填/默认值/非法值。
2. **`.env` 加载**：不存在不报错；`KEY=VALUE` 解析 + 注释/空行跳过；真实环境变量优先。
3. **loader**：契约解析链两级优先 + 无契约 → 整批终止；`dataModel` 缺失 → `null`（**断言不是空 map**）、`{}` → 空 map；id 重复 / dsl.txt 空 / case.json 非法 → 整批终止且报错列出全部问题案例；`issues.json` 透传。
4. **复现确定性**（语料回归）：对 `repair-cases/` 固定子集（3-5 例）断言 v0 status + initialIssueCodes 快照稳定。**随语料就位补入**（转换先不需要，见 §2.4——语料未进仓前此项空缺，bench 单测不依赖它）。校验器规则迭代会改快照——期望中的行为，快照更新即"规则生效"的记录。
5. **假 transport 单案例流程**（照 `FlakyTransport` 模式）：首答无效次答有效 → repaired、attemptCount=2、两轮 codes 正确；恒无效 → still-invalid；抛 `LlmTransportException` → transport-error；`--max-attempts=1` 边界。
6. **汇总行**：小语料 + 假 transport 跑全批，断言 byStatus / 双口径公式 / issueBuckets 算术（含 transport-error 剔除分母、分母 0 → rate null）。
7. **`FullRepairOutcome`**：四种 kind 的工厂 + attemptLog 组装 + 派生访问器；GenUiGenerator 既有调用点回归（全量基线）。

### 验证配方（写给实现 agent，mvn 会 fork OOM）

本机 commit charge 常耗尽，`mvn test` 不可依赖。用独立 javac + JUnit launcher：

- classpath 里的 Windows 路径用 `cygpath -w` 转换；
- 资源目录 `src/main/resources`、`src/test/resources` 要显式上 classpath；
- launcher：小 main 反射跑 `LauncherDiscoveryRequestBuilder`，或直接拼 `~/.m2/repository` 里 org.junit.platform 系列 jar；
- 涉及中文的 python 输出加 `PYTHONIOENCODING=utf-8`；
- SDK 全量基线（当前 552 绿）+ bench 新增单测一起跑。

## 8. 开放问题

1. **首批语料如何/何时进入 `repair-cases/`**：转换工具先不需要（§2.4），进入方式（手工整理 vs 届时补工具）与时机待定——语料未就位前 bench 无真实输入，切片 2 baseline 以此为前置。内部导出工具格式对齐（上游 spec B 的"反向同步"）同样待内部侧确认；对齐后新案例经导出工具进入。
2. **`compare` 子命令 / prompt 变体**：切片 3 若频繁需要 run 间对比或 ReaskPromptBuilder A/B，评估加 `compare` 还是全靠外部脚本；prompt 变体需要 SDK seam（ReaskPromptBuilder 目前是静态方法）。等真实痛感。
3. **`internalIssueCodes` 比对是否升级为指标**：当前纯信息透传。若内外校验器版本对齐工作开始，可加 overlap 率作漂移辅助信号。

## 附录 A：顺带修改项（单独决策，不随切片 1）

`RepairPolicy.from(FAIL_FAST_REASK)` 默认 `maxAttempts` 1 → 2（7 月分析 finding #4 / spec D 杠杆，一行改动 + 单测）。属 SDK 行为变更、影响内部生产语义，**不混入切片 1 提交**，单独提给维护者决策。bench 的 `--max-attempts` 不受影响（bench 自建 policy）。

## 附录 B：类布局清单

| 类 | 位置（均在 genui-java-sdk test scope） | 职责 |
|---|---|---|
| `bench/RepairBenchCli` | test | main 入口 + `RepairBenchArgs` 参数解析（照 ValidateCli 形态） |
| `bench/BenchEnv` | test | `.env` 解析合并（~20 行；真实环境变量优先） |
| `bench/OpenAiCompatBenchTransport` | test | OpenAI 兼容 transport 拷贝（~100 行，LlmTransport 实现） |
| `bench/RepairCase` | test | record：id、originalIntent、dataModel、dsl、contract、internalIssueCodes |
| `bench/RepairCasePack` | test | 目录扫描 + 解析 + 结构性校验（失败整批终止） |
| `bench/RepairBenchRunner` | test | 线程池 + 单案例流程（§3）+ JSONL/汇总 emit |

（转换工具 `RepairCaseConverter` 先不需要，2026-10-14 复审移出——见 §2.4；届时若决定补，仍落位同包 test scope。）

SDK main scope 仅 §4 的 `FullRepairOutcome` 改动。所有新代码遵循现有风格：final 类、record、静态工厂、无外部依赖。
