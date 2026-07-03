package com.huawei.cloudsop.genui.core.validation.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Hint routing for unresolved references (design Decision 11.2). Hint text and the root-cause
 * predicate MUST stay in the same class: {@code ReaskPromptBuilder} exempts root-cause-hinted
 * issues from cascade suppression by this predicate, so a wording change that breaks the
 * predicate silently re-enables suppression of the very hints that explain the failure.
 */
class RepairHintsTest {

  // ── tier 1: builtin name missing '@' (exact, case-sensitive match) ─────────

  @Test
  void builtinNameMissingAtGetsDidYouMean() {
    assertEquals(
        "did you mean \"@FormatNumber\"? openui-lang builtins must be called with a leading '@'",
        RepairHints.unresolvedRefHint("FormatNumber"));
  }

  @Test
  void builtinMatchIsCaseSensitive() {
    // "formatnumber" is not an exact builtin name → falls through to the default hint.
    assertEquals(
        "define a statement named \"formatnumber\" earlier in the document",
        RepairHints.unresolvedRefHint("formatnumber"));
  }

  @Test
  void builtinMatchWinsOverJsGlobal() {
    // "Set" is both an openui-lang action builtin and a JS global; the builtin hint is
    // actionable ("@Set") so it takes precedence.
    assertEquals(
        "did you mean \"@Set\"? openui-lang builtins must be called with a leading '@'",
        RepairHints.unresolvedRefHint("Set"));
  }

  // ── tier 2: JS globals / keywords not in the openui-lang subset ────────────

  @Test
  void jsGlobalGetsRootCauseHint() {
    assertEquals(
        "\"Math\" is a JavaScript global — JS globals and methods are not available in"
            + " openui-lang; use builtins like @Abs, @Round, @FormatNumber, @FormatDate instead",
        RepairHints.unresolvedRefHint("Math"));
  }

  @Test
  void jsKeywordNewGetsRootCauseHint() {
    // The parser surfaces keywords like `new` (from `new Date()`) as plain refs.
    assertTrue(RepairHints.unresolvedRefHint("new").contains("JavaScript global"));
  }

  // ── tier 3: default — no misleading external-ref advice ────────────────────

  @Test
  void defaultHintNeverSuggestsExternalRef() {
    String hint = RepairHints.unresolvedRefHint("kpiValue");
    assertEquals("define a statement named \"kpiValue\" earlier in the document", hint);
    assertFalse(hint.contains("external ref"));
  }

  // ── root-cause predicate (cascade-suppression exemption) ───────────────────

  @Test
  void rootCausePredicateMatchesTier1And2Only() {
    assertTrue(RepairHints.isRootCauseHint(RepairHints.unresolvedRefHint("FormatNumber")));
    assertTrue(RepairHints.isRootCauseHint(RepairHints.unresolvedRefHint("Math")));
    assertFalse(RepairHints.isRootCauseHint(RepairHints.unresolvedRefHint("kpiValue")));
    assertFalse(RepairHints.isRootCauseHint(null));
    assertFalse(RepairHints.isRootCauseHint("may resolve as more of the stream arrives"));
  }
}
