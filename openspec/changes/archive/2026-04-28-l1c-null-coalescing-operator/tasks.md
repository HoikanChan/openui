## 1. Token

- [ ] 1.1 Add `NullCoal = 36` to the `T` const enum in `packages/lang-core/src/parser/tokens.ts`

## 2. Lexer

- [ ] 2.1 Change the `?` branch in `packages/lang-core/src/parser/lexer.ts` to peek at `src[i+1]`: emit `T.NullCoal` (consume 2 chars) if next char is `?`, otherwise emit `T.Question` (consume 1 char)

## 3. Expression Parser

- [ ] 3.1 Add `case T.NullCoal: return PREC_OR;` to `getInfixPrec` in `packages/lang-core/src/parser/expressions.ts`
- [ ] 3.2 Add infix branch for `T.NullCoal` in `parseInfix`: advance, return `{ k: "BinOp", op: "??", left, right: parseExpr(PREC_OR) }`

## 4. Runtime Evaluator

- [ ] 4.1 Add short-circuit `??` case in the `BinOp` handler in `packages/lang-core/src/runtime/evaluator.ts` — after the `&&`/`||` blocks: evaluate left, return it if `!= null && !== undefined`, otherwise evaluate and return right

## 5. Tests

- [ ] 5.1 Add parser AST tests to `packages/lang-core/src/parser/__tests__/parser.test.ts`: basic `a ?? b`, left-associativity `a ?? b ?? c`, coexistence with ternary `a ?? b ? c : d`, parenthesized sub-expression `(v ?? "—") + " 核"`
- [ ] 5.2 Create `packages/lang-core/src/__tests__/null-coalescing.test.ts` with runtime behavior tests: null triggers right, undefined triggers right, `0` does not, `false` does not, `""` does not, non-null returns left, DSL string-concat scenarios

## 6. Verification

- [ ] 6.1 Run `pnpm --filter @openuidev/lang-core run test` and confirm all tests pass
