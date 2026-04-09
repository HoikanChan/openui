# `requirements.md` 审计结果

审计对象：
- 需求文档：`.claude/specs/lang-core-spec/requirements.md`
- 实际代码：`src/`

审计方法：
- 逐条对照 Requirement 1-12 和 Non-Functional Requirements。
- 对每条 requirement 标注 `Implemented` / `Partial` / `Missing`。
- `Partial` 表示“主体实现存在，但与 requirements 文案不完全一致”或“requirements 漏写了关键实现细节”。

## 总览

| Requirement | 结论 | 主要实现位置 | 备注 |
| --- | --- | --- | --- |
| 1. Lexer: Token Rules | Implemented | `src/parser/lexer.ts:13` | 规则基本完整落地 |
| 2. Statement Grammar | Partial | `src/parser/statements.ts:72`, `src/parser/parser.ts:56` | `Query/Mutation` 会优先覆盖 `$lhs` 的 state 分类，spec 未写 |
| 3. Expression Grammar / Precedence | Partial | `src/parser/expressions.ts:24`, `src/runtime/evaluator.ts:42` | `@Each` 真实语法需带 `@`，spec 文案不够准确 |
| 4. Component Call / Positional Args | Partial | `src/parser/parser.ts:598`, `src/parser/materialize.ts:201` | `required + null + default` 的行为与 spec 不一致 |
| 5. ParseResult Structure | Partial | `src/parser/types.ts:227`, `src/parser/parser.ts:182` | streaming 路径的 `statementCount` 不是严格按去重后计算 |
| 6. Root Statement Selection | Implemented | `src/parser/parser.ts:164` | 与代码一致 |
| 7. Fence Stripping / Comment Removal | Implemented | `src/parser/parser.ts:251` | 另有一些 spec 未写细节 |
| 8. Auto-close for Partial Input | Implemented | `src/parser/statements.ts:18` | 与代码一致 |
| 9. Streaming Parser Contract | Implemented | `src/parser/parser.ts:412` | 主体契约成立 |
| 10. Built-in Functions | Partial | `src/parser/builtins.ts:41`, `src/runtime/evaluator.ts:306` | 除 `Action` 外，其余 action/builtin 实际都要求 `@` 前缀 |
| 11. Runtime Evaluator | Partial | `src/runtime/evaluator.ts:42`, `src/runtime/evaluate-tree.ts:34`, `src/reactive.ts:6` | reactive 标记实现方式与 spec 不一致 |
| 12. Merge / Patch Model | Partial | `src/parser/merge.ts:18` | GC 行为比 spec 写得更宽 |
| Non-Functional Requirements | Partial | `src/parser/*`, `src/runtime/*`, `package.json:43-48` | 大多数满足；兼容性/“never throw” 仍有边界说明 |

## 逐条对照

### Requirement 1 — Lexer: Token Rules

结论：`Implemented`

对应实现：
- `src/parser/lexer.ts:13` `tokenize()`
- 关键分支：
  - newline：`src/parser/lexer.ts:23`
  - punctuation：`src/parser/lexer.ts:31`
  - `=` / `==`：`src/parser/lexer.ts:68`
  - `!` / `!=`：`src/parser/lexer.ts:81`
  - `>` / `>=` / `<` / `<=`：`src/parser/lexer.ts:94`, `src/parser/lexer.ts:107`
  - `&` / `&&` / `|` / `||`：`src/parser/lexer.ts:120`, `src/parser/lexer.ts:133`
  - `. ? + * / %`：`src/parser/lexer.ts:146-186`
  - 双引号字符串：`src/parser/lexer.ts:188`
  - 单引号字符串：`src/parser/lexer.ts:220`
  - `-` 作为负数或减号：`src/parser/lexer.ts:251`
  - 数字：`src/parser/lexer.ts:278`
  - `$state`：`src/parser/lexer.ts:300`
  - `Ident` / `Type` / `true` / `false` / `null`：`src/parser/lexer.ts:322`
  - `@builtin`：`src/parser/lexer.ts:353`
  - 其他字符跳过：`src/parser/lexer.ts:375`
  - EOF：`src/parser/lexer.ts:378`

补充细节（requirements 未写，但代码里有）：
- `BuiltinCall` token 的 `v` 不包含 `@`，只保存名字本身。
- `StateVar` token 的 `v` 保留 `$` 前缀。
- 双引号字符串 fallback 时只去掉首尾引号，不做额外修复。

### Requirement 2 — Statement Grammar

结论：`Partial`

对应实现：
- 语句切分：`src/parser/statements.ts:72` `split()`
- 语句分类：`src/parser/parser.ts:56` `classifyStatement()`
- 顶层 parse：`src/parser/parser.ts:380` `parse()`

已实现内容：
- `identifier = expression` 语句模型：`src/parser/statements.ts:78-97`
- `StateVar` / `Ident` / `Type` 作为合法 lhs：`src/parser/statements.ts:78`
- depth 0 newline 才分句：`src/parser/statements.ts:104-129`
- 括号内 newline 忽略：`src/parser/statements.ts:121`
- 跨行 ternary 不切句：`src/parser/statements.ts:104-129`
- 非法行静默跳过：`src/parser/statements.ts:70`, `src/parser/statements.ts:81-95`
- duplicate id 后写覆盖前写：`src/parser/parser.ts:392-397`

与 requirements 不完全一致的地方：
- spec AC4/AC5 说“value declaration 的 RHS 是 `Query(...)` / `Mutation(...)` 时再重分类”；代码实际是先看表达式形状，再看 lhs token 类型，所以：
  - `$foo = Query(...)` 会被分类为 `query`
  - `$foo = Mutation(...)` 会被分类为 `mutation`
  - 证据：`src/parser/parser.ts:56-79`

建议补到 spec 的细节：
- `Query/Mutation` 的重分类优先级高于 `$state` 声明分类。

### Requirement 3 — Expression Grammar and Operator Precedence

结论：`Partial`

对应实现：
- Pratt parser：`src/parser/expressions.ts:24` `parseExpression()`
- precedence 常量：`src/parser/expressions.ts:10-18`
- 运行时求值：`src/runtime/evaluator.ts:42` `evaluate()`
- `@Each` 惰性求值：`src/runtime/evaluator.ts:447`

已实现内容：
- Pratt 解析：`src/parser/expressions.ts:24-66`
- 优先级 1-9：`src/parser/expressions.ts:10-18`, `src/parser/expressions.ts:34-63`
- `$var = expr` 生成 `Assign`：`src/parser/expressions.ts:116-126`
- `.` / `[]` 访问：`src/parser/expressions.ts:255-275`
- `+` 的字符串拼接与 `null/undefined -> ""`：`src/runtime/evaluator.ts:151-156`
- `/`、`%` 除零返回 `0`：`src/runtime/evaluator.ts:161-164`
- `==` / `!=` 宽松比较：`src/runtime/evaluator.ts:165-170`
- 比较运算统一 `toNumber`：`src/runtime/evaluator.ts:171-178`
- `&&` / `||` 短路：`src/runtime/evaluator.ts:143-149`
- 数组 `.field` pluck，`.length` 特判：`src/runtime/evaluator.ts:208-216`
- 数组索引转 number，对象索引转 string：`src/runtime/evaluator.ts:220-228`
- `@Each` 惰性模板求值：`src/runtime/evaluator.ts:447-479`

与 requirements 不完全一致的地方：
- AC11 写的是 `Each(array, varName, template)`，但真实语法要求 `@Each(...)`。
  - 证据：普通 `Type(...)` builtin 会被当成 `Ref`，只有 `@builtin` 才会走 builtin call；`Action` 是唯一例外。
  - 证据：`src/parser/expressions.ts:129-143`

建议补到 spec 的细节：
- 除 `Action(...)` 外，builtin 调用都要求 `@` 前缀。

### Requirement 4 — Component Call Syntax and Positional Argument Mapping

结论：`Partial`

对应实现：
- parser factory / schema 编译：`src/parser/parser.ts:598-640`
- component materialization：`src/parser/materialize.ts:201-323`
- builtin 与 component 入口区分：`src/parser/expressions.ts:129-143`

已实现内容：
- PascalCase + `(` 进入组件调用：`src/parser/expressions.ts:129-137`
- `$defs[Component].properties` 顺序映射 positional args：`src/parser/parser.ts:602-610`, `src/parser/materialize.ts:261-264`
- excess args 丢弃并记 `"excess-args"`：`src/parser/materialize.ts:266-281`
- missing required 时尝试 schema default，否则 `"missing-required"` 并丢弃组件：`src/parser/materialize.ts:286-307`
- required 显式为 `null` 时在无 default 的情况下报 `"null-required"` 并丢弃：`src/parser/materialize.ts:295-307`
- unknown component -> `"unknown-component"` + `null`：`src/parser/materialize.ts:310-319`
- inline `Query/Mutation` -> `"inline-reserved"` + `null`：`src/parser/materialize.ts:250-258`
- 静态 props 节点 `hasDynamicProps=false`：`src/parser/materialize.ts:322-323`

requirements 写了但代码里找不到“完全一致实现”的地方：
- AC5 说“required prop 显式为 `null` 时，应直接报 `null-required` 并 drop”；代码实际会先尝试 default，如果 schema 有 default，就不会报错，也不会 drop。
  - 证据：`src/parser/materialize.ts:286-307`

建议补到 audit / spec 的细节：
- 代码把“缺失 required”和“required = null”统一放进 `missingRequired` 流程，再由 `p.name in props` 决定最终 error code。

### Requirement 5 — ParseResult Structure

结论：`Partial`

对应实现：
- 类型定义：`src/parser/types.ts:227-250`
- result 组装：`src/parser/parser.ts:182-240`
- statement 提取：`src/parser/parser.ts:89-147`

已实现内容：
- `root` 为 `ElementNode | null`：`src/parser/types.ts:227`, `src/parser/parser.ts:218-239`
- `meta.incomplete`：`src/parser/parser.ts:229`
- `meta.unresolved`：`src/parser/parser.ts:198-239`
- `meta.orphaned`：`src/parser/parser.ts:200-239`
- `meta.errors`：`src/parser/parser.ts:199`, `src/parser/parser.ts:232-234`
- `stateDeclarations`：`src/parser/parser.ts:98-107`, `src/parser/parser.ts:131-147`
- 自动补 `referenced but undeclared` 的 `$var = null`：`src/parser/parser.ts:131-147`
- `queryStatements` / `mutationStatements`：`src/parser/parser.ts:107-126`

requirements 写了但代码里找不到“完全一致实现”的地方：
- AC6 说 `statementCount` 是“去重后的 unique statement IDs 数量”；one-shot 路径是这样做的，但 streaming 路径不是。
  - one-shot：`src/parser/parser.ts:399` 传入 `stmtMap.size`
  - streaming：`src/parser/parser.ts:551-557` 传入 `completedCount + stmts.length`
  - `completedCount` 在 commit 时每次都递增，不按最终 map 去重：`src/parser/parser.ts:422-428`

建议补到 spec 的细节：
- `ElementNode` 还有一个 requirements 未写到的可选字段 `statementId`：`src/parser/types.ts:36-39`
- `meta.unresolved` 当前实现不是去重集合：
  - 同一 unresolved ref 可能重复出现
  - cycle 也会被塞进 `unresolved`
  - 证据：`src/parser/materialize.ts:42-74`, `src/parser/parser.ts:198`
- 空输入会走 `emptyResult()`，默认 `meta.incomplete = true`；requirements 没定义这一点。

### Requirement 6 — Root Statement Selection

结论：`Implemented`

对应实现：
- `src/parser/parser.ts:164-180` `pickEntryId()`
- `src/parser/parser.ts:182-192` `buildResult()`

已实现内容：
- `root` 优先：`src/parser/parser.ts:170`
- 其次 `rootName` 命中 statement id：`src/parser/parser.ts:171`
- 再其次按 `typeName === rootName` 找首个 component statement：`src/parser/parser.ts:173-176`
- 再其次首个 component statement：`src/parser/parser.ts:178-179`
- 否则首条 statement：`src/parser/parser.ts:179`
- entry statement 不存在则 `root = null`：`src/parser/parser.ts:191-192`

补充细节（requirements 未写，但代码里有）：
- 即使 entry statement 存在，如果 materialize 后不是 `ElementNode`，最终 `ParseResult.root` 仍然是 `null`：`src/parser/parser.ts:218-221`

### Requirement 7 — Pre-processing: Fence Stripping and Comment Removal

结论：`Implemented`

对应实现：
- `src/parser/parser.ts:251-332` `stripFences()`
- `src/parser/parser.ts:334-366` `stripComments()`
- `src/parser/parser.ts:369-370` `preprocess()`

已实现内容：
- fenced code 提取：`src/parser/parser.ts:255-315`
- 双引号字符串内的 ``` 不作为 fence：`src/parser/parser.ts:271-303`
- 缺 closing fence 时取 opening 之后全部内容：`src/parser/parser.ts:263-267`, `src/parser/parser.ts:308-311`
- `//` / `#` 行注释移除：`src/parser/parser.ts:354-360`
- 无 fences 时 comment stripping 后原样继续：`src/parser/parser.ts:331`, `src/parser/parser.ts:369-370`

补充细节（requirements 未写，但代码里有）：
- 如果输入里有多个 fenced block，代码会把多个 block 用 `\n` 拼接起来：`src/parser/parser.ts:252`, `src/parser/parser.ts:315`
- `stripComments()` 同时支持单引号和双引号字符串上下文：`src/parser/parser.ts:339-353`
- `preprocess()` 会在 strip 前后各做一次 `.trim()`：`src/parser/parser.ts:369-370`

### Requirement 8 — Auto-close for Partial / Streaming Input

结论：`Implemented`

对应实现：
- `src/parser/statements.ts:18-50` `autoClose()`
- one-shot 接入：`src/parser/parser.ts:384-399`
- streaming pending 接入：`src/parser/parser.ts:520-558`

已实现内容：
- 未闭合字符串 / 括号最小闭合：`src/parser/statements.ts:18-50`
- reverse stack 顺序补 bracket closers：`src/parser/statements.ts:47-48`
- `wasIncomplete` 透传到 `ParseResult.meta.incomplete`：`src/parser/parser.ts:385`, `src/parser/parser.ts:555`
- 无需补全时保持原样：`src/parser/statements.ts:42-43`

补充细节（requirements 未写，但代码里有）：
- 如果字符串以反斜杠结尾，`autoClose()` 会先补一个 `\`，再补 closing quote：`src/parser/statements.ts:44-46`

### Requirement 9 — Streaming Parser Behavior Contract

结论：`Implemented`

对应实现：
- `src/parser/parser.ts:412-583` `createStreamParser()`
- 对外 schema 版工厂：`src/parser/parser.ts:639-640` `createStreamingParser()`

已实现内容：
- `push(chunk)`：`src/parser/parser.ts:571-574`
- `set(fullText)`：`src/parser/parser.ts:575-581`
- `getResult()`：`src/parser/parser.ts:583`
- depth 0 / ternary-safe commit：`src/parser/parser.ts:433-483`
- completed cache 不被 pending 覆盖：`src/parser/parser.ts:536-545`
- pending statement auto-close 后只补充新 id：`src/parser/parser.ts:520-558`
- 无 pending 时 `meta.incomplete = false`：`src/parser/parser.ts:493-500`
- reset 清空内部状态：`src/parser/parser.ts:562-568`

补充细节（requirements 未写，但代码里有）：
- 内部还有一个未在 requirements 中出现的低层工厂 `createStreamParser(cat, rootName?)`；对外公开的是 `createStreamingParser(schema, rootName?)`：`src/parser/parser.ts:412`, `src/parser/parser.ts:639`

### Requirement 10 — Built-in Functions

结论：`Partial`

对应实现：
- builtin registry：`src/parser/builtins.ts:41-228`
- action / lazy builtin 求值：`src/runtime/evaluator.ts:306-479`
- query/mutation statement 提取：`src/parser/parser.ts:56-76`, `src/parser/parser.ts:98-126`

已实现内容：
- eager builtins `Count / First / Last / Sum / Avg / Min / Max / Sort / Filter / Round / Abs / Floor / Ceil`：`src/parser/builtins.ts:41-183`
- lazy builtin `Each`：`src/parser/builtins.ts:185-193`, `src/runtime/evaluator.ts:447-479`
- `Action` / `Run` / `ToAssistant` / `OpenUrl` / `Set` / `Reset`：`src/runtime/evaluator.ts:306-360`
- top-level `Query` / `Mutation` 提取：`src/parser/parser.ts:56-76`, `src/parser/parser.ts:98-126`
- inline reserved call -> error：`src/parser/materialize.ts:250-258`

requirements 写了但代码里找不到“完全一致实现”的地方：
- “Action expressions usable without `@` prefix” 这句按字面不成立：
  - 真实代码里只有 `Action(...)` 可以不用 `@`
  - `Run / ToAssistant / OpenUrl / Set / Reset / Each / Count / ...` 都要求 `@`
  - 证据：`src/parser/expressions.ts:129-143`, `src/runtime/evaluator.ts:306-357`
- AC12 如果按字面理解成 `Each(...)`，代码也不支持，必须是 `@Each(...)`

建议补到 spec 的细节：
- `@Each` 的第二个参数 `varName` 既可以是 `Ref`，也可以是字符串字面量：`src/parser/materialize.ts:85-88`, `src/runtime/evaluator.ts:457-460`
- `Action` step 里被过滤的条件是“非 null 且含 `type` 字段的对象”：`src/runtime/evaluator.ts:314-319`

### Requirement 11 — Runtime Evaluator

结论：`Partial`

对应实现：
- AST 求值：`src/runtime/evaluator.ts:42-235`
- prop tree 求值：`src/runtime/evaluate-tree.ts:34-75`
- shared prop eval core：`src/runtime/evaluate-prop.ts:30-98`
- reactive marker：`src/reactive.ts:6-16`

已实现内容：
- `evaluate(node, context)` 递归求值：`src/runtime/evaluator.ts:42-235`
- `StateRef` 用 `getState()`，`Ref/RuntimeRef` 用 `resolveRef()`：`src/runtime/evaluator.ts:61-66`
- `extraScope` 优先于 `getState()`：`src/runtime/evaluator.ts:62`
- `Assign` -> `ReactiveAssign`：`src/runtime/evaluator.ts:231-235`
- `isReactiveAssign()`：`src/runtime/evaluator.ts:35`
- `evaluateElementProps()` 递归求值：`src/runtime/evaluate-tree.ts:34-59`
- `hasDynamicProps === false` 直接返回：`src/runtime/evaluate-tree.ts:35`
- reactive prop 上 `StateRef` -> `ReactiveAssign`：`src/runtime/evaluator.ts:97-104`, `src/runtime/evaluate-prop.ts:43-51`
- `@Each` 先替换 loop ref，再处理 deferred expression：`src/runtime/evaluator.ts:371-479`

requirements 写了但代码里找不到“完全一致实现”的地方：
- AC8 里写 `markReactive(schema)` “在 Zod schema object 上设置 symbol marker”；实际实现不是 symbol，也不修改 schema object，而是把 schema 放进 `WeakSet`。
  - 证据：`src/reactive.ts:6-16`

建议补到 spec 的细节：
- 非 reactive prop 上如果求值得到 `ReactiveAssign`，shared prop evaluator 会把它剥成当前 state 值：`src/runtime/evaluate-prop.ts:64-66`
- `evaluatePropCore()` 会保留 `ActionPlan` / `ActionStep`，不在 prop eval 阶段展开：`src/runtime/evaluate-prop.ts:80-81`
- runtime prop 求值异常会被收集成 `OpenUIError { source: "runtime", code: "runtime-error" }`：`src/runtime/evaluate-tree.ts:46-56`

### Requirement 12 — Merge / Patch Model

结论：`Partial`

对应实现：
- `src/parser/merge.ts:18-182`

已实现内容：
- patch override existing：`src/parser/merge.ts:148-171`
- `name = null` 表示删除：`src/parser/merge.ts:162-169`
- 新 statement 按 patch 出现顺序 append：`src/parser/merge.ts:170-171`
- merge 完成后做 GC：`src/parser/merge.ts:92-139`
- `$state` 永远保留：`src/parser/merge.ts:122-123`
- patch 先 `stripFences()`：`src/parser/merge.ts:143`
- existing 为空时直接返回 patch：`src/parser/merge.ts:145-147`
- patch 为空时直接返回 existing：`src/parser/merge.ts:148`

与 requirements 不完全一致的地方：
- AC4 文案说 GC 删除的是“不可达的 value statements”；实际 merge 层并不区分 statement kind，而是删除所有不可达、且不是 `$state` 的 statement。
  - 这意味着不可达的 `Query` / `Mutation` 声明也会被 GC 掉。
  - 证据：`src/parser/merge.ts:92-139`

建议补到 spec 的细节：
- merge 层自己的 `splitStatementSource()` 只跟踪 bracket depth，不跟踪 ternary depth：`src/parser/merge.ts:18-55`

## requirements.md 写了，但代码里找不到完全对应实现的项

1. Requirement 4 AC5
   - spec：required prop 显式为 `null` 时，无条件报 `null-required` 并 drop
   - code：如果 schema 有 default，会直接用 default，不报错
   - 位置：`src/parser/materialize.ts:286-307`

2. Requirement 5 AC6
   - spec：`statementCount` 为“去重后的 unique statement IDs”
   - code：streaming 路径使用 `completedCount + stmts.length`
   - 位置：`src/parser/parser.ts:422-428`, `src/parser/parser.ts:551-557`

3. Requirement 10 中“Action expressions usable without `@` prefix`”的字面表述
   - code：只有 `Action(...)` 例外，其他 builtin/action step 都要求 `@`
   - 位置：`src/parser/expressions.ts:129-143`, `src/runtime/evaluator.ts:306-357`

4. Requirement 11 AC8 中“symbol marker”表述
   - code：真实实现是 `WeakSet`
   - 位置：`src/reactive.ts:6-16`

## 代码里有，但 requirements.md 没写到的内容

### 明确超出当前 spec scope 的模块

这些代码在 `src/` 中存在，但 introduction 明确说不在本 requirements scope 内：

- library definition API
  - `createLibrary` / `defineComponent`
  - 位置：`src/index.ts:2`, `src/library.ts`
- prompt generation
  - `generatePrompt`
  - 位置：`src/parser/index.ts:21`, `src/index.ts:32`, `src/parser/prompt.ts`
- reactive store
  - `createStore`
  - 位置：`src/index.ts:71`, `src/runtime/store.ts`
- query manager / mutation runtime / MCP integration
  - `createQueryManager`, `extractToolResult`, `McpToolError`, `ToolNotFoundError`
  - 位置：`src/index.ts:54-67`, `src/runtime/queryManager.ts`, `src/runtime/mcp.ts`, `src/runtime/toolProvider.ts`
- validation DSL
  - `builtInValidators`, `parseRules`, `parseStructuredRules`, `validate`
  - 位置：`src/index.ts:76`, `src/utils/validation.ts`

### 在当前 spec 范围内，但 spec 漏写的实现细节

1. `ElementNode.statementId`
   - root 和被引用元素节点会携带来源 statement id
   - 位置：`src/parser/types.ts:36-39`, `src/parser/materialize.ts:57-67`, `src/parser/parser.ts:218-221`

2. `meta.unresolved` 的真实语义更宽
   - 不仅包含 never-defined ref，也会包含循环引用
   - 可能重复，不是去重集合
   - 位置：`src/parser/materialize.ts:42-74`

3. 空输入返回的 `ParseResult.meta.incomplete`
   - `emptyResult()` 默认 `incomplete = true`
   - 位置：`src/parser/parser.ts:20-37`, `src/parser/parser.ts:381-382`

4. 多个 fenced code block 会被拼接
   - 位置：`src/parser/parser.ts:252-315`

5. `autoClose()` 会修补 dangling escape
   - 字符串末尾若只有反斜杠，会先补一个 `\` 再补 closing quote
   - 位置：`src/parser/statements.ts:44-46`

6. builtin 真实语法规则
   - `Action(...)` 可以裸写
   - 其他 builtin/action step 必须 `@Name(...)`
   - 位置：`src/parser/expressions.ts:129-143`

7. merge 的 GC 比 spec 更强
   - 不可达 `Query/Mutation` 也会被删
   - 位置：`src/parser/merge.ts:92-139`

8. reactive marker 真实实现
   - 不是 symbol marker，而是 `WeakSet`
   - 位置：`src/reactive.ts:6-16`

## Non-Functional Requirements 对照

### Performance

结论：`基本满足`

对应实现：
- one-shot parser 无回溯，多阶段均线性扫描：`src/parser/lexer.ts`, `src/parser/statements.ts`, `src/parser/expressions.ts`
- streaming 不重 parse committed statements：`src/parser/parser.ts:433-558`
- `hasDynamicProps === false` 优化：
  - parser 侧打标：`src/parser/materialize.ts:322-323`
  - runtime 侧跳过：`src/runtime/evaluate-tree.ts:35`

### Reliability

结论：`Partial`

已满足：
- 对不认识字符静默跳过：`src/parser/lexer.ts:375`
- 字符串 / bracket auto-close：`src/parser/statements.ts:18-50`
- 除零返回 `0`：`src/runtime/evaluator.ts:161-164`

需要说明的边界：
- 我没有在 parser 入口看到一个 catch-all 异常边界去“机械保证 never throw”；实现整体是防御式的，但 requirements 的“must never throw for any input”比代码里可直接证明的范围更强。

### Compatibility

结论：`Partial`

已满足：
- `@modelcontextprotocol/sdk` 是 optional peer dependency：`package.json:43-48`
- 代码没有对 sdk 做硬 import，只在注释和 structural typing 中引用：`src/runtime/mcp.ts:1-80`

不完全满足 / 需要说明：
- one-shot 与 streaming 在绝大多数完整输入上会生成同 shape 的 `ParseResult`，但若存在 duplicate statement IDs，streaming 的 `statementCount` 可能和 one-shot 不一致。
  - 位置：`src/parser/parser.ts:399`, `src/parser/parser.ts:422-428`, `src/parser/parser.ts:551-557`

### Usability

结论：`基本满足`

对应实现：
- `ValidationError` 定义含 `code`, `component`, `statementId?`：`src/parser/types.ts:82-92`
- `OpenUIError` 定义含 `code`, `component?`, `statementId?`：`src/parser/types.ts:126-141`
- parser error enrich：`src/parser/enrich-errors.ts:23-37`
- runtime query/mutation error 也会带 `component` 与 `statementId`
  - 位置：`src/runtime/queryManager.ts:259-285`, `src/runtime/queryManager.ts:502-527`

## 最终结论

1. `requirements.md` 对 parser / evaluator / merge 的主干行为覆盖度已经很高，大部分 requirement 在代码中都能找到明确落点。
2. 主要差异集中在“文案不够精确”而不是“完全没实现”：
   - builtin / action 的 `@` 语法
   - `required = null` 且 schema 有 default 时的处理
   - streaming `statementCount`
   - reactive marker 的实现方式
3. 如果要把 `requirements.md` 升级成真正的“authoritative implementation-level reference”，优先建议修正文中的这 4 处差异，并补上本 audit 里列出的“漏写细节”。
