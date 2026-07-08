/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OpenuiPreprocessorTest {

    @Test
    void extractsFencedOpenuiBlock() {
        String md = "Here you go:\n```openui\nroot = Title(\"hi\")\n```\nDone.";
        assertEquals("root = Title(\"hi\")", OpenuiPreprocessor.preprocess(md));
    }

    @Test
    void stripsSlashSlashComments() {
        assertEquals("root = Title(\"hi\")", OpenuiPreprocessor.preprocess("root = Title(\"hi\") // a comment"));
    }

    @Test
    void stripsHashComments() {
        assertEquals("root = Title(\"hi\")", OpenuiPreprocessor.preprocess("root = Title(\"hi\") # note"));
    }

    @Test
    void doesNotStripCommentMarkersInsideStrings() {
        String src = "root = Title(\"http://example.com\")";
        assertEquals(src, OpenuiPreprocessor.preprocess(src));
    }

    @Test
    void doesNotStripHashInsideStrings() {
        String src = "root = Title(\"a # b\")";
        assertEquals(src, OpenuiPreprocessor.preprocess(src));
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertEquals("root = X()", OpenuiPreprocessor.preprocess("\n\n   root = X()  \n\n"));
    }

    @Test
    void plainCodeWithoutFencesPassesThrough() {
        assertEquals("a = 1\nb = 2", OpenuiPreprocessor.preprocess("a = 1\nb = 2"));
    }

    @Test
    void autoCloseRepairsUnclosedBracketInStreamingMode() {
        OpenuiPreprocessor.Result r = OpenuiPreprocessor.process("root = Table([col", ParseMode.STREAMING);
        assertTrue(r.wasIncomplete());
        assertEquals("root = Table([col])", r.text());
    }

    @Test
    void finalModeDoesNotAutoClose() {
        OpenuiPreprocessor.Result r = OpenuiPreprocessor.process("root = Table([col", ParseMode.FINAL);
        assertFalse(r.wasIncomplete());
        assertEquals("root = Table([col", r.text());
    }

    @Test
    void autoCloseRepairsUnclosedString() {
        OpenuiPreprocessor.Result r = OpenuiPreprocessor.process("root = Title(\"hi", ParseMode.STREAMING);
        assertTrue(r.wasIncomplete());
        assertEquals("root = Title(\"hi\")", r.text());
    }
}
