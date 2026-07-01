package com.huawei.cloudsop.genui.core.validation.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ExpressionParserTest {

  private static AstNode parse(String src) {
    List<Token> toks = OpenuiLexer.tokenize(src);
    return new ExpressionParser(toks).parse();
  }

  @Test
  void stringLiteral() {
    assertEquals(new AstNode.Str("hi"), parse("\"hi\""));
  }

  @Test
  void numberLiteral() {
    assertEquals(new AstNode.Num(42d), parse("42"));
  }

  @Test
  void booleanAndNull() {
    assertEquals(new AstNode.Bool(true), parse("true"));
    assertEquals(new AstNode.Null(), parse("null"));
  }

  @Test
  void lowercaseIdentIsRef() {
    assertEquals(new AstNode.Ref("rows"), parse("rows"));
  }

  @Test
  void stateVarIsStateRef() {
    assertEquals(new AstNode.StateRef("$count"), parse("$count"));
  }

  @Test
  void stateVarAssignment() {
    AstNode n = parse("$count = 1");
    AstNode.Assign a = assertInstanceOf(AstNode.Assign.class, n);
    assertEquals("$count", a.target());
    assertEquals(new AstNode.Num(1d), a.value());
  }

  @Test
  void multiplicationBindsTighterThanAddition() {
    // a + b * c  =>  (a + (b * c))
    AstNode.BinOp top = assertInstanceOf(AstNode.BinOp.class, parse("a + b * c"));
    assertEquals("+", top.op());
    AstNode.BinOp right = assertInstanceOf(AstNode.BinOp.class, top.right());
    assertEquals("*", right.op());
  }

  @Test
  void comparisonAndEqualityPrecedence() {
    // a == b > c  => (a == (b > c))
    AstNode.BinOp top = assertInstanceOf(AstNode.BinOp.class, parse("a == b > c"));
    assertEquals("==", top.op());
    assertInstanceOf(AstNode.BinOp.class, top.right());
    assertEquals(">", ((AstNode.BinOp) top.right()).op());
  }

  @Test
  void logicalAndBindsTighterThanOr() {
    // a || b && c => (a || (b && c))
    AstNode.BinOp top = assertInstanceOf(AstNode.BinOp.class, parse("a || b && c"));
    assertEquals("||", top.op());
    assertEquals("&&", ((AstNode.BinOp) top.right()).op());
  }

  @Test
  void nullCoalescing() {
    AstNode.BinOp top = assertInstanceOf(AstNode.BinOp.class, parse("a ?? b"));
    assertEquals("??", top.op());
  }

  @Test
  void unaryNot() {
    AstNode.UnaryOp u = assertInstanceOf(AstNode.UnaryOp.class, parse("!x"));
    assertEquals("!", u.op());
    assertEquals(new AstNode.Ref("x"), u.operand());
  }

  @Test
  void ternary() {
    AstNode.Ternary t = assertInstanceOf(AstNode.Ternary.class, parse("c ? a : b"));
    assertEquals(new AstNode.Ref("c"), t.cond());
    assertEquals(new AstNode.Ref("a"), t.then());
    assertEquals(new AstNode.Ref("b"), t.otherwise());
  }

  @Test
  void memberAccess() {
    AstNode.Member m = assertInstanceOf(AstNode.Member.class, parse("data.rows"));
    assertEquals(new AstNode.Ref("data"), m.obj());
    assertEquals("rows", m.field());
  }

  @Test
  void indexAccess() {
    AstNode.Index ix = assertInstanceOf(AstNode.Index.class, parse("arr[0]"));
    assertEquals(new AstNode.Ref("arr"), ix.obj());
    assertEquals(new AstNode.Num(0d), ix.index());
  }

  @Test
  void memberChainBindsTighterThanArithmetic() {
    // a.b + c => (a.b) + c
    AstNode.BinOp top = assertInstanceOf(AstNode.BinOp.class, parse("a.b + c"));
    assertEquals("+", top.op());
    assertInstanceOf(AstNode.Member.class, top.left());
  }

  @Test
  void componentCall() {
    AstNode.Comp comp = assertInstanceOf(AstNode.Comp.class, parse("Table([col], data.rows)"));
    assertEquals("Table", comp.name());
    assertEquals(2, comp.args().size());
    assertInstanceOf(AstNode.Arr.class, comp.args().get(0));
    assertInstanceOf(AstNode.Member.class, comp.args().get(1));
  }

  @Test
  void pascalCaseWithoutParensIsRef() {
    assertEquals(new AstNode.Ref("MyRef"), parse("MyRef"));
  }

  @Test
  void builtinNeedsAtPrefixSoBareCountIsRef() {
    // Count is a builtin — bare `Count(...)` should NOT be a component call, it becomes a Ref.
    AstNode n = parse("Count(items)");
    assertInstanceOf(AstNode.Ref.class, n);
    assertEquals("Count", ((AstNode.Ref) n).n());
  }

  @Test
  void atBuiltinCallIsComp() {
    AstNode.Comp comp = assertInstanceOf(AstNode.Comp.class, parse("@Render(\"v\", TextContent(v))"));
    assertEquals("Render", comp.name());
    assertEquals(2, comp.args().size());
  }

  @Test
  void actionIsExemptAndParsesAsComp() {
    AstNode.Comp comp = assertInstanceOf(AstNode.Comp.class, parse("Action(Run(x))"));
    assertEquals("Action", comp.name());
  }

  @Test
  void objectLiteral() {
    AstNode.Obj obj = assertInstanceOf(AstNode.Obj.class, parse("{ key: \"val\", n: 1 }"));
    assertEquals(2, obj.entries().size());
    assertEquals("key", obj.entries().get(0).key());
    assertEquals(new AstNode.Str("val"), obj.entries().get(0).value());
  }

  @Test
  void arrayLiteral() {
    AstNode.Arr arr = assertInstanceOf(AstNode.Arr.class, parse("[1, 2, 3]"));
    assertEquals(3, arr.els().size());
  }

  @Test
  void groupingParensOverridePrecedence() {
    // (a + b) * c => ((a+b) * c)
    AstNode.BinOp top = assertInstanceOf(AstNode.BinOp.class, parse("(a + b) * c"));
    assertEquals("*", top.op());
    assertInstanceOf(AstNode.BinOp.class, top.left());
    assertEquals("+", ((AstNode.BinOp) top.left()).op());
  }

  @Test
  void unclosedParenReportsDiagnostic() {
    ExpressionParser p = new ExpressionParser(OpenuiLexer.tokenize("(a + b"));
    p.parse();
    assertTrue(
        p.diagnostics().stream().anyMatch(d -> d.code() == ParseErrorCode.UNCLOSED_BRACKET),
        "expected an UNCLOSED_BRACKET diagnostic");
  }
}
