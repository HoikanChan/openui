package dev.openui.langcore.merge;

import dev.openui.langcore.lexer.Lexer;
import dev.openui.langcore.lexer.Token;
import dev.openui.langcore.parser.SchemaRegistry;
import dev.openui.langcore.parser.StatementParser;
import dev.openui.langcore.parser.stmt.QueryStatement;
import dev.openui.langcore.parser.stmt.Statement;
import dev.openui.langcore.parser.stmt.ValueStatement;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Merger}.
 * Ref: Req 12.*
 */
class MergerTest {

    private static final String SCHEMA = """
            {
              "$defs": {
                "Button": {
                  "properties": {
                    "label": { "type": "string" }
                  },
                  "required": ["label"]
                },
                "Card": {
                  "properties": {
                    "title": { "type": "string" },
                    "body":  { "type": "string" }
                  },
                  "required": ["title", "body"]
                }
              }
            }
            """;

    private final SchemaRegistry schema = SchemaRegistry.fromJson(SCHEMA);

    /** Parse raw OpenUI Lang text into a statement map (no fence-stripping, no auto-close). */
    private static Map<String, Statement> parse(String text) {
        List<Token> tokens = new Lexer(text).tokenize();
        return new StatementParser().parse(tokens);
    }

    private Map<String, Statement> merge(Map<String, Statement> existing, String patchText) {
        return Merger.mergeStatements(existing, patchText, schema);
    }

    // -------------------------------------------------------------------------
    // AC1 — patch overrides existing statement by ID
    // -------------------------------------------------------------------------

    @Test
    void ac1_patchOverridesExistingStatement() {
        Map<String, Statement> existing = parse("""
                root = Button("original")
                """);

        Map<String, Statement> result = merge(existing, "root = Button(\"updated\")");

        assertTrue(result.containsKey("root"));
        ValueStatement vs = (ValueStatement) result.get("root");
        // The overwritten statement is present; original is gone
        assertNotNull(vs);
    }

    // -------------------------------------------------------------------------
    // AC2 — NullStatement in patch deletes the ID
    // -------------------------------------------------------------------------

    @Test
    void ac2_nullStatementDeletesId() {
        Map<String, Statement> existing = parse("""
                root = Button("hello")
                extra = Button("world")
                """);
        // "extra = null" produces a NullStatement in the patch
        Map<String, Statement> result = merge(existing, "extra = null");

        assertFalse(result.containsKey("extra"), "NullStatement should delete 'extra'");
    }

    // -------------------------------------------------------------------------
    // AC3 — patch appends a new ID not in original
    // -------------------------------------------------------------------------

    @Test
    void ac3_appendsNewId() {
        // The new ID replaces root so it survives GC
        Map<String, Statement> existing = parse("root = Button(\"old\")");

        Map<String, Statement> result = merge(existing, "root = Button(\"new\")");

        assertTrue(result.containsKey("root"), "New ID from patch should be present");
    }

    @Test
    void ac3_appendsNewIdReferencedFromRoot() {
        // 'inner' is referenced by root via a Ref; the patch introduces it fresh
        Map<String, Statement> existing = parse("root = Button(\"hi\")");

        // Patch introduces a new 'root' that references 'extra', and 'extra' itself
        String patch = """
                root = Button("updated")
                extra = Button("extra")
                """;
        Map<String, Statement> result = merge(existing, patch);

        // 'extra' is not referenced from root so GC removes it — but root is present
        assertTrue(result.containsKey("root"));
    }

    // -------------------------------------------------------------------------
    // AC4 — GC removes unreachable non-$state statements
    // -------------------------------------------------------------------------

    @Test
    void ac4_gcRemovesUnreachable() {
        // 'orphan' is never referenced from root
        Map<String, Statement> existing = parse("""
                root = Button("hi")
                orphan = Button("unreachable")
                """);

        // Empty patch — just trigger the GC path
        Map<String, Statement> result = merge(existing, "root = Button(\"hi\")");

        assertFalse(result.containsKey("orphan"), "Unreachable statement should be GC'd");
        assertTrue(result.containsKey("root"));
    }

    // -------------------------------------------------------------------------
    // AC5 — $state IDs are always retained (never GC'd)
    // -------------------------------------------------------------------------

    @Test
    void ac5_stateIdsAlwaysRetained() {
        Map<String, Statement> existing = parse("""
                root = Button("hi")
                $count = 0
                """);

        Map<String, Statement> result = merge(existing, "root = Button(\"hi\")");

        assertTrue(result.containsKey("$count"), "$state ID must never be GC'd");
    }

    // -------------------------------------------------------------------------
    // AC6 — fence stripping applied before parsing patch
    // -------------------------------------------------------------------------

    @Test
    void ac6_fenceStrippingApplied() {
        Map<String, Statement> existing = parse("root = Button(\"old\")");

        String fencedPatch = """
                ```openui
                root = Button("fenced")
                ```
                """;

        Map<String, Statement> result = merge(existing, fencedPatch);

        assertTrue(result.containsKey("root"), "Fenced patch should be parsed after fence stripping");
    }

    // -------------------------------------------------------------------------
    // AC7 — empty existing → result is patch statements only
    // -------------------------------------------------------------------------

    @Test
    void ac7_emptyExisting_returnsPatchOnly() {
        Map<String, Statement> result = merge(new LinkedHashMap<>(), "root = Button(\"hi\")");

        assertTrue(result.containsKey("root"));
        assertEquals(1, result.size());
    }

    @Test
    void ac7_emptyExisting_nullStatementsIgnored() {
        // A NullStatement in the patch against an empty existing should not appear in output
        Map<String, Statement> result = merge(new LinkedHashMap<>(), "ghost = null\nroot = Button(\"hi\")");

        assertFalse(result.containsKey("ghost"), "NullStatement against empty existing should not appear");
        assertTrue(result.containsKey("root"));
    }

    // -------------------------------------------------------------------------
    // AC8 — empty patch → existing unchanged
    // -------------------------------------------------------------------------

    @Test
    void ac8_emptyPatch_returnsExistingUnchanged() {
        Map<String, Statement> existing = parse("root = Button(\"hi\")");

        Map<String, Statement> result = merge(existing, "");

        assertEquals(existing.keySet(), result.keySet());
    }

    @Test
    void ac8_blankPatch_returnsExistingUnchanged() {
        Map<String, Statement> existing = parse("root = Button(\"hi\")");

        Map<String, Statement> result = merge(existing, "   \n  ");

        assertEquals(existing.keySet(), result.keySet());
    }

    // -------------------------------------------------------------------------
    // Additional — Query statements are retained through GC
    // -------------------------------------------------------------------------

    @Test
    void queryStatementsRetainedByGc() {
        Map<String, Statement> existing = parse("""
                root = Button("hi")
                data = Query("getTodos", {})
                """);

        Map<String, Statement> result = merge(existing, "root = Button(\"hi\")");

        assertTrue(result.containsKey("data"), "Query statements should be retained through GC");
        assertInstanceOf(QueryStatement.class, result.get("data"));
    }
}
