package dev.openui.langcore.parser;

import dev.openui.langcore.lexer.Lexer;
import dev.openui.langcore.parser.ast.LiteralNode;
import dev.openui.langcore.parser.stmt.MutationStatement;
import dev.openui.langcore.parser.stmt.QueryStatement;
import dev.openui.langcore.parser.stmt.StateStatement;
import dev.openui.langcore.parser.stmt.Statement;
import dev.openui.langcore.parser.stmt.ValueStatement;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link StatementParser} — one test per AC (AC2-10).
 * Ref: Req 2.*
 */
class StatementParserTest {

    private LinkedHashMap<String, Statement> parse(String src) {
        return new StatementParser().parse(new Lexer(src).tokenize());
    }

    // AC2 — StateVar LHS → StateStatement
    @Test
    void ac2_stateVarStatement() {
        var map = parse("$count = 0");
        assertEquals(1, map.size());
        assertTrue(map.containsKey("$count"));
        assertInstanceOf(StateStatement.class, map.get("$count"));
        StateStatement stmt = (StateStatement) map.get("$count");
        assertEquals("$count", stmt.id());
    }

    // AC3 — Ident LHS → ValueStatement
    @Test
    void ac3_identStatement() {
        var map = parse("items = foo");
        assertEquals(1, map.size());
        assertInstanceOf(ValueStatement.class, map.get("items"));
        assertEquals("items", ((ValueStatement) map.get("items")).id());
    }

    // AC3 — Type (PascalCase) LHS → ValueStatement
    @Test
    void ac3_typeStatement() {
        var map = parse("Root = Button()");
        assertEquals(1, map.size());
        assertInstanceOf(ValueStatement.class, map.get("Root"));
        assertEquals("Root", ((ValueStatement) map.get("Root")).id());
    }

    // AC4 — Query(...) call → QueryStatement (Ident LHS)
    @Test
    void ac4_queryStatement() {
        var map = parse("data = Query(\"tool\", {})");
        assertEquals(1, map.size());
        assertInstanceOf(QueryStatement.class, map.get("data"));
        QueryStatement stmt = (QueryStatement) map.get("data");
        assertEquals("data", stmt.id());
    }

    // AC4 — Query(...) call with StateVar LHS is also reclassified as QueryStatement
    @Test
    void ac4_queryStatementWithStateVarLhs() {
        var map = parse("$data = Query(\"tool\")");
        assertEquals(1, map.size());
        assertInstanceOf(QueryStatement.class, map.get("$data"));
        assertEquals("$data", ((QueryStatement) map.get("$data")).id());
    }

    // AC5 — Mutation(...) call → MutationStatement
    @Test
    void ac5_mutationStatement() {
        var map = parse("save = Mutation(\"save\", {})");
        assertEquals(1, map.size());
        assertInstanceOf(MutationStatement.class, map.get("save"));
        MutationStatement stmt = (MutationStatement) map.get("save");
        assertEquals("save", stmt.id());
    }

    // AC6 — newlines at depth 0 separate statements
    @Test
    void ac6_multipleStatements() {
        var map = parse("a = 1\nb = 2");
        assertEquals(2, map.size());
        assertTrue(map.containsKey("a"));
        assertTrue(map.containsKey("b"));
        List<String> keys = List.copyOf(map.keySet());
        assertEquals("a", keys.get(0));
        assertEquals("b", keys.get(1));
    }

    // AC7 — newline inside () is NOT a statement boundary
    @Test
    void ac7_bracketedNewlineIgnored() {
        var map = parse("a = foo(\n1,\n2\n)");
        assertEquals(1, map.size());
        assertInstanceOf(ValueStatement.class, map.get("a"));
    }

    // AC8 — multiline ternary (a\n? b\n: c) is ONE statement
    @Test
    void ac8_multilineTernary() {
        var map = parse("a = x\n? 1\n: 2");
        assertEquals(1, map.size());
        assertTrue(map.containsKey("a"));
    }

    // AC9 — line not matching <lhs> = <expr> is silently skipped (two tokens, no equals)
    @Test
    void ac9_malformedLineSkipped() {
        var map = parse("hello world\na = 1");
        assertEquals(1, map.size());
        assertTrue(map.containsKey("a"));
        assertFalse(map.containsKey("hello"));
    }

    // AC9 — line with no equals is silently skipped
    @Test
    void ac9_noEqualsSkipped() {
        var map = parse("foobar\na = 2");
        assertEquals(1, map.size());
        assertTrue(map.containsKey("a"));
        assertFalse(map.containsKey("foobar"));
    }

    // AC10 — duplicate IDs → last definition wins
    @Test
    void ac10_duplicateIdLastWins() {
        var map = parse("x = 1\nx = 2");
        assertEquals(1, map.size());
        assertTrue(map.containsKey("x"));
        assertInstanceOf(ValueStatement.class, map.get("x"));
        ValueStatement stmt = (ValueStatement) map.get("x");
        assertInstanceOf(LiteralNode.class, stmt.expr());
        assertEquals(2.0, ((LiteralNode) stmt.expr()).value());
    }

    // Query with only 1 arg → argsAST is LiteralNode(null)
    @Test
    void queryArgsDefault() {
        var map = parse("q = Query(\"tool\")");
        assertInstanceOf(QueryStatement.class, map.get("q"));
        QueryStatement stmt = (QueryStatement) map.get("q");
        assertInstanceOf(LiteralNode.class, stmt.argsAST());
        assertNull(((LiteralNode) stmt.argsAST()).value());
    }

    // LinkedHashMap preserves insertion order
    @Test
    void insertionOrder() {
        var map = parse("c = 3\na = 1\nb = 2");
        List<String> keys = List.copyOf(map.keySet());
        assertEquals(List.of("c", "a", "b"), keys);
    }
}
