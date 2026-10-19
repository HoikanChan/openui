# Repair Bench（真实错误语料修复基准）设计

- 日期：2026-10-13
- 状态：已与维护者对齐（方案 A + 前置判断 1/2）
- 分支策略：`validation-iteration` 长期分支，不合回 master，作为外部迭代线

> **2026-10-14 修订**（维护者 grilling 后与实现对齐，见 [2026-10-14 实现设计](2026-10-14-repair-bench-impl-design.md)）：
>
> 1. **双轨道取消**（修订 B/C/D 相关段落）：真实语料不存在独立的 render 发现通道——`error_detail.txt` 是挂在同一批流量语料上的人工渲染问题标注，不是一条错误报告来源。bench 案例包只收校验器拦截的失败案例（首批 26 例 INVALID）；MISSED 案例的"补规则"工作流留在 validation-test 缺口分析工具链（MISSED→DETECTED 复跑度量），不进 bench。`reportedBy` 字段、按轨道汇总、gapOpen/gapClosed 等轨道 2 概念全部废弃。
> 2. **bench 驻 genui-java-sdk test scope**（修订 C）：`genui-eval-cli` 预留为 validation 模块向内部测试服务交付的载体（形态待定，另开设计），不再承载 bench——避免测试服务的交付工作牵动内部迭代工具。
> 3. **主口径剔除 transport-error 与 timeout**（修订 D）：基础设施噪声与自设预算不算"可复现但修不动"，可筛出重跑；主口径 = repaired / (repaired + still-invalid)。
> 4. bench 的 `.env` 从**仓库根**读取（原文 B 密钥搬迁小节的指向相应调整；eval-cli 包内 .env 留给交付链路，交付不依赖 .env——另议）。
>
> 本文其余内容保留原状，作为当时的决策记录；实现以上游 spec + 本修订注 + 2026-10-14 实现设计为准。


## 背景与目标

公司内部与外部仓库已发生显著漂移：前端 UI 基本全改（外部视为不可用），后端提示词部分调整但整体逻辑未变。外部环境要继续迭代 validation 模块，输入是内部捞出的真实错误数据（生成失败的 DSL + 报错 + 当时的组件契约）。

目标：**基于真实错误数据做错误校验与反思修复（Reflection repair），可复现案例的修复成功率达到 80%。**

已确认的事实前提：

1. 内部错误数据**契约完整可导出**（DSL + 报错 + 当时生成所用的 base contract / Generation Extension）。
2. 错误发现源两类皆有：内部校验拦截（带报错）与渲染/人工发现（漏网）。后者是**校验器的缺口**，迭代的核心杠杆是把它们转化为可拦截错误。
3. repair 骨架已存在于 genui-java-sdk `core/validation/repair/`（RepairCoordinator.repairFull / reaskStream、ReaskPromptBuilder、RepairPolicy）。缺的是**用真实错误语料度量修复成功率的基准设施**。
4. `reports/reask-repair-rate-analysis.html` 的既有结论：修复率低的主因是 repair 请求未继承足够的语法与组件上下文、且用高温/单次尝试做本应确定性的修复。本基准就是持续验证此类改进的度量设施。

## 前置判断

1. **UI 代码删除，不保留**（分支删除，master 不动）。理由：外部前端对内部错误数据不可用；且过期前端代码会被 Agent 误读，污染迭代。删除在 `validation-iteration` 分支进行，master 始终保留完整前端。
2. **修复成功 = 修复后 FINAL 校验通过（语法 + 语义）**。视觉正确性不在本基准的度量范围内（无内部渲染环境）；像素级抽检如未来需要，用外部组件库近似渲染，明示「非内部像素」。
3. **修复迭代以内部捞出的失败 DSL 为起点**做 reask，不在外部重新生成——对内部提示词漂移不敏感，外部只迭代「校验 + reask」逻辑本身。

## A. 删除范围（validation-iteration 分支）

### 删除

| 路径 | 说明 |
|---|---|
| `packages/react-ui` | workspace 已标注 "just for reference"（638 文件） |
| `packages/react-ui-dsl` | 408 文件；含 e2e/benchmark/fuzz/eval 全套渲染验证基建 |
| `packages/react-lang` | React 渲染运行时 |
| `packages/react-headless` | 无头聊天状态层 |
| `packages/openui-cli` | 脚手架 CLI |
| `examples/react-ui-dsl-demo` | 前端演示 app |
| `examples/canvas-demo` | 演示 app |
| `skills/react-ui-dsl-genui-eval-loop/`、`skills/openui/` | 引用已删包的技能 |
| `.claude/skills/react-ui-dsl-genui-eval-loop/`、`.claude/skills/react-ui-dsl-llm-regression-debugging/` | 同上，Agent 误读重灾区 |
| `.github/workflows/build-js.yml` | pnpm 全仓构建，删包后必挂 |
| `.github/workflows/publish-npm-package.yml` | 发布对象全部已删（workflow_dispatch 手动触发，删除属清理） |

### 保留

| 路径 | 保留理由 |
|---|---|
| `packages/lang-core` | 框架无关 Lang 内核（74 文件）。validation 规则迭代需保住 TS/Java 语义一致性 |
| `packages/genui-java-sdk` | validation + repair 主战场 |
| `packages/genui-eval-cli` | repair-bench 扩展点 |
| `examples/genui-service` | SmartCanvasService 契约参考实现（内部仿照对象） |
| `interal-code/` | 内部参照代码 |
| `docs/` | 独立 package（不在 pnpm workspace），死重但无害；SYSTEM-DESIGN.md 是 Reflection 设计原始出处 |
| `reports/reask-repair-rate-analysis.html` | 既有修复率根因分析，迭代方向的直接输入 |
| `openspec/`、根 CONTEXT.md / CONTEXT-MAP.md | 历史 spec 归档；其中引用已删包的文档按历史文档对待，不追改 |

### 关键细节

- **密钥搬迁**：`packages/react-ui-dsl/.env`（未跟踪文件，LLM 密钥）在删除前拷贝到 `packages/genui-eval-cli/.env`，随 repair-bench 一并加载。
- **workspace 收缩**：从 `pnpm-workspace.yaml` 删除包后需重跑 `pnpm install` 让 lockfile 收缩，消除幽灵依赖；Node 侧安装验证以 `docs/` 可装、无 ghost reference 报错为准。
- **gitignore 空洞**：原匹配 `packages/react-ui-dsl/...` 的 ignore 条目对不存在路径失效，无副作用，保留原状。
- **CONTEXT-MAP.md / README.md** 引用已删包：属于文档更新项，不阻塞环境可用性，随首个 repair-bench 提交一并更新（CONTEXT-MAP 表格收缩为保留包）。

## B. 错误案例包格式

```
repair-cases/
  case-0001/
    case.json      # id, generationId, reportedBy: "validator" | "render", originalIntent?, dataModel?
    contract.json  # 生成当时的契约；格式 = GenerationContractLoader.fromJson 的输入
    dsl.txt        # 失败的 DSL 原文
    issues.json    # 可选：内部拦截时的报错，用于外部复现比对
```

- `reportedBy` 是两条轨道的分流开关：`validator` → 轨道 1（外部直接复现-修复-复验）；`render` → 轨道 2（先诊断校验缺口，补规则后转入轨道 1）。
- `dataModel` 应随案例携带：部分校验/修复规则依赖 externalRefs（RepairCoordinator#setExternalRefs）。
- 契约格式与内部 SDK 同源，shape 天然一致；此格式将反向同步给内部导出工具。

## C. repair-bench 子命令

```
java -jar genui-eval-cli.jar repair-bench --cases=<dir> [--concurrency=N] [--max-attempts=3]
```

沿用 genui-eval-cli 既有模式：无状态计算器（案例包进、JSONL 出），文件簿记（run 目录、报告汇总）由外部脚本负责。单案例流程：

1. 载入契约 + DSL → FINAL 模式校验 → issues_v0
2. 复现不出任何错误 → `not-reproduced`（内外漂移信号，单独统计，**不进分母**）
3. 复现 → `RepairCoordinator.repairFull`（attempts 可配，默认 3）→ 每轮记录 issue codes
4. 逐案例输出 `{caseId, reportedBy, reproduced, issueCodes, attempts:[{issueCodes}], finalStatus, success}`；末尾输出按轨道、按 issue code 分桶的汇总

LLM 配置沿用环境变量 `LLM_BASE_URL / LLM_API_KEY / LLM_MODEL / HTTPS_PROXY`，并从 `packages/genui-eval-cli/.env` 加载（密钥搬迁来源）。

## D. 指标口径与迭代工作流

**成功 = 修复后 FINAL 校验通过。** 双口径：

- **主口径（80% 立在这上面）**：`success / 可复现案例数`
- **副口径**：`success / 全部案例`（含 not-reproduced 惩罚，度量外部环境保真度）

迭代循环：baseline → 按 issue code 分桶看失败 → 三类根因三类药：

| 根因 | 药 |
|---|---|
| ReaskPromptBuilder 描述质量差 | 改修复提示词（结构化 issue → 模型可执行的修正指令） |
| 校验规则缺失（轨道 2 漏网） | 补 semantic 规则，案例自动转入轨道 1 |
| 模型修不动 | 调 RepairPolicy（attempts / 超时 / 上下文继承） |

**回归保护**：案例**复现性**（同一 DSL 稳定打出相同 issue codes）是确定性的 → 常规测试；**修复成功率**依赖 LLM → bench 指标，不做 CI 门禁。

## E. 风险与边界

- **模型差异**：外部 `.env` 模型 ≠ 内部生产模型时，绝对数字仅供参考；可迁移的是校验规则、Reask 提示词、策略参数。run manifest 记录模型名。
- **repairFull vs reaskStream**：bench 先做 repairFull（同步、确定性、好度量）。若内部生产实际走流式 reaskStream，需回内部确认后补流式 bench。
- **契约漂移监控**：`not-reproduced` 率是外部环境对内部代表性的直接度量；超阈值先修环境再谈修复率。

## 实施切片（本 spec 只覆盖切片 0）

- **切片 0（本次）**：环境准备——拉分支、按 A 删除、密钥搬迁、workspace 收缩、验证 Java 侧全绿（genui-java-sdk 单测 + eval-cli 打包 + print-prompt 冒烟）、文档收缩。
- 切片 1：repair-bench 子命令 + 案例包 loader。
- 切片 2：首批真实语料 baseline 与分桶报告。
- 切片 3+：按 D 的迭代循环持续改进，直至主口径 ≥ 80%。
