package com.huawei.cloudsop.genui.core.validation.parser;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Top-level orchestrator: preprocess → tokenize → split → parse → classify → {@link Program}.
 *
 * <p>Mirrors the pipeline in {@code packages/lang-core/src/parser/parser.ts} up to the syntactic
 * layer (statement classification), stopping before schema materialization (Section 3+ handles the
 * semantic layer). Statement classification follows {@code classifyStatement}: {@code Query(...)} /
 * {@code Mutation(...)} are detected BEFORE {@code $var} state, then {@code $var} → state, else
 * value.
 *
 * <p>Syntax problems from every stage are collected as {@link ParseDiagnostic}s on the {@link
 * Program}; nothing is thrown. A malformed statement degrades to a diagnostic and is skipped, so
 * later statements still parse.
 */
public final class OpenuiParser {

  private OpenuiParser() {}

  /** Parse complete (non-streaming) input. Unclosed constructs surface as diagnostics. */
  public static Program parse(String input) {
    return parse(input, ParseMode.FINAL);
  }

  /** Parse input in the given {@link ParseMode}. */
  public static Program parse(String input, ParseMode mode) {
    OpenuiPreprocessor.Result pre = OpenuiPreprocessor.process(input, mode);
    List<ParseDiagnostic> diagnostics = new ArrayList<>();
    List<Statement> statements = new ArrayList<>();

    if (pre.text().isBlank()) {
      return new Program(statements, diagnostics, pre.wasIncomplete());
    }

    List<Token> tokens = OpenuiLexer.tokenize(pre.text());
    StatementSplitter.SplitResult split = StatementSplitter.splitDetailed(tokens);

    // Map skipped lines into diagnostics (streaming tolerance — parser never throws).
    for (StatementSplitter.SkippedLine skip : split.skipped()) {
      ParseErrorCode code =
          skip.reason() == StatementSplitter.SkipReason.MISSING_ASSIGNMENT
              ? ParseErrorCode.MISSING_ASSIGNMENT
              : ParseErrorCode.INVALID_STATEMENT;
      String msg =
          skip.reason() == StatementSplitter.SkipReason.MISSING_ASSIGNMENT
              ? "Statement '" + skip.idText() + "' is missing '='"
              : "Line does not start a valid statement";
      diagnostics.add(ParseDiagnostic.at(code, msg, skip.idText(), skip.span()));
    }

    for (StatementSplitter.RawStmt raw : split.statements()) {
      ExpressionParser exprParser = new ExpressionParser(raw.tokens(), raw.id());
      AstNode expr = exprParser.parse();
      diagnostics.addAll(exprParser.diagnostics());
      statements.add(classify(raw, expr));
    }

    return new Program(statements, diagnostics, pre.wasIncomplete());
  }

  /** Classify a raw statement + parsed expression, mirroring {@code classifyStatement}. */
  private static Statement classify(StatementSplitter.RawStmt raw, AstNode expr) {
    // Query(...) → query — checked BEFORE $var so `$foo = Query(...)` is a query.
    if (expr instanceof AstNode.Comp comp && comp.name().equals(Builtins.QUERY)) {
      List<String> deps =
          comp.args().size() > 1 ? collectStateDeps(comp.args().get(1)) : List.of();
      return new Statement.Query(
          raw.id(), new Statement.CallNode(Builtins.QUERY, comp.args()), expr, deps, raw.span());
    }
    // Mutation(...) → mutation.
    if (expr instanceof AstNode.Comp comp && comp.name().equals(Builtins.MUTATION)) {
      return new Statement.Mutation(
          raw.id(), new Statement.CallNode(Builtins.MUTATION, comp.args()), expr, raw.span());
    }
    // $var → state declaration.
    if (raw.idTokenType() == TokenType.STATE_VAR) {
      return new Statement.State(raw.id(), expr, raw.span());
    }
    // Everything else → value declaration.
    return new Statement.Value(raw.id(), expr, raw.span());
  }

  /** Collect distinct {@code $state} references reachable from a node (query dep pre-computation). */
  static List<String> collectStateDeps(AstNode node) {
    Set<String> refs = new LinkedHashSet<>();
    walk(node, refs);
    return List.copyOf(refs);
  }

  private static void walk(AstNode node, Set<String> refs) {
    if (node == null) {
      return;
    }
    switch (node) {
      case AstNode.StateRef s -> refs.add(s.n());
      case AstNode.Comp c -> {
        c.args().forEach(a -> walk(a, refs));
        c.mappedProps().values().forEach(v -> walk(v, refs));
      }
      case AstNode.Arr a -> a.els().forEach(e -> walk(e, refs));
      case AstNode.Obj o -> o.entries().forEach(e -> walk(e.value(), refs));
      case AstNode.BinOp b -> {
        walk(b.left(), refs);
        walk(b.right(), refs);
      }
      case AstNode.UnaryOp u -> walk(u.operand(), refs);
      case AstNode.Ternary t -> {
        walk(t.cond(), refs);
        walk(t.then(), refs);
        walk(t.otherwise(), refs);
      }
      case AstNode.Member m -> walk(m.obj(), refs);
      case AstNode.Index ix -> {
        walk(ix.obj(), refs);
        walk(ix.index(), refs);
      }
      case AstNode.Assign as -> walk(as.value(), refs);
      default -> {
        // leaf node — nothing to walk
      }
    }
  }
}
