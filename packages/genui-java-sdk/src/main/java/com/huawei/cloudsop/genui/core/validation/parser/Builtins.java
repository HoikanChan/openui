package com.huawei.cloudsop.genui.core.validation.parser;

import java.util.Set;

/**
 * Builtin/reserved call classification for the parser.
 *
 * <p>Mirrors the name sets in {@code packages/lang-core/src/parser/builtins.ts} that the expression
 * parser consults ({@code isBuiltin}, action names, reserved calls). Only the parser-relevant subset
 * is ported — runtime {@code fn} implementations and prompt docs are out of scope.
 */
public final class Builtins {

  private Builtins() {}

  /** Data builtins ({@code BUILTINS} keys in builtins.ts). */
  private static final Set<String> DATA_BUILTINS =
      Set.of(
          "Count",
          "First",
          "Last",
          "Sum",
          "Avg",
          "Min",
          "Max",
          "Sort",
          "Filter",
          "ObjectEntries",
          "ObjectKeys",
          "Round",
          "Abs",
          "Floor",
          "Ceil",
          "Switch",
          "FormatDate",
          "FormatBytes",
          "FormatNumber",
          "FormatPercent",
          "FormatDuration");

  /** Lazy/template builtins ({@code LAZY_BUILTINS}). */
  private static final Set<String> LAZY_BUILTINS = Set.of("Each", "Render");

  /** Action step names ({@code ACTION_STEPS} keys). */
  private static final Set<String> ACTION_STEPS =
      Set.of("Run", "ToAssistant", "OpenUrl", "Set", "Reset");

  /** All action expression names — steps plus the {@code Action} container. */
  private static final Set<String> ACTION_NAMES = union(ACTION_STEPS, Set.of("Action"));

  /** Full builtin name set ({@code BUILTIN_NAMES}). */
  private static final Set<String> BUILTIN_NAMES =
      union(union(DATA_BUILTINS, LAZY_BUILTINS), ACTION_NAMES);

  /** Reserved statement-level call names ({@code RESERVED_CALLS}). */
  public static final String QUERY = "Query";

  public static final String MUTATION = "Mutation";

  private static final Set<String> RESERVED_CALLS = Set.of(QUERY, MUTATION);

  /** {@code true} if {@code name} is a builtin function (not a component). */
  public static boolean isBuiltin(String name) {
    return BUILTIN_NAMES.contains(name);
  }

  /** {@code true} if {@code name} is a reserved statement call ({@code Query}/{@code Mutation}). */
  public static boolean isReservedCall(String name) {
    return RESERVED_CALLS.contains(name);
  }

  /** Data and lazy/template builtin names covered by static expression typing. */
  public static Set<String> typeCheckedNames() {
    return union(DATA_BUILTINS, LAZY_BUILTINS);
  }

  private static Set<String> union(Set<String> a, Set<String> b) {
    var s = new java.util.HashSet<>(a);
    s.addAll(b);
    return Set.copyOf(s);
  }
}
