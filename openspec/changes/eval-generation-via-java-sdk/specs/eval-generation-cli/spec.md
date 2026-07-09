## ADDED Requirements

### Requirement: Eval Generation CLI SHALL 以无状态批量方式生成 DSL

Eval Generation CLI（`packages/genui-eval-cli`）SHALL 提供 `generate` 命令：从 `--jobs` 指定的 JSON 文件读取用例清单（每项含 `id`、`userInput`、`dataModel`），对每个用例经 Java Generation SDK 生成 openui-lang，并以 JSONL 格式逐行输出结果到 stdout。CLI MUST NOT 写入任何快照文件、run 清单或其他 eval 簿记文件。

#### Scenario: 批量生成并逐行输出

- **WHEN** Node 侧以 `--jobs=jobs.json` 启动 CLI，清单含 3 个用例
- **THEN** stdout MUST 逐行输出 3 个 JSON 对象，每行形如 `{"id":"<case-id>","status":"ok","dsl":"<openui-lang>"}`
- **AND** 每完成一个用例即输出一行，不等待整批完成

#### Scenario: 单用例失败不中断批次

- **WHEN** 某个用例的 LLM 调用失败（网络错误或空响应）
- **THEN** 该用例 MUST 输出 `{"id":…,"status":"error","error":"<message>"}` 行
- **AND** 其余用例 MUST 继续执行
- **AND** 进程 exit code MUST 为 0（单 case 失败不是基础设施失败）

#### Scenario: 基础设施失败以非零退出

- **WHEN** 参数错误、jobs 文件不存在或 base contract 文件无法解析
- **THEN** CLI MUST 以非零 exit code 退出
- **AND** MUST 在 stderr 输出可定位原因的错误信息

### Requirement: CLI SHALL 显式注入外部 base contract

CLI SHALL 要求 `--base-contract` 参数指向一个 base contract JSON 文件，并以其构建 `GenerationSdk`（经 `GenerationSdk.builder().baseContract(...)`）。CLI MUST NOT 回退到 SDK 内置打包的 `base-contract.json`。

#### Scenario: 使用传入契约拼装 prompt

- **WHEN** `--base-contract` 指向从当前 `dslLibrary` 导出的契约文件
- **THEN** 发送给模型的 system prompt MUST 由该契约拼装而来
- **AND** 组件库变更（增删组件）后重新导出契约并调用，prompt MUST 反映变更

#### Scenario: 缺少契约参数即失败

- **WHEN** 启动 `generate` 或 `--print-prompt` 时未提供 `--base-contract`
- **THEN** CLI MUST 以非零 exit code 退出并提示该参数必填

### Requirement: 生成 SHALL 走 GenUiGenerator 全路径

CLI 内部 MUST NOT 包含任何 prompt 拼接逻辑；prompt 拼装、chat completions 请求体构造、响应解析与 openui-lang 提取 MUST 全部委托 `GenUiGenerator`。LLM 传输 SHALL 由 CLI 自实现的 OpenAI 兼容 `LlmTransport` 承担：完整 URL 取 `LLM_BASE_URL` + `/chat/completions`，鉴权用 Bearer `LLM_API_KEY`，支持 `HTTPS_PROXY` 代理；模型名取 `LLM_MODEL`，temperature 固定为 0。

#### Scenario: 生产语义生效

- **WHEN** CLI 对任一用例发起生成
- **THEN** 发送的 user message 末尾 MUST 含 ` /no_think` 后缀
- **AND** 模型返回内容 MUST 经 `OpenuiCodeExtractor` 提取（剥除 markdown 围栏）后输出

#### Scenario: 连接配置取自环境变量

- **WHEN** Node 侧以环境变量 `LLM_API_KEY` / `LLM_BASE_URL` / `LLM_MODEL` 启动 CLI 子进程
- **THEN** CLI MUST 使用这些值构造请求，MUST NOT 使用 SDK 默认模型名与默认 BSP 网关 endpoint

### Requirement: CLI SHALL 支持有界并发

`generate` 命令 SHALL 接受 `--concurrency=<n>` 参数（缺省 6），以有界线程池并发执行用例。

#### Scenario: 并发上限生效

- **WHEN** 以 `--concurrency=3` 启动且清单含 10 个用例
- **THEN** 同一时刻处于进行中的 LLM 请求数 MUST 不超过 3

### Requirement: CLI SHALL 提供规范 prompt 输出命令

CLI SHALL 提供 `--print-prompt` 子命令：以占位 dataModel（与 eval 现有 `__EVAL_DATA_MODEL_PLACEHOLDER__` 一致）调用 SDK prompt 拼装，将完整 system prompt 输出到 stdout，不发起任何 LLM 请求。

#### Scenario: 输出拼装结果且不联网

- **WHEN** 执行 `--print-prompt --base-contract=<path>`
- **THEN** stdout MUST 输出完整拼装的 system prompt
- **AND** MUST NOT 发起任何 HTTP 请求，无需 `LLM_API_KEY`

### Requirement: Node 侧 SHALL 在 jar 缺失时自动构建

Node 侧调用方 SHALL 约定 jar 路径为 `packages/genui-eval-cli/target/genui-eval-cli.jar`：jar 存在时直接使用；缺失时 MUST 自动执行 `mvn -q -pl packages/genui-eval-cli -am package -DskipTests` 构建。MUST NOT 做基于文件时间戳的新旧检测；显式重建经 `pnpm eval build-cli`。

#### Scenario: 首次运行自动构建

- **WHEN** jar 不存在且本机有 JDK 与 Maven
- **THEN** 调用方 MUST 先构建再继续，构建成功后本次调用正常执行

#### Scenario: 缺少 JDK/Maven 给出指引

- **WHEN** jar 不存在且检测不到可用的 `mvn` 或 `java`
- **THEN** MUST 报错并给出安装 JDK/Maven 的指引信息，MUST NOT 静默跳过生成

### Requirement: vitest 快照缺失兜底 SHALL 经由 CLI

vitest 运行中快照缺失且设置了 `LLM_API_KEY` 时，`loadOrGenerate` SHALL 以子进程调用 Eval Generation CLI（单用例清单）补生成快照；未设置 `LLM_API_KEY` 时 MUST 报错并指引运行 `pnpm eval regen`。测试进程内 MUST NOT 存在任何直连 LLM 的 TS 代码路径。

#### Scenario: 有 key 时经 CLI 补生成

- **WHEN** vitest 用例的 `.dsl` 快照缺失且环境有 `LLM_API_KEY`
- **THEN** MUST 经 CLI 子进程生成并写入快照后继续测试
- **AND** 生成使用的 base contract MUST 为进程内从当前 `dslLibrary` 导出的最新契约

#### Scenario: 无 key 时报错指引

- **WHEN** 快照缺失且未设置 `LLM_API_KEY`
- **THEN** MUST 抛出错误，信息指向 `pnpm eval regen`
