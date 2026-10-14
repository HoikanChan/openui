# Context Map

这是一个 monorepo,每个 package 是独立的子领域(bounded context)。各自有自己的术语表 `CONTEXT.md`。本文件只指路、说边界,不重复术语定义。

> **本文件描述 validation-iteration 分支的收缩后形态。** 前端包(react-lang / react-headless / react-ui / react-ui-dsl / openui-cli)与前端演示 examples 已从本分支删除,完整形态见 master。背景与删除决策:[repair-bench 设计](docs/superpowers/specs/2026-10-13-repair-bench-design.md)。

## 跨上下文核心概念

以下几个概念跨多个 package 出现,语义统一,各 package 各自展开:

- **OpenUI Lang / Stream IR** — 模型生成的、可流式的 UI 中间表示语言。在 [lang-core/CONTEXT.md](packages/lang-core/CONTEXT.md) 定义。渲染侧消费方(react-lang / react-ui-dsl)已随前端删除,仅保留内核与 Java 侧镜像。
- **Library** — 组件库定义(组件名 + props schema + 描述;渲染实现字段在前端包,已删)。lang-core 中的无渲染版本是 Java Generation Contract 的对齐基线。

## Package 边界

| Package | 角色 | 依赖方向 |
|---|---|---|
| [lang-core](packages/lang-core/CONTEXT.md) | 框架无关的 Lang 内核:parser / runtime / prompt 生成 / 校验;Java parser 的跨语言对齐 oracle 来源 | 无上游 |
| [genui-java-sdk](CONTEXT.md) | Java 后端 SDK:Generation Contract 注册 + prompt 拼装(与 lang-core prompt 字节对齐)+ validation/repair;术语在系统词汇表 | 无上游(镜像 lang-core prompt 语义) |

## 工具边界

- `packages/genui-eval-cli` — **Eval Generation CLI**:包装 genui-java-sdk 生成能力的 Java 命令行工具。预留为 validation 模块向内部测试服务交付的载体(交付形态待定,另行设计),仓库自用工具,不是子领域,无独立 CONTEXT.md。内部迭代基准(repair-bench)不驻此包——住 genui-java-sdk test scope 的 `bench/`(与 ValidateCli、ValidationCorpusReportGenerator 同先例),见 [repair-bench 实现设计](docs/superpowers/specs/2026-10-14-repair-bench-impl-design.md)。

## repair-bench

`packages/genui-java-sdk/src/test/java/com/huawei/cloudsop/genui/bench/RepairBenchCli.java` 是 repair-bench 的入口(案例包进 → stdout JSONL 出)。运行方式:

```bash
java -cp <test-classpath> com.huawei.cloudsop.genui.bench.RepairBenchCli \
  --cases=repair-cases/ [--concurrency=4] [--max-attempts=3] [--timeout-minutes=0] [--limit=N]
```

LLM 配置:`LLM_BASE_URL / LLM_API_KEY / LLM_MODEL / HTTPS_PROXY`,真实环境变量优先,其次从仓库根 `.env` 读取。

## Examples 边界

- `examples/genui-service` — **GenUI Service**:genui-java-sdk 的 REST 参考实现(SmartCanvasService 契约的可运行模板),供公司内部仿照搭建;术语在[系统词汇表](CONTEXT.md)。

## 不在术语表里的东西

`CONTEXT.md` 是**纯词汇表**,不写:
- 实现细节、文件路径、API 签名(这些读代码即可)
- 路线图、待办、bug(用 issue 系统)
- 架构决策的理由(写 `docs/adr/`)

业务侧术语(如 NOE Mate 的 SmartCanvasService、AICOService、PIU、eview)**不属于** openui 本仓——它们是 [DESIGN.md](DESIGN.md) 描述的下游消费方,本仓 CONTEXT 只在确有边界引用时一句话带过,不展开。
