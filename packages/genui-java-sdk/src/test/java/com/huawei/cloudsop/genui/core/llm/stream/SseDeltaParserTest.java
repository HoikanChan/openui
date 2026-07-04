/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.llm.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

class SseDeltaParserTest {
    @Test
    void parsesJoinedFramesAndSkipsDone() throws IOException {
        String stream = frame("hel") + frame("lo") + SseFrames.done();
        ArrayList<String> deltas = new ArrayList<>();

        String accumulated = SseDeltaParser.parse(input(stream), deltas::add);

        assertEquals(List.of("hel", "lo"), deltas);
        assertEquals("hello", accumulated);
    }

    @Test
    void handlesFramesSplitAcrossReads() throws IOException {
        ArrayList<String> deltas = new ArrayList<>();

        String accumulated = SseDeltaParser.parse(oneByteAtATime(input(frame("split"))), deltas::add);

        assertEquals(List.of("split"), deltas);
        assertEquals("split", accumulated);
    }

    @Test
    void skipsBadFramesAndContinues() throws IOException {
        String stream = "data: not-json\n\n" + frame("ok");
        ArrayList<String> deltas = new ArrayList<>();

        String accumulated = SseDeltaParser.parse(input(stream), deltas::add);

        assertEquals(List.of("ok"), deltas);
        assertEquals("ok", accumulated);
    }

    @Test
    void parsesFinalFrameWithoutTrailingBlankLine() throws IOException {
        String stream = "data: {\"choices\":[{\"delta\":{\"content\":\"tail\"}}]}";
        ArrayList<String> deltas = new ArrayList<>();

        String accumulated = SseDeltaParser.parse(input(stream), deltas::add);

        assertEquals(List.of("tail"), deltas);
        assertEquals("tail", accumulated);
    }

    @Test
    void skipsEmptyDeltaContent() throws IOException {
        String stream = frame("") + frame("next");
        ArrayList<String> deltas = new ArrayList<>();

        String accumulated = SseDeltaParser.parse(input(stream), deltas::add);

        assertEquals(List.of("next"), deltas);
        assertEquals("next", accumulated);
    }

    @Test
    void propagatesSinkFailures() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> SseDeltaParser.parse(input(frame("boom")), delta -> {
                    throw new IllegalStateException("sink failed: " + delta);
                }));

        assertEquals("sink failed: boom", error.getMessage());
    }

    private static String frame(String content) {
        return "data: {\"choices\":[{\"delta\":{\"content\":\"" + content + "\"}}]}\n\n";
    }

    private static InputStream input(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    private static InputStream oneByteAtATime(InputStream delegate) {
        return new FilterInputStream(delegate) {
            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                return super.read(b, off, Math.min(len, 1));
            }
        };
    }
}
