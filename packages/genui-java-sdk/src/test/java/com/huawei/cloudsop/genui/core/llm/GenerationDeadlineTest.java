/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.huawei.cloudsop.genui.core.llm;

import static org.junit.jupiter.api.Assertions.*;

import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.core.llm.transport.*;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * #102: the 20-second budget no longer cancels a running stage. The initial generation and the first incremental repair
 * always run to completion; only the decision to start the second and later repair rounds consults the budget.
 */
class GenerationDeadlineTest {

    // ── 首轮生成慢过预算仍应正常完成 ─────────────────────────────────────

    @Test
    void slowValidInitialStreamIsNotCancelledOrDowngraded() {
        ManualTime time = new ManualTime();
        ScriptedTransport transport = new ScriptedTransport()
                .stream(() -> advancing(frame("root = Stack([])") + "data: [DONE]", time, 45));
        CopyOnWriteArrayList<RenderStreamEnvelope> events = new CopyOnWriteArrayList<>();
        AtomicInteger handoffs = new AtomicInteger();

        GenUiGenerationResult result = generator(transport, time).generateStream(request("slow initial"), events::add,
                (original, reason, sink) -> {
                    handoffs.incrementAndGet();
                    return new GenUiGenerationResult("fallback", original.response());
                });

        assertEquals(GenerationEndReason.CHECK_PASSED, result.endReason());
        assertEquals("root = Stack([])", result.dsl());
        assertEquals(0, handoffs.get(), "a slow but valid stream must not fall back to Markdown");
        assertEquals(List.of("dataModel", "dsl", "done"), events.stream().map(RenderStreamEnvelope::type).toList());
    }

    // ── 首轮修复固定执行 ────────────────────────────────────────────────

    @Test
    void firstRepairStillRunsAfterInitialExceedsBudget() {
        ManualTime time = new ManualTime();
        ScriptedTransport transport = new ScriptedTransport().stream(() -> {
            time.advanceTo(30);
            return input(frame("root = Bogus()\n") + "data: [DONE]");
        }).stream(() -> input(frame("root = Stack([])") + "data: [DONE]"));

        GenUiGenerationResult result = generator(transport, time).generateStream(request("slow invalid initial"),
                event -> {
                });

        assertEquals(GenerationEndReason.CHECK_PASSED, result.endReason(),
                "the first repair is not gated by the 20-second budget");
        assertEquals("root = Stack([])", result.dsl());
        assertEquals(2, transport.modelCalls.get(), "initial round + one first repair");
        assertEquals(2, result.rounds().size());
    }

    @Test
    void firstRepairMayCrossTheBudgetAndStillSucceed() {
        ManualTime time = new ManualTime();
        ScriptedTransport transport = new ScriptedTransport().stream(() -> {
            time.advanceTo(10);
            return input(frame("root = Bogus()\n") + "data: [DONE]");
        }).stream(() -> {
            time.advanceTo(35);
            return input(frame("root = Stack([])") + "data: [DONE]");
        });

        GenUiGenerationResult result = generator(transport, time)
                .generateStream(request("repair crosses budget"), event -> {
                });

        assertEquals(GenerationEndReason.CHECK_PASSED, result.endReason(),
                "a repair that starts before the budget expires may run past it");
        assertEquals("root = Stack([])", result.dsl());
        assertEquals(2, transport.modelCalls.get());
    }

    @Test
    void invalidFirstRepairAtExactBudgetBoundaryFallsBackTimeoutWithoutSecondRepair() {
        ManualTime time = new ManualTime();
        ScriptedTransport transport = new ScriptedTransport().stream(() -> {
            time.advanceTo(20); // exact boundary: the first repair is still allowed to start
            return input(frame("root = Bogus()\n") + "data: [DONE]");
        }).stream(() -> {
            time.advanceTo(20);
            return input(frame("root = StillBogus()\n") + "data: [DONE]");
        });
        AtomicInteger handoffs = new AtomicInteger();

        GenUiGenerationResult result = generator(transport, time).generateStream(request("invalid first repair"),
                event -> {
                }, (original, reason, sink) -> {
                    handoffs.incrementAndGet();
                    assertEquals(GenerationEndReason.TIMEOUT, reason);
                    return new GenUiGenerationResult("fallback", original.response());
                });

        assertEquals(GenerationEndReason.TIMEOUT, result.endReason());
        assertEquals(2, transport.modelCalls.get(),
                "the first repair runs, but no second repair is requested at the 20-second boundary");
        assertEquals(2, result.rounds().size());
        assertEquals(1, handoffs.get());
    }

    @Test
    void repairStartedBeforeBudgetMayCompleteAfterIt() {
        ManualTime time = new ManualTime();
        ScriptedTransport transport = new ScriptedTransport().stream(() -> {
            time.advanceTo(1);
            return input(frame("root = Bogus()\n") + "data: [DONE]");
        }).stream(() -> {
            time.advanceTo(10);
            return input(frame("root = StillBogus()\n") + "data: [DONE]");
        }).stream(() -> {
            time.advanceTo(40);
            return input(frame("root = Stack([])") + "data: [DONE]");
        });

        GenUiGenerationResult result = generator(transport, time).generateStream(request("later repair"),
                event -> {
                });

        assertEquals(GenerationEndReason.CHECK_PASSED, result.endReason(),
                "the second repair was started before the budget expired, so it may finish after it");
        assertEquals("root = Stack([])", result.dsl());
        assertEquals(3, transport.modelCalls.get());
        assertEquals(3, result.rounds().size());
    }

    // ── 模型失败与超时原因互不混淆 ──────────────────────────────────────

    @Test
    void modelFailureAfterBudgetRemainsModelRequestFailed() {
        ManualTime time = new ManualTime();
        ScriptedTransport transport = new ScriptedTransport().stream(() -> {
            time.advanceTo(30);
            throw new LlmTransportException("connection dropped");
        });
        AtomicInteger handoffs = new AtomicInteger();

        GenUiGenerationResult result = generator(transport, time).generateStream(request("model failure"), event -> {
        }, (original, reason, sink) -> {
            handoffs.incrementAndGet();
            return new GenUiGenerationResult("fallback", original.response());
        });

        assertEquals(GenerationEndReason.MODEL_REQUEST_FAILED, result.endReason(),
                "a transport failure stays a model failure even after 20 seconds");
        assertEquals(1, handoffs.get());
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private static GenUiGenerator generator(LlmTransport transport, ManualTime time) {
        return GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), transport).withTiming(time.timing());
    }

    private static UiGenerationRequest request(String input) {
        return UiGenerationRequest.builder().userInput(input).build();
    }

    private static String syncContent(String content) {
        return Json.stringify(Map.of("choices", List.of(Map.of("message", Map.of("content", content)))));
    }

    private static String frame(String delta) {
        return "data: " + Json.stringify(Map.of("choices", List.of(Map.of("delta", Map.of("content", delta)))))
                + "\n\n";
    }

    private static InputStream input(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    /** Advances the fake clock when the stream reaches EOF, i.e. after the initial round's deltas were emitted. */
    private static InputStream advancing(String text, ManualTime time, long seconds) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)) {
            public synchronized int read(byte[] bytes, int offset, int length) {
                int count = super.read(bytes, offset, length);
                if (count == -1) {
                    time.advanceTo(seconds);
                }
                return count;
            }
        };
    }

    @FunctionalInterface
    private interface StreamResponse {
        InputStream get() throws LlmTransportException;
    }

    /** Public-API transport double: each model request consumes one scripted sync or stream response in order. */
    private static final class ScriptedTransport implements LlmTransport {
        private final Deque<StreamResponse> streams = new ArrayDeque<>();
        final AtomicInteger modelCalls = new AtomicInteger();

        ScriptedTransport stream(StreamResponse response) {
            streams.add(response);
            return this;
        }

        @Override
        public String post(String body) {
            throw new AssertionError("DSL requests are opened through newCall");
        }

        @Override
        public InputStream postStream(String body) {
            throw new AssertionError("DSL requests are opened through newCall");
        }

        /** Model calls arrive here; the independent Markdown fallback is answered without consuming the script. */
        @Override
        public LlmCall newCall(String body, boolean stream) {
            if (body.contains("Markdown only")) {
                return call(() -> input(stream ? frame("# fallback") + "data: [DONE]" : syncContent("# fallback")));
            }
            int callNumber = modelCalls.incrementAndGet();
            if (!stream) {
                throw new AssertionError("non-Markdown sync requests are unexpected in this stream test #"
                        + callNumber);
            }
            StreamResponse response = streams.poll();
            if (response == null) {
                throw new AssertionError("unexpected stream model request #" + callNumber);
            }
            return call(response);
        }

        private static LlmCall call(StreamResponse body) {
            return new LlmCall() {
                public InputStream execute() throws LlmTransportException {
                    return body.get();
                }

                public void cancel() {
                }
            };
        }
    }

    /**
     * Manual clock and timer for deterministic tests. The scheduler members are retained for compatibility with
     * {@code GenUiGeneratorRepairTest}; under #102 production no longer installs a 20-second cancellation.
     */
    static final class ManualTime {
        final AtomicLong now = new AtomicLong();
        final AtomicInteger schedules = new AtomicInteger();
        volatile Runnable fire;

        GenerationTiming timing() {
            return new GenerationTiming(now::get, (action, delay) -> {
                schedules.incrementAndGet();
                fire = action;
                return () -> {
                };
            });
        }

        void advanceTo(long seconds) {
            now.set(TimeUnit.SECONDS.toNanos(seconds));
        }

        /** Fire a scheduled deadline if production still installed one; a no-op once no timer is scheduled. */
        void expire() {
            advanceTo(20);
            Runnable action = fire;
            if (action != null) {
                action.run();
            }
        }
    }
}
