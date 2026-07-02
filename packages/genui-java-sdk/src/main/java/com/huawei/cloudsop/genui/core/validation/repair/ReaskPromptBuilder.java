package com.huawei.cloudsop.genui.core.validation.repair;

import com.huawei.cloudsop.genui.core.contract.ComponentPromptSpec;
import com.huawei.cloudsop.genui.core.contract.ComponentPropsSchema;
import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import com.huawei.cloudsop.genui.core.llm.protocol.ChatMessage;
import com.huawei.cloudsop.genui.core.validation.ValidationIssue;
import com.huawei.cloudsop.genui.core.validation.ValidationSeverity;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds deterministic repair chat messages for the two Section 7 repair shapes (design Decision
 * #6). Pure and side-effect free — its output text is asserted directly in unit tests.
 *
 * <ul>
 *   <li>{@link #buildFullRepair} — "here is the COMPLETE DSL you produced; it failed validation with
 *       these machine-readable issues; regenerate a corrected COMPLETE openui-lang".
 *   <li>{@link #buildRepairAndContinue} — "here is the accepted prefix (valid so far); your next
 *       statement was invalid for these reasons; continue producing ONLY the remaining DSL starting
 *       from a corrected version of that statement".
 * </ul>
 *
 * <p>The issue block is built from the structured {@link ValidationIssue} fields (code / message /
 * hint / component / path) — never from raw parser exception text. Component signature hints come
 * from the merged {@link GenerationContract} via {@link ComponentPropsSchema#formatSignature}.
 */
public final class ReaskPromptBuilder {

  private ReaskPromptBuilder() {}

  /** Machine-parsable marker so tests / logs can recognise a repair system message. */
  public static final String FULL_REPAIR_MARKER = "[REPAIR:FULL]";

  public static final String CONTINUE_REPAIR_MARKER = "[REPAIR:CONTINUE]";

  /**
   * FULL repair prompt: the whole invalid DSL + issues → regenerate a corrected complete DSL.
   *
   * @param userIntent the original user message / intent (may be {@code null})
   * @param invalidDsl the complete DSL that failed validation
   * @param issues structured validation issues explaining the failure
   * @param contract merged contract (for signature hints), may be {@code null}
   */
  public static List<ChatMessage> buildFullRepair(
      String userIntent,
      String invalidDsl,
      List<ValidationIssue> issues,
      GenerationContract contract) {
    StringBuilder system = new StringBuilder();
    system.append(FULL_REPAIR_MARKER).append('\n');
    system.append(
        "You previously produced an openui-lang document that FAILED validation. "
            + "Regenerate a CORRECTED, COMPLETE openui-lang document that fixes every issue below. "
            + "Return ONLY the corrected openui-lang inside a single ```openui fenced block. "
            + "Do not explain.\n\n");
    appendIssues(system, issues);
    appendSignatureHints(system, issues, contract);

    StringBuilder user = new StringBuilder();
    if (userIntent != null && !userIntent.isBlank()) {
      user.append("## Original request\n").append(userIntent.trim()).append("\n\n");
    }
    user.append("## DSL that failed validation\n```openui\n")
        .append(invalidDsl == null ? "" : invalidDsl.trim())
        .append("\n```");

    return List.of(ChatMessage.system(system.toString()), ChatMessage.user(user.toString()));
  }

  /**
   * REPAIR-AND-CONTINUE prompt: accepted prefix is valid; the next statement was invalid; continue
   * producing only the remaining DSL starting from a corrected version of that statement.
   *
   * @param userIntent original user message / intent (may be {@code null})
   * @param acceptedPrefix the already-accepted, valid DSL so far (may be empty)
   * @param invalidStatement the single statement that failed validation
   * @param issues structured issues for the invalid statement
   * @param contract merged contract (for signature hints), may be {@code null}
   */
  public static List<ChatMessage> buildRepairAndContinue(
      String userIntent,
      String acceptedPrefix,
      String invalidStatement,
      List<ValidationIssue> issues,
      GenerationContract contract) {
    StringBuilder system = new StringBuilder();
    system.append(CONTINUE_REPAIR_MARKER).append('\n');
    system.append(
        "You are continuing an openui-lang document. Everything in \"Accepted so far\" is ALREADY "
            + "valid and MUST NOT be repeated. Your previous NEXT statement was invalid. Produce a "
            + "CORRECTED version of that statement and then the REST of the document. Emit ONLY the "
            + "remaining openui-lang (do not re-emit the accepted prefix). Return the continuation "
            + "inside a single ```openui fenced block. Do not explain.\n\n");
    appendIssues(system, issues);
    appendSignatureHints(system, issues, contract);

    StringBuilder user = new StringBuilder();
    if (userIntent != null && !userIntent.isBlank()) {
      user.append("## Original request\n").append(userIntent.trim()).append("\n\n");
    }
    user.append("## Accepted so far (valid — do not repeat)\n```openui\n")
        .append(acceptedPrefix == null ? "" : acceptedPrefix.trim())
        .append("\n```\n\n");
    user.append("## Invalid next statement (fix and continue from here)\n```openui\n")
        .append(invalidStatement == null ? "" : invalidStatement.trim())
        .append("\n```");

    return List.of(ChatMessage.system(system.toString()), ChatMessage.user(user.toString()));
  }

  // ── shared rendering ────────────────────────────────────────────────────────

  private static void appendIssues(StringBuilder sb, List<ValidationIssue> issues) {
    sb.append("## Validation issues to fix\n");
    if (issues == null || issues.isEmpty()) {
      sb.append("- (no structured issues supplied)\n");
      return;
    }
    for (ValidationIssue issue : issues) {
      if (issue == null || issue.severity() != ValidationSeverity.ERROR) {
        continue; // machine-readable: only blocking issues drive the repair
      }
      sb.append("- [").append(issue.code()).append("] ").append(issue.message());
      if (issue.component() != null && !issue.component().isBlank()) {
        sb.append(" (component: ").append(issue.component()).append(')');
      }
      if (issue.path() != null && !issue.path().isBlank()) {
        sb.append(" (at ").append(issue.path()).append(')');
      }
      if (issue.hint() != null && !issue.hint().isBlank()) {
        sb.append("\n  hint: ").append(issue.hint());
      }
      sb.append('\n');
    }
  }

  /**
   * Emit signature hints for every component named by an issue (so the model sees the exact allowed
   * shape of the thing it got wrong). Deterministic order: issue order, de-duplicated.
   */
  private static void appendSignatureHints(
      StringBuilder sb, List<ValidationIssue> issues, GenerationContract contract) {
    if (contract == null || issues == null) {
      return;
    }
    Map<String, ComponentPromptSpec> components = contract.components();
    if (components == null || components.isEmpty()) {
      return;
    }
    Set<String> named = new LinkedHashSet<>();
    for (ValidationIssue issue : issues) {
      if (issue != null && issue.component() != null && !issue.component().isBlank()) {
        named.add(issue.component());
      }
    }
    List<String> lines = new ArrayList<>();
    for (String name : named) {
      ComponentPromptSpec spec = components.get(name);
      if (spec != null) {
        lines.add("- " + ComponentPropsSchema.formatSignature(name, spec));
      }
    }
    if (lines.isEmpty()) {
      return;
    }
    sb.append("\n## Component signatures\n");
    for (String line : lines) {
      sb.append(line).append('\n');
    }
  }
}
