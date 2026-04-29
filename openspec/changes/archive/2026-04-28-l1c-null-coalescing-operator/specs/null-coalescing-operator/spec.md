## ADDED Requirements

### Requirement: Lexer recognizes ?? as a distinct token
The lexer SHALL tokenize `??` as `T.NullCoal` and SHALL NOT split it into two `T.Question` tokens. When `?` is followed by a second `?`, the lexer SHALL consume both characters and emit a single `T.NullCoal` token. When `?` is not followed by `?`, the lexer SHALL emit `T.Question` as before.

#### Scenario: Double question mark produces NullCoal token
- **WHEN** the lexer processes the source string `a ?? b`
- **THEN** the token sequence SHALL be `[Ident("a"), NullCoal, Ident("b"), EOF]`

#### Scenario: Single question mark still produces Question token
- **WHEN** the lexer processes the source string `a ? b : c`
- **THEN** the token sequence SHALL be `[Ident("a"), Question, Ident("b"), Colon, Ident("c"), EOF]`

#### Scenario: Double question mark inside expression
- **WHEN** the lexer processes `(v ?? "—") + " 核"`
- **THEN** the `??` SHALL be emitted as a single `NullCoal` token, not two `Question` tokens

---

### Requirement: Parser produces BinOp AST node for ??
The parser SHALL parse `??` as a left-associative infix binary operator with precedence equal to `||` (PREC_OR = 2). The resulting AST node SHALL be `{k: "BinOp", op: "??", left, right}`.

#### Scenario: Basic null-coalescing expression
- **WHEN** the parser receives `a ?? b`
- **THEN** the AST SHALL be `BinOp { op: "??", left: Ref("a"), right: Ref("b") }`

#### Scenario: Left-associativity for chained ??
- **WHEN** the parser receives `a ?? b ?? c`
- **THEN** the AST SHALL be `BinOp { op: "??", left: BinOp { op: "??", left: Ref("a"), right: Ref("b") }, right: Ref("c") }`

#### Scenario: ?? and ternary coexist without conflict
- **WHEN** the parser receives `a ?? b ? c : d`
- **THEN** the ternary SHALL bind looser than `??`, producing a ternary with `BinOp("??", a, b)` as its condition

#### Scenario: ?? inside parenthesized sub-expression
- **WHEN** the parser receives `(v ?? "—") + " 核"`
- **THEN** the `+` BinOp SHALL have `BinOp("??", v, "—")` as its left operand

---

### Requirement: Evaluator short-circuits on non-null left side
The evaluator SHALL evaluate the right-hand side of `??` only when the left side evaluates to `null` or `undefined`. If the left side is any other value — including `0`, `false`, or `""` — the evaluator SHALL return the left side without evaluating the right side.

#### Scenario: null left side triggers right side
- **WHEN** `v ?? "—"` is evaluated with `v = null`
- **THEN** the result SHALL be `"—"`

#### Scenario: undefined left side triggers right side
- **WHEN** `v ?? "—"` is evaluated with `v = undefined`
- **THEN** the result SHALL be `"—"`

#### Scenario: zero left side does not trigger right side
- **WHEN** `v ?? 99` is evaluated with `v = 0`
- **THEN** the result SHALL be `0`

#### Scenario: false left side does not trigger right side
- **WHEN** `v ?? true` is evaluated with `v = false`
- **THEN** the result SHALL be `false`

#### Scenario: empty string left side does not trigger right side
- **WHEN** `v ?? "default"` is evaluated with `v = ""`
- **THEN** the result SHALL be `""`

#### Scenario: non-null left side returns left without evaluating right
- **WHEN** `v ?? "fallback"` is evaluated with `v = "present"`
- **THEN** the result SHALL be `"present"`

#### Scenario: DSL use case — null field with string concat
- **WHEN** `(row.cpuCores ?? "—") + "核"` is evaluated with `row = { cpuCores: null }`
- **THEN** the result SHALL be `"—核"`

#### Scenario: DSL use case — non-null field with string concat
- **WHEN** `(row.cpuCores ?? "—") + "核"` is evaluated with `row = { cpuCores: 8 }`
- **THEN** the result SHALL be `"8核"`
