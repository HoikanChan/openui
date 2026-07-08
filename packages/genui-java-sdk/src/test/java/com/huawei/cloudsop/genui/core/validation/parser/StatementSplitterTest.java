/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;

class StatementSplitterTest {

    private static List<StatementSplitter.RawStmt> split(String src) {
        return StatementSplitter.split(OpenuiLexer.tokenize(src));
    }

    @Test
    void splitsTwoStatementsOnNewline() {
        var stmts = split("a = 1\nb = 2");
        assertEquals(2, stmts.size());
        assertEquals("a", stmts.get(0).id());
        assertEquals("b", stmts.get(1).id());
    }

    @Test
    void multilineComponentInsideBracketsIsOneStatement() {
        String src = "root = Table([\n  col1,\n  col2\n],\ndata.rows)";
        var stmts = split(src);
        assertEquals(1, stmts.size());
        assertEquals("root", stmts.get(0).id());
    }

    @Test
    void blankLinesAreSkipped() {
        var stmts = split("a = 1\n\n\nb = 2\n");
        assertEquals(2, stmts.size());
    }

    @Test
    void stateVarIdKeepsDollar() {
        var stmts = split("$count = 0");
        assertEquals(1, stmts.size());
        assertEquals("$count", stmts.get(0).id());
        assertEquals(TokenType.STATE_VAR, stmts.get(0).idTokenType());
    }

    @Test
    void lineWithoutEqualsIsSkipped() {
        var stmts = split("justAWord\nb = 2");
        assertEquals(1, stmts.size());
        assertEquals("b", stmts.get(0).id());
    }

    @Test
    void multilineTernaryStaysOneStatement() {
        // condition on one line, ? and : continuation on following lines
        String src = "x = cond\n? a\n: b";
        var stmts = split(src);
        assertEquals(1, stmts.size());
        assertEquals("x", stmts.get(0).id());
    }

    @Test
    void tracksLineAndColumnOfStatement() {
        String src = "a = 1\nb = 2";
        var stmts = split(src);
        SourceSpan sp = stmts.get(1).span();
        assertEquals(2, sp.line());
        assertEquals(1, sp.column());
    }

    @Test
    void spanCoversMultilineStatement() {
        String src = "root = Table([\n  col1\n], rows)";
        var stmts = split(src);
        SourceSpan sp = stmts.get(0).span();
        assertEquals(1, sp.line());
        assertTrue(sp.endOffset() > sp.startOffset());
    }
}
