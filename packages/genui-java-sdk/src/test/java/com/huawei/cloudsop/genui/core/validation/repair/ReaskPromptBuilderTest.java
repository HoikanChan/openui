package com.huawei.cloudsop.genui.core.validation.repair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.contract.ComponentPromptSpec;
import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import com.huawei.cloudsop.genui.core.llm.protocol.ChatMessage;
import com.huawei.cloudsop.genui.core.validation.ValidationIssue;
import com.huawei.cloudsop.genui.core.validation.ValidationSeverity;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Pure/deterministic assertions on {@link ReaskPromptBuilder} output. */
class ReaskPromptBuilderTest {

  private static GenerationContract contractWith(String name, String signature) {
    return new GenerationContract(
        "1.0",
        "Stack",
        Map.of(name, new ComponentPromptSpec(signature, "desc")),
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }

  private static ValidationIssue issue(String code, String message, String component, String hint) {
    return new ValidationIssue(
        code, ValidationSeverity.ERROR, "contract", message, null, component, "root.children[0]",
        3, 5, hint, false);
  }

  private static String join(List<ChatMessage> messages) {
    StringBuilder sb = new StringBuilder();
    for (ChatMessage m : messages) sb.append(m.role()).append(':').append(m.content()).append('\n');
    return sb.toString();
  }

  @Test
  void fullRepair_containsIssueCodeHintPrefixAndSignature() {
    ValidationIssue issue =
        issue("unknown-component", "Component 'Bogus' is not defined", "Bogus", "did you mean Card?");
    GenerationContract contract = contractWith("Bogus", "Bogus(title: string)");

    List<ChatMessage> messages =
        ReaskPromptBuilder.buildFullRepair(
            "show a bogus widget", "root = Bogus(\"x\")", List.of(issue), contract);
    String text = join(messages);

    assertTrue(text.contains(ReaskPromptBuilder.FULL_REPAIR_MARKER), "full-repair marker present");
    assertTrue(text.contains("unknown-component"), "issue code present");
    assertTrue(text.contains("Component 'Bogus' is not defined"), "issue message present");
    assertTrue(text.contains("did you mean Card?"), "issue hint present");
    assertTrue(text.contains("root.children[0]"), "issue path present");
    assertTrue(text.contains("root = Bogus(\"x\")"), "the invalid (accepted-context) DSL present");
    assertTrue(text.contains("Bogus(title: string)"), "component signature hint present");
    assertTrue(text.contains("show a bogus widget"), "original user intent present");
  }

  @Test
  void repairAndContinue_containsAcceptedPrefixInvalidStatementIssuesAndSignature() {
    ValidationIssue issue =
        issue("invalid-prop", "Unknown prop 'foo' on CardHeader", "CardHeader", "use title instead");
    GenerationContract contract = contractWith("CardHeader", "CardHeader(title?: string)");

    List<ChatMessage> messages =
        ReaskPromptBuilder.buildRepairAndContinue(
            "build a card",
            "root = Stack([a])\na = TextContent(\"hi\")",
            "b = CardHeader(foo: 1)",
            List.of(issue),
            contract);
    String text = join(messages);

    assertTrue(text.contains(ReaskPromptBuilder.CONTINUE_REPAIR_MARKER), "continue marker present");
    assertTrue(text.contains("root = Stack([a])"), "accepted prefix present");
    assertTrue(text.contains("a = TextContent(\"hi\")"), "full accepted prefix present");
    assertTrue(text.contains("b = CardHeader(foo: 1)"), "invalid next statement present");
    assertTrue(text.contains("invalid-prop"), "issue code present");
    assertTrue(text.contains("use title instead"), "issue hint present");
    assertTrue(text.contains("CardHeader(title?: string)"), "signature hint present");
    assertTrue(text.contains("build a card"), "original user intent present");
  }

  @Test
  void twoMessages_systemThenUser() {
    List<ChatMessage> messages =
        ReaskPromptBuilder.buildFullRepair("x", "root = Stack([])", List.of(), null);
    assertEquals(2, messages.size());
    assertEquals("system", messages.get(0).role());
    assertEquals("user", messages.get(1).role());
  }

  @Test
  void issueLineCarriesStatementIdAndPosition() {
    // Decision 11.3: multi-statement documents need per-issue location context.
    ValidationIssue located =
        new ValidationIssue(
            "syntax-unexpected-token", ValidationSeverity.ERROR, "syntax",
            "Unexpected token R_PAREN", "kpiValue", null, null, 3, 51, null, false);
    String text =
        join(ReaskPromptBuilder.buildFullRepair("x", "root = Stack([])", List.of(located), null));
    assertTrue(text.contains("(stmt=kpiValue, line 3:51)"), "stmt and line:col rendered");
  }

  @Test
  void issueLineOmitsUnknownLocationParts() {
    ValidationIssue stmtOnly =
        new ValidationIssue(
            "unresolved-ref", ValidationSeverity.ERROR, "reference",
            "unresolved reference \"x\"", "kpi", null, null, -1, -1, null, false);
    ValidationIssue neither =
        new ValidationIssue(
            "root-missing", ValidationSeverity.ERROR, "root",
            "no root statement", null, null, null, -1, -1, null, false);
    String text =
        join(
            ReaskPromptBuilder.buildFullRepair(
                "x", "root = Stack([])", List.of(stmtOnly, neither), null));
    assertTrue(text.contains("(stmt=kpi)"), "stmt-only location rendered without line part");
    assertFalse(text.contains("-1"), "unknown line/column never rendered");
  }

  // ── cascade suppression (Decision 11.4) ─────────────────────────────────────

  private static ValidationIssue syntaxError(String stmtId) {
    return new ValidationIssue(
        "syntax-unexpected-token", ValidationSeverity.ERROR, "syntax",
        "Unexpected token R_PAREN", stmtId, null, null, 3, 51, null, false);
  }

  private static ValidationIssue derived(String code, String message, String stmtId, String hint) {
    return new ValidationIssue(
        code, ValidationSeverity.ERROR,
        code.equals("unresolved-ref") ? "reference" : "contract",
        message, stmtId, null, null, -1, -1, hint, false);
  }

  @Test
  void derivedIssuesOfSyntaxBrokenStatementAreSuppressed() {
    // Parse tore one expression into 3 args → fake excess-args + fake null-required.
    // Only the syntax root cause should reach the repair model.
    List<ValidationIssue> issues =
        List.of(
            syntaxError("kpiValue"),
            derived("excess-args", "TextContent takes 2 arg(s), got 3 (1 excess dropped)", "kpiValue", null),
            derived("null-required", "required field \"text\" cannot be null", "kpiValue", null),
            derived("unresolved-ref", "unresolved reference \"d\"", "kpiValue",
                "define a statement named \"d\" earlier in the document"));
    String text =
        join(ReaskPromptBuilder.buildFullRepair("x", "root = Stack([])", issues, null));
    assertTrue(text.contains("syntax-unexpected-token"), "syntax root cause kept");
    assertFalse(text.contains("excess-args"), "derived excess-args suppressed");
    assertFalse(text.contains("null-required"), "derived null-required suppressed");
    assertFalse(text.contains("unresolved reference \"d\""), "derived unresolved-ref suppressed");
  }

  @Test
  void suppressedIssueDoesNotAddComponentSignatureNoise() {
    ValidationIssue derivedComponentIssue =
        new ValidationIssue(
            "type-prop-mismatch", ValidationSeverity.ERROR, "type", "derived component mismatch",
            "kpiValue", "Card", "/gap", 3, 5, null, false);
    String text =
        join(
            ReaskPromptBuilder.buildFullRepair(
                "x",
                "root = Stack([])",
                List.of(syntaxError("kpiValue"), derivedComponentIssue),
                contractWith("Card", "Card(children: Component[])")));

    assertFalse(text.contains("derived component mismatch"));
    assertFalse(text.contains("Card(children: Component[])"));
  }

  @Test
  void rootCauseHintedUnresolvedRefSurvivesSuppression() {
    // `Math.abs(v).toFixed(1)` → same statement carries a syntax ERROR and an unresolved-ref
    // whose hint IS the root-cause explanation (JS global → @Abs). It must not be suppressed.
    List<ValidationIssue> issues =
        List.of(
            syntaxError("kpiValue"),
            derived("unresolved-ref", "unresolved reference \"Math\"", "kpiValue",
                com.huawei.cloudsop.genui.core.validation.semantic.RepairHints.unresolvedRefHint("Math")));
    String text =
        join(ReaskPromptBuilder.buildFullRepair("x", "root = Stack([])", issues, null));
    assertTrue(text.contains("unresolved reference \"Math\""), "root-cause-hinted ref kept");
    assertTrue(text.contains("JavaScript global"), "root-cause hint text kept");
  }

  @Test
  void derivedIssuesOfHealthyStatementsAreKept() {
    // Suppression is per-statement: a real excess-args on a statement WITHOUT syntax errors stays.
    List<ValidationIssue> issues =
        List.of(
            syntaxError("kpiValue"),
            derived("excess-args", "Header takes 2 arg(s), got 3", "header", null));
    String text =
        join(ReaskPromptBuilder.buildFullRepair("x", "root = Stack([])", issues, null));
    assertTrue(text.contains("excess-args"), "independent excess-args kept");
  }

  @Test
  void rootNotRenderableSuppressedWhenAnotherErrorExplainsIt() {
    List<ValidationIssue> issues =
        List.of(
            derived("unknown-component", "Unknown component \"Div\"", "root", null),
            derived("root-not-renderable", "root did not materialize", "root", null));
    String text =
        join(ReaskPromptBuilder.buildFullRepair("x", "root = Div()", issues, null));
    assertTrue(text.contains("unknown-component"), "explaining error kept");
    assertFalse(text.contains("root-not-renderable"), "cascade tail suppressed");
  }

  @Test
  void rootNotRenderableKeptWhenSoleError() {
    List<ValidationIssue> issues =
        List.of(derived("root-not-renderable", "root did not materialize", "root", null));
    String text =
        join(ReaskPromptBuilder.buildFullRepair("x", "root = $count", issues, null));
    assertTrue(text.contains("root-not-renderable"), "sole structural error kept");
  }

  @Test
  void nonErrorIssuesAreNotRendered() {
    ValidationIssue warning =
        new ValidationIssue(
            "soft-warn", ValidationSeverity.WARNING, "x", "just a warning", null, null, null, -1,
            -1, null, true);
    List<ChatMessage> messages =
        ReaskPromptBuilder.buildFullRepair("x", "root = Stack([])", List.of(warning), null);
    String text = join(messages);
    assertFalse(text.contains("soft-warn"), "non-ERROR issues must not drive repair");
  }
}
