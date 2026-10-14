# repair-bench 使用指南

repair-bench 是真实错误语料的修复基准（内部迭代工具,住 genui-java-sdk **test scope**,永不进交付物）。设计背景见 `docs/superpowers/specs/2026-10-13-repair-bench-design.md` 与实现设计 `2026-10-14-repair-bench-impl-design.md`。

形态：**案例包进 → stdout JSONL 出**。bench 自身无状态,不写任何文件;run 目录 / manifest / HTML 报告由外部脚本消费 JSONL 生成（切片 2 职责）。

## 运行

```bash
java -cp <test-classpath> com.huawei.cloudsop.genui.bench.RepairBenchCli \
  --cases=repair-cases/ [--concurrency=4] [--max-attempts=3] [--timeout-minutes=0] [--limit=N]
```

| 参数 | 默认 | 说明 |
|---|---|---|
| `--cases` | 必填 | 案例包根目录,扫一层子目录中含 `case.json` 者 |
| `--concurrency` | 4 | 有界线程池大小 |
| `--max-attempts` | 3 | 单案例修复尝试预算 |
| `--timeout-minutes` | 0（关） | 尝试之间的墙钟预算;关闭时靠 transport 的 10 分钟请求上限兜底 |
| `--limit` | 无 | 按目录名取前 N,冒烟用 |

### test-classpath 怎么拼（本机 mvn 会 fork OOM）

本机 commit charge 常耗尽,不能用 `mvn test-compile` 依赖。用独立 javac + launcher：

```bash
cd packages/genui-java-sdk
M2=~/.m2/repository

# 1. 编译（增量可跳过）
javac -encoding UTF-8 -d /tmp/rb-build/main \
  -cp "$M2/com/alibaba/fastjson2/fastjson2/2.0.61/fastjson2-2.0.61.jar" \
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

环境变量 `LLM_BASE_URL / LLM_API_KEY / LLM_MODEL / HTTPS_PROXY`。真实环境变量优先;其次读**仓库根** `.env`（文件可选,只解析 `KEY=VALUE`,`#` 注释与空行跳过）。

当前 `.env` 指向 `qwen3.6-27b`,该账号未购买此模型 → 403 `AccessDenied.Unpurchased`。跑真实 baseline 前先换已购模型或开通。

## 案例包格式

```
repair-cases/
  contract.json          # 语料级共享契约
  case-0001/
    case.json            # 必需:id / originalIntent? / dataModel?
    contract.json        # 可选:案例级契约,优先于语料级
    dsl.txt              # 必需:失败 DSL 原文,不包 fence
    issues.json          # 可选:内部拦截报错 [{"code": "..."}],仅透传不参与判定
```

`case.json` 字段：

- `id`：必填非空,全语料唯一（以它为准,目录名只管排序）
- `originalIntent`：缺省传 null,修复提示词跳过 Original request 段
- `dataModel`：**缺失/显式 null → null（类型校验关闭）;`{}` → 空 map（具体空模型）**。两者语义不同,别混

契约格式 = `GenerationContractLoader.fromJson` 的输入,即组件是 `description` + `propsSchema` 包装（**不是**拦截语料那种裸 schema——当初就是这里踩的坑,loader 直接解析失败）。

## 输出解读

逐案例行（完成顺序）+ 末尾一行 summary。字段口径见实现设计 §5,关键点：

| status | 含义 | 迭代动作 |
|---|---|---|
| `repaired` | 某轮修复后 FINAL 校验通过 | — |
| `still-invalid` | 试满预算仍无效 | 改提示词 / 调策略 |
| `transport-error` | LLM 请求失败（403、网络等） | 重跑;**不进主口径分母** |
| `timeout` | 尝试之间命中墙钟预算 | 重跑;不进主口径分母 |
| `not-reproduced` | 初次校验就通过（复现失败） | 先修环境——契约漂移信号,惩罚副口径 |

- **主口径**（80% 目标立在这）：`repaired / (repaired + still-invalid)`,基础设施噪声剔除
- **副口径**：`repaired / total`,含 not-reproduced 惩罚
- `initialIssueCodes` / `attempts[].issueCodes` / `finalIssueCodes` 全部是 **actionable 视图**（修复提示词消费的去噪视图,与完整 raw issues 有差）
- `issueBuckets` 只统计 reproduced 案例,按初始 codes 分桶——迭代循环"按 code 看哪类修不动"的直接数据源

## 注意事项

1. **坏语料整批终止,不降级**。加载阶段任何解析失败（case.json 非法 / dsl.txt 空 / id 重复 / 契约解析失败 / 零案例）→ 退出码 1,报错列出全部问题案例。语料是 git 静态资产,坏一个就是全坏。
2. **契约没有 SDK 默认兜底**。两级契约都缺 → 报错终止。故意的设计：静默兜底会把契约漂移伪装成 not-reproduced。
3. `dsl.txt` 读原文**不 strip**,存证优先。
4. `--timeout-minutes` 是尝试之间的检查,不是硬超时;单请求挂死靠 transport 10 分钟上限。
5. 修复请求固定温度 0 + 不开 thinking,`originalSystemPrompt` 从案例契约现算（案例包不带 system prompt）。
6. 新增案例后跑 `RepairCasePackTest`（loader 校验）确认语料结构合法。
