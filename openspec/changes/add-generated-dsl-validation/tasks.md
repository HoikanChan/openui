## 1. Validation 数据模型与配置

- [x] 1.1 新增 `validation` 包及 `ValidationStatus`、`ValidationMode`、`ValidationSeverity`、`ValidationIssue`、`ValidationResult`、`ValidationMetadata` 数据模型
- [x] 1.2 新增 `ValidationRequest`，包含 DSL 文本、`GenerationContract`、rootName、externalRefs、mode 和 request context
- [x] 1.3 新增精简 `GenUiValidationConfig`，只暴露 `validationMode` 与 `repairPolicy` 两类策略，并提供 `finalOnly()`、`streamingGate()`、`streamingGateWithReask()`、`disabled()` 预设
- [x] 1.4 为 validation 数据模型补充不可变性、空值归一、序列化和 builder 单元测试

## 2. Java openui-lang Parser

- [x] 2.1 新增 `parser` 子包，定义 `TokenType`、`Token`、`AstNode` sealed records、`Statement` 和 `Program`
- [x] 2.2 实现 `OpenuiPreprocessor`，支持 fenced code 提取、注释清理、final/streaming 模式空白归一
- [x] 2.3 实现 `OpenuiLexer`，覆盖字符串、数字、boolean/null、identifier、component type、`@Builtin`、`$state`、标点和运算符 token
- [x] 2.4 实现 `StatementSplitter`，按 depth-aware newline 切分 `statementId = expression`，并保留 line/column/span
- [x] 2.5 实现 Pratt `ExpressionParser`，覆盖 component/builtin call、array/object、refs、member/index、unary/binary/null-coalescing/ternary 表达式
- [x] 2.6 为 lexer、statement splitter、Pratt precedence、fence/comment 和语法错误诊断补充单元测试

## 3. Contract Catalog 与语义校验

- [x] 3.1 实现 `ContractCatalog`，从 merged `GenerationContract` 提取 component 参数顺序、required props、nested schema 和 signature hint
- [x] 3.2 实现 `ProgramAnalyzer`，构建符号表、选择 root、解析 hoisted refs、收集 unresolved/orphaned 状态
- [x] 3.3 实现 `ComponentContractValidator`，校验 unknown component、missing/null required、excess args、invalid nested prop
- [x] 3.4 实现 final mode 规则：root 缺失、root 不可渲染、最终 unresolved refs 均为 blocking issue
- [x] 3.5 实现 streaming mode 规则：pending statement 和 temporary unresolved refs 返回 `PARTIAL` 或 non-blocking issue
- [x] 3.6 为 contract validation 覆盖 unknown component、required prop、`Col.options.format`、excess args、root missing、final unresolved 测试

## 4. Validator API 与 GenUiGenerator 同步接入

- [x] 4.1 实现 `OpenuiLangValidator` 接口和默认 Java-native 实现
- [x] 4.2 在 `GenerationSdk` 或 `GenUiGenerator` 内部提供获取当前 merged `GenerationContract` 的受控路径，供 validator 使用
- [x] 4.3 扩展 `GenUiGenerator` 构造入口以接收 `GenUiValidationConfig` 和可选 `OpenuiLangValidator`
- [x] 4.4 将 final validation 接入 `GenUiGenerator.generate(...)`，在返回前验证 extracted DSL
- [x] 4.5 定义 invalid sync 行为：repair 关闭时抛出包含 `ValidationResult` 的 `GenerationSdkException` 或专用异常
- [x] 4.6 为同步 valid、invalid、validation disabled 和自定义 validator 覆盖单元测试

## 5. 流式 Envelope 与 SDK Accepted DSL 语义

- [x] 5.1 保持 `RenderStreamEnvelope` 公开类型为 `dataModel`、`dsl`、`error`、`done`，并将 `dsl` 语义改为 SDK accepted DSL 而非 raw LLM delta
- [x] 5.2 定义 envelope factory 方法，约束 `dataModel`、`dsl`、`error`、`done` 的 content shape 和 seq 递增规则
- [x] 5.3 更新 `GenUiGenerationResult` 或相关结果模型以携带 final validation status/metadata，供服务端缓存和日志使用
- [x] 5.4 更新 SDK stream 测试，覆盖 `dataModel -> dsl -> done`、repair success 以普通 `dsl` 输出、unrecoverable `error -> done` 顺序

## 6. Streaming Statement Gate

- [x] 6.1 实现 `StatementBoundaryScanner`，维护括号深度、字符串状态、escape、fence、ternary depth、pending offsets
- [x] 6.2 实现 `StreamingValidationSession`，累积 raw buffer、accepted prefix、accepted-but-buffered statements、pending statement 和 latest validation result
- [x] 6.3 将 streaming gate 接入 `GenUiGenerator.generateStream(...)`，只向 sink 转发 accepted completed statements
- [x] 6.4 对 temporary unresolved refs 保持 SDK 内部可容忍；默认只 flush render-safe statements，对 definitively invalid completed statement 保持 withheld
- [x] 6.5 补充流式测试：多行 component、数组/对象、字符串换行、三元跨行、half statement、fence、invalid statement withheld、temporary unresolved buffered then flushed

## 7. Reflection Repair 与 Fail-Fast Reask

- [x] 7.1 实现 `RepairPolicy`，以 `NONE`、`FINAL_REPAIR`、`FAIL_FAST_REASK` 表达修复策略，并在高级参数中封装 attempt limit 与 timeout
- [x] 7.2 实现 `ReaskPromptBuilder`，用 accepted prefix、invalid statement、structured issues、component hints 和原始用户意图构造 repair prompt
- [x] 7.3 实现 `RepairCoordinator`，通过现有 `LlmTransport` 发起 full repair 和 repair-and-continue 请求
- [x] 7.4 将 full repair 接入同步 final invalid 路径，repair 输出必须再次经过 validator
- [x] 7.5 将 Fail-Fast Reask 接入流式 invalid statement 路径，取消原 stream 后用新 stream 接管，并确保坏 statement 不进入公开 `dsl`
- [x] 7.6 为 repair prompt、repair success as ordinary `dsl`、repair invalid、retry exhausted、timeout 和 stream cancellation 覆盖 fake transport 测试

## 8. TypeScript Oracle 与 Cross-Language Parity

- [x] 8.1 在 TS 侧新增或复用 fixture 生成脚本，输出 Java 测试可读的 parse/validation oracle JSON
- [x] 8.2 增加 Java parity fixtures，覆盖 supported syntax、unknown component、missing required、invalid nested prop、final unresolved、syntax failure
- [x] 8.3 将 parity 测试接入 Maven test，确保 Java validator 与 TypeScript parser 在支持子集上的 issue class 和 context 对齐
- [x] 8.4 在 README 或测试文档中说明如何重新生成 oracle fixtures

## 9. GenUI Service 与 REST 契约

- [x] 9.1 更新 GenUI Service `/v1/generate` 流式实现，将 SDK gated `dataModel`、`dsl`、`error`、`done` 序列化为 SSE JSON
- [x] 9.2 更新服务层缓存策略，只缓存 SDK completion result 最终状态为 `VALID` 的 normalized DSL；repair 成功通过 `VALID` 加 metadata 表达
- [x] 9.3 更新 Swagger 2.0 文档，描述 `dataModel`、SDK accepted `dsl`、`error`、`done` envelope schema
- [x] 9.4 确认前端/demo stream parser 不需要消费 validation/repair/replace/commit/discard，仅按现有 `dsl` 和 `error` 行为处理
- [x] 9.5 更新 GenUI Service 流式测试，覆盖 valid stream、invalid statement withheld、unrecoverable validation `error -> done`、pre-stream 502 和 mid-stream structured error

## 10. 文档、验证与回滚

- [x] 10.1 更新 `packages/genui-java-sdk/README.md`，说明 Generated DSL Validation、stream gate、repair 配置和调用示例
- [x] 10.2 更新迁移说明，指导服务调用方从裸 LLM delta 迁移到 SDK accepted DSL chunk，并说明 validation/repair 状态默认不进入公开 stream
- [x] 10.3 运行 `mvn test` 覆盖 Java SDK 新旧测试
- [x] 10.4 运行相关 TypeScript parser/oracle 测试，确认 fixture 生成和 parity 数据稳定
- [x] 10.5 记录回滚方式：切换到 `GenUiValidationConfig.disabled()`、`finalOnly()` 或恢复裸 delta streaming
