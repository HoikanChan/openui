# Tasks — lang-core Java 21 Rewrite

## Overview

Each task touches 1-3 files, has a single testable outcome, and references the requirement(s) it satisfies. Tasks are ordered so that each builds on the previous group.

---

## Group 1 — Project Scaffold

- [x] **Task 1.1** — Create Maven project skeleton
  - Files: `lang-core-java/pom.xml`
  - Create `pom.xml` with `<release>21</release>`, zero runtime deps, JUnit Jupiter 5.10.2 test-scope only, standard source layout `src/main/java` / `src/test/java`
  - _Ref: Design §Technology Choices_

- [x] **Task 1.2** — Create root package and `LangCore` façade stub
  - Files: `src/main/java/dev/openui/langcore/LangCore.java`
  - Empty public final class with TODO stubs for `createParser`, `createStreamingParser`, `parse`, `mergeStatements`, `createLibrary`
  - _Ref: Design §20_

---

## Group 2 — Lexer

- [x] **Task 2.1** — Implement `TokenType` enum and `Token` record
  - Files: `lexer/TokenType.java`, `lexer/Token.java`
  - 35 variants as listed in design; `Token(TokenType, Object)` with `stringValue()` and `numValue()` accessors
  - _Ref: Req 1, Design §1_

- [x] **Task 2.2** — Implement `JsonStringUtil` (double-quote string unescaping)
  - Files: `util/JsonStringUtil.java`
  - State-machine unescape of `"..."` strings; handles `\"`, `\\`, `\n`, `\t`, `\r`, `\uXXXX`; on malformed input strips quotes and returns raw text
  - _Ref: Req 1 AC9_

- [x] **Task 2.3** — Implement `Lexer` — whitespace, punctuation, operators, keywords
  - Files: `lexer/Lexer.java`
  - `tokenize()` method; handles AC1-8, AC13-18 (whitespace skip, newline, punctuation, `=`/`==`, `!`/`!=`, `>=/<=`, `&&`/`||`, single-char ops, `$stateVar`, `@BuiltinCall`, PascalCase/lowercase ident, keywords, EOF)
  - _Ref: Req 1 AC1-8, AC13-18_

- [x] **Task 2.4** — Implement `Lexer` — string and number literals
  - Files: `lexer/Lexer.java` (extend Task 2.3)
  - Double-quoted strings via `JsonStringUtil`; single-quoted strings with manual unescape; numeric literals (int, decimal, scientific notation, negative-number vs minus disambiguation)
  - _Ref: Req 1 AC9-12_

- [x] **Task 2.5** — Unit tests for `Lexer`
  - Files: `test/.../lexer/LexerTest.java`
  - One test per AC (AC1-18); include edge cases: unterminated string, negative number vs binary minus, emoji skipped, `&&` vs `&`
  - _Ref: Req 1.*_

---

## Group 3 — Pre-Processor

- [x] **Task 3.1** — Implement `PreProcessor.stripComments`
  - Files: `parser/PreProcessor.java`
  - Remove `//…` and `#…` to end-of-line when outside a double-quoted string
  - _Ref: Req 7 AC5-6_

- [x] **Task 3.2** — Implement `PreProcessor.stripFences`
  - Files: `parser/PreProcessor.java` (extend)
  - Extract content from `` ``` ``-fenced blocks; concatenate multiple blocks with `\n`; pass through if no fences; do not treat `` ``` `` inside strings as fence boundary; handle missing closing fence (streaming)
  - _Ref: Req 7 AC1-4, AC7_

- [x] **Task 3.3** — Implement `PreProcessor.autoClose` and `AutoCloseResult`
  - Files: `parser/PreProcessor.java` (extend), `parser/AutoCloseResult.java`
  - Bracket stack + string state machine; append closers in reverse order; handle trailing `\` in strings; `wasIncomplete` flag
  - _Ref: Req 8 AC1-4_

- [x] **Task 3.4** — Unit tests for `PreProcessor`
  - Files: `test/.../parser/PreProcessorTest.java`
  - One test per AC for stripComments, stripFences, autoClose; include nested fences, missing closing fence, trailing escape
  - _Ref: Req 7.*, 8.*_

---

## Group 4 — AST

- [x] **Task 4.1** — Implement AST node sealed interface and all record subtypes
  - Files: `parser/ast/Node.java`, `parser/ast/LiteralNode.java`, `parser/ast/RefNode.java`, `parser/ast/StateRefNode.java`, `parser/ast/RuntimeRefNode.java`, `parser/ast/AssignNode.java`, `parser/ast/BinaryNode.java`, `parser/ast/UnaryNode.java`, `parser/ast/MemberNode.java`, `parser/ast/TernaryNode.java`, `parser/ast/CallNode.java`, `parser/ast/BuiltinCallNode.java`, `parser/ast/ElementNode.java`, `parser/ast/ArrayNode.java`, `parser/ast/ObjectNode.java`
  - All as `record implements Node`; `ElementNode` includes `hasDynamicProps` and nullable `statementId`
  - _Ref: Design §3_

---

## Group 5 — Expression Parser

- [x] **Task 5.1** — Implement Pratt expression parser
  - Files: `parser/ExpressionParser.java`
  - `parseExpression(int minBp)` with binding power table matching design §4; handles all operators, unary prefix, member access `.` and `[…]`, function calls `Name(…)`, builtin calls `@Name(…)`, ternary `?:`
  - _Ref: Req 3_

- [x] **Task 5.2** — Unit tests for `ExpressionParser`
  - Files: `test/.../parser/ExpressionParserTest.java`
  - Precedence ordering tests (all 9 levels); member access on arrays (pluck vs length); ternary spanning lines; `$var = expr` inside expression → `AssignNode`
  - _Ref: Req 3.*_

---

## Group 6 — Statement Parser

- [x] **Task 6.1** — Implement `Statement` sealed interface subtypes
  - Files: `parser/Statement.java` (sealed), `parser/ValueStatement.java`, `parser/StateStatement.java`, `parser/QueryStatement.java`, `parser/MutationStatement.java`, `parser/NullStatement.java`
  - _Ref: Req 2, Design §5_

- [x] **Task 6.2** — Implement `StatementParser`
  - Files: `parser/StatementParser.java`
  - Top-level loop: scan for `<lhs> = <expr>` patterns; track `bracketDepth` and `ternaryDepth`; emit statement on newline at depth 0 when next token is not `?`/`:`; reclassify `Query`/`Mutation` calls; last-write-wins deduplication; silently skip non-matching lines
  - _Ref: Req 2 AC1-10_

- [x] **Task 6.3** — Unit tests for `StatementParser`
  - Files: `test/.../parser/StatementParserTest.java`
  - State/value/query/mutation classification; multiline ternary continuations; duplicate IDs; bracketed newline ignore; silent skip of malformed lines
  - _Ref: Req 2.*_

---

## Group 7 — Schema Registry

- [x] **Task 7.1** — Implement `SchemaRegistry` with hand-written JSON Schema parser
  - Files: `parser/SchemaRegistry.java`, `parser/PropDef.java` (parser package version), `parser/ComponentSchema.java`
  - Parse `$defs[Name].properties` key order, `required` array, and `default` values from JSON Schema string using `JsonStringUtil`; no external JSON library
  - _Ref: Design §7_

- [x] **Task 7.2** — Unit tests for `SchemaRegistry`
  - Files: `test/.../parser/SchemaRegistryTest.java`
  - Round-trip: schema with required/optional props, default values, nested types; unknown component lookup returns empty
  - _Ref: Req 4 AC2-4_

---

## Group 8 — Materializer

- [x] **Task 8.1** — Implement `ValidationError`, `OpenUIError`, `ParseMeta`, `ParseResult` records
  - Files: `parser/ValidationError.java`, `parser/OpenUIError.java`, `parser/ParseMeta.java`, `parser/ParseResult.java`
  - Exact field shapes from Design §6 and Req 5
  - _Ref: Req 5_

- [x] **Task 8.2** — Implement `Materializer` — prop mapping and validation
  - Files: `parser/Materializer.java`
  - Positional arg → named prop via `SchemaRegistry`; generate excess-args, missing-required, null-required, unknown-component, inline-reserved errors; `hasDynamicProps` detection; mark `Query`/`Mutation` inline usage
  - _Ref: Req 4 AC1-8_

- [x] **Task 8.3** — Implement `Materializer` — root selection, unresolved, orphaned, state/query/mutation extraction
  - Files: `parser/Materializer.java` (extend Task 8.2)
  - Root selection priority (Req 6 AC1-6); BFS to find unresolved refs and orphaned statements; build `stateDeclarations`, `queryStatements`, `mutationStatements`; set `meta.incomplete` from `wasIncomplete`
  - _Ref: Req 5, Req 6_

- [ ] **Task 8.4** — Unit tests for `Materializer`
  - Files: `test/.../parser/MaterializerTest.java`
  - All four ValidationError codes; `hasDynamicProps` true/false; root selection all 5 priority cases; orphaned GC; `stateDeclarations` implicit null entry
  - _Ref: Req 4.*, 5.*, 6.*_

---

## Group 9 — One-Shot and Streaming Parsers

- [x] **Task 9.1** — Implement `OneShot`
  - Files: `parser/OneShot.java`
  - Wire PreProcessor → Lexer → StatementParser → Materializer; update `LangCore.createParser` and `LangCore.parse`
  - _Ref: Design §8_

- [x] **Task 9.2** — Implement `StreamParser`
  - Files: `parser/StreamParser.java`
  - Buffer management (`push`, `set`, `getResult`); `completedEnd` pointer; `putIfAbsent` for committed statements; delta-append vs reset logic; `synchronized` on all public methods; update `LangCore.createStreamingParser`
  - _Ref: Req 9 AC1-9_

- [x] **Task 9.3** — Unit tests for `StreamParser`
  - Files: `test/.../parser/StreamParserTest.java`
  - Incremental chunk delivery matches one-shot result on same complete input; committed statements not overwritten; `set()` delta vs reset; `meta.incomplete` correctly set; `getResult()` idempotent
  - _Ref: Req 9.*_

---

## Group 10 — Merger

- [x] **Task 10.1** — Implement `Merger`
  - Files: `merge/Merger.java`
  - `mergeStatements(existing, patchText, schema)`: strip fences, parse patch, copy existing, overwrite/delete, BFS GC keeping `$state` IDs; update `LangCore.mergeStatements`
  - _Ref: Req 12 AC1-8_

- [x] **Task 10.2** — Unit tests for `Merger`
  - Files: `test/.../merge/MergerTest.java`
  - Override, delete (`NullStatement`), append new ID, GC unreachable, preserve `$state`, fence stripping, empty existing, empty patch
  - _Ref: Req 12.*_

---

## Group 11 — Reactive Marker

- [ ] **Task 11.1** — Implement `ReactiveSchemas`
  - Files: `reactive/ReactiveSchemas.java`
  - `markReactive(Object schema)` and `isReactiveSchema(Object schema)` using `Collections.synchronizedMap(new WeakHashMap<>())`
  - _Ref: Design §12_

---

## Group 12 — Evaluator

- [ ] **Task 12.1** — Implement `EvaluationContext` interface and `ReactiveAssign` record
  - Files: `runtime/EvaluationContext.java`, `runtime/ReactiveAssign.java`
  - `EvaluationContext`: `getState(name)`, `resolveRef(name)`, `extraScope()` map, `getPropSchema(name)`, `withExtraScope(Map)`; `ReactiveAssign(String target, Node expr)`
  - _Ref: Req 11 AC2-4_

- [ ] **Task 12.2** — Implement `Evaluator` — literals, state, refs, binary ops, unary ops
  - Files: `runtime/Evaluator.java`
  - Pattern-matching switch; `toNumber` helper; `+` string concat; `/` `%` zero-guard; loose `==`/`!=`; comparison coercion; short-circuit `&&`/`||`
  - _Ref: Req 11 AC1-2, Req 3 AC3-7_

- [ ] **Task 12.3** — Implement `Evaluator` — member access, ternary, calls, assign, element, array, object
  - Files: `runtime/Evaluator.java` (extend)
  - `.field` on array → pluck / `.length`; `[idx]` coercion; ternary; `CallNode` dispatch (Action); `BuiltinCallNode` dispatch; `AssignNode` → `ReactiveAssign`; `ElementNode` static skip; `ArrayNode`/`ObjectNode` recursive eval
  - _Ref: Req 3 AC9-10, Req 10 AC13-18, Req 11 AC6-7_

- [ ] **Task 12.4** — Implement `Evaluator` — reactive prop dispatch
  - Files: `runtime/Evaluator.java` (extend)
  - `resolveState` checks `ReactiveSchemas.isReactiveSchema(ctx.getPropSchema(name))` → emit `ReactiveAssign`; non-reactive prop stripping of stray `ReactiveAssign`
  - _Ref: Req 11 AC8-9_

- [ ] **Task 12.5** — Implement `PropEvaluator` (`evaluateElementProps`)
  - Files: `runtime/PropEvaluator.java`
  - Recursively evaluate all props in `ElementNode` tree; skip `hasDynamicProps == false` nodes (Req 11 AC7)
  - _Ref: Req 11 AC6-7_

- [ ] **Task 12.6** — Unit tests for `Evaluator` and `PropEvaluator`
  - Files: `test/.../runtime/EvaluatorTest.java`
  - All Req 11 ACs; plus arithmetic edge cases (div-by-zero, loose equality, string concat with null), member access pluck, reactive prop round-trip
  - _Ref: Req 11.*_

---

## Group 13 — Built-in Functions

- [ ] **Task 13.1** — Implement eager builtins (`@Count` through `@Ceil`)
  - Files: `runtime/Builtins.java`
  - All 11 eager builtins as static methods; `@Sort` dot-path resolver; `@Filter` 6 operators including `"contains"`; `@Round` decimal places
  - _Ref: Req 10 AC1-11_

- [ ] **Task 13.2** — Implement lazy builtin `@Each` in `Evaluator`
  - Files: `runtime/Evaluator.java` (extend), `runtime/Builtins.java`
  - Walk template AST substituting `RefNode(varName)` with `LiteralNode(element)` before evaluating; result is `List<Object>`
  - _Ref: Req 10 AC12, Req 11 AC10_

- [ ] **Task 13.3** — Implement Action builtins and `ActionStep` hierarchy
  - Files: `runtime/ActionStep.java` (sealed), `runtime/RunStep.java`, `runtime/SetStep.java`, `runtime/ResetStep.java`, `runtime/ToAssistantStep.java`, `runtime/OpenUrlStep.java`, `runtime/ActionPlan.java`, `runtime/Builtins.java` (extend)
  - `Action([…])` filters nulls, collects steps; `@Run`, `@Set`, `@Reset`, `@ToAssistant`, `@OpenUrl` produce typed records
  - _Ref: Req 10 AC13-18_

- [ ] **Task 13.4** — Unit tests for builtins
  - Files: `test/.../runtime/BuiltinsTest.java`
  - All Req 10 ACs; `@Sort` ascending/descending/dot-path/numeric-string ordering; `@Filter` all 6 operators; `@Each` loop var substitution; `@Set` with non-StateRef → null; `Action` null filtering
  - _Ref: Req 10.*_

---

## Group 14 — Store

- [ ] **Task 14.1** — Implement `Store` interface and `DefaultStore`
  - Files: `store/Store.java`, `store/DefaultStore.java`
  - `get`, `set` (shallow equality check), `subscribe` (returns unsubscribe `Runnable`), `getSnapshot`, `initialize` (persisted first, defaults for new keys only), `dispose`; `CopyOnWriteArraySet` for listeners
  - _Ref: Design §14_

- [ ] **Task 14.2** — Unit tests for `Store`
  - Files: `test/.../store/StoreTest.java`
  - Set triggers listener; identity-equal value skips notify; shallow-equal object skips notify; initialize persisted overrides defaults; dispose clears; unsubscribe works
  - _Ref: Design §14_

---

## Group 15 — StateField

- [ ] **Task 15.1** — Implement `StateField` record and `resolveStateField`
  - Files: `runtime/StateField.java`
  - `StateField<T>(name, value, setValue Consumer<T>, isReactive boolean)`; `resolveStateField` — if `bindingValue instanceof ReactiveAssign` and store+ctx present → reactive branch with `$value` scope injection; else static branch
  - _Ref: Design §17 (StateField section)_

---

## Group 16 — Prompt Generation

- [ ] **Task 16.1** — Implement `PromptSpec`, `ComponentPromptSpec`, `ToolSpec`, `ComponentGroup` records
  - Files: `prompt/PromptSpec.java`, `prompt/ComponentPromptSpec.java`, `prompt/ToolSpec.java`, `prompt/ComponentGroup.java`
  - Exact field shapes from Design §11
  - _Ref: Design §11_

- [ ] **Task 16.2** — Implement `PromptGenerator.generatePrompt`
  - Files: `prompt/PromptGenerator.java`
  - Build system-prompt string from `PromptSpec`; grammar overview section; component signatures section; tool descriptors section; examples and toolExamples sections; additionalRules; preamble prepend; `jsonSchemaTypeStr` helper for tool schema types
  - _Ref: Design §11_

- [ ] **Task 16.3** — Unit tests for `PromptGenerator`
  - Files: `test/.../prompt/PromptGeneratorTest.java`
  - Output contains component signatures; tool section present when tools given; examples/toolExamples appear; preamble prepended; editMode/inlineMode flags alter output
  - _Ref: Design §11_

---

## Group 17 — Library API

- [ ] **Task 17.1** — Implement `PropDef`, `ComponentDef`, `ComponentGroup`, `LibraryDefinition` records
  - Files: `library/PropDef.java`, `library/ComponentDef.java`, `library/ComponentGroup.java`, `library/LibraryDefinition.java`
  - `PropDef(name, required, defaultValue, typeAnnotation, isArray, isReactive)`; `ComponentDef(name, List<PropDef>, description, component: Object)`
  - _Ref: Design §13_

- [ ] **Task 17.2** — Implement `ComponentDefBuilder` (fluent builder)
  - Files: `library/ComponentDefBuilder.java`, `library/PropDefBuilder.java`
  - `Libraries.defineComponent(name)` → `ComponentDefBuilder`; `.description(…)`, `.prop(name)` → `PropDefBuilder`; `.type(…)`, `.required()`, `.optional()`, `.defaultValue(…)`, `.reactive()`, `.add()` → back to component builder; `.component(Object)`, `.build()` → `ComponentDef`
  - _Ref: Design §13_

- [ ] **Task 17.3** — Implement `Library` interface and `DefaultLibrary`
  - Files: `library/Library.java`, `library/DefaultLibrary.java`, `library/Libraries.java`
  - `createLibrary(LibraryDefinition)` validates root exists; `components()`, `componentGroups()`, `root()`; `prompt(PromptOptions)` → builds `PromptSpec` from `PropDef` lists and delegates to `PromptGenerator`; `toJSONSchema()` builds JSON Schema `Map` from `PropDef` lists; update `LangCore.createLibrary`
  - _Ref: Design §13_

- [ ] **Task 17.4** — Implement `SchemaRegistry.fromLibrary` integration
  - Files: `parser/SchemaRegistry.java` (extend)
  - `SchemaRegistry.fromLibrary(Library)` — build registry directly from `Library.components()` without JSON round-trip; used when caller has a `Library` object rather than a raw JSON Schema string
  - _Ref: Design §7_

- [ ] **Task 17.5** — Unit tests for Library API
  - Files: `test/.../library/LibraryTest.java`
  - `defineComponent` builder round-trip; `toJSONSchema` produces correct `$defs`; `prompt()` includes component signatures; unknown root throws; `SchemaRegistry.fromLibrary` lookup
  - _Ref: Design §13_

---

## Group 18 — Tool Provider and MCP

- [ ] **Task 18.1** — Implement `ToolProvider`, `ToolNotFoundError`
  - Files: `query/ToolProvider.java`, `query/ToolNotFoundError.java`
  - `ToolProvider` functional interface: `CompletableFuture<Object> callTool(String, Map<String,Object>)`; `ToolNotFoundError(toolName, availableTools)` extends `RuntimeException`
  - _Ref: Design §15_

- [ ] **Task 18.2** — Implement `McpClientLike`, `McpToolError`, `McpAdapter`
  - Files: `mcp/McpClientLike.java`, `mcp/McpToolError.java`, `mcp/McpAdapter.java`
  - `McpClientLike` interface with nested `McpCallParams`, `McpContentItem`, `McpResult` records; `McpToolError(toolErrorText)`; `McpAdapter implements ToolProvider` wrapping `McpClientLike`; `extractToolResult` logic (isError → throw; structuredContent → return; text → parse JSON or return string)
  - _Ref: Design §16_

- [ ] **Task 18.3** — Unit tests for MCP adapter
  - Files: `test/.../mcp/McpAdapterTest.java`
  - `extractToolResult` with `structuredContent`; with text JSON; with text plain string; `isError: true` → throws `McpToolError`; `callTool` delegates and unwraps
  - _Ref: Design §16_

---

## Group 19 — Query Manager

- [ ] **Task 19.1** — Implement `QueryNode`, `MutationNode`, `MutationResult`, `QuerySnapshot` records
  - Files: `query/QueryNode.java`, `query/MutationNode.java`, `query/MutationResult.java`, `query/QuerySnapshot.java`
  - _Ref: Design §15_

- [ ] **Task 19.2** — Implement `StableJson` — stable JSON serializer for cache keys
  - Files: `util/StableJson.java`
  - `stringify(Object v)` → sorted-key JSON string; `undefined`→`"__undefined__"`, `NaN`→`"__NaN__"`, `Infinity`→`"__Inf__"`, `-Infinity`→`"__-Inf__"`; hand-written — no external deps
  - _Ref: Design §15 (cache key generation)_

- [ ] **Task 19.3** — Implement `QueryManager` interface
  - Files: `query/QueryManager.java`
  - All 11 methods from Design §15: `evaluateQueries`, `getResult`, `isLoading`, `isAnyLoading`, `invalidate`, `registerMutations`, `fireMutation`, `getMutationResult`, `subscribe`, `getSnapshot`, `activate`, `dispose`
  - _Ref: Design §15_

- [ ] **Task 19.4** — Implement `DefaultQueryManager` — fetch, cache, snapshot
  - Files: `query/DefaultQueryManager.java`
  - `ConcurrentHashMap` for queries and cache; `CompletableFuture` async fetch; stale-fetch guard via `AtomicLong generation`; snapshot rebuild + string-equality change detection; `CopyOnWriteArraySet` listeners; `evaluateQueries` cleanup of removed IDs
  - _Ref: Design §15_

- [ ] **Task 19.5** — Implement `DefaultQueryManager` — timers, mutations, invalidate, lifecycle
  - Files: `query/DefaultQueryManager.java` (extend)
  - `ScheduledExecutorService` for auto-refresh timers; `registerMutations` / `fireMutation` (concurrent loading guard, generation check, refresh after success); `invalidate` (needsRefetch if in-flight); `activate` / `dispose` (clear timers, clear mutations, preserve cache)
  - _Ref: Design §15_

- [ ] **Task 19.6** — Unit tests for `QueryManager`
  - Files: `test/.../query/QueryManagerTest.java`
  - Fetch fires on first `evaluateQueries`; cache hit skips second fetch; `invalidate` re-fetches; concurrent mutation loading guard; `fireMutation` refresh; `dispose` / `activate` lifecycle; `getSnapshot` reflects loading state; stale fetch discarded after `dispose`
  - _Ref: Design §15_

---

## Group 20 — Validation Utilities

- [ ] **Task 20.1** — Implement `Validation` utility class
  - Files: `util/Validation.java`, `util/ParsedRule.java`
  - `builtInValidators()` map (min, max, minLength, maxLength, pattern, required, …); `parseRules(Object)` handles String and List inputs; `validate(value, rules, validators)`
  - _Ref: Design §19_

- [ ] **Task 20.2** — Unit tests for `Validation`
  - Files: `test/.../util/ValidationTest.java`
  - All built-in validators pass/fail; `parseRules` from string, list, map; `validate` short-circuits on first failure
  - _Ref: Design §19_

---

## Group 21 — Integration and Wire-up

- [ ] **Task 21.1** — Wire all components into `LangCore` façade
  - Files: `LangCore.java`
  - Fill in all stubs: `createParser(jsonSchema)`, `createStreamingParser(jsonSchema)`, `parse(input, jsonSchema)`, `mergeStatements(existing, patch, jsonSchema)`, `createLibrary(def)`, `createQueryManager(toolProvider)`
  - _Ref: Design §20_

- [ ] **Task 21.2** — Integration test: one-shot parse ↔ streaming parse parity
  - Files: `test/.../IntegrationTest.java`
  - For a set of complete inputs: one-shot result and streaming (single-chunk) result produce identical `root`, `stateDeclarations`, `queryStatements`, `mutationStatements`; `meta.statementCount` may differ per Req 5 AC6
  - _Ref: Req 5 AC6, Design §Compatibility_

- [ ] **Task 21.3** — Integration test: full parse → evaluate cycle
  - Files: `test/.../IntegrationTest.java` (extend)
  - Parse a UI expression with `$state` vars and component calls; evaluate props with a mock `EvaluationContext`; verify concrete values; verify `ReactiveAssign` emitted for reactive props
  - _Ref: Req 11_
