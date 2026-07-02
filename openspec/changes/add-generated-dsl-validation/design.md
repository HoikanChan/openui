## Context

`genui-java-sdk` 当前已经承担 Java Generation SDK 的核心后端职责：加载 DSLEngine base **Component Contract**、注册 **Generation Extension**、组装 prompt、调用 LLM、解析 SSE delta，并从模型响应中提取 `openui-lang`。缺口在生成结果可信化：SDK 目前把提取后的 DSL 直接返回或流式转发，无法在服务端确认它是否语法可解析、是否满足 `propsSchema`、是否有 root、最终引用是否完整，也无法给模型提供结构化错误来做 reflection repair。

从系统视角看，相关模块和边界如下：

```text
Host service / SmartCanvasService
  -> GenUiGenerator
       -> GenerationSdk
            -> GenerationContract + GenUIExtension + propsSchema
            -> PromptAssembler + Characterization
       -> LlmTransport
       -> SseDeltaParser
       -> OpenuiCodeExtractor
       -> Generated DSL Validation      (new)
       -> Repair Coordinator            (new, optional)
  -> SSE RenderStreamEnvelope
  -> DSLEngine / Renderer
       -> TypeScript parser/runtime      (rendering authority)
```

新模块的定位是：**在 Java SDK 内建立生成结果进入前端、缓存和调用方业务逻辑前的确定性校验边界**。它不取代 DSLEngine 的渲染 parser，也不判断 UI 视觉质量；它负责把 AI 生成的 `openui-lang` 判定为 `VALID`、`PARTIAL` 或 `INVALID`，并产出足够结构化的 diagnostics，供调用方、流式 gate 和 reflection repair 使用。

生产运行时保持 Java-only：不依赖 Node、Graal、ANTLR、tree-sitter 或远程 parser sidecar。TypeScript `packages/lang-core` parser 作为测试 oracle 使用，帮助 Java validator 与前端语义保持对齐。

## Goals / Non-Goals

**Goals:**

- 在 SDK 内提供 Java-native `openui-lang` parser/validator，覆盖语法解析、组件契约校验、root 存在性、最终引用完整性和结构化 validation issues。
- 为同步生成提供 final validation 与可配置 full repair：只有通过验证的 DSL 才作为成功结果返回或缓存。
- 为流式生成提供 statement-level gate：按完整 statement 放行 SDK accepted DSL，流式中容忍 temporary unresolved refs，拦截 definitively invalid statement。
- 使用 Fail-Fast Reask 处理流式坏语句：取消当前 LLM stream，用 accepted prefix、invalid statement、validation issues 发起新的 repair-and-continue 请求，避免两个模型流并行拼接。
- 保持公开 `RenderStreamEnvelope` 简单：前端只接收 `dataModel`、SDK 已接受的 `dsl`、`error` 和 `done`；validation、repair、最终成功/失败语义留在 SDK / 服务端内部。
- 让 validation 与 repair 松耦合：validator 必须确定性、无 LLM、可单测；repair coordinator 只消费结构化结果并通过现有 `LlmTransport` 发起受控 reask。
- 建立 Java validator 与 TypeScript parser 的 parity fixtures/golden，覆盖常见语法、流式边界、组件契约错误和修复提示。

**Non-Goals:**

- 不实现完整 DSLEngine runtime，不执行 Query/Mutation，不渲染 React，不判断视觉布局质量或业务表达是否“好看/准确”。
- 不让 Java SDK 生产运行时调用 Node/TS parser。
- 不在第一版追求 100% AST parity；第一版聚焦生成结果验证所需的语法和 contract semantics。
- 不做两个 LLM stream 并行协作和拼接。
- 不缓存 invalid 或 partial DSL；缓存策略仍由服务层负责，但 SDK 提供可信状态。

## Decisions

### 1. 模块边界：validator 是确定性核心，repair 是可选编排层

新增包建议：

```text
com.huawei.cloudsop.genui.core.validation
  OpenuiLangValidator
  ValidationRequest
  ValidationResult
  ValidationIssue
  ValidationStatus
  ValidationMode
  ValidationSeverity
  GenUiValidationConfig

com.huawei.cloudsop.genui.core.validation.parser
  OpenuiLexer
  StatementSplitter
  ExpressionParser
  AstNode records
  Program
  Statement

com.huawei.cloudsop.genui.core.validation.semantic
  ContractCatalog
  ProgramAnalyzer
  ReferenceResolver
  ComponentContractValidator

com.huawei.cloudsop.genui.core.validation.stream
  StatementBoundaryScanner
  StreamingValidationSession
  StreamingGateDecision

com.huawei.cloudsop.genui.core.validation.repair
  RepairCoordinator
  ReaskPromptBuilder
  RepairPolicy
```

`validation` 不依赖 `llm.transport`；`repair` 可以依赖 `LlmTransport`。这样同步调用方也能独立调用 validator，而生成链路可以按配置接入 repair。

备选方案是把 parser/repair 直接写进 `GenUiGenerator`。该方案初期代码少，但会把 transport、LLM prompt、语法解析、contract validation 和 stream state 混在一个类里，难以测试和替换，因此拒绝。

### 2. Java parser 复刻 TypeScript parser 的分层，而不是引入 parser generator

Java validator 采用与 `packages/lang-core/src/parser` 对齐的四段式：

```text
raw model output
  -> OpenuiCodeExtractor / Preprocessor
  -> Lexer
  -> StatementSplitter
  -> Pratt ExpressionParser
  -> ProgramAnalyzer + Contract validation
```

第一版语法覆盖生成场景常用子集：

- `statementId = expression`
- component call：`Table([col], data.rows)`
- builtin call：`@Render("v", TextContent(v))`
- string/number/boolean/null literals
- array/object literals
- refs、`data.rows`、member/index access
- `+ - * / % == != > < >= <= && || ??`、unary、ternary
- `$state` reference/assignment 的解析与基本保留

表达式 parser 使用 Pratt parser。它比 ANTLR/tree-sitter 更轻，符合当前 DSL 规模，也与 TypeScript parser 的实现方式一致。parser generator 会引入构建和运行复杂度，并不能直接解决流式容错、hoisting、propsSchema 映射和 diagnostics，因此不作为第一版方案。

### 3. 语法错误要结构化，不只抛异常

`ValidationResult` 是所有入口的统一输出：

```java
public record ValidationResult(
    ValidationStatus status,
    String normalizedDsl,
    List<ValidationIssue> issues,
    ValidationMetadata metadata
) {}
```

`ValidationIssue` 至少包含：

```java
code, severity, source, message, statementId,
component, path, line, column, hint, retryable
```

状态语义：

- `VALID`: final 或 stream checkpoint 没有 blocking issue。
- `PARTIAL`: 仅在 `STREAMING` mode 出现，表示 pending statement、temporary unresolved refs 或可自动闭合的输入。
- `INVALID`: 有 syntax/contract/root/final unresolved 等 blocking issue。

这样 repair prompt 可以直接使用机器可读错误，而不是解析异常文本。

### 4. Contract validation 以 `GenerationContract.propsSchema` 为权威

SDK 已经把 DSLEngine base contract 和 selected **Generation Extension** 合并到 prompt assembly 路径中。validator 应使用同一份 contract catalog：

- component 名称必须在 merged contract 中存在，或为已知 builtin/reserved call。
- positional args 按 `propsSchema.properties` 顺序映射到 prop 名称。
- `required` prop 缺失或为 null 时产出 `MISSING_REQUIRED_PROP` / `NULL_REQUIRED_PROP`。
- 多余 positional args 产出 `EXCESS_ARGS`，默认视为 repairable error。
- nested object 中 `additionalProperties=false` 且出现未知 key 时产出 `INVALID_PROP`，例如 `Col.options.format`。
- final mode 中 `root` 必须存在并能解析为组件节点。
- final mode 中 unresolved refs 必须是 error；streaming mode 中 unresolved refs 是 temporary signal，除非引用来自已拒绝的 statement 或 repair 已失败。

备选方案是只做 syntax check。该方案成本低，但无法捕获 AI 最常见且最需要修复的问题，如组件名错误、必填参数缺失、移除 prop 使用，因此拒绝。

### 5. 流式 gate 以完整 statement 为最小放行单位

裸 delta 不再直接表示安全 DSL。流式链路改为：

```text
LLM delta
  -> accumulated raw buffer
  -> StatementBoundaryScanner
  -> completed statement candidate
  -> streaming validator
      -> render-safe accepted statement: emit dsl statement
      -> temporary-unresolved but not render-safe: buffer inside SDK
      -> definitively invalid: withhold + fail-fast reask
      -> final validation: emit remaining accepted DSL or error
```

`StatementBoundaryScanner` 维护：

- `depthParen`
- `depthBracket`
- `depthBrace`
- `inString`
- `escapeNext`
- `insideFence`
- `ternaryDepth`
- `lastAcceptedOffset`
- `pendingStartOffset`

只有在括号深度为 0、字符串外、非三元续行的换行处，才认为 statement 完成。半截 statement 不触发 repair。

`PARTIAL` 和 temporary unresolved 是 SDK 内部状态，不等于必须立刻流出前端。第一版建议偏保守：如果一个完整 statement 语法和 contract 都通过，但依赖未来 statement 才能 render-safe，可以先进入 accepted buffer；等依赖补齐或 final validation 通过后再 flush。这样保留流式体验，同时减少“前端已经渲染了最终会失败的 DSL”的概率。

备选方案是继续裸 delta 流式并只在最终验证。该方案简单，但会把明显坏语句发给前端，后续只能要求前端 replace 或 rollback，用户体验和缓存安全都更差。另一个备选是全量 buffer 到最终再返回，该方案最安全但失去流式体验。statement gate 是两者之间的折中。

### 6. 流式 repair 使用 Fail-Fast Reask，不做并行模型协作

当完整 statement 出现 definitively invalid issue：

1. SDK 不向前端发送该 statement。
2. SDK 记录结构化 validation issue，并可通过日志、metrics 或内部 listener 暴露修复状态。
3. SDK 取消当前 LLM stream。
4. SDK 构造 repair-and-continue 请求，包含 accepted prefix、invalid statement、structured issues、selected component signatures 和用户原始意图摘要。
5. 新请求必须从修复后的 statement 开始继续输出剩余 DSL。
6. 新流继续经过同一 statement gate；只有修复后且被接受的 DSL 才以普通 `dsl` envelope 发给前端。
7. 如果 repair 关闭、超时或再次验证失败，SDK 发出 `error` envelope 后结束本次 stream；服务端不得缓存该结果。

并行修复原流的备选方案被拒绝：两个模型请求会产生上下文分叉，后续语句可能依赖坏语句旧形态，拼接策略复杂且不稳定。Fail-Fast Reask 牺牲一次额外请求的延迟，换取清晰的单一生成轨道。

### 7. 公开流式协议保持简单：SDK 内部验证/修复，前端只消费 accepted DSL

流式验证不是把控制权交给前端，也不要求前端理解 validation、repair、replace 或 commit。**SDK / SmartCanvasService 是生成会话状态机的权威方**，负责决定哪些 DSL 可以流出、哪些需要内部修复、哪些结果可以进入服务端缓存。

公开 `RenderStreamEnvelope` 保持当前简单形态：

```text
dataModel     seq=0, content=Render Data Model
dsl           content=SDK-accepted openui-lang chunk or statement batch
error         content={code, message, retryable}
done          content=null
```

语义规则：

- `dsl` 不再是 raw LLM delta，而是 SDK gate 之后允许前端渲染的 DSL 片段。第一版推荐以完整 statement 或 statement batch 为粒度。
- validation、repairing、replace、commit、discard 都不是公开 envelope type；它们是 SDK 内部状态、日志事件或服务端结果 metadata。
- 坏 statement 在 SDK 内部 withheld，不发送给前端；repair 成功后，修复后的内容以普通 `dsl` 发出，前端不需要知道它曾经被修过。
- `error` 表示本次生成已经无法产出可信最终 DSL。前端只需要按现有错误流处理，不需要理解语法错误细节；服务端不得缓存该次结果。
- `done` 只表示 SSE 传输结束。业务上的最终成功由 SDK completion result / `GenUiGenerationResult.validationStatus` 表达，供服务端日志和缓存策略使用。

SDK 内部状态机：

```text
RAW_STREAMING
  -> STATEMENT_GATING
  -> ACCEPTED_BUFFER
  -> INTERNAL_REPAIRING
  -> FINAL_VALIDATING
  -> INTERNAL_COMMITTED | FAILED
```

协同场景：

```text
场景 A：流式 gate 发现坏语句
  SDK withheld bad statement
  SDK 取消原始 LLM stream -> Fail-Fast Reask
  Reask 成功 -> repaired statement 通过 gate -> dsl -> done
  Reask 失败 -> error -> done

场景 B：temporary unresolved
  SDK 接受但暂存 statement
  后续 statement 补齐依赖 -> flush accepted dsl
  final validation 通过 -> done + SDK result valid

场景 C：最终校验发现 late error
  若 repair 只影响未发出的 tail -> repair tail -> dsl -> done
  若 repair 需要改写已发出的前缀 -> error -> done，服务端不缓存

场景 D：传输失败
  首帧前失败 -> HTTP 502，不进入 SSE 协议
  已写出首帧后失败 -> error(code=LLM_STREAM_FAILED) -> done
```

这里有一个硬约束：公开协议不提供 `replace` 时，SDK 不能在流式中静默改写已经发给前端的 DSL。要么在发出前 gate/repair，要么在无法保证一致性时失败本次 stream。这个约束换来的是前端协议简单，复杂度集中在 SDK 的 streaming gate、accepted buffer 和 repair coordinator。

### 8. 同步生成默认 final validation，可选 bounded full repair

同步链路：

```text
assemble prompt -> call LLM -> extract DSL -> validate final
  -> VALID: return result
  -> INVALID + repair enabled: repair full DSL -> validate again -> return or throw
  -> INVALID + repair disabled: throw GenerationSdkException with ValidationResult
```

repair 次数默认最多 1 次。repair 输出必须是纯 `openui-lang`，并再次通过 validator。同步路径不需要 candidate 状态，但返回结果可以携带 validation metadata，方便服务层日志和缓存策略。

### 9. 配置暴露产品级策略，而不是实现级开关

`GenUiValidationConfig` 不暴露一组彼此容易冲突的 boolean 开关。调用方只需要选择两个策略：

```java
validationMode = FINAL_ONLY | STREAMING_GATE | DISABLED
repairPolicy = NONE | FINAL_REPAIR | FAIL_FAST_REASK
```

推荐 public API：

```java
GenUiValidationConfig.finalOnly()
GenUiValidationConfig.streamingGate()
GenUiValidationConfig.streamingGateWithReask()
GenUiValidationConfig.disabled()
```

默认值为 `FINAL_ONLY + NONE`：同步和流式最终结果都会被校验，失败显式暴露；流式仍可保持原有较低延迟。需要更强流式安全时切到 `STREAMING_GATE`；需要自动修复时显式选择 `FINAL_REPAIR` 或 `FAIL_FAST_REASK`。

`maxRepairAttempts`、repair timeout、statement repair timeout 等属于 `RepairPolicy` 的高级参数或内部默认值，不放在顶层配置里。这样调用方不会面对多个组合爆炸的开关，SDK 内部仍保留受控 retry/timeout 能力。

### 10. 测试策略以 cross-language parity 和风险分层为核心

- Java parser unit tests 覆盖 lexer、statement splitting、Pratt precedence、autoclose、fence/comment stripping。
- Contract validator tests 覆盖 unknown component、missing/null required、excess args、invalid nested prop、root missing、final unresolved。
- Streaming gate tests 覆盖括号/字符串/对象/数组/三元跨行、fence、pending buffer、seq 顺序。
- Repair coordinator tests 使用 fake `LlmTransport`，覆盖 fail-fast cancel、repair prompt 内容、repair 成功继续、repair 失败 fallback。
- Cross-language parity tests 从 TypeScript parser 生成 fixture JSON，Java 测试断言 status、issue code、statementId、component、path 与 oracle 对齐。
- `GenUiGenerator` integration tests 覆盖 sync validation、streaming `dataModel/dsl/error/done` 顺序、bad statement withheld、repair success 以普通 `dsl` 输出、缓存前状态标记。

## Risks / Trade-offs

- [Risk] Java parser 与 TypeScript parser drift。-> Mitigation: 使用 TS parser 作为测试 oracle，新增 parity fixtures；只承诺第一版验证子集，不承诺完整 AST parity。
- [Risk] statement gate 增加首个可见 DSL chunk 延迟。-> Mitigation: 以完整 statement 为最小单位，通常仍能快速输出 `root` shell；保留配置允许关闭 gate。
- [Risk] Fail-Fast Reask 增加一次模型请求延迟和成本。-> Mitigation: 只对 definitively invalid completed statement 触发，默认 repair 关闭，开启后最多 1 次并有超时。
- [Risk] 不提供公开 `replace` 后，SDK 无法在流式中静默改写已发出的 DSL。-> Mitigation: 坏 statement 发出前 withheld，temporary unresolved 默认进入 accepted buffer，final repair 只允许修改未发出的 tail；需要改写已发出前缀时失败本次 stream 且不缓存。
- [Risk] validator 拦截过严导致可渲染 DSL 被拒。-> Mitigation: 流式中 unresolved 仅作 temporary signal；第一版错误码分类明确区分 blocking 与 warning。

## Migration Plan

1. 新增 validator 数据模型、parser、semantic analyzer 和独立 API，不接入生成链路。
2. 增加 TS oracle fixtures 与 Java parity tests，先锁定语法和 contract error 子集。
3. 将 final validation 接入 `GenUiGenerator.generate(...)` 和 `generateStream(...)` 的流尾，默认不自动 repair。
4. 保持 `RenderStreamEnvelope` 公开类型为 `dataModel`、`dsl`、`error`、`done`，但把 `dsl` 语义改为 SDK accepted DSL，而不是 raw LLM delta。
5. 接入 statement boundary scanner 和 streaming gate，先只 gate 明确 invalid statement。
6. 接入可选 full repair，再接入可选 Fail-Fast Reask statement repair。
7. 服务层缓存策略切换为只缓存 SDK completion result 最终状态为 `VALID` 的 DSL；repair 成功通过 `VALID` 加 metadata 表达。
8. 回滚时可切到 `GenUiValidationConfig.disabled()`，或从 `STREAMING_GATE` 降级到 `FINAL_ONLY`，保留原始 LLM delta 流式路径。

## Open Questions

- 第一版是否要求 `GenUiGenerationResult` 携带 `ValidationResult`，还是仅在失败时抛出包含 validation details 的异常？
- temporary unresolved 默认应该内部 buffer 到依赖补齐，还是允许特定 DSLEngine 消费方 opt-in 接收 unresolved preview？
- `Fail-Fast Reask` 是否应复用同一 `GenUiLlmConfig.defaultModel`，还是允许配置更便宜/更稳定的 repair model？
- 是否需要给 `ValidationIssue` 增加 `sourceText` / `span` 字段，方便前端或日志高亮错误 statement？
