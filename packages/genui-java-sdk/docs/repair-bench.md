# repair-bench 使用指南

repair-bench 是真实错误语料的修复基准（内部迭代工具,住 genui-java-sdk **test scope**,永不进交付物）。设计背景见 `docs/superpowers/specs/2026-10-13-repair-bench-design.md` 与实现设计 `2026-10-14-repair-bench-impl-design.md`。

形态：**案例包进 → JSONL 出 → 同批自动生成一份自包含 HTML 报告**。JSONL 默认写 stdout;报告每次都生成,数据内嵌、不依赖网络或外部资源,跑完直接打开即可看结果。bench 自身不维护 run 目录 / manifest 等跨轮状态。

## 运行

```bash
# 默认 validation-only：零 LLM 调用,不需要 .env
java -cp <test-classpath> com.huawei.cloudsop.genui.bench.RepairBenchCli \
  --cases=repair-cases/ [--concurrency=4] [--limit=N] [--out=target/run.jsonl]

# 流式修复：显式加 --repair 才烧 LLM
java -cp <test-classpath> com.huawei.cloudsop.genui.bench.RepairBenchCli \
  --cases=repair-cases/ --repair [--max-attempts=3] [--timeout-minutes=0] \
  [--out=target/run.jsonl] [--html=target/run.html]
```

bench 的修复链路只走 `GenUiGenerator.generateStream`：流式是 SDK 唯一的修复路径,同步 `generate` 不校验、不修复。三种模型请求都以 SSE 处理：初次生成由 bench 本地回放案例 DSL（不调模型）,增量修复转交真实模型流,修复失败后的整页 Markdown 降级由本地桩 SSE 承接（不再额外消耗模型调用）。

| 参数 | 默认 | 说明 |
|---|---|---|
| `--cases` | 必填 | 案例包根目录,扫一层子目录中含 `case.json` 者 |
| `--repair` | 关 | 开关。**不带 = validation-only**：每案例只做 FINAL 校验,输出 `invalid`（issueCodes 全量）/ `not-reproduced`,不读 .env、不建 transport;**带 = 流式增量修复**,走 `generateStream` |
| `--concurrency` | 4 | 有界线程池大小 |
| `--max-attempts` | 3 | 单案例修复尝试预算（仅 `--repair`） |
| `--timeout-minutes` | 0（关） | 尝试之间的墙钟预算;关闭时靠 transport 的 10 分钟请求上限兜底 |
| `--limit` | 无 | 按目录名取前 N,冒烟用 |
| `--out` | stdout | JSONL 输出文件;父目录自动创建 |
| `--html` | 派生 | HTML 报告路径。不传时：有 `--out` 则以其同名 `.html` 落盘;没有 `--out` 则写 `target/repair-bench/repair-bench-<yyyyMMdd-HHmmss>.html` |

报告路径始终打印到 stderr（`[repair-bench] report: <abs path>`）。报告是运行结果的视图,写失败只打 warning,不影响批次退出码。

### 语料从哪来

`repair-cases/` 由 `scripts/md-to-repair-cases.mjs` 从 smartcanvas 失败语料生成（首次已落地,产物进 git;语料更新后重跑）：

```bash
node scripts/md-to-repair-cases.mjs            # 常规：LLM 反推缺失 intent(复用已落地值)
node scripts/md-to-repair-cases.mjs --no-llm   # 不调 LLM(缺失 intent 留空)
```

缺口（缺 dataModel、反推 intent 待核对、空分类文件）记录在 `repair-cases/CONVERSION-GAPS.md`。

### test-classpath 怎么拼（本机 mvn 会 fork OOM）

本机 commit charge 常耗尽,不能用 `mvn test-compile` 依赖。用独立 javac + launcher：

```bash
cd packages/genui-java-sdk
M2=~/.m2/repository

# 1. 编译（增量可跳过）
javac -encoding UTF-8 -d /tmp/rb-build/main \
  -cp "$M2/com/alibaba/fastjson2/fastjson2/2.0.60/fastjson2-2.0.60.jar" \
  $(find src/main/java -name "*.java")
javac -encoding UTF-8 -d /tmp/rb-build/test \
  -cp "/tmp/rb-build/main;src/main/resources;$M2/…fastjson2…jar;…junit…jar" \
  $(find src/test/java -name "*.java")

# 2. 运行（Windows 路径用 cygpath -w 转,BSP jar 和资源目录要显式上 classpath）
java -cp "/tmp/rb-build/test;/tmp/rb-build/main;src/main/resources;…jars" \
  com.huawei.cloudsop.genui.bench.RepairBenchCli --cases=repair-cases/
```

classpath 必含：`fastjson2`、`com.huawei.bsp.commonlib.resetclient`（编译期）、`src/main/resources`（默认契约）。单测验证用同一 classpath 跑 `TestLauncher`（JUnit platform launcher 小 main）。

### LLM 配置

环境变量 `LLM_BASE_URL / LLM_API_KEY / LLM_MODEL / HTTPS_PROXY`。真实环境变量优先;其次读**仓库根** `.env`（文件可选,只解析 `KEY=VALUE`,`#` 注释与空行跳过）。**只有 `--repair` 才读**——validation-only 不碰 LLM 配置。

当前 `.env` 指向 `qwen3.8-flash`（已验证可调通）。换模型改 `.env` 的 `LLM_MODEL` 或设环境变量。

## 案例包格式

```
repair-cases/
  case-0001/
    case.json            # 必需:id / originalIntent? / dataModel?
    dsl.txt              # 必需:失败 DSL 原文,不包 fence
    issues.json          # 可选:内部拦截报错 [{"code": "..."}],仅透传不参与判定
```

`case.json` 字段：

- `id`：必填非空,全语料唯一（以它为准,目录名只管排序）
- `originalIntent`：缺省传 null,修复提示词跳过 Original request 段
- `dataModel`：**缺失/显式 null → null（数据依赖部分未知,静态检查继续）;`{}` → 空 map（具体空模型）**。两者语义不同,别混

仓库内语料与生产统一调用 `GenerationContractLoader.loadDefault()`，读取唯一的 `src/main/resources/openui/base-contract.json`，不再保存完整契约副本。

外部案例包可显式提供包级或案例级 `contract.json`，优先级为案例级 > 包级 > SDK 默认契约。显式文件无效时整批终止，不回退。自定义契约格式为 `GenerationContractLoader.fromJson` 的输入（组件使用 `description` + `propsSchema` 包装）。

历史评测结果应记录运行时 Git commit、契约版本及原始契约文件的 SHA-256。复现时检出对应 commit；不得把旧版契约下的结果直接当作当前默认契约的基线。

## 输出解读

逐案例行（完成顺序）+ 末尾一行 summary。字段口径见实现设计 §5,关键点：

| status | 含义 | 迭代动作 |
|---|---|---|
| `invalid` | validation-only 下复现成功（FINAL 校验无效） | 看 `initialIssueCodes` 对比根因 |
| `repaired` | 某轮修复后 FINAL 校验通过 | — |
| `still-invalid` | 试满预算仍无效 | 改提示词 / 调策略 |
| `transport-error` | LLM 请求失败（403、网络等） | 重跑;**不进主口径分母** |
| `timeout` | 尝试之间命中墙钟预算 | 重跑;不进主口径分母 |
| `not-reproduced` | 初次校验就通过（复现失败） | 先修环境——契约漂移信号,惩罚副口径 |

- **主口径**（80% 目标立在这）：`repaired / (repaired + still-invalid)`,基础设施噪声剔除;validation-only 跑法下主口径恒为 null（没有修复环节）
- **副口径**：`repaired / total`,含 not-reproduced 惩罚
- `initialIssueCodes` / `attempts[].issueCodes` / `finalIssueCodes` 全部是 **actionable 视图**（修复提示词消费的去噪视图,与完整 raw issues 有差）
- `issueBuckets` 只统计 reproduced 案例,按初始 codes 分桶——迭代循环"按 code 看哪类修不动"的直接数据源

## HTML 报告

每次运行结束自动生成（`RepairBenchReport`）：用本批 JSONL 行 + `--cases` 目录里的 `case.json` 元数据拼成 payload,内嵌进单个 `.html`。CSS/JS/数据全内联,无 CDN、无外链,可直接离线打开或作为附件发出。

页面内容：

- **顶部汇总**：模型、尝试预算、超时、总案例数、已修复 / 仍无效 / 仅校验无效 / 未复现 / 错误 / 超时、主口径与副口径、issue 分桶表;状态过滤条按案例数着色
- **每案例卡片**：原始意图、数据模型、根因、初始 DSL、修复轮次
- **修复轮次**：每轮可展开看该轮**累积后的完整 DSL**、原始模型输出、轮末诊断（issue code / message / hint / 关联语句）;默认只展开最后一轮
- **反思 Prompt**：按 `IncrementalRepairPromptBuilder` 的段落结构逐段重建（标注为"重建",因为原始 prompt 不落盘）
- **最终态**：最后一次校验通过的 DSL,以及与上一轮的逐行 diff
- **降级不展示**：修复失败时 `outputDsl` 是 Markdown 兜底页,报告不嵌入它;只展示 `finalDsl`（最后一次真实校验的 DSL）

validation-only 运行同样有报告：案例状态为 `invalid` / `not-reproduced`,顶部多一个"仅校验无效"计数与过滤项,卡片里注明"无轮次记录"。

布局在 720 / 600 / 420px 三个断点下均做了适配,手机上可正常阅读。

## 注意事项

1. **坏语料整批终止,不降级**。加载阶段任何解析失败（case.json 非法 / dsl.txt 空 / id 重复 / 契约解析失败 / 零案例）→ 退出码 1,报错列出全部问题案例。语料是 git 静态资产,坏一个就是全坏。
2. **契约没有 SDK 默认兜底**。两级契约都缺 → 报错终止。故意的设计：静默兜底会把契约漂移伪装成 not-reproduced。
3. `dsl.txt` 读原文**不 strip**,存证优先。
4. `--timeout-minutes` 是尝试之间的检查,不是硬超时;单请求挂死靠 transport 10 分钟上限。
5. 修复请求固定温度 0 + 不开 thinking,`originalSystemPrompt` 从案例契约现算（案例包不带 system prompt）。
6. 新增案例后跑 `RepairCasePackTest`（loader 校验）确认语料结构合法。
7. **dataModel 为 null 的案例也固定 `data` externalRef**：DSL 引用 `data` 是提示词契约的产物,与数据是否在案无关;类型校验才由 dataModel 缺失关闭。语料包的 7 个缺 dataModel 案例因此是 `not-reproduced`（类型类根因不触发）,回填后即恢复——见 `CONVERSION-GAPS.md`。
