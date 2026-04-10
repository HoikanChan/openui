package dev.openui.langcore.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link PreProcessor}.
 * Ref: Req 7.*, 8.*
 */
class PreProcessorTest {

    // -------------------------------------------------------------------------
    // stripComments — Req 7 AC5-6
    // -------------------------------------------------------------------------

    @Test
    void stripComments_slashSlash() {
        // space before // is preserved; only the comment marker and rest of line are removed
        assertEquals("x = 1 \n", PreProcessor.stripComments("x = 1 // ignore me\n"));
    }

    @Test
    void stripComments_hash() {
        assertEquals("x = 1 \n", PreProcessor.stripComments("x = 1 # ignore me\n"));
    }

    @Test
    void stripComments_insideString_notStripped() {
        String input = "x = \"hello // world\"";
        assertEquals(input, PreProcessor.stripComments(input));
    }

    @Test
    void stripComments_insideString_hashNotStripped() {
        String input = "x = \"hello # world\"";
        assertEquals(input, PreProcessor.stripComments(input));
    }

    @Test
    void stripComments_multipleLines() {
        String input = "a = 1 // first\nb = 2 # second\n";
        assertEquals("a = 1 \nb = 2 \n", PreProcessor.stripComments(input));
    }

    // -------------------------------------------------------------------------
    // stripFences — Req 7 AC1-4, AC7
    // -------------------------------------------------------------------------

    @Test
    void stripFences_singleBlock() {
        String input = "```\nx = 1\n```\n";
        assertEquals("x = 1", PreProcessor.stripFences(input));
    }

    @Test
    void stripFences_withLanguageTag() {
        String input = "```openui\nx = 1\n```\n";
        assertEquals("x = 1", PreProcessor.stripFences(input));
    }

    @Test
    void stripFences_multipleBlocks() {
        String input = "```\na = 1\n```\nsome text\n```\nb = 2\n```\n";
        assertEquals("a = 1\nb = 2", PreProcessor.stripFences(input));
    }

    @Test
    void stripFences_noFences_passThrough() {
        String input = "x = 1\ny = 2\n";
        assertEquals(input, PreProcessor.stripFences(input));
    }

    @Test
    void stripFences_missingClosingFence_streaming() {
        String input = "```\nx = 1\ny = 2\n";
        assertEquals("x = 1\ny = 2\n", PreProcessor.stripFences(input));
    }

    @Test
    void stripFences_backtickInsideString_notBoundary() {
        // The ``` inside the string should not be treated as a fence
        String input = "x = \"```not a fence```\"\n```\nreal = 1\n```\n";
        assertEquals("real = 1", PreProcessor.stripFences(input));
    }

    // -------------------------------------------------------------------------
    // autoClose — Req 8 AC1-4
    // -------------------------------------------------------------------------

    @Test
    void autoClose_balanced_noChange() {
        AutoCloseResult r = PreProcessor.autoClose("f(1, [2])");
        assertEquals("f(1, [2])", r.text());
        assertFalse(r.wasIncomplete());
    }

    @Test
    void autoClose_missingClosingParen() {
        AutoCloseResult r = PreProcessor.autoClose("f(1");
        assertEquals("f(1)", r.text());
        assertTrue(r.wasIncomplete());
    }

    @Test
    void autoClose_missingClosingBracketsInOrder() {
        AutoCloseResult r = PreProcessor.autoClose("f([{");
        assertEquals("f([{}])", r.text());
        assertTrue(r.wasIncomplete());
    }

    @Test
    void autoClose_unterminatedDoubleQuote() {
        AutoCloseResult r = PreProcessor.autoClose("x = \"hello");
        assertEquals("x = \"hello\"", r.text());
        assertTrue(r.wasIncomplete());
    }

    @Test
    void autoClose_unterminatedSingleQuote() {
        AutoCloseResult r = PreProcessor.autoClose("x = 'hello");
        assertEquals("x = 'hello'", r.text());
        assertTrue(r.wasIncomplete());
    }

    @Test
    void autoClose_trailingEscapeInString() {
        // Streaming split mid-escape: "hello\  — trailing backslash
        AutoCloseResult r = PreProcessor.autoClose("\"hello\\");
        assertTrue(r.wasIncomplete());
        assertTrue(r.text().endsWith("\""));
    }

    @Test
    void autoClose_missingMultipleBrackets() {
        // ( and [ are both unclosed — closers appended in reverse (LIFO)
        AutoCloseResult r = PreProcessor.autoClose("f(1\nb = [2");
        assertTrue(r.wasIncomplete());
        assertTrue(r.text().endsWith("])")); // ] closes [, ) closes (
    }
}
