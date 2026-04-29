## Why

The openui-lang DSL has no null-coalescing operator, forcing the LLM to write verbose ternary workarounds (`v != null ? v : "—"`) wherever nullable fields appear. When the LLM generates `??` directly (which it knows from JavaScript), the current lexer silently mis-parses it as a broken ternary, producing wrong AST without a parse error — a correctness hazard that gets worse as nullable data patterns become more common in benchmarks.

## What Changes

- New `??` infix operator in the openui-lang expression grammar
- Lexer distinguishes `??` (two chars) from `?` (ternary) via single lookahead
- Parser treats `??` as a left-associative binary operator at `PREC_OR` precedence
- Runtime evaluator short-circuits: returns left if non-null/non-undefined, otherwise evaluates right
- New parser unit tests and runtime behavior tests

## Capabilities

### New Capabilities

- `null-coalescing-operator`: `??` binary operator in openui-lang — lexer token, parser precedence, runtime short-circuit semantics

### Modified Capabilities

<!-- None — no existing spec-level behavior changes -->

## Impact

- `packages/lang-core/src/parser/tokens.ts` — new `T.NullCoal` token constant
- `packages/lang-core/src/parser/lexer.ts` — two-char lookahead on `?`
- `packages/lang-core/src/parser/expressions.ts` — precedence entry + infix branch
- `packages/lang-core/src/runtime/evaluator.ts` — short-circuit `??` in BinOp handler
- `packages/lang-core/src/parser/__tests__/parser.test.ts` — new parser AST tests
- `packages/lang-core/src/__tests__/null-coalescing.test.ts` — new runtime tests (new file)
- No breaking changes; no public API surface change; no downstream package changes required
