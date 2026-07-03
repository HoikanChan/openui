package com.huawei.cloudsop.genui.core.validation.parser;

/**
 * A source location span within the preprocessed DSL text.
 *
 * <p>{@code line}/{@code column} are 1-based; {@code startOffset}/{@code endOffset} are 0-based
 * indices into the preprocessed source. {@code endOffset} is exclusive. A value of {@code -1} on any
 * field denotes "unknown".
 */
public record SourceSpan(int line, int column, int startOffset, int endOffset) {

  /** Unknown span (all fields {@code -1}). */
  public static final SourceSpan UNKNOWN = new SourceSpan(-1, -1, -1, -1);

  /** Length of the span in characters, or {@code -1} if either offset is unknown. */
  public int length() {
    if (startOffset < 0 || endOffset < 0) {
      return -1;
    }
    return endOffset - startOffset;
  }
}
