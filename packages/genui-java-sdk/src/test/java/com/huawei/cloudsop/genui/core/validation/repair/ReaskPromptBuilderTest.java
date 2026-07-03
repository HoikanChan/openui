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
