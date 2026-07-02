package com.huawei.cloudsop.genui.core.validation.stream;

import com.huawei.cloudsop.genui.core.validation.parser.OpenuiLexer;
import com.huawei.cloudsop.genui.core.validation.parser.OpenuiPreprocessor;
import com.huawei.cloudsop.genui.core.validation.parser.ParseMode;
import com.huawei.cloudsop.genui.core.validation.parser.StatementSplitter;
import com.huawei.cloudsop.genui.core.validation.parser.Token;
import java.util.ArrayList;
import java.util.List;

/**
 * Incrementally identifies COMPLETE openui-lang statements as raw LLM deltas arrive.
 *
 * <p><b>Approach — reuse, do not reinvent.</b> This scanner does not re-implement depth/string/
 * ternary/fence rules. On every delta it runs the proven Section-2 pipeline over the whole
 * accumulated buffer: {@link OpenuiPreprocessor#preprocess(String)} (fence extraction + comment
 * stripping, WITHOUT auto-close so incompleteness is preserved) → {@link OpenuiLexer#tokenize}
 * → {@link StatementSplitter#split}. Statement boundaries are exactly the depth-0 / outside-string /
 * outside-ternary newlines that {@code StatementSplitter} already computes, so there is zero drift
 * risk versus the parser.
 *
 * <p><b>Completeness rule.</b> Because a stream only ever appends, all statements returned by the
 * splitter <em>except the last</em> are definitively terminated (a later depth-0 boundary closed
 * them) and are therefore complete. The last statement may still be a partially-received tail, so it
 * is withheld as pending until either a subsequent statement appears after it (proving it complete)
 * or {@link #drainAtEnd()} is called. The count of complete statements is monotonic, so we only need
 * to remember how many we have already surfaced ({@code emittedCount}).
 *
 * <p>Statement text returned is the trimmed slice of the <em>preprocessed</em> buffer for that
 * statement's {@link StatementSplitter.RawStmt#span()} — i.e. the same canonical text the validator
 * parses. Not the raw delta text.
 */
public final class StatementBoundaryScanner {

  private final StringBuilder rawBuffer = new StringBuilder();

  /** Number of complete statements already surfaced via {@link #onDelta} / {@link #drainAtEnd}. */
  private int emittedCount = 0;

  /** Append {@code delta} and return any statements that became COMPLETE as a result. */
  public List<String> onDelta(String delta) {
    if (delta != null && !delta.isEmpty()) {
      rawBuffer.append(delta);
    }
    return newlyComplete(false);
  }

  /**
   * Signal end-of-stream: every remaining split statement (including the last, previously-pending
   * tail) is now definitively complete. Returns the statements not yet surfaced.
   */
  public List<String> drainAtEnd() {
    return newlyComplete(true);
  }

  /** The full raw (un-preprocessed) buffer accumulated so far. */
  public String rawBuffer() {
    return rawBuffer.toString();
  }

  private List<String> newlyComplete(boolean atEnd) {
    // Mid-stream: preprocess WITHOUT auto-close so an unclosed tail stays a single (still-pending)
    // statement and is not surfaced early. At end-of-stream: auto-close (STREAMING mode) so the
    // final pending tail is repaired into a well-formed, FINAL-validatable statement.
    String cleaned =
        atEnd
            ? OpenuiPreprocessor.process(rawBuffer.toString(), ParseMode.STREAMING).text()
            : OpenuiPreprocessor.preprocess(rawBuffer.toString());
    if (cleaned.isBlank()) {
      return List.of();
    }
    List<Token> tokens = OpenuiLexer.tokenize(cleaned);
    List<StatementSplitter.RawStmt> stmts = StatementSplitter.split(tokens);

    // All-but-last are complete mid-stream; at end, the last is complete too.
    int completeCount = atEnd ? stmts.size() : Math.max(0, stmts.size() - 1);
    if (completeCount <= emittedCount) {
      return List.of();
    }

    List<String> result = new ArrayList<>();
    for (int i = emittedCount; i < completeCount; i++) {
      String text = sliceOf(cleaned, stmts.get(i));
      if (!text.isBlank()) {
        result.add(text);
      }
    }
    emittedCount = completeCount;
    return result;
  }

  /** Slice the preprocessed text for a statement's span, falling back to the whole statement. */
  private static String sliceOf(String cleaned, StatementSplitter.RawStmt stmt) {
    int start = stmt.span().startOffset();
    int end = stmt.span().endOffset();
    if (start >= 0 && end >= 0 && start <= end && end <= cleaned.length()) {
      return cleaned.substring(start, end).trim();
    }
    return cleaned.trim();
  }
}
