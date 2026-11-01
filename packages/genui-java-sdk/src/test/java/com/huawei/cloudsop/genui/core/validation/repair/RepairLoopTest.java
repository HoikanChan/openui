/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.huawei.cloudsop.genui.core.validation.repair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.core.contract.GenerationContractLoader;
import com.huawei.cloudsop.genui.core.llm.GenUiGenerationResult;
import com.huawei.cloudsop.genui.core.llm.GenUiLlmConfig;
import com.huawei.cloudsop.genui.core.llm.GenerationEndReason;
import com.huawei.cloudsop.genui.core.llm.stream.SseFrames;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransport;
import com.huawei.cloudsop.genui.core.validation.DefaultOpenuiLangValidator;
import com.huawei.cloudsop.genui.core.validation.ValidationStatus;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Repair is exercised through execution and snapshots, without exposing round lifecycle steps. */
class RepairLoopTest {
    @Test
    void attemptLimitReturnsLastMergedProgramAndCompletedRounds() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger closed = new AtomicInteger();
        LlmTransport transport =
                new LlmTransport() {
                    @Override
                    public String post(String body) {
                        throw new AssertionError("Only streaming requests are expected");
                    }

                    @Override
                    public InputStream postStream(String body) {
                        assertEquals(calls.get(), closed.get(), "previous request must be closed");
                        int index = calls.getAndIncrement();
                        assertTrue(index < 2, "one initial request and one repair are allowed");
                        String dsl = index == 0 ? "root = Bogus()" : "keep = TextContent(\"keep\")";
                        return new ByteArrayInputStream(stream(dsl)) {
                            private final AtomicBoolean released = new AtomicBoolean();

                            @Override
                            public void close() throws IOException {
                                super.close();
                                if (released.compareAndSet(false, true)) {
                                    closed.incrementAndGet();
                                }
                            }
                        };
                    }
                };
        List<String> output = new ArrayList<>();
        RepairLoop session = session(transport, output, 1);

        GenUiGenerationResult result = session.run("initial request");

        assertEquals(GenerationEndReason.VALIDATION_FAILED, result.endReason());
        assertEquals(ValidationStatus.INVALID, result.validationStatus());
        assertEquals("root = Bogus()\nkeep = TextContent(\"keep\")", result.dsl());
        assertEquals(2, result.rounds().size());
        assertEquals(2, closed.get());
        assertTrue(String.join("", output).contains("keep = TextContent(\"keep\")"));
        GenUiGenerationResult snapshot = session.snapshot(GenerationEndReason.VALIDATION_FAILED);
        assertEquals(result.rounds(), snapshot.rounds(), "completed rounds must not be duplicated");
    }

    @Test
    void snapshotDuringRepairKeepsCompletedAndUnvalidatedCurrentRound() throws Exception {
        String patch = "helper = TextContent(\"new\");\nroot = Stack([helper])";
        CountDownLatch waiting = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        LlmTransport transport =
                new LlmTransport() {
                    @Override
                    public String post(String body) {
                        throw new AssertionError("Only streaming requests are expected");
                    }

                    @Override
                    public InputStream postStream(String body) {
                        if (calls.getAndIncrement() == 0) {
                            return new ByteArrayInputStream(stream("root = Bogus()"));
                        }
                        // Pause after the patch delta, before the terminal [DONE] frame.
                        return new ByteArrayInputStream(stream(patch)) {
                            @Override
                            public synchronized int available() {
                                // Prevent the character decoder from reading ahead into the blocked
                                // frame.
                                int pauseOffset =
                                        count
                                                - SseFrames.done()
                                                        .getBytes(StandardCharsets.UTF_8)
                                                        .length;
                                return pos < pauseOffset ? pauseOffset - pos : 0;
                            }

                            @Override
                            public synchronized int read(byte[] bytes, int offset, int length) {
                                int pauseOffset =
                                        count
                                                - SseFrames.done()
                                                        .getBytes(StandardCharsets.UTF_8)
                                                        .length;
                                if (pos < pauseOffset) {
                                    return super.read(
                                            bytes, offset, Math.min(length, pauseOffset - pos));
                                }
                                if (pos == pauseOffset) {
                                    waiting.countDown();
                                    try {
                                        assertTrue(finish.await(5, TimeUnit.SECONDS));
                                    } catch (InterruptedException error) {
                                        Thread.currentThread().interrupt();
                                        throw new AssertionError(error);
                                    }
                                }
                                return super.read(bytes, offset, length);
                            }
                        };
                    }
                };
        RepairLoop session = session(transport, new ArrayList<>(), 1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var running = executor.submit(() -> session.run("initial request"));
            try {
                assertTrue(waiting.await(5, TimeUnit.SECONDS), "repair must reach the pause");
                GenUiGenerationResult snapshot =
                        session.snapshot(GenerationEndReason.MODEL_REQUEST_FAILED);
                assertEquals(2, snapshot.rounds().size());
                assertEquals(
                        ValidationStatus.INVALID,
                        snapshot.rounds().get(0).validationResult().status());
                assertEquals(patch, snapshot.rounds().get(1).rawOutput());
                assertNull(snapshot.rounds().get(1).validationResult());
                assertEquals(
                        GenerationEndReason.MODEL_REQUEST_FAILED,
                        snapshot.rounds().get(1).endReason());
                assertTrue(snapshot.dsl().contains("helper = TextContent"));
            } finally {
                finish.countDown();
            }
            GenUiGenerationResult result = running.get(5, TimeUnit.SECONDS);
            assertEquals(ValidationStatus.VALID, result.validationStatus());
            assertEquals(2, result.rounds().size());
            assertEquals("true", result.validationResult().metadata().extra().get("repaired"));
        }
    }

    private static RepairLoop session(LlmTransport transport, List<String> output, int limit) {
        return new RepairLoop(
                new RepairLoop.Context(
                        "original system",
                        "original intent",
                        GenerationContractLoader.loadDefault(),
                        Map.of(),
                        limit),
                DefaultOpenuiLangValidator.INSTANCE,
                GenUiLlmConfig.defaults(),
                transport,
                () -> false,
                output::add);
    }

    private static byte[] stream(String content) {
        return (SseFrames.of(
                                Json.stringify(
                                        Map.of(
                                                "choices",
                                                List.of(
                                                        Map.of(
                                                                "delta",
                                                                Map.of("content", content))))))
                        + SseFrames.done())
                .getBytes(StandardCharsets.UTF_8);
    }
}
