package com.huawei.cloudsop.genui.core.validation.parser;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a flat token stream into individual {@code id = expression} statements.
 *
 * <p>Mirrors {@code split()} in {@code packages/lang-core/src/parser/statements.ts}: statements are
 * separated by depth-0 newlines; newlines inside brackets/parens/braces or inside a multi-line
 * ternary do NOT split. Accepts {@link TokenType#IDENT}, {@link TokenType#TYPE} and {@link
 * TokenType#STATE_VAR} as identifiers. Lines with no {@code =} or no identifier are skipped (they
 * surface as diagnostics in {@link OpenuiParser}).
 *
 * <p>Java extension: each {@link RawStmt} carries a {@link SourceSpan} (line/column/offset span)
 * derived from its tokens, for diagnostics.
 */
public final class StatementSplitter {

  private StatementSplitter() {}

  /**
   * A raw (pre-expression-parse) statement: the LHS id, its token type, the RHS expression tokens,
   * and the source span of the whole statement.
   */
  public record RawStmt(
      String id, TokenType idTokenType, List<Token> tokens, SourceSpan span) {
    public RawStmt {
      tokens = tokens == null ? List.of() : List.copyOf(tokens);
    }
  }

  /** Reason a raw line was rejected (used to emit diagnostics upstream). */
  public enum SkipReason {
    /** Line starts with a token that cannot be a statement id. */
    INVALID_STATEMENT,
    /** Identifier was not followed by {@code =}. */
    MISSING_ASSIGNMENT
  }

  /** A skipped (malformed) line: where it started and why. */
  public record SkippedLine(SkipReason reason, String idText, SourceSpan span) {}

  /** Output of {@link #splitDetailed}: valid statements plus skipped lines. */
  public record SplitResult(List<RawStmt> statements, List<SkippedLine> skipped) {
    public SplitResult {
      statements = statements == null ? List.of() : List.copyOf(statements);
      skipped = skipped == null ? List.of() : List.copyOf(skipped);
    }
  }

  /** Convenience: valid statements only (parity with the TS {@code split}). */
  public static List<RawStmt> split(List<Token> tokens) {
    return splitDetailed(tokens).statements();
  }

  /** Split with structured skip reporting so the parser can emit diagnostics. */
  public static SplitResult splitDetailed(List<Token> tokens) {
    List<RawStmt> stmts = new ArrayList<>();
    List<SkippedLine> skipped = new ArrayList<>();
    int pos = 0;
    int n = tokens.size();

    while (pos < n) {
      // Skip blank lines.
      while (pos < n && tokens.get(pos).type() == TokenType.NEWLINE) {
        pos++;
      }
      if (pos >= n || tokens.get(pos).type() == TokenType.EOF) {
        break;
      }

      Token idTok = tokens.get(pos);
      TokenType tt = idTok.type();
      if (tt != TokenType.IDENT && tt != TokenType.TYPE && tt != TokenType.STATE_VAR) {
        SourceSpan span = spanOf(idTok, idTok);
        skipped.add(new SkippedLine(SkipReason.INVALID_STATEMENT, idText(idTok), span));
        pos = skipToLineEnd(tokens, pos);
        continue;
      }
      String id = idTok.text();
      pos++;

      // Must be followed by `=`.
      if (pos >= n || tokens.get(pos).type() != TokenType.EQUALS) {
        skipped.add(
            new SkippedLine(SkipReason.MISSING_ASSIGNMENT, id, spanOf(idTok, idTok)));
        pos = skipToLineEnd(tokens, pos);
        continue;
      }
      pos++; // consume '='

      // Collect expression tokens until a depth-0 newline / EOF, honoring ternary continuation.
      List<Token> expr = new ArrayList<>();
      int depth = 0;
      int ternaryDepth = 0;
      while (pos < n && tokens.get(pos).type() != TokenType.EOF) {
        TokenType cur = tokens.get(pos).type();
        if (cur == TokenType.NEWLINE && depth <= 0 && ternaryDepth <= 0) {
          int peek = pos + 1;
          while (peek < n && tokens.get(peek).type() == TokenType.NEWLINE) {
            peek++;
          }
          TokenType nextT = peek < n ? tokens.get(peek).type() : TokenType.EOF;
          if (nextT == TokenType.QUESTION || (nextT == TokenType.COLON && ternaryDepth > 0)) {
            pos++; // ternary continuation — keep collecting
            continue;
          }
          break; // statement boundary
        }
        if (cur == TokenType.NEWLINE) {
          pos++; // newline inside bracket/ternary — skip
          continue;
        }
        if (cur == TokenType.L_PAREN || cur == TokenType.L_BRACK || cur == TokenType.L_BRACE) {
          depth++;
        } else if ((cur == TokenType.R_PAREN
                || cur == TokenType.R_BRACK
                || cur == TokenType.R_BRACE)
            && depth > 0) {
          depth--;
        } else if (cur == TokenType.QUESTION && depth == 0) {
          ternaryDepth++;
        } else if (cur == TokenType.COLON && depth == 0 && ternaryDepth > 0) {
          ternaryDepth--;
        }
        expr.add(tokens.get(pos));
        pos++;
      }

      if (!expr.isEmpty()) {
        Token last = expr.get(expr.size() - 1);
        stmts.add(new RawStmt(id, tt, expr, spanOf(idTok, last)));
      }
    }

    return new SplitResult(stmts, skipped);
  }

  private static int skipToLineEnd(List<Token> tokens, int pos) {
    int n = tokens.size();
    while (pos < n
        && tokens.get(pos).type() != TokenType.NEWLINE
        && tokens.get(pos).type() != TokenType.EOF) {
      pos++;
    }
    return pos;
  }

  private static SourceSpan spanOf(Token start, Token end) {
    int endOff = end.offset() + Math.max(0, end.length());
    return new SourceSpan(start.line(), start.column(), start.offset(), endOff);
  }

  private static String idText(Token t) {
    return t.text() != null ? t.text() : t.type().name();
  }
}
