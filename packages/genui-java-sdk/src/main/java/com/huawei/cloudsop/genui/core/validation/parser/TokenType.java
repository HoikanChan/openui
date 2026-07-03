package com.huawei.cloudsop.genui.core.validation.parser;

/**
 * Token type discriminant for openui-lang.
 *
 * <p>Mirrors the TypeScript {@code const enum T} in {@code packages/lang-core/src/parser/tokens.ts}.
 * Ordinal order is kept identical to the TS enum so cross-language parity fixtures line up.
 */
public enum TokenType {
  NEWLINE, // \n (significant — statement separator at depth 0)
  L_PAREN, // (
  R_PAREN, // )
  L_BRACK, // [
  R_BRACK, // ]
  L_BRACE, // {
  R_BRACE, // }
  COMMA, // ,
  COLON, // :
  EQUALS, // =
  TRUE, // true
  FALSE, // false
  NULL, // null
  EOF, // end of input
  STR, // string literal (carries value)
  NUM, // number literal (carries value)
  IDENT, // lowercase identifier — becomes a reference
  TYPE, // PascalCase identifier — component name or reference
  STATE_VAR, // $identifier — reactive state reference (value includes leading $)
  DOT, // .
  PLUS, // +
  MINUS, // -
  STAR, // *
  SLASH, // /
  PERCENT, // %
  EQ_EQ, // ==
  NOT_EQ, // !=
  GREATER, // >
  LESS, // <
  GREATER_EQ, // >=
  LESS_EQ, // <=
  AND, // &&
  OR, // ||
  NOT, // !
  QUESTION, // ?
  BUILTIN_CALL, // @identifier — builtin function call (value excludes leading @)
  NULL_COAL // ??
}
