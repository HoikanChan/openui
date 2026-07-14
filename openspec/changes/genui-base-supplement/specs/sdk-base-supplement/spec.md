# sdk-base-supplement 规格

## ADDED Requirements

### Requirement: 增补包 JSON 加载与严格顶层键校验
`GenUIBaseSupplementLoader` SHALL 从 JSON 解析出 `GenUIBaseSupplement`，允许的顶层键仅为 `supplementVersion`、`components`、`componentGroups`、`examples`、`additionalRules`（全部选填）；出现任何其他顶层键时 MUST 抛出 `GenerationSdkException`，且异常消息列出全部非法键。组件条目 SHALL 支持与 base-contract.json 相同的两种形态（`description`+`propsSchema` 与 `signature`+`description`）。

#### Scenario: 合法增补包解析成功
- **WHEN** 以仅含白名单顶层键、组件条目为任一合法形态的 JSON 调用 `fromJson`
- **THEN** 返回 `GenUIBaseSupplement`，各字段与 JSON 内容一致，缺省 section 解析为空集合

#### Scenario: 非法顶层键被拒绝
- **WHEN** JSON 顶层含 `tools`（或 `root`、`builtins`、`contractVersion`、拼错的键名等任何白名单以外的键）
- **THEN** `fromJson` 抛出 `GenerationSdkException`，消息中包含该非法键名

#### Scenario: classpath 便捷加载
- **WHEN** 以存在的 classpath 路径调用 `fromResource`
- **THEN** 行为等价于读取该资源文本后调用 `fromJson`；资源不存在时抛出 `GenerationSdkException`

### Requirement: 构建期合并产出 Effective Base Contract
`GenerationSdk.Builder` SHALL 提供 `baseSupplement(GenUIBaseSupplement)` 入口（最多一份）；构建时 SHALL 将增补包合并进 base contract：`components` 同名整体替换（保持 base 原有顺序位置）、新组件按增补包顺序追加到末尾；同名 `componentGroups` 做 components 保序去重并集（base 成员在前）且 notes 追加，新 group 追加到末尾；`examples` 与 `additionalRules` 追加到 base 之后；`contractVersion`、`root`、`tools`、`builtins` MUST 保持 base 原值。合并结果即后续 prompt 组装与 extension 校验所见的唯一 base。

#### Scenario: 新组件全局生效
- **WHEN** 增补包含 base 中不存在的组件 `TopoChart` 并构建 SDK 后，以不带 extensionId 的请求调用 `assemblePrompt`
- **THEN** 生成的 prompt 的组件文档中包含 `TopoChart` 的 spec

#### Scenario: 同名组件整体替换
- **WHEN** 增补包对 base 已有组件（如 `Tag`）提供新 spec 并构建 SDK
- **THEN** prompt 中该组件的 description 与 propsSchema 完全来自增补包，base 原 spec 不残留，且该组件在组件文档中的顺序位置与替换前一致

#### Scenario: 同名 group 并集合并
- **WHEN** 增补包声明 `{"name": "Charts", "components": ["TopoChart"]}` 且 base 已有 Charts 组
- **THEN** Effective Base Contract 中 Charts 组的成员为 base 原成员在前、`TopoChart` 追加在后的去重并集，base 的 notes 保留且增补包 notes 追加

#### Scenario: 不可变 section 保持原值
- **WHEN** 任意合法增补包参与构建
- **THEN** Effective Base Contract 的 `contractVersion`、`root`、`tools`、`builtins` 与 jar 内 base contract 相同

### Requirement: 构建期校验与错误归因
增补包自身组件 SHALL 在合并前以 scope「base supplement」通过 propsSchema 校验，错误消息 MUST 指明来源为增补包；合并后的 Effective Base Contract SHALL 通过既有的组件与 group 引用校验（group 引用了不存在的组件 MUST 在构建期抛出 `GenerationSdkException`）；`register(GenUIExtension)` 的同名碰撞校验 SHALL 针对 Effective Base Contract 执行。

#### Scenario: 增补包组件 propsSchema 非法
- **WHEN** 增补包中某组件的 propsSchema 不合法并构建 SDK
- **THEN** 构建抛出 `GenerationSdkException`，消息包含「base supplement」及该组件名

#### Scenario: 增补包 group 引用不存在的组件
- **WHEN** 增补包 group 引用了既不在 base 也不在增补包中的组件名
- **THEN** `GenerationSdk` 构建抛出 `GenerationSdkException`，消息包含缺失的组件名

#### Scenario: extension 与增补包组件同名被拒绝
- **WHEN** SDK 含追加了 `TopoChart` 的增补包，随后 `register` 一个也声明 `TopoChart` 的 `GenUIExtension`
- **THEN** `register` 抛出组件名碰撞的 `GenerationSdkException`

### Requirement: 无增补包时行为不变
未调用 `baseSupplement` 时，`GenerationSdk` 的构建与 prompt 输出 MUST 与引入本能力前字节级一致。

#### Scenario: golden prompt 保持字节级一致
- **WHEN** 不带增补包构建 SDK 并对既有 golden 输入执行 `assemblePrompt`
- **THEN** 输出与 `src/test/resources/prompt-golden/*.txt` 逐字节一致（`PromptGoldenTest` 通过）
