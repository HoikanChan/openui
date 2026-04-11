package dev.openui.langcore.parser;

import dev.openui.langcore.lexer.Lexer;
import dev.openui.langcore.lexer.Token;
import dev.openui.langcore.parser.ast.*;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ExpressionParser} — one test per AC.
 * Ref: Req 3.*
 */
class ExpressionParserTest {

    /** Tokenize and parse a full expression string. */
    private Node parse(String src) {
        List<Token> tokens = new Lexer(src).tokenize();
        return new ExpressionParser(tokens, 0).parseExpression(0);
    }

    // AC1 (partial) — Pratt precedence: multiplication before addition
    @Test
    void ac1_precedenceMulBeforeAdd() {
        // 1 + 2 * 3  =>  BinaryNode("+", LiteralNode(1), BinaryNode("*", LiteralNode(2), LiteralNode(3)))
        Node result = parse("1 + 2 * 3");
        assertInstanceOf(BinaryNode.class, result);
        BinaryNode add = (BinaryNode) result;
        assertEquals("+", add.op());
        assertEquals(new LiteralNode(1.0), add.left());
        assertInstanceOf(BinaryNode.class, add.right());
        BinaryNode mul = (BinaryNode) add.right();
        assertEquals("*", mul.op());
        assertEquals(new LiteralNode(2.0), mul.left());
        assertEquals(new LiteralNode(3.0), mul.right());
    }

    // AC1/AC2 — All 9 precedence levels in one expression
    @Test
    void ac2_allNinePrecedenceLevels() {
        // a || b && c == d > e + f * !g
        // Expected grouping (outermost to innermost):
        //   || (level 2) wraps everything on its right
        //   && (level 3) wraps b && (c == ...)
        //   == (level 4) wraps c == (d > ...)
        //   > (level 5) wraps d > (e + ...)
        //   + (level 6) wraps e + (f * ...)
        //   * (level 7) wraps f * !g
        //   ! (level 8) wraps g
        Node result = parse("a || b && c == d > e + f * !g");

        assertInstanceOf(BinaryNode.class, result);
        BinaryNode orNode = (BinaryNode) result;
        assertEquals("||", orNode.op());
        assertEquals(new RefNode("a"), orNode.left());

        assertInstanceOf(BinaryNode.class, orNode.right());
        BinaryNode andNode = (BinaryNode) orNode.right();
        assertEquals("&&", andNode.op());
        assertEquals(new RefNode("b"), andNode.left());

        assertInstanceOf(BinaryNode.class, andNode.right());
        BinaryNode eqNode = (BinaryNode) andNode.right();
        assertEquals("==", eqNode.op());
        assertEquals(new RefNode("c"), eqNode.left());

        assertInstanceOf(BinaryNode.class, eqNode.right());
        BinaryNode gtNode = (BinaryNode) eqNode.right();
        assertEquals(">", gtNode.op());
        assertEquals(new RefNode("d"), gtNode.left());

        assertInstanceOf(BinaryNode.class, gtNode.right());
        BinaryNode addNode = (BinaryNode) gtNode.right();
        assertEquals("+", addNode.op());
        assertEquals(new RefNode("e"), addNode.left());

        assertInstanceOf(BinaryNode.class, addNode.right());
        BinaryNode mulNode = (BinaryNode) addNode.right();
        assertEquals("*", mulNode.op());
        assertEquals(new RefNode("f"), mulNode.left());

        assertInstanceOf(UnaryNode.class, mulNode.right());
        UnaryNode notNode = (UnaryNode) mulNode.right();
        assertEquals("!", notNode.op());
        assertEquals(new RefNode("g"), notNode.operand());
    }

    // AC1 — Unary minus via expression (standalone -5 is lexed as Num(-5), use -(x) for unary)
    @Test
    void ac1_unaryMinus() {
        Node result = parse("-(x)");
        assertInstanceOf(UnaryNode.class, result);
        UnaryNode unary = (UnaryNode) result;
        assertEquals("-", unary.op());
        assertEquals(new RefNode("x"), unary.operand());
    }

    // AC1 — Unary not
    @Test
    void ac1_unaryNot() {
        Node result = parse("!true");
        assertInstanceOf(UnaryNode.class, result);
        UnaryNode unary = (UnaryNode) result;
        assertEquals("!", unary.op());
        assertEquals(new LiteralNode(true), unary.operand());
    }

    // AC1 — Ternary operator
    @Test
    void ac1_ternaryBasic() {
        Node result = parse("a ? b : c");
        assertInstanceOf(TernaryNode.class, result);
        TernaryNode ternary = (TernaryNode) result;
        assertEquals(new RefNode("a"), ternary.condition());
        assertEquals(new RefNode("b"), ternary.consequent());
        assertEquals(new RefNode("c"), ternary.alternate());
    }

    // AC1 — Ternary spanning newlines (newlines inside expression are skipped)
    @Test
    void ac1_ternarySpanningNewlines() {
        Node result = parse("a\n? b\n: c");
        assertInstanceOf(TernaryNode.class, result);
        TernaryNode ternary = (TernaryNode) result;
        assertEquals(new RefNode("a"), ternary.condition());
        assertEquals(new RefNode("b"), ternary.consequent());
        assertEquals(new RefNode("c"), ternary.alternate());
    }

    // AC3 — String concat: AST emits BinaryNode("+", ...) with string operands
    @Test
    void ac3_stringConcatAstShape() {
        Node result = parse("\"hello\" + \" world\"");
        assertInstanceOf(BinaryNode.class, result);
        BinaryNode bin = (BinaryNode) result;
        assertEquals("+", bin.op());
        assertEquals(new LiteralNode("hello"), bin.left());
        assertEquals(new LiteralNode(" world"), bin.right());
    }

    // AC5 — == emits BinaryNode("==", ...)
    @Test
    void ac5_equalityAstShape() {
        Node result = parse("a == b");
        assertInstanceOf(BinaryNode.class, result);
        BinaryNode bin = (BinaryNode) result;
        assertEquals("==", bin.op());
    }

    // AC5 — != emits BinaryNode("!=", ...)
    @Test
    void ac5_notEqualAstShape() {
        Node result = parse("a != b");
        assertInstanceOf(BinaryNode.class, result);
        BinaryNode bin = (BinaryNode) result;
        assertEquals("!=", bin.op());
    }

    // AC7 — && emits BinaryNode("&&", ...)
    @Test
    void ac7_andAstShape() {
        Node result = parse("a && b");
        assertInstanceOf(BinaryNode.class, result);
        BinaryNode bin = (BinaryNode) result;
        assertEquals("&&", bin.op());
    }

    // AC7 — || emits BinaryNode("||", ...)
    @Test
    void ac7_orAstShape() {
        Node result = parse("a || b");
        assertInstanceOf(BinaryNode.class, result);
        BinaryNode bin = (BinaryNode) result;
        assertEquals("||", bin.op());
    }

    // AC8 — StateVar on LHS of = → AssignNode
    @Test
    void ac8_stateVarAssignment() {
        Node result = parse("$count = 5");
        assertInstanceOf(AssignNode.class, result);
        AssignNode assign = (AssignNode) result;
        assertEquals("$count", assign.target());
        assertEquals(new LiteralNode(5.0), assign.expr());
    }

    // AC8 — StateVar without = → StateRefNode
    @Test
    void ac8_stateVarReference() {
        Node result = parse("$count");
        assertInstanceOf(StateRefNode.class, result);
        StateRefNode ref = (StateRefNode) result;
        assertEquals("$count", ref.name());
    }

    // AC9 — Member access dot: items.name → MemberNode(RefNode("items"), LiteralNode("name"), false)
    @Test
    void ac9_memberAccessDot() {
        Node result = parse("items.name");
        assertInstanceOf(MemberNode.class, result);
        MemberNode member = (MemberNode) result;
        assertEquals(new RefNode("items"), member.object());
        assertEquals(new LiteralNode("name"), member.property());
        assertFalse(member.computed());
    }

    // AC10 — Computed member access: items[0] → MemberNode(RefNode("items"), LiteralNode(0.0), true)
    @Test
    void ac10_memberAccessBracket() {
        Node result = parse("items[0]");
        assertInstanceOf(MemberNode.class, result);
        MemberNode member = (MemberNode) result;
        assertEquals(new RefNode("items"), member.object());
        assertEquals(new LiteralNode(0.0), member.property());
        assertTrue(member.computed());
    }

    // AC11 — @BuiltinCall: @Count(items) → BuiltinCallNode("Count", [RefNode("items")])
    @Test
    void ac11_builtinCallCount() {
        Node result = parse("@Count(items)");
        assertInstanceOf(BuiltinCallNode.class, result);
        BuiltinCallNode call = (BuiltinCallNode) result;
        assertEquals("Count", call.name());
        assertEquals(List.of(new RefNode("items")), call.args());
    }

    // AC11 — @Each lazy builtin with bare ident varName
    @Test
    void ac11_eachWithStringVarName() {
        // @Each(items, "i", i) → BuiltinCallNode("Each", [RefNode("items"), LiteralNode("i"), RefNode("i")])
        Node result = parse("@Each(items, \"i\", i)");
        assertInstanceOf(BuiltinCallNode.class, result);
        BuiltinCallNode call = (BuiltinCallNode) result;
        assertEquals("Each", call.name());
        assertEquals(3, call.args().size());
        assertEquals(new RefNode("items"), call.args().get(0));
        assertEquals(new LiteralNode("i"), call.args().get(1));
        assertEquals(new RefNode("i"), call.args().get(2));
    }

    // AC12 — PascalCase call without @ prefix → CallNode("Action", [...])
    @Test
    void ac12_pascalCaseCallNode() {
        Node result = parse("Action([])");
        assertInstanceOf(CallNode.class, result);
        CallNode call = (CallNode) result;
        assertEquals("Action", call.callee());
        assertEquals(1, call.args().size());
        assertInstanceOf(ArrayNode.class, call.args().get(0));
        ArrayNode arr = (ArrayNode) call.args().get(0);
        assertTrue(arr.elements().isEmpty());
    }

    // Grouping — parentheses override precedence
    @Test
    void grouping_parenthesesOverridePrecedence() {
        // (1 + 2) * 3 → BinaryNode("*", BinaryNode("+", LiteralNode(1), LiteralNode(2)), LiteralNode(3))
        Node result = parse("(1 + 2) * 3");
        assertInstanceOf(BinaryNode.class, result);
        BinaryNode mul = (BinaryNode) result;
        assertEquals("*", mul.op());
        assertInstanceOf(BinaryNode.class, mul.left());
        BinaryNode add = (BinaryNode) mul.left();
        assertEquals("+", add.op());
        assertEquals(new LiteralNode(1.0), add.left());
        assertEquals(new LiteralNode(2.0), add.right());
        assertEquals(new LiteralNode(3.0), mul.right());
    }

    // Array literal: [1, 2, 3] → ArrayNode([LiteralNode(1), LiteralNode(2), LiteralNode(3)])
    @Test
    void arrayLiteral() {
        Node result = parse("[1, 2, 3]");
        assertInstanceOf(ArrayNode.class, result);
        ArrayNode arr = (ArrayNode) result;
        assertEquals(3, arr.elements().size());
        assertEquals(new LiteralNode(1.0), arr.elements().get(0));
        assertEquals(new LiteralNode(2.0), arr.elements().get(1));
        assertEquals(new LiteralNode(3.0), arr.elements().get(2));
    }

    // Object literal: {x: 1, y: 2} → ObjectNode({x: LiteralNode(1), y: LiteralNode(2)})
    @Test
    void objectLiteral() {
        Node result = parse("{x: 1, y: 2}");
        assertInstanceOf(ObjectNode.class, result);
        ObjectNode obj = (ObjectNode) result;
        assertEquals(2, obj.entries().size());
        assertEquals(new LiteralNode(1.0), obj.entries().get("x"));
        assertEquals(new LiteralNode(2.0), obj.entries().get("y"));
    }

    // Nested member access: a.b.c → left-associative chain of MemberNodes
    @Test
    void nestedMemberAccess_leftAssociative() {
        // a.b.c → MemberNode(MemberNode(RefNode("a"), LiteralNode("b"), false), LiteralNode("c"), false)
        Node result = parse("a.b.c");
        assertInstanceOf(MemberNode.class, result);
        MemberNode outer = (MemberNode) result;
        assertEquals(new LiteralNode("c"), outer.property());
        assertFalse(outer.computed());

        assertInstanceOf(MemberNode.class, outer.object());
        MemberNode inner = (MemberNode) outer.object();
        assertEquals(new RefNode("a"), inner.object());
        assertEquals(new LiteralNode("b"), inner.property());
        assertFalse(inner.computed());
    }
}
