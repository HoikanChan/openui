# Requirements Document

## Introduction

This document specifies the complete behavioral contract of `@openuidev/lang-core` — the framework-agnostic parser, runtime, and prompt-generation core of the OpenUI Lang ecosystem. The goal is to serve as an authoritative, implementation-level reference that:

1. Defines the OpenUI Lang grammar and token rules
2. Documents the parser's output contract (ParseResult structure)
3. Documents streaming incremental parsing behavior
4. Documents the runtime evaluation model
5. Documents the merge/patch model

This is a descriptive spec of the existing implementation, not a feature request. Future contributors and framework adapter authors should be able to implement a compatible system from this document alone.

**Out of scope for this spec:** prompt generation (`generatePrompt` / `PromptSpec`), the library definition API (`defineComponent` / `createLibrary`), the reactive store (`createStore`), and the query manager (`createQueryManager`). These subsystems are intentionally excluded to keep this document focused on the parser and evaluator contracts.

## Alignment with Product Vision

This spec directly supports the product principle of "zero runtime surprises" by making every behavioral guarantee explicit. It also enables the "framework-agnostic" principle by fully separating the core contracts from any framework-specific rendering concerns.

---

## Requirements

### Requirement 1 — Lexer: Token Rules

**User Story:** As a framework adapter author, I want a precise specification of every token in OpenUI Lang, so that I can build compatible tooling (syntax highlighters, linters, editors).

#### Acceptance Criteria

1. WHEN the lexer encounters horizontal whitespace (space, tab, carriage return) THEN it SHALL skip it without emitting a token.
2. WHEN the lexer encounters a newline (`\n`) THEN it SHALL emit a `Newline` token (newlines are significant as statement terminators).
3. WHEN the lexer encounters `(`, `)`, `[`, `]`, `{`, `}`, `,`, `:` THEN it SHALL emit the corresponding single-character punctuation token.
4. WHEN the lexer encounters `=` not followed by `=` THEN it SHALL emit `Equals`; WHEN followed by `=` THEN it SHALL emit `EqEq`.
5. WHEN the lexer encounters `!` not followed by `=` THEN it SHALL emit `Not`; WHEN followed by `=` THEN it SHALL emit `NotEq`.
6. WHEN the lexer encounters `>` / `>=` / `<` / `<=` THEN it SHALL emit `Greater` / `GreaterEq` / `Less` / `LessEq`.
7. WHEN the lexer encounters `&&` or a single `&` THEN it SHALL emit `And`. WHEN it encounters `||` or a single `|` THEN it SHALL emit `Or`.
8. WHEN the lexer encounters `.` THEN it SHALL emit `Dot`; `?` → `Question`; `+` → `Plus`; `*` → `Star`; `/` → `Slash`; `%` → `Percent`.
9. WHEN the lexer encounters a double-quoted string `"..."` THEN it SHALL use `JSON.parse` to unescape it. IF the string is unterminated (streaming) THEN it SHALL append a closing quote before parsing. IF `JSON.parse` throws THEN it SHALL strip the quotes and emit the raw text.
10. WHEN the lexer encounters a single-quoted string `'...'` THEN it SHALL manually unescape `\'`, `\\`, `\n`, `\t`, passing through all other escape sequences.
11. WHEN the lexer encounters a numeric literal (optionally preceded by `-` when not following a value token) THEN it SHALL parse integer, decimal, and scientific notation (`1e10`) and emit a `Num` token.
12. WHEN the lexer encounters a `-` following a value-producing token (`Num`, `Str`, `Ident`, `Type`, `RParen`, `RBrack`, `True`, `False`, `Null`, `StateVar`, `BuiltinCall`) THEN it SHALL emit `Minus` (binary subtraction). OTHERWISE IF a digit follows THEN it SHALL treat `-` as part of a negative number literal.
13. WHEN the lexer encounters `$` followed by a letter or `_` THEN it SHALL scan the full `$identifier` and emit `StateVar` (e.g. `$count`, `$filter`).
14. WHEN the lexer encounters an `@` followed by a letter or `_` THEN it SHALL scan the identifier and emit `BuiltinCall` (e.g. `@Count`, `@Each`).
15. WHEN the lexer encounters a PascalCase identifier (starts with uppercase `A-Z`) THEN it SHALL emit `Type`. WHEN it encounters a lowercase identifier (starts with `a-z` or `_`) THEN it SHALL emit `Ident`.
16. WHEN the lexer encounters the keywords `true`, `false`, `null` THEN it SHALL emit `True`, `False`, `Null` respectively.
17. WHEN the lexer encounters any other character (emoji, `#`, etc.) THEN it SHALL skip it silently.
18. WHEN the lexer reaches end of input THEN it SHALL append an `EOF` token.

---

### Requirement 2 — Statement Grammar

**User Story:** As an LLM author generating OpenUI Lang, I want to know the exact statement grammar, so that I can produce valid programs.

#### Acceptance Criteria

1. WHEN a program is parsed THEN it SHALL be treated as a sequence of statements, where each statement has the form: `<lhs> = <expression>`.
2. WHEN `<lhs>` is a `StateVar` token (e.g. `$count`) THEN the statement SHALL be a **state declaration**.
3. WHEN `<lhs>` is an `Ident` (lowercase) or `Type` (PascalCase) token THEN the statement SHALL be a **value declaration**.
4. WHEN a statement's expression is `Query(...)` THEN it SHALL be reclassified as a **query declaration**, regardless of whether the LHS token was originally `StateVar`, `Ident`, or `Type`.
5. WHEN a statement's expression is `Mutation(...)` THEN it SHALL be reclassified as a **mutation declaration**, regardless of whether the LHS token was originally `StateVar`, `Ident`, or `Type`.
6. WHEN statements are separated by newlines at bracket depth 0 THEN they SHALL be treated as separate statements.
7. WHEN a newline occurs inside `()`, `[]`, or `{}` THEN it SHALL be ignored (not a statement boundary).
8. WHEN a ternary expression spans multiple lines (next non-whitespace token is `?` or `:`) THEN it SHALL NOT be split into separate statements. Ternary depth is tracked as an integer that increments when `?` is encountered at bracket depth 0 and decrements when `:` is encountered at bracket depth 0 and ternary depth > 0.
9. WHEN a line does not match `<identifier> = <expression>` THEN it SHALL be silently skipped.
10. WHEN the same statement `id` appears more than once THEN the later definition SHALL overwrite the earlier one (last-write-wins).

---

### Requirement 3 — Expression Grammar and Operator Precedence

**User Story:** As a framework adapter author, I want the complete expression grammar and precedence rules, so that I can evaluate expressions correctly.

#### Acceptance Criteria

1. WHEN parsing an expression THEN the parser SHALL use a Pratt (top-down operator precedence) algorithm.
2. Operator precedence from lowest to highest SHALL be:
   - Ternary (`?:`) — level 1
   - Logical OR (`||`) — level 2
   - Logical AND (`&&`) — level 3
   - Equality (`==`, `!=`) — level 4
   - Comparison (`>`, `<`, `>=`, `<=`) — level 5
   - Additive (`+`, `-`) — level 6
   - Multiplicative (`*`, `/`, `%`) — level 7
   - Unary (`!`, unary `-`) — level 8
   - Member access (`.`, `[...]`) — level 9
3. WHEN `+` is applied where at least one operand is a string THEN it SHALL perform string concatenation; `null`/`undefined` shall coerce to `""`.
4. WHEN `/` or `%` encounters a zero divisor THEN it SHALL return `0` (not `Infinity` or `NaN`).
5. WHEN `==` or `!=` is used THEN it SHALL use loose equality (`==` / `!=` in JavaScript), not strict.
6. WHEN `>`, `<`, `>=`, `<=` are used THEN both operands SHALL be coerced to numbers via `toNumber`.
7. WHEN `&&` or `||` are used THEN they SHALL short-circuit (left operand evaluated first, right only if needed).
8. WHEN a `StateVar` appears on the LHS of `=` inside an expression THEN it SHALL produce an `Assign` AST node (not a statement binding).
9. WHEN `.field` access is performed on an array THEN it SHALL pluck that field from every element (returning an array), except `.length` which returns array length.
10. WHEN `[expr]` index access is used on an array THEN the index SHALL be coerced to a number. WHEN used on an object THEN the index SHALL be coerced to a string.
11. WHEN `@Each(array, varName, template)` is used THEN it SHALL be a lazy builtin — `template` receives each element bound to `varName` and is evaluated independently per item. `varName` SHALL be specified either as a bare identifier reference or a string literal naming the loop variable, and SHALL NOT be a `$state` variable.
12. Builtins SHALL use `@Name(...)` syntax. `Action(...)` is the only builtin-like call that is valid without the `@` prefix.

---

### Requirement 4 — Component Call Syntax and Positional Argument Mapping

**User Story:** As an LLM generating UI, I want to know how component calls are mapped to props, so that I produce valid element nodes.

#### Acceptance Criteria

1. WHEN a PascalCase name is followed by `(` THEN it SHALL be parsed as a component call `TypeName(arg1, arg2, ...)` with positional arguments.
2. WHEN the parser has a library JSON schema for the component THEN positional arguments SHALL be mapped to named props in the order defined by `$defs[ComponentName].properties` key order.
3. WHEN more arguments are provided than the schema defines THEN excess arguments SHALL be silently dropped and a `ValidationError` with code `"excess-args"` SHALL be recorded.
4. WHEN a required prop is missing (argument not provided) THEN the parser SHALL attempt to use the schema `default` value. IF no default exists THEN a `ValidationError` with code `"missing-required"` SHALL be recorded and the component SHALL be dropped (returns `null`).
5. WHEN a required prop is explicitly provided as `null` THEN the parser SHALL treat it as a missing required value for validation purposes. It SHALL first attempt to use the schema `default` value; IF no default exists THEN a `ValidationError` with code `"null-required"` SHALL be recorded and the component SHALL be dropped.
6. WHEN a component name is not found in the schema THEN a `ValidationError` with code `"unknown-component"` SHALL be recorded and the component SHALL return `null`.
7. WHEN `Query()` or `Mutation()` appears as an inline value (not as a top-level statement RHS) THEN a `ValidationError` with code `"inline-reserved"` SHALL be recorded and it SHALL return `null`.
8. WHEN a component has no dynamic props (all props are static literals) THEN `hasDynamicProps` SHALL be `false`, enabling the evaluator to skip it.

---

### Requirement 5 — ParseResult Structure

**User Story:** As a framework adapter, I want a precise description of `ParseResult` so that I can consume it correctly.

#### Acceptance Criteria

1. WHEN parsing succeeds and a root statement is found THEN `ParseResult.root` SHALL be an `ElementNode` with `type: "element"`, `typeName`, `props`, `partial`, and `hasDynamicProps`. IF the node originated from a named statement THEN it MAY also include `statementId`.
2. WHEN no root is available THEN `ParseResult.root` SHALL be `null`.
3. WHEN the parser auto-closes an incomplete input (streaming) THEN `ParseResult.meta.incomplete` SHALL be `true`.
4. `ParseResult.meta.unresolved` SHALL list all identifiers referenced but never defined. This list is not deduplicated and may also contain identifiers encountered during cycle detection.
5. `ParseResult.meta.orphaned` SHALL list all value statements defined but not reachable from root (excluding `$state`, `Query`, and `Mutation` declarations).
6. `ParseResult.meta.statementCount` SHALL report the parser's current statement count. In one-shot parsing this is the count of unique parsed statement IDs after deduplication. In streaming parsing it is derived from committed statement count plus the currently parsed pending statement set, and can therefore temporarily differ from the final deduplicated unique-ID count in duplicate-ID edge cases.
7. `ParseResult.meta.errors` SHALL contain all `ValidationError` objects recorded during materialization.
8. `ParseResult.stateDeclarations` SHALL be a map of `"$varName" → materializedDefaultValue`. WHEN a `$var` is referenced in code but never explicitly declared THEN it SHALL appear with value `null`.
9. `ParseResult.queryStatements` SHALL list extracted `Query()` calls with `statementId`, `toolAST`, `argsAST`, `defaultsAST`, `refreshAST`, `deps` (pre-computed `$var` names in `argsAST`), and `complete: true`.
10. `ParseResult.mutationStatements` SHALL list extracted `Mutation()` calls with `statementId`, `toolAST`, and `argsAST`.

---

### Requirement 6 — Root Statement Selection

**User Story:** As an LLM, I want to know which statement becomes the root of the element tree.

#### Acceptance Criteria

1. WHEN a statement named `"root"` exists THEN it SHALL always be selected as the entry point.
2. WHEN no `"root"` statement exists AND a `rootName` was passed to `createParser` AND a statement with that name exists THEN that statement SHALL be the entry point.
3. IF no statement whose name matches `rootName` exists (AC2 fails) AND a `rootName` was passed THEN the first value statement whose expression is a component call with `typeName === rootName` SHALL be selected.
4. WHEN none of the above apply THEN the first value statement whose expression is a component call (not a builtin or reserved call) SHALL be selected.
5. WHEN no component statement exists THEN the first statement overall SHALL be selected.
6. IF the resolved entry statement does not exist in the map THEN `ParseResult.root` SHALL be `null`.

---

### Requirement 7 — Pre-processing: Fence Stripping and Comment Removal

**User Story:** As a consumer calling `parse()`, I want the parser to handle LLM markdown wrapping automatically, so that I don't need to strip it manually.

#### Acceptance Criteria

1. WHEN the input contains `` ``` ``-fenced code blocks (with optional language tag) THEN the parser SHALL extract only the content inside the fences and discard the fences.
2. WHEN multiple fenced code blocks are present THEN the parser SHALL concatenate their contents with newline separators.
3. WHEN `` ``` `` appears inside a double-quoted string THEN it SHALL NOT be treated as a fence boundary.
4. WHEN a closing `` ``` `` fence is missing (streaming) THEN the parser SHALL take everything after the opening fence.
5. WHEN a line contains `//` outside a string THEN everything from `//` to end-of-line SHALL be removed.
6. WHEN a line contains `#` outside a string THEN everything from `#` to end-of-line SHALL be removed.
7. WHEN the input has no fences THEN it SHALL be passed through as-is after comment stripping.

---

### Requirement 8 — Auto-close for Partial / Streaming Input

**User Story:** As a streaming parser consumer, I want the parser to gracefully handle truncated input without throwing exceptions.

#### Acceptance Criteria

1. WHEN parsing partial input with unclosed brackets or strings THEN `autoClose` SHALL append the minimal closing tokens: unterminated string → matching quote; unmatched `(` → `)`; unmatched `[` → `]`; unmatched `{` → `}`. Closers are appended in reverse stack order.
2. WHEN auto-closing occurs THEN `wasIncomplete` SHALL be `true`, which propagates to `ParseResult.meta.incomplete`.
3. WHEN an unterminated string ends with a trailing escape character (`\`) THEN `autoClose` SHALL append a matching escape before appending the closing quote.
4. WHEN no open brackets or strings are found THEN `wasIncomplete` SHALL be `false` and input returned unchanged.

---

### Requirement 9 — Streaming Parser Behavior Contract

**User Story:** As a framework adapter streaming LLM output, I want the streaming parser to produce correct incremental results on every chunk without losing previously-parsed statements.

#### Acceptance Criteria

1. WHEN `createStreamingParser(schema)` is called THEN it SHALL return a `StreamParser` with methods `push(chunk)`, `set(fullText)`, and `getResult()`.
2. WHEN `push(chunk)` is called THEN the chunk SHALL be appended to the internal buffer and a new `ParseResult` returned.
3. WHEN `set(fullText)` is called THEN:
   - IF `fullText` starts with the current buffer AND is longer THEN only the delta SHALL be appended.
   - IF `fullText` is shorter than the buffer OR does not start with it THEN the parser SHALL reset (full re-parse from the new text).
4. WHEN a newline is encountered at bracket depth 0 and ternary depth 0 (as defined in Requirement 2 AC8), and the next non-whitespace character is not `?` or `:` THEN the statement up to that newline SHALL be committed to the completed statement cache.
5. WHEN a statement is committed to the completed cache THEN it SHALL NOT be overwritten by a subsequent incomplete (pending) re-parse of the same statement ID. Completed statements are immutable for the lifetime of the buffer.
6. WHEN a pending (last, incomplete) statement is present THEN it SHALL be auto-closed and merged with completed statements for the current result — but only contributes NEW IDs, never overwrites completed ones.
7. WHEN all statements are complete (no pending text remains) THEN `meta.incomplete` SHALL be `false`.
8. WHEN the buffer is reset THEN completed statement cache, `completedEnd`, `completedCount`, and `firstId` SHALL all be cleared.
9. WHEN `getResult()` is called THEN it SHALL return the current result without consuming new input.

---

### Requirement 10 — Built-in Functions

**User Story:** As an LLM generating UI expressions, I want to know the full list of available built-in functions and their signatures.

#### Acceptance Criteria

**Eager builtins** (invoked with `@Name(...)` syntax in expressions; evaluated immediately with resolved argument values):

1. WHEN `@Count(array)` is evaluated THEN it SHALL return the array's length, or `0` if the argument is not an array.
2. WHEN `@First(array)` is evaluated THEN it SHALL return the first element, or `null` if the array is empty or not an array.
3. WHEN `@Last(array)` is evaluated THEN it SHALL return the last element, or `null` if the array is empty or not an array.
4. WHEN `@Sum(array)` is evaluated THEN it SHALL return the sum of all elements coerced to numbers via `toNumber`, or `0` if the argument is not an array.
5. WHEN `@Avg(array)` is evaluated THEN it SHALL return the numeric average, or `0` if the array is empty or not an array.
6. WHEN `@Min(array)` / `@Max(array)` is evaluated THEN it SHALL return the numeric minimum / maximum, or `0` if the array is empty or not an array.
7. WHEN `@Sort(array, field, direction?)` is evaluated THEN it SHALL return a new sorted array. IF `direction` is `"desc"` THEN it SHALL sort descending; otherwise ascending. IF `field` is a non-empty string THEN elements shall be sorted by that field path (dot-path supported); IF `field` is empty THEN elements shall be sorted directly. Numeric strings SHALL sort numerically, not lexicographically.
8. WHEN `@Filter(array, field, operator, value)` is evaluated THEN it SHALL return only those elements where the comparison holds. IF `field` is non-empty THEN the comparison is on `element[field]`; otherwise on the element itself. Operators SHALL be: `"=="` (loose equality), `"!="`, `">"`, `"<"`, `">="`, `"<="` (all numeric-coerced), `"contains"` (substring check via `String.includes`).
9. WHEN `@Round(number, decimals?)` is evaluated THEN it SHALL return the number rounded to `decimals` decimal places (default `0`).
10. WHEN `@Abs(number)` is evaluated THEN it SHALL return the absolute value of the argument coerced to a number.
11. WHEN `@Floor(number)` / `@Ceil(number)` is evaluated THEN it SHALL return the floor / ceiling of the argument coerced to a number.

**Lazy builtin** (receives AST nodes rather than evaluated values; controls its own evaluation):

12. WHEN `@Each(array, varName, template)` is evaluated THEN: the array SHALL be evaluated eagerly; `varName` SHALL be a string identifier (not a `$state` variable); the template expression SHALL be evaluated once per array item with all `Ref(varName)` nodes pre-substituted with the item's concrete value before deferred expressions (e.g. `Action` steps) are resolved; the result SHALL be an array of evaluated template values.

**Action container and action steps**:

13. WHEN `Action([step1, step2, ...])` is evaluated THEN it SHALL return an `ActionPlan { steps }` where `steps` contains only non-null objects with a `type` field.
14. WHEN `@Run(ref)` is evaluated THEN IF `ref` resolves to a `RuntimeRef` node THEN it SHALL return `ActionStep { type: "run", statementId: ref.n, refType: ref.refType }`. IF `ref` is unresolved THEN it SHALL return `null` (filtered out by `Action`).
15. WHEN `@ToAssistant("message", "context"?)` is evaluated THEN it SHALL return `ActionStep { type: "continue_conversation", message, context? }`.
16. WHEN `@OpenUrl("url")` is evaluated THEN it SHALL return `ActionStep { type: "open_url", url }`.
17. WHEN `@Set($var, valueExpr)` is evaluated THEN IF the first argument is a `StateRef` THEN it SHALL return `ActionStep { type: "set", target: $var.name, valueAST: valueExpr }`. IF the first argument is not a `StateRef` THEN it SHALL return `null`.
18. WHEN `@Reset($var1, ...)` is evaluated THEN it SHALL collect all `StateRef` arguments into `targets` and return `ActionStep { type: "reset", targets }`. IF no `StateRef` arguments are present THEN it SHALL return `null`.

**Reserved statement-level calls** (not valid as inline prop values; only valid as the RHS of a top-level statement):

19. WHEN `Query(toolName, args?, defaults?, refreshSeconds?)` is used as a top-level statement RHS THEN it SHALL produce a `QueryStatementInfo` entry with the four positional args stored as AST nodes. IF used inline as a prop value THEN it SHALL produce a `ValidationError` with code `"inline-reserved"` and return `null`.
20. WHEN `Mutation(toolName, args?)` is used as a top-level statement RHS THEN it SHALL produce a `MutationStatementInfo` with `toolAST` and `argsAST`. IF used inline THEN it SHALL produce a `ValidationError` with code `"inline-reserved"` and return `null`.

---

### Requirement 11 — Runtime Evaluator

**User Story:** As a framework adapter, I want the evaluator's behavior to be fully specified so that I can implement reactive UI updates correctly.

#### Acceptance Criteria

1. WHEN `evaluate(node, context)` is called THEN it SHALL recursively evaluate the AST node to a concrete JavaScript value.
2. WHEN an `EvaluationContext` is provided THEN `getState(name)` SHALL be called for `StateRef` nodes; `resolveRef(name)` SHALL be called for `Ref` and `RuntimeRef` nodes.
3. WHEN `extraScope` is set on context THEN it SHALL take precedence over `getState` for matching `StateRef` names.
4. WHEN an `Assign` node is evaluated THEN it SHALL return `ReactiveAssign { __reactive: "assign", target, expr }` — the framework adapter is responsible for wiring this to a reactive setter.
5. WHEN `isReactiveAssign(value)` returns `true` THEN the framework adapter SHALL use it to create a reactive binding (e.g., `onChange` handler) rather than a static value.
6. WHEN `evaluateElementProps(root, context)` is called THEN it SHALL recursively evaluate all props in the element tree, returning a new tree with concrete values.
7. WHEN `hasDynamicProps === false` on an `ElementNode` THEN the evaluator SHALL return it unchanged (skip evaluation).
8. WHEN a prop's schema has been tagged with `markReactive(schema)` (a helper from `src/reactive.ts` that registers the schema in an internal marker set detectable at runtime via `isReactiveSchema(schema)`, without mutating the schema object) AND the prop value is a `StateRef` THEN the evaluator SHALL emit a `ReactiveAssign` for that prop instead of reading the current state value.
9. WHEN a non-reactive prop evaluates to a `ReactiveAssign` THEN the evaluator SHALL strip it to the current state value rather than surfacing the reactive marker to the renderer.
10. WHEN `@Each(array, varName, template)` is evaluated THEN loop variable refs inside the template SHALL be substituted with concrete values BEFORE deferred expressions (like `Action` steps) are evaluated, capturing the correct item value at click-time.

---

### Requirement 12 — Merge / Patch Model

**User Story:** As a framework adapter implementing incremental LLM edits, I want the merge contract fully specified so that I apply patches correctly.

#### Acceptance Criteria

1. WHEN `mergeStatements(existing, patch)` is called THEN patch statements SHALL override existing statements by name (last-write-wins on ID).
2. WHEN a patch statement has value `null` (i.e. `name = null`) THEN the statement SHALL be deleted from the output.
3. WHEN a patch adds a statement ID not in the original THEN it SHALL be appended in the order it appears in the patch.
4. WHEN the merge is complete THEN a garbage-collection pass SHALL remove all non-`$state` statements not reachable from the `root` statement via `Ref`/`RuntimeRef` traversal.
5. WHEN `$state` variables (IDs starting with `$`) are present THEN they SHALL always be retained (never GC'd) because they are bound at runtime, not via `Ref`.
6. WHEN the input patch contains markdown fences THEN `stripFences` SHALL be applied before parsing.
7. WHEN `existing` is empty THEN the result SHALL be the patch statements only.
8. WHEN `patch` is empty THEN the result SHALL be the existing program unchanged.

---

## Non-Functional Requirements

### Performance
- The one-shot parser must run in a single pass over the token stream (O(n) in input length, no backtracking).
- The streaming parser must not re-parse committed statements on each chunk; only the pending (last incomplete) statement should be re-parsed.
- `hasDynamicProps: false` is a mandatory optimization signal — evaluators must respect it and skip static nodes.

### Reliability
- The parser must never throw an exception for any input, including empty strings, truncated UTF-8, random bytes, and partial LLM output.
- `autoClose` must always produce syntactically parseable output for any string input.
- Division by zero in expressions must return `0` rather than `Infinity` or `NaN`.

### Compatibility
- The library must produce identical `ParseResult` shapes whether invoked via `createParser` (one-shot) or `createStreamingParser` (streaming) on the same complete input, except that `meta.statementCount` in streaming mode reflects the parser's committed-plus-pending counting model described in Requirement 5.
- The `@modelcontextprotocol/sdk` dependency is optional — all exports must be usable without it.

### Usability
- `ValidationError` and `OpenUIError` must always include `code`, `component`, and `statementId` (when applicable) so that LLM correction loops can target specific statements for re-generation.
