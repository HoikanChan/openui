package com.huawei.cloudsop.genui.core.validation.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Direct coverage of the incremental boundary detection (§6.1). Verifies that statements only
 * surface once definitively terminated, that multi-line / array / ternary / string-with-newline
 * constructs stay a single statement, and that a pending tail is withheld until {@link
 * StatementBoundaryScanner#drainAtEnd()}.
 */
class StatementBoundaryScannerTest {

  private static List<String> feed(String... deltas) {
    StatementBoundaryScanner scanner = new StatementBoundaryScanner();
    List<String> all = new ArrayList<>();
    for (String d : deltas) {
      all.addAll(scanner.onDelta(d));
    }
    all.addAll(scanner.drainAtEnd());
    return all;
  }

  @Test
  void singleStatementSurfacesOnlyAtEnd() {
    StatementBoundaryScanner scanner = new StatementBoundaryScanner();
    // Mid-stream a lone statement is the pending tail → nothing complete yet.
    assertTrue(scanner.onDelta("root = Stack([])").isEmpty());
    List<String> end = scanner.drainAtEnd();
    assertEquals(1, end.size());
    assertTrue(end.get(0).contains("Stack"));
  }

  @Test
  void firstStatementCompletesWhenSecondStarts() {
    StatementBoundaryScanner scanner = new StatementBoundaryScanner();
    List<String> afterFirstLine = scanner.onDelta("a = Header(\"A\")\n");
    // The newline alone does not complete `a` — a following statement must begin.
    assertTrue(afterFirstLine.isEmpty());
    List<String> afterSecond = scanner.onDelta("root = Stack([a])\n");
    assertEquals(1, afterSecond.size(), "first statement completes once the second begins");
    assertTrue(afterSecond.get(0).contains("Header"));
  }

  @Test
  void multiLineArrayStaysOneStatement() {
    List<String> stmts = feed("root = Stack([\n", "  Header(\"A\"),\n", "  Text(\"B\")\n", "])\n");
    assertEquals(1, stmts.size());
    assertTrue(stmts.get(0).contains("Header"));
    assertTrue(stmts.get(0).contains("Text"));
  }

  @Test
  void stringWithNewlineDoesNotSplit() {
    List<String> stmts = feed("root = Header(\"line1\\nline2\")\n");
    assertEquals(1, stmts.size());
    assertTrue(stmts.get(0).contains("line1"));
  }

  @Test
  void ternaryAcrossLinesStaysOneStatement() {
    List<String> stmts = feed("x = true\n", "  ? Header(\"y\")\n", "  : Header(\"n\")\n");
    assertEquals(1, stmts.size());
    assertTrue(stmts.get(0).contains("y") && stmts.get(0).contains("n"));
  }

  @Test
  void unclosedTailIsAutoClosedAtEnd() {
    List<String> stmts = feed("root = Stack([Header(\"A\")");
    assertEquals(1, stmts.size());
    assertTrue(stmts.get(0).contains("Header"));
    // Auto-close repaired the brackets so the drained statement is well-formed.
    assertTrue(stmts.get(0).endsWith(")]") || stmts.get(0).endsWith(")])"));
  }
}
