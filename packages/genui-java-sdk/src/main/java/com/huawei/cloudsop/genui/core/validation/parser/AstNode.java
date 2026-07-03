package com.huawei.cloudsop.genui.core.validation.parser;

import java.util.List;
import java.util.Map;

/**
 * Every value that can appear in an openui-lang expression.
 *
 * <p>Sealed interface mirroring the TS discriminated union {@code ASTNode} in {@code
 * packages/lang-core/src/parser/ast.ts}. Each variant is a record. The parser produces these nodes;
 * evaluation/materialization is out of scope (Section 3+ consumes the tree).
 */
public sealed interface AstNode
    permits AstNode.Comp,
        AstNode.Str,
        AstNode.Num,
        AstNode.Bool,
        AstNode.Null,
        AstNode.Arr,
        AstNode.Obj,
        AstNode.Ref,
        AstNode.Ph,
        AstNode.StateRef,
        AstNode.RuntimeRef,
        AstNode.BinOp,
        AstNode.UnaryOp,
        AstNode.Ternary,
        AstNode.Member,
        AstNode.Index,
        AstNode.Assign {

  /** Runtime reference kinds resolved outside the parser. */
  enum RefType {
    QUERY,
    MUTATION,
    DATA
  }

  /**
   * A component call: {@code Header("Hello", "Subtitle")}. {@code mappedProps} is populated by
   * later positional-to-named mapping (out of parser scope) and is empty from the parser.
   */
  record Comp(String name, List<AstNode> args, Map<String, AstNode> mappedProps)
      implements AstNode {
    public Comp {
      args = args == null ? List.of() : List.copyOf(args);
      mappedProps = mappedProps == null ? Map.of() : Map.copyOf(mappedProps);
    }

    /** Convenience constructor with no mapped props (the parser's normal output). */
    public Comp(String name, List<AstNode> args) {
      this(name, args, Map.of());
    }
  }

  /** A string literal: {@code "hello"}. */
  record Str(String v) implements AstNode {}

  /** A number literal: {@code 42} or {@code 3.14}. */
  record Num(double v) implements AstNode {}

  /** A boolean literal: {@code true} / {@code false}. */
  record Bool(boolean v) implements AstNode {}

  /** The {@code null} literal. */
  record Null() implements AstNode {}

  /** An array literal: {@code [a, b, c]}. */
  record Arr(List<AstNode> els) implements AstNode {
    public Arr {
      els = els == null ? List.of() : List.copyOf(els);
    }
  }

  /** An object literal: {@code { key: value }}. Entries preserve insertion order. */
  record Obj(List<Entry> entries) implements AstNode {
    public Obj {
      entries = entries == null ? List.of() : List.copyOf(entries);
    }

    /** A single {@code key: value} pair in an object literal. */
    public record Entry(String key, AstNode value) {}
  }

  /** A reference to another statement: {@code myTable}. */
  record Ref(String n) implements AstNode {}

  /** A placeholder for an unresolvable reference. */
  record Ph(String n) implements AstNode {}

  /** A reactive state variable reference: {@code $count} ({@code n} includes the leading $). */
  record StateRef(String n) implements AstNode {}

  /** A reference resolved at runtime (Query/Mutation/data results). */
  record RuntimeRef(String n, RefType refType) implements AstNode {}

  /** A binary operation: {@code a + b}, {@code x == y}. */
  record BinOp(String op, AstNode left, AstNode right) implements AstNode {}

  /** A unary operation: {@code !flag}, {@code -x}. */
  record UnaryOp(String op, AstNode operand) implements AstNode {}

  /** A conditional expression: {@code cond ? then : otherwise}. */
  record Ternary(AstNode cond, AstNode then, AstNode otherwise) implements AstNode {}

  /** Dot member access: {@code obj.field}. */
  record Member(AstNode obj, String field) implements AstNode {}

  /** Bracket index access: {@code arr[0]}. */
  record Index(AstNode obj, AstNode index) implements AstNode {}

  /** State assignment: {@code $count = $count + 1} ({@code target} includes the leading $). */
  record Assign(String target, AstNode value) implements AstNode {}
}
