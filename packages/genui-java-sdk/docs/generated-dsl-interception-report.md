# Generated DSL Validation 拦截实践报告

## 结论摘要

本次离线拦截验证为 `DefaultOpenuiLangValidator` 建立了一组可重复执行的基线 corpus：

- 有效控制组：8 个来自 `packages/react-ui-dsl/src/__tests__/e2e/snapshots` 的真实 AI 生成 `.dsl` 副本。
- 已知坏例：9 个错误类，每类 3 个样本，共 27 个。
- 测量结果：27/27 个坏例被 `FINAL` 模式拦截；8/8 个有效控制样本通过。
- 误拦截率：0/8 = 0%。
- 漏拦截：0 个。

这说明当前 Java SDK 校验器对本 corpus 覆盖的语法、契约、root 与引用错误有稳定拦截能力。结论仅代表离线样本；真实模型端到端生成仍需接入实际 `LlmProperties` endpoint/key 后单独验证。

## 方法

验证入口是新增 JUnit：`InterceptionCorpusTest`。测试从 `src/test/resources/interception-corpus/manifest.json` 读取每个样本，加载共享 `GenerationContract`，以 `ValidationMode.FINAL` 调用真实 `DefaultOpenuiLangValidator.INSTANCE.validate(...)`。

样本来源分三类：

| source | 含义 |
| --- | --- |
| `harvested` | 从 `react-ui-dsl` 真实 snapshot 复制的 AI 生成 DSL，只读采集，不修改原文件 |
| `mutated` | 基于真实 DSL 形态做局部变异，例如替换组件名、删除必填参数、插入旧 prop |
| `handcrafted` | 针对语法/root/inline reserved 等边界手写的最小样本 |

共享 contract 刻意保持小而确定：覆盖 `Stack`、`TextContent`、`Table`、`Col`、`Card`、`List`、`Series`、`BarChart`、`PieChart`、`LineChart`、`Tabs` 等本 corpus 需要的组件，并对 `Col.options`、`Tabs.options` 使用闭合对象以触发 `invalid-prop`。

## Corpus 构成

| 类别 | 样本数 | 预期 |
| --- | ---: | --- |
| valid-control | 8 | `VALID` |
| unknown-component | 3 | `INVALID` 且包含 `unknown-component` |
| missing-required | 3 | `INVALID` 且包含 `missing-required` |
| null-required | 3 | `INVALID` 且包含 `null-required` |
| excess-args | 3 | `INVALID` 且包含 `excess-args` |
| invalid-prop | 3 | `INVALID` 且包含 `invalid-prop` |
| inline-reserved | 3 | `INVALID` 且包含 `inline-reserved` |
| syntax | 3 | `INVALID` 且包含 syntax diagnostic |
| root-missing | 3 | `INVALID` 且包含 `root-missing` |
| unresolved-ref | 3 | `INVALID` 且包含 `unresolved-ref` |

总计 35 个样本。

## 拦截率

| 错误类 | 命中/总数 | 拦截率 | 说明 |
| --- | ---: | ---: | --- |
| unknown-component | 3/3 | 100% | 未注册组件名均被 contract checker 标记 |
| missing-required | 3/3 | 100% | 缺少必填 positional prop 被标记 |
| null-required | 3/3 | 100% | 必填参数显式为 `null` 被区分为 null-required |
| excess-args | 3/3 | 100% | 额外 positional args 被报告为丢弃的 excess |
| invalid-prop | 3/3 | 100% | 包含 `Col.options.format` 旧用法样本 |
| inline-reserved | 3/3 | 100% | `Query` / `Mutation` 内联使用被阻断 |
| syntax | 3/3 | 100% | 未闭合括号/数组、缺少赋值均有语法诊断 |
| root-missing | 3/3 | 100% | 空输出、注释输出、空 fenced block 均无 renderable root |
| unresolved-ref | 3/3 | 100% | 未定义 statement 引用在 FINAL 模式为 blocking |
| 合计 | 27/27 | 100% | 本离线 corpus 无漏拦截 |

## 有效控制组误拦截率

| 控制组来源 | 通过/总数 | 误拦截率 |
| --- | ---: | ---: |
| `react-ui-dsl` e2e snapshots 副本 | 8/8 | 0% |

控制组包括表格、渲染单元格、排序日期列、列表、卡片、柱图、饼图和折线图。测试使用 `externalRefs` 允许 `data` 及 render lambda 局部变量，避免把运行时数据绑定误判为缺失 statement。

## 漏拦截分析

本次测量没有漏拦截样本；manifest 中没有 `expectedOutcome: "missed"` 条目。

需要注意的是，`excess-args` 按当前 validator 规则属于 blocking issue，但底层 TS materialize 语义会丢弃多余参数后继续渲染。这里将其作为拦截命中是为了保护生成质量和迁移安全；后续若产品语义调整为 warning，应同步更新 corpus 和报告。

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
Tests run: 36, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 局限与后续

- 这是离线 corpus，不等价于真实 LLM 分布；live LLM e2e 仍依赖 genui-service 的 `LlmProperties` endpoint/key。
- 共享 contract 只覆盖本 corpus 中需要的组件，不代表完整 DSLEngine contract。
- 有效控制组来自已存在 snapshot 的副本，能够测误拦截，但样本量仍有限。
- 本报告不覆盖视觉质量、业务语义正确性或 React renderer runtime 行为；这些属于 GenUI eval/render 层。
