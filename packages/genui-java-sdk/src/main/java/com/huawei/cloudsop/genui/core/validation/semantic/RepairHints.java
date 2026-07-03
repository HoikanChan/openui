package com.huawei.cloudsop.genui.core.validation.semantic;

import com.huawei.cloudsop.genui.core.validation.parser.Builtins;
import java.util.Set;

/**
 * Repair-oriented hint routing for unresolved references (design Decision 11.2).
 *
 * <p>Enrichment lives in the {@code hint} field ONLY. The issue {@code message} is bound by
 * TypeScript parity: {@code CrossLanguageParityTest} extracts the referenced name from the first
 * and last quote of the message, so quoted additions to the message would corrupt name extraction.
 *
 * <p>Hint wording and the {@link #isRootCauseHint} predicate deliberately live in this single
 * class: {@code ReaskPromptBuilder} uses the predicate to exempt root-cause-hinted issues from
 * cascade suppression. Keeping both here means a wording change that would break the predicate
 * fails {@code RepairHintsTest} instead of silently suppressing the hints that explain a failure.
 */
public final class RepairHints {

  private RepairHints() {}

  /**
   * JavaScript globals and keywords a model commonly leaks into openui-lang. Keywords such as
   * {@code new} appear because the parser surfaces them as plain refs (e.g. from {@code new
   * Date()}). Names that are also openui-lang builtins (e.g. {@code Set}) are matched by the
   * builtin tier first.
   */
  private static final Set<String> JS_GLOBAL_NAMES =
      Set.of(
          "Math",
          "JSON",
          "Date",
          "String",
          "Number",
          "Boolean",
          "Array",
          "Object",
          "RegExp",
          "Map",
          "Set",
          "Promise",
          "Intl",
          "NaN",
          "Infinity",
          "undefined",
          "globalThis",
          "window",
          "document",
          "console",
          "parseInt",
          "parseFloat",
          "isNaN",
          "isFinite",
          "new",
          "typeof",
          "instanceof",
          "function",
          "async",
          "await");

  /** Root-cause prefix of the missing-'@' hint; pinned by {@code RepairHintsTest}. */
  private static final String DID_YOU_MEAN_PREFIX = "did you mean \"@";

  /** Root-cause marker of the JS-global hint; pinned by {@code RepairHintsTest}. */
  private static final String JS_GLOBAL_MARKER = "is a JavaScript global";

  /**
   * Routes an unresolved reference name to a repair hint:
   *
   * <ol>
   *   <li>exact (case-sensitive) builtin name → the model forgot the leading {@code @};
   *   <li>known JS global/keyword → the JS subset does not exist in openui-lang, point at builtins;
   *   <li>otherwise → define the statement (never "pass it as an external ref": external refs are
   *       an SDK-caller API the repair model cannot act on).
   * </ol>
   */
  public static String unresolvedRefHint(String name) {
    if (Builtins.isBuiltin(name)) {
      return DID_YOU_MEAN_PREFIX
          + name
          + "\"? openui-lang builtins must be called with a leading '@'";
    }
    if (JS_GLOBAL_NAMES.contains(name)) {
      return "\""
          + name
          + "\" "
          + JS_GLOBAL_MARKER
          + " — JS globals and methods are not available in openui-lang; use builtins like"
          + " @Abs, @Round, @FormatNumber, @FormatDate instead";
    }
    return "define a statement named \"" + name + "\" earlier in the document";
  }

  /**
   * {@code true} when the hint itself names the root cause (missing-'@' or JS-global tier). Such
   * issues are exempt from cascade suppression in the reask prompt: the statement may also carry a
   * syntax error, but this hint — not the token-level symptom — is the actionable explanation.
   */
  public static boolean isRootCauseHint(String hint) {
    return hint != null && (hint.startsWith(DID_YOU_MEAN_PREFIX) || hint.contains(JS_GLOBAL_MARKER));
  }
}
