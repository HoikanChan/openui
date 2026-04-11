package dev.openui.langcore.parser;

import dev.openui.langcore.parser.ast.ElementNode;
import dev.openui.langcore.parser.ast.LiteralNode;
import dev.openui.langcore.parser.ast.Node;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link StreamParser} — AC2-9.
 * Ref: Req 9.*
 */
class StreamParserTest {

    private static final String SCHEMA = """
        {
          "$defs": {
            "Button": {
              "properties": {
                "label": { "type": "string" }
              },
              "required": ["label"]
            }
          }
        }
        """;

    private StreamParser parser() {
        return new StreamParser(SchemaRegistry.fromJson(SCHEMA));
    }

    private ParseResult oneShot(String input) {
        return new OneShot(SchemaRegistry.fromJson(SCHEMA)).parse(input);
    }

    // -------------------------------------------------------------------------
    // AC2: push(chunk) appends to buffer and returns ParseResult
    // -------------------------------------------------------------------------

    /** AC2 — push a complete statement returns a non-null result with root Button */
    @Test
    void ac2_pushChunk_returnsResult() {
        StreamParser p = parser();
        ParseResult result = p.push("root = Button(\"click\")\n");
        assertNotNull(result, "push() should return a non-null ParseResult");
        assertNotNull(result.root(), "root should not be null for a valid Button statement");
        assertEquals("Button", result.root().typeName());
    }

    /** AC2 — single chunk matches one-shot result */
    @Test
    void ac2_singleChunkMatchesOneShot() {
        String program = "root = Button(\"click\")\n";
        StreamParser p = parser();
        ParseResult streamResult = p.push(program);
        ParseResult oneShotResult = oneShot(program);

        assertNotNull(streamResult.root());
        assertNotNull(oneShotResult.root());
        assertEquals(oneShotResult.root().typeName(), streamResult.root().typeName());
    }

    // -------------------------------------------------------------------------
    // AC3: set(fullText) — delta if starts-with-current && longer; reset otherwise
    // -------------------------------------------------------------------------

    /** AC3 — set with a longer text that starts with current → delta append */
    @Test
    void ac3_set_deltaAppend() {
        StreamParser p = parser();
        p.set("root = Button(");
        ParseResult r = p.set("root = Button(\"click\")\n");
        // second set starts with first → delta append
        assertNotNull(r);
        assertNotNull(r.root(), "root should resolve after completing the statement");
        assertEquals("Button", r.root().typeName());
    }

    /** AC3 — set with different text (not a prefix) → reset, new content wins */
    @Test
    void ac3_set_reset_whenNotPrefix() {
        StreamParser p = parser();
        p.push("root = Button(\"a\")\n");
        ParseResult r = p.set("root = Button(\"b\")\n");
        // different text — reset, "b" wins
        assertNotNull(r.root());
        assertEquals("Button", r.root().typeName());
        Node labelNode = r.root().props().get("label");
        assertNotNull(labelNode);
        assertEquals("b", ((LiteralNode) labelNode).value());
    }

    // -------------------------------------------------------------------------
    // AC5: committed statements NOT overwritten by subsequent pending re-parse
    // -------------------------------------------------------------------------

    /** AC5 — once committed, a statement's value is immutable (putIfAbsent) */
    @Test
    void ac5_committedNotOverwritten() {
        StreamParser p = parser();
        p.push("root = Button(\"first\")\n");
        // Re-parse same ID: committed "first" should win over "second"
        ParseResult r = p.push("root = Button(\"second\")\n");
        assertNotNull(r.root());
        assertEquals("Button", r.root().typeName());
        Node labelNode = r.root().props().get("label");
        assertNotNull(labelNode);
        assertEquals("first", ((LiteralNode) labelNode).value(),
                "Committed 'first' should not be overwritten by 'second'");
    }

    // -------------------------------------------------------------------------
    // AC6: pending statement auto-closed and merged; contributes new IDs only
    // -------------------------------------------------------------------------

    /** AC6 — push incomplete statement (no closing paren) → auto-closed, result not null, incomplete=true */
    @Test
    void ac6_pendingAutoCloseContributesNewIds() {
        StreamParser p = parser();
        ParseResult r = p.push("root = Button(\"click\"\n");
        assertNotNull(r, "push() should never return null");
        assertTrue(r.meta().incomplete(),
                "meta.incomplete should be true when statement is pending/auto-closed");
    }

    // -------------------------------------------------------------------------
    // AC7: meta.incomplete correctly set
    // -------------------------------------------------------------------------

    /** AC7 — all complete statements → meta.incomplete == false */
    @Test
    void ac7_metaIncomplete_false_whenAllComplete() {
        StreamParser p = parser();
        ParseResult r = p.push("root = Button(\"click\")\n");
        assertFalse(r.meta().incomplete(),
                "meta.incomplete should be false when all statements are complete");
    }

    /** AC7 — no trailing newline → pending text → meta.incomplete == true */
    @Test
    void ac7_metaIncomplete_true_whenPending() {
        StreamParser p = parser();
        ParseResult r = p.push("root = Button(\"click\")");  // no trailing newline
        assertTrue(r.meta().incomplete(),
                "meta.incomplete should be true when text is pending (no trailing newline)");
    }

    // -------------------------------------------------------------------------
    // AC8: buffer reset clears committed cache
    // -------------------------------------------------------------------------

    /** AC8 — set with non-prefix text resets; committed cache is cleared */
    @Test
    void ac8_reset_onNonPrefixSet() {
        StreamParser p = parser();
        p.push("root = Button(\"a\")\n");  // commits root with "a"

        // "other = ..." does NOT start with the current buffer → reset
        ParseResult r = p.set("other = Button(\"b\")\n");
        assertNotNull(r);
        assertNotNull(r.meta());

        // After reset, only "other" statement exists (statementCount == 1)
        assertEquals(1, r.meta().statementCount(),
                "After reset, only 'other' statement should exist");

        // "other" is the first (and only) component statement, so Materializer selects it as root
        assertNotNull(r.root(), "After reset, 'other' should be selected as root (first component statement)");
        assertEquals("Button", r.root().typeName());
        Node labelNode = r.root().props().get("label");
        assertNotNull(labelNode);
        assertEquals("b", ((LiteralNode) labelNode).value(),
                "After reset, label should be 'b' from the new statement");
    }

    // -------------------------------------------------------------------------
    // AC9: getResult() idempotent
    // -------------------------------------------------------------------------

    /** AC9 — getResult() returns same result repeatedly without side effects */
    @Test
    void ac9_getResult_idempotent() {
        StreamParser p = parser();
        p.push("root = Button(\"click\")\n");

        ParseResult r1 = p.getResult();
        ParseResult r2 = p.getResult();
        ParseResult r3 = p.getResult();

        assertNotNull(r1.root());
        assertNotNull(r2.root());
        assertNotNull(r3.root());

        assertEquals(r1.root().typeName(), r2.root().typeName());
        assertEquals(r2.root().typeName(), r3.root().typeName());
        assertEquals("Button", r1.root().typeName());
    }

    // -------------------------------------------------------------------------
    // Incremental chunk delivery matches one-shot result
    // -------------------------------------------------------------------------

    /** Deliver program character-by-character; final result matches one-shot */
    @Test
    void incrementalChunks_matchOneShot() {
        String program = "root = Button(\"click\")\n";
        StreamParser p = parser();

        ParseResult lastResult = null;
        for (int i = 0; i < program.length(); i++) {
            lastResult = p.push(String.valueOf(program.charAt(i)));
        }

        assertNotNull(lastResult);
        ParseResult oneShotResult = oneShot(program);

        assertNotNull(lastResult.root());
        assertNotNull(oneShotResult.root());
        assertEquals(oneShotResult.root().typeName(), lastResult.root().typeName());
    }

    // -------------------------------------------------------------------------
    // AC5 + AC6 combined: pending does not overwrite committed
    // -------------------------------------------------------------------------

    /** AC5+AC6 — pending text for already-committed ID should not overwrite committed value */
    @Test
    void ac5_ac6_pendingDoesNotOverwriteCommitted() {
        StreamParser p = parser();
        p.push("root = Button(\"committed\")\n");  // commits root

        // Push pending (no newline, no closing paren) — still pending
        ParseResult r = p.push("root = Button(\"pending\"");
        assertNotNull(r);
        assertNotNull(r.root());

        // Committed entry wins; label should still be "committed"
        Node labelNode = r.root().props().get("label");
        assertNotNull(labelNode);
        assertEquals("committed", ((LiteralNode) labelNode).value(),
                "Committed 'committed' should win over pending 'pending'");
    }
}
