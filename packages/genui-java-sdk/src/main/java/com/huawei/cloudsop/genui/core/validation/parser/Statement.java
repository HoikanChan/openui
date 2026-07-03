package com.huawei.cloudsop.genui.core.validation.parser;

import java.util.List;

/**
 * A typed top-level statement of the form {@code id = expression}.
 *
 * <p>Mirrors the TS {@code Statement} union in {@code ast.ts} (kinds value/state/query/mutation).
 * Each variant additionally carries a {@link SourceSpan} (line/column/offset span) — a Java
 * extension for diagnostics that the TS side does not track.
 */
public sealed interface Statement
    permits Statement.Value, Statement.State, Statement.Query, Statement.Mutation {

  /** Statement identifier (LHS). For state statements this includes the leading {@code $}. */
  String id();

  /** Source location of the whole statement. */
  SourceSpan span();

  /** A tool/query call shape extracted from a {@code Comp} node. */
  record CallNode(String callee, List<AstNode> args) {
    public CallNode {
      args = args == null ? List.of() : List.copyOf(args);
    }
  }

  /** {@code id = expr} — a plain value declaration (possibly a component). */
  record Value(String id, AstNode expr, SourceSpan span) implements Statement {}

  /** {@code $id = init} — a reactive state declaration. */
  record State(String id, AstNode init, SourceSpan span) implements Statement {}

  /** {@code id = Query(...)} — a query declaration. */
  record Query(String id, CallNode call, AstNode expr, List<String> deps, SourceSpan span)
      implements Statement {
    public Query {
      deps = deps == null ? List.of() : List.copyOf(deps);
    }
  }

  /** {@code id = Mutation(...)} — a mutation declaration. */
  record Mutation(String id, CallNode call, AstNode expr, SourceSpan span) implements Statement {}
}
