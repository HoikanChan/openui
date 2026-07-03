/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.llm.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class OpenuiCodeExtractorTest {
    @Test
    void extractsOpenuiBlockFirst() {
        String raw = """
                prose
                ```openui
                  root = Stack([])
                ```
                """;

        assertEquals("root = Stack([])", OpenuiCodeExtractor.extract(raw));
    }

    @Test
    void extractsOpenuiLangBlock() {
        String raw = """
                ```openui-lang
                root = Stack([table])
                ```
                """;

        assertEquals("root = Stack([table])", OpenuiCodeExtractor.extract(raw));
    }

    @Test
    void prefersTextBlockOverStackHeuristic() {
        String raw = """
                ```js
                root = Stack([fromJs])
                ```
                ```text
                root = Stack([fromText])
                ```
                """;

        assertEquals("root = Stack([fromText])", OpenuiCodeExtractor.extract(raw));
    }

    @Test
    void fallsBackToStackHeuristicInAnyCodeBlock() {
        String raw = """
                ```json
                {"ok":true}
                ```
                ```
                root = Stack([fallback])
                ```
                """;

        assertEquals("root = Stack([fallback])", OpenuiCodeExtractor.extract(raw));
    }

    @Test
    void fallsBackToTrimmedRawTextWhenNoMarkerExists() {
        assertEquals("root = Stack([])", OpenuiCodeExtractor.extract("  root = Stack([])  "));
    }

    @Test
    void emptyInputReturnsEmptyString() {
        assertEquals("", OpenuiCodeExtractor.extract(""));
        assertEquals("", OpenuiCodeExtractor.extract("  "));
        assertEquals("", OpenuiCodeExtractor.extract(null));
    }
}
