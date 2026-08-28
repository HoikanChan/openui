# Generated DSL Validation 拦截实践报告

> 当前状态（2026-08）：本报告主体记录的是仅有语法/contract 校验时的基线。Java SDK
> 现在可通过 `ValidationRequest.dataModel(...)` 显式启用数据感知的 FINAL 静态校验，已能拦截
> `data.*` 缺失/非法遍历、可证明的 prop/slot/builtin/operator 类型错误，以及 Table/Col 字段错误。
> 未传 `dataModel` 时仍保留本文 corpus 的历史兼容行为；显式空 map 代表真实的空数据模型。
> `ValidationResult.issues()` 保留完整诊断；`actionableIssues()` 统一去除语法、Table row shape
> 和 root 级联噪音，Repair 与用户报告使用后者。

## 结论摘要

本报告为 `DefaultOpenuiLangValidator` 建立了一组可重复执行的离线拦截 corpus，并在第二轮扩充中加入了从真实 eval 数据（`packages/react-ui-dsl/src/__tests__/e2e/eval/runs`，2038 个 `.dsl` 产物）挖掘出的"差模型典型错误路径"：JS 方法调用、JS 全局对象、箭头函数、发明的 builtin/组件名、prose 夹杂，以及**已确认无法拦截**的数据路径类错误。

- 有效控制组：12 个（8 个真实 snapshot 副本 + 4 个边界合法形态：单引号字符串、三元/`??`、markdown fence 包裹）。
- 已知坏例（预期拦截）：15 个错误类共 46 个样本，46/46 全部被 `FINAL` 模式拦截。
- 已知漏拦截（明确记录，不计入拦截率）：1 个类共 3 个样本，validator 全部放行，corpus 以 `expectedOutcome: "missed"` 钉住 —— 未来 validator 若新增该能力，测试会主动报警提示更新报告。
- 误拦截率：0/12 = 0%。
- 关键边界结论（经 TS oracle 对照验证）：**单参数 JS 方法调用（如 `data.value.toFixed(1)`）在 TS 与 Java 两侧解析器中都会被当作合法调用表达式放行**，这是上游语言子集的既有行为，不是 Java 移植缺陷。

结论仅代表离线样本；真实模型端到端生成仍需接入实际 `LlmProperties` endpoint/key 后单独验证。

## 错误路径梳理（基于真实 eval 数据）

对主工作区 `eval/runs` 下 2038 个 AI 生成 `.dsl` 文件做模式统计，差模型的错误路径按"validator 能否拦截"分为三档：

### 第一档：可拦截（语法/契约/引用层）

| 错误路径 | 真实出现（文件数） | 典型样本 | 拦截机制 |
| --- | ---: | --- | --- |
| JS 方法调用 `.toString()`（零参） | 10 | `Text(data.totalDevices.toString(), ...)` | `syntax-unexpected-token`（零参调用 `()` 触发） |
| JS 方法调用 + 箭头 `.map(d => ...)` | 4 | `@Sum(data.devices.map(d => d.inBytes))` | `syntax-unexpected-token` + `unresolved-ref`（lambda 参数） |
| JS 全局对象 `Math.abs(...)` | 1 | `Math.abs(data.totalAlarms.delta)` | `unresolved-ref "Math"`（注意：语法层能解析，靠引用检查兜住） |
| 箭头函数 helper `f = (a) => ...` | 33 | `cardHeader = (title) => CardHeader(title)` | `unresolved-ref`（lambda 参数不可解析；**不是** syntax 诊断） |
| 发明的 builtin `@Div`/`@Mul` | 见下 | `@Mul(@Div(a, b), 100)` | `unknown-component`（builtin 白名单外的 `@` 调用按组件名查表） |
| 漏写 `@` 的 builtin | 未见真实样本（手工） | `FormatNumber(data.total, 1)` | `unresolved-ref "FormatNumber"` |
| 发明/过时组件名 | 大量 | `MiniChart`、`Descriptions`/`DescField`、旧名 `VLayout`/`Text` | `unknown-component` |
| `new`/`String()`/模板字符串/展开 | 手工（LLM 习惯性写法） | `new Date()`、`` `${data.total}` ``、`[...data.items]` | syntax + `unresolved-ref`/`unknown-component` 组合 |
| prose 夹杂（无 fence） | — | `Sure! Here is...` / 尾部 `Hope this helps!` / bullet 说明 | `syntax-missing-assignment` / `syntax-invalid-statement` |

### 第二档：不拦截但合法（不得误报）

这些"JS 味"写法实际在 openui-lang 子集内，corpus 以 valid-control 钉住防止未来误拦截：

- 三元表达式、`??`、比较、字符串拼接（`data.criticalAlarms > 0 ? "..." : "..."`）。
- **单引号字符串**（lexer 与 TS 对齐，合法）。
- markdown fence 包裹（```` ```openui-lang ````），甚至 fence 外带 prose —— 预处理器会提取 fenced block。注意与第一档的"无 fence prose"相反：**有 fence 则 prose 可容忍，无 fence 则 prose 挡下整个输出**。

### 第三档：确认漏拦截（需 dataModel schema 或 eval 层兜底）

| 错误路径 | 真实出现（文件数） | 典型样本 | 为什么漏 |
| --- | ---: | --- | --- |
| 数据路径包错一层 | 36 | `data.data.users`（正确是 `data.users`） | validator 不做 dataModel schema 对照，`data` 是外部 runtime ref，任意子路径都合法 |
| 字段名拼错 | —（同类） | `data.userz` / `Col("Name", "nmae")` | 同上，语法/引用层无法区分 |
| **单参数 JS 方法调用** | —（toFixed/join 未在现有 runs 出现，但为 toString 家族的等价形态） | `data.value.toFixed(1)`、`data.tags.join(", ")` | 解析器把"member 后接单参调用"当作合法调用表达式；**TS oracle 实测同样放行**（零参和 ≥2 参数会触发语法错，唯独 1 参是盲区） |

此外，eval 数据的 failing-patterns 汇总（53 个 run 带有 `failing-patterns.json`）显示质量类问题的量级：Missing data fields 50 次、Poor value formatting 47 次、Whitespace imbalance 47 次、Incoherent layout 46 次。这些属于渲染质量/语义正确性问题，**本 validator 定位上不拦截**，列出是为了划清"拦截报告不能声称解决"的外圈风险。

## 方法

验证入口是 JUnit：`InterceptionCorpusTest`。测试从 `src/test/resources/interception-corpus/manifest.json` 读取每个样本，加载共享 `GenerationContract`，以 `ValidationMode.FINAL` 调用真实 `DefaultOpenuiLangValidator.INSTANCE.validate(...)`。

样本来源分三类：

| source | 含义 |
| --- | --- |
| `harvested` | 从 `react-ui-dsl` 真实 snapshot / eval run 产物复制或按契约词表适配的 AI 生成 DSL（manifest `origin` 字段记录出处文件） |
| `mutated` | 基于真实 DSL 形态做局部变异，例如替换组件名、删除必填参数、插入旧 prop |
| `handcrafted` | 针对语法/root/inline reserved/JS 习惯写法等边界手写的最小样本 |

真实样本适配说明：eval runs 中的产物使用旧组件名（`VLayout`/`Text` 等），为了让 JS 错误模式不被 `unknown-component` 掩盖，`js-*` 类样本把组件名替换为 corpus 契约词表（`Stack`/`TextContent`），JS 错误表达式**保持原样**；另有 1 个完整真实文件逐字保留（见下文 real-file 案例）。

新增错误类的期望码全部来自两轮独立 Java probe 实测 + TS oracle（`packages/lang-core` parser）交叉对照，不是推测。

## Corpus 构成

| 类别 | 样本数 | 预期 |
| --- | ---: | --- |
| valid-control | 12 | `VALID` |
| unknown-component | 6 | `INVALID` 且包含 `unknown-component` |
| missing-required / null-required / excess-args / invalid-prop / inline-reserved / syntax / root-missing / unresolved-ref | 各 3 | `INVALID` 且包含对应码 |
| js-method-call | 3 | `INVALID`，syntax 与/或 `unresolved-ref` |
| js-global | 3 | `INVALID`，`unresolved-ref` / `unknown-component` |
| js-syntax-construct | 3 | `INVALID`（箭头 helper 经由 `unresolved-ref` 拦截） |
| builtin-misuse | 3 | `INVALID`，`unresolved-ref` / `unknown-component` |
| prose-chatter | 3 | `INVALID`，`syntax-missing-assignment` / `syntax-invalid-statement` |
| real-file | 1 | `INVALID`（完整真实 eval 产物，多类错误并发） |
| known-miss | 3 | **`missed`**：validator 放行，corpus 钉住这一事实 |

总计 61 个样本。

## 拦截率

| 错误类 | 命中/总数 | 拦截率 | 说明 |
| --- | ---: | ---: | --- |
| unknown-component | 6/6 | 100% | 含真实 eval 中的 `MiniChart`、`Descriptions`/`DescField`、旧名 `VLayout`/`Text` |
| missing-required | 3/3 | 100% | 缺少必填 positional prop |
| null-required | 3/3 | 100% | 必填参数显式为 `null` |
| excess-args | 3/3 | 100% | 额外 positional args |
| invalid-prop | 3/3 | 100% | 含 `Col.options.format` 旧用法 |
| inline-reserved | 3/3 | 100% | `Query` / `Mutation` 内联使用 |
| syntax | 3/3 | 100% | 未闭合括号/数组、缺少赋值 |
| root-missing | 3/3 | 100% | 空输出、注释输出、空 fenced block |
| unresolved-ref | 3/3 | 100% | 未定义 statement 引用 |
| js-method-call | 3/3 | 100% | `toString()` 零参、三元中的 `toString()` 链、`.map(d => ...)` |
| js-global | 3/3 | 100% | `Math.abs`、`new Date()`、`String()` 转换 |
| js-syntax-construct | 3/3 | 100% | 箭头 helper、模板字符串、展开运算符 |
| builtin-misuse | 3/3 | 100% | 漏 `@`、发明 `@Div`/`@Mul`、发明 `@Clamp` |
| prose-chatter | 3/3 | 100% | 开头/结尾/bullet prose（无 fence） |
| real-file | 1/1 | 100% | 完整真实 eval 产物（41 个 issue） |
| **合计（预期拦截）** | **46/46** | **100%** | |
| known-miss | 0/3 | 0% | **设计上无法拦截**，见漏拦截分析 |

## 有效控制组误拦截率

| 控制组来源 | 通过/总数 | 误拦截率 |
| --- | ---: | ---: |
| `react-ui-dsl` e2e snapshots 副本 | 8/8 | 0% |
| 边界合法形态（单引号、三元/`??`、fence、fence+prose） | 4/4 | 0% |

## 拦截报告示例：validator 实际能产出什么

`ValidationResult.issues()` 的每条 `ValidationIssue` 携带 `code`、`severity`、`source`（syntax/contract/reference/root）、`message`、`statementId`、`component`、`path`、`line:column`、`hint`、`retryable`。`actionableIssues()` 保持这些结构不变，只进行稳定的 dominance reduction：同一语法坏语句保留首个语法根因、Table row shape 压过同路径的通用 prop mismatch、有其他根因时移除 root tail，并保留不同 statement/path 的独立错误。以下均为对 corpus 样本的真实运行输出（非手写示意）。

### 单条错误的形态

`js-method-call-tostring`（`data.totalDevices.toString()`，源自真实 eval 文件）：

```text
status=INVALID
[ERROR] syntax-unexpected-token  src=syntax  stmt=kpiValue  line=3:54  Unexpected token R_PAREN
[ERROR] syntax-unclosed-bracket  src=syntax  stmt=kpiValue  line=3:13  Unclosed '('
[ERROR] excess-args              src=contract stmt=kpiValue comp=TextContent  TextContent takes 2 arg(s), got 3 (1 excess dropped)
```

`js-global-math-abs`（`Math.abs(data.delta)`）—— 注意语法层可解析，靠引用检查拦截，并带修复提示：

```text
status=INVALID
[ERROR] unresolved-ref  src=reference  stmt=root  line=1:1  unresolved reference "Math"
        hint: "Math" is a JavaScript global — JS globals and methods are not available in openui-lang; use builtins like @Abs, @Round, @FormatNumber, @FormatDate instead
```

hint 由 `RepairHints` 三档分流：精确同名 builtin（漏 `@`）→ did-you-mean `@X`；已知 JS global/关键字 → 上面的根因提示；其余 → `define a statement named "X" earlier in the document`（已移除误导修复模型的 "pass it as an external ref" 措辞）。`ReaskPromptBuilder` 在 prompt 视图做级联抑制（validator 输出保持全量），逐例对照见 `mvn test` 生成的 `target/interception-corpus-dump.md`。

`prose-chatter-intro`（无 fence 的开场白）：

```text
status=INVALID
[ERROR] syntax-missing-assignment  src=syntax  stmt=Sure  line=1:1  Statement 'Sure' is missing '='
```

### 完整真实文件的拦截报告（real-file-aggregated-kpi）

对 eval run `20260527_172847_mddu` 的 `aggregated-only.dsl`（91 行，KPI 摘要页）逐字校验，validator 产出 **41 条 issue**，按语句归属、语法错误带行号：

```text
status=INVALID  issues=41
  [ERROR] syntax-unexpected-token  stmt=kpiItem1  line=18  Unexpected token R_PAREN   ← data.totalDevices.toString()
  [ERROR] syntax-unclosed-bracket  stmt=kpiItem1  line=18  Unclosed '('
  ... （kpiItem2/3/4 同型，共 4 组 toString 语法错）
  [ERROR] unknown-component  stmt=root       comp=VLayout    Unknown component "VLayout" — not found in catalog or builtins
  [ERROR] unknown-component  stmt=cardTitle  comp=Text       （旧组件名，Stack/TextContent 重命名后失效）
  [ERROR] unknown-component  stmt=deviceStatusRow  comp=Div  （发明的 @Div builtin）
  [ERROR] unknown-component  stmt=performanceStatusRow comp=Mul
  [ERROR] unknown-component  stmt=cpuTrendChart  comp=MiniChart  （发明的组件）
  ... （unknown-component 共 32 条）
  [ERROR] root-not-renderable  stmt=root  root statement "root" did not resolve to a renderable component
汇总: syntax-unexpected-token=4, syntax-unclosed-bracket=4, unknown-component=32, root-not-renderable=1
```

这份输出说明拦截报告的实际粒度：一次校验给出全部问题（不是 fail-fast），每条可定位到语句与组件，语法错误可定位到行列，`hint` 字段可直接用于 re-ask 修复提示（`ReaskPromptBuilder` 消费同一结构）。

## 漏拦截分析

本轮把"漏拦截"从空集变成了**明确测量并钉住的边界**。3 个 `known-miss` 样本 validator 全部返回 `VALID`：

1. **`data.data.users`（信封包错一层）** —— 真实 eval 中 36 个文件出现。`data` 是外部 runtime ref，validator 无 dataModel schema 可对照，任何子路径都无法证伪。
2. **字段/集合名拼错**（`data.userz`、`Col` field `"nmae"`）—— 同因。
3. **单参数 JS 方法调用**（`data.value.toFixed(1)`、`data.tags.join(", ")`）—— 解析器语法子集把"member 后接单参调用"当合法调用表达式。已用 TS oracle（`packages/lang-core` `createParser`）实测确认 **TS 侧行为一致**（errors=[] unresolved=[data]），属于跨语言的语言子集盲区，不是 Java 移植缺陷。零参（`toString()`）和 ≥2 参（`slice(0, 5)`）都会触发语法错，唯独 1 参放行。

manifest 用 `expectedOutcome: "missed"` + 期望码钉住这三类：一旦未来 validator 具备 dataModel 对照或收紧调用语法，`InterceptionCorpusTest` 会失败报警，强制同步更新本报告。

**当前能力更新**：`ValidationRequest` 已支持传入具体 dataModel，并按真实数据证据检查
`data.*` 路径，因此第 1、2 类在数据模型可用时已经能够拦截。未传模型时继续 fail-open，
以兼容只做语法/contract 校验的旧调用。第 3 类仍需上游 lang-core 语法收紧或 eval 层渲染兜底。

另注：`excess-args` 按当前 validator 规则属于 blocking issue，但底层 TS materialize 语义会丢弃多余参数后继续渲染。这里将其作为拦截命中是为了保护生成质量和迁移安全；后续若产品语义调整为 warning，应同步更新 corpus 和报告。

## 验证命令

当前机器曾在默认 512MB/256MB/128MB/64MB heap 下出现 Windows DOS 1455 / heap reserve 失败。最终使用同样的离线、单类、single-fork 形态，把 Maven 与 surefire heap 压到 32MB 后完成验证：

```powershell
$env:MAVEN_OPTS='-Xms4m -Xmx32m -XX:+UseSerialGC -XX:MaxMetaspaceSize=48m -XX:ReservedCodeCacheSize=16m'
mvn -o '-Dsurefire.forkCount=1' '-DreuseForks=false' `
  "-DargLine=-Xms4m -Xmx32m -XX:+UseSerialGC -XX:MaxMetaspaceSize=48m -XX:ReservedCodeCacheSize=16m" `
  '-Dtest=InterceptionCorpusTest' test
```

结果：

```text
Tests run: 62, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 局限与后续

- 这是离线 corpus，不等价于真实 LLM 分布；live LLM e2e 仍依赖 genui-service 的 `LlmProperties` endpoint/key。
- 共享 contract 只覆盖本 corpus 中需要的组件，不代表完整 DSLEngine contract；真实产物中的旧组件名在完整 contract 下同样是 `unknown-component`（重命名后的必然结果），但拦截码分布可能不同。
- 数据路径类错误只有在调用方提供具体 dataModel 时才能拦截；没有运行时数据证据时会保守放行，因此不要在任何对外口径中声称 100% 拦截。
- 本报告不覆盖视觉质量、布局合理性、格式化质量（Poor value formatting 等）；这些属于 GenUI eval/render 层。
