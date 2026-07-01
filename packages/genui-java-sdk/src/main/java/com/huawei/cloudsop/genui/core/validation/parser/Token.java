package com.huawei.cloudsop.genui.core.validation.parser;

/**
 * A lexical token.
 *
 * <p>Mirrors the TS {@code Token{t, v}} but adds source-position fields ({@code line}, {@code
 * column}, {@code offset}, {@code length}) as a Java extension so diagnostics can carry
 * line/column/span information. Positions are 1-based for {@code line}/{@code column} and 0-based for
 * {@code offset} (index into the preprocessed source).
 *
 * <p>{@code text} carries the decoded string value for {@link TokenType#STR}, {@link
 * TokenType#IDENT}, {@link TokenType#TYPE}, {@link TokenType#STATE_VAR} (including the leading {@code
 * $}) and {@link TokenType#BUILTIN_CALL} (without the leading {@code @}). {@code number} carries the
 * parsed value for {@link TokenType#NUM}. Both are {@code null}/{@code NaN} for punctuation.
 */
public record Token(
    TokenType type, String text, double number, int line, int column, int offset, int length) {

  /** Create a value-less punctuation/keyword token. */
  public static Token of(TokenType type, int line, int column, int offset, int length) {
    return new Token(type, null, Double.NaN, line, column, offset, length);
  }

  /** Create a token carrying a string value ({@code STR}/{@code IDENT}/{@code TYPE}/etc.). */
  public static Token ofText(
      TokenType type, String text, int line, int column, int offset, int length) {
    return new Token(type, text, Double.NaN, line, column, offset, length);
  }

  /** Create a numeric token. */
  public static Token ofNumber(
      double number, int line, int column, int offset, int length) {
    return new Token(TokenType.NUM, null, number, line, column, offset, length);
  }
}
