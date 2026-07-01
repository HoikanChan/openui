package com.huawei.cloudsop.genui.core.validation.parser;

/**
 * A structured, line/column-bearing parser diagnostic.
 *
 * <p>Syntax problems are COLLECTED as these records on the {@link Program} rather than thrown, so a
 * single malformed statement degrades gracefully and later statements still parse (streaming
 * tolerance). Section 3 maps these into {@code ValidationIssue}s.
 *
 * <p>{@code line}/{@code column} are 1-based; {@code startOffset}/{@code endOffset} are 0-based
 * indices into the preprocessed source ({@code endOffset} exclusive). {@code -1} denotes "unknown".
 */
public record ParseDiagnostic(
    ParseErrorCode code,
    String message,
    String statementId,
    int line,
    int column,
    int startOffset,
    int endOffset) {

  /** Build a diagnostic from a {@link SourceSpan}. */
  public static ParseDiagnostic at(
      ParseErrorCode code, String message, String statementId, SourceSpan span) {
    SourceSpan s = span == null ? SourceSpan.UNKNOWN : span;
    return new ParseDiagnostic(
        code, message, statementId, s.line(), s.column(), s.startOffset(), s.endOffset());
  }
}
