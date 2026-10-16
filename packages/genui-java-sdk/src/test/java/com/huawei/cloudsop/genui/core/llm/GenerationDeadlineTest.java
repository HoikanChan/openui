/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.huawei.cloudsop.genui.core.llm;

import static org.junit.jupiter.api.Assertions.*;

import com.huawei.cloudsop.genui.core.llm.transport.*;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

class GenerationDeadlineTest {
    @Test
    void synchronousRepairSharesOriginalDeadlineAndMarkdownStartsOutsideIt() throws Exception {
        ManualTime time = new ManualTime();
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch repairStarted = new CountDownLatch(1);
        CountDownLatch repairCancelled = new CountDownLatch(1);
        LlmTransport transport = new LlmTransport() {
            public String post(String body) {
                throw new AssertionError();
            }
            public InputStream postStream(String body) {
                throw new AssertionError();
            }
            public LlmCall newCall(String body, boolean stream) {
                int callNumber = calls.incrementAndGet();
                return new LlmCall() {
                    public InputStream execute() throws LlmTransportException {
                        if (callNumber == 2) {
                            repairStarted.countDown();
                            try {
                                repairCancelled.await();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            throw new LlmTransportException("cancelled repair");
                        }
                        if (callNumber == 1)
                            time.now.set(TimeUnit.SECONDS.toNanos(19));
                        else {
                            assertTrue(body.contains("Markdown only"));
                            time.now.set(TimeUnit.MINUTES.toNanos(2));
                        }
                        String content = callNumber == 1 ? "root = UnknownComponent([])" : "# Fallback";
                        String response = com.huawei.cloudsop.genui.core.Json.stringify(java.util.Map.of("choices",
                                List.of(java.util.Map.of("message", java.util.Map.of("content", content)))));
                        return new java.io.ByteArrayInputStream(
                                response.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    }
                    public void cancel() {
                        if (callNumber == 2)
                            repairCancelled.countDown();
                    }
                };
            }
        };
        var validation = new com.huawei.cloudsop.genui.core.validation.GenUiValidationConfig(
                com.huawei.cloudsop.genui.core.validation.ValidationConfigMode.FINAL_ONLY,
                com.huawei.cloudsop.genui.core.validation.RepairPolicyKind.FINAL_REPAIR);
        var generator = GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), transport, validation, null)
                .withTiming(time.timing());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var future = executor.submit(() -> generator.generate(null));
            assertTrue(repairStarted.await(3, TimeUnit.SECONDS));
            time.expire();
            var result = future.get(3, TimeUnit.SECONDS);
            assertEquals(GenerationEndReason.TIMEOUT, result.endReason());
            assertEquals("root = Stack([MarkDownRenderer(\"# Fallback\")])", result.dsl());
            assertEquals(3, calls.get());
            assertEquals(1, time.schedules.get(), "repair and Markdown must not reset the DSL timer");
        }
    }

    @Test
    void deadlineIsInstalledBeforeTheInitialHostWrite() throws Exception {
        ManualTime time = new ManualTime();
        CountDownLatch writing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        LlmTransport transport = new LlmTransport() {
            public String post(String body) {
                throw new AssertionError();
            }
            public InputStream postStream(String body) {
                throw new AssertionError();
            }
            public LlmCall newCall(String body, boolean stream) {
                calls.incrementAndGet();
                throw new AssertionError();
            }
        };
        var generator = GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), transport).withTiming(time.timing());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var result = executor.submit(() -> generator.generateStream(null, event -> {
                if ("dataModel".equals(event.type())) {
                    writing.countDown();
                    while (release.getCount() > 0) {
                        try {
                            release.await();
                        } catch (InterruptedException ignored) {
                        }
                    }
                }
            }, (request, reason, sink) -> new GenUiGenerationResult("fallback", request.response())));
            try {
                assertTrue(writing.await(3, TimeUnit.SECONDS));
                assertNotNull(time.fire);
                executor.submit(time::expire).get(1, TimeUnit.SECONDS);
            } finally {
                release.countDown();
            }
            assertEquals(GenerationEndReason.TIMEOUT, result.get(3, TimeUnit.SECONDS).endReason());
            assertEquals(0, calls.get());
        }
    }

    @Test
    void slowConsumerCannotPreventTransportCancellation() throws Exception {
        ManualTime time = new ManualTime();
        CountDownLatch consuming = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch cancelled = new CountDownLatch(1);
        LlmCall call = new LlmCall() {
            public InputStream execute() {
                return new java.io.ByteArrayInputStream(
                        frame("root = Stack([])").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            public void cancel() {
                cancelled.countDown();
            }
        };
        var generator = GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), single(call)).withTiming(time.timing());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var result = executor.submit(() -> generator.generateStream(null, event -> {
                if ("dsl".equals(event.type())) {
                    consuming.countDown();
                    while (release.getCount() > 0) {
                        try {
                            release.await();
                        } catch (InterruptedException ignored) {
                        }
                    }
                }
            }, (request, reason, sink) -> new GenUiGenerationResult("fallback", request.response())));
            try {
                assertTrue(consuming.await(3, TimeUnit.SECONDS));
                var expiration = executor.submit(time::expire);
                assertTrue(cancelled.await(1, TimeUnit.SECONDS),
                        "consumer callbacks must not hold the cancellation lock");
                expiration.get(1, TimeUnit.SECONDS);
            } finally {
                release.countDown();
            }
            assertEquals(GenerationEndReason.TIMEOUT, result.get(3, TimeUnit.SECONDS).endReason());
        }
    }

    @Test
    void completionJustBeforeDeadlineWinsAndKeepsRawOutput() {
        ManualTime time = new ManualTime();
        var events = new CopyOnWriteArrayList<RenderStreamEnvelope>();
        var transport = immediate(time, 19);
        var result = GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), transport).withTiming(time.timing())
                .generateStream(UiGenerationRequest.builder().build(), events::add, (request, reason, sink) -> {
                    throw new AssertionError("unexpected fallback");
                });
        time.expire();
        assertEquals(GenerationEndReason.CHECK_PASSED, result.endReason());
        assertEquals(List.of("dataModel", "dsl", "done"), events.stream().map(RenderStreamEnvelope::type).toList());
        assertEquals("root = Stack([])", events.get(1).content());
    }

    @Test
    void exactDeadlineIsCheckedEvenIfSchedulerHasNotRun() {
        ManualTime time = new ManualTime();
        AtomicInteger handoffs = new AtomicInteger();
        var generator = GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), immediate(time, 20))
                .withTiming(time.timing());
        var result = generator.generateStream(UiGenerationRequest.builder().build(), e -> {
        }, (request, reason, sink) -> {
            handoffs.incrementAndGet();
            return new GenUiGenerationResult("fallback", request.response());
        });
        assertEquals(GenerationEndReason.TIMEOUT, result.endReason());
        assertEquals(1, handoffs.get());
    }

    @Test
    void blockedReadIsCancelledAndLateFragmentsCannotFollowReplacement() throws Exception {
        ManualTime time = new ManualTime();
        CountDownLatch reading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch deliveredLate = new CountDownLatch(1);
        AtomicBoolean cancelled = new AtomicBoolean();
        LlmCall call = new LlmCall() {
            public InputStream execute() {
                return new InputStream() {
                    int reads;
                    public int read() {
                        throw new AssertionError("use bulk read");
                    }
                    public int read(byte[] bytes, int offset, int len) {
                        if (reads++ > 0)
                            return -1;
                        reading.countDown();
                        boolean interrupted = false;
                        while (true) {
                            try {
                                release.await();
                                break;
                            } catch (InterruptedException e) {
                                interrupted = true;
                            }
                        }
                        if (interrupted)
                            Thread.currentThread().interrupt();
                        byte[] late = frame("root = Stack([])").getBytes(java.nio.charset.StandardCharsets.UTF_8);
                        System.arraycopy(late, 0, bytes, offset, late.length);
                        deliveredLate.countDown();
                        return late.length;
                    }
                };
            }
            public void cancel() {
                cancelled.set(true);
                release.countDown();
            }
        };
        var generator = GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), single(call)).withTiming(time.timing());
        var events = new CopyOnWriteArrayList<RenderStreamEnvelope>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var future = executor.submit(() -> generator.generateStream(null, events::add, (original, reason, sink) -> {
                assertTrue(cancelled.get());
                return new GenUiGenerationResult("fallback", original.response());
            }));
            assertTrue(reading.await(3, TimeUnit.SECONDS));
            time.expire();
            assertEquals(GenerationEndReason.TIMEOUT, future.get(3, TimeUnit.SECONDS).endReason());
            assertTrue(deliveredLate.await(3, TimeUnit.SECONDS));
            assertEquals(List.of("dataModel", "replace", "done"),
                    events.stream().map(RenderStreamEnvelope::type).toList());
        }
    }

    private static LlmTransport immediate(ManualTime time, int seconds) {
        return single(new LlmCall() {
            public InputStream execute() {
                return new java.io.ByteArrayInputStream(
                        frame("root = Stack([])").getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
                    public synchronized int read(byte[] b, int off, int len) {
                        int count = super.read(b, off, len);
                        if (count == -1)
                            time.now.set(TimeUnit.SECONDS.toNanos(seconds));
                        return count;
                    }
                };
            }
            public void cancel() {
            }
        });
    }

    private static String frame(String dsl) {
        return "data: " + com.huawei.cloudsop.genui.core.Json.stringify(
                java.util.Map.of("choices", List.of(java.util.Map.of("delta", java.util.Map.of("content", dsl)))))
                + "\n\n";
    }

    private static LlmTransport single(LlmCall call) {
        return new LlmTransport() {
            public String post(String body) {
                throw new AssertionError();
            }
            public InputStream postStream(String body) {
                throw new AssertionError();
            }
            public LlmCall newCall(String body, boolean stream) {
                return call;
            }
        };
    }

    @Test
    void timeoutBeforeHeadersCancelsAndHandsOffOnce() throws Exception {
        ManualTime time = new ManualTime();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch cancelled = new CountDownLatch(1);
        LlmTransport transport = new LlmTransport() {
            public String post(String body) {
                throw new AssertionError();
            }
            public InputStream postStream(String body) {
                throw new AssertionError();
            }
            public LlmCall newCall(String body, boolean stream) {
                return new LlmCall() {
                    public InputStream execute() throws LlmTransportException {
                        entered.countDown();
                        try {
                            cancelled.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        throw new LlmTransportException("cancelled");
                    }
                    public void cancel() {
                        cancelled.countDown();
                    }
                };
            }
        };
        var generator = GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), transport).withTiming(time.timing());
        var events = new CopyOnWriteArrayList<RenderStreamEnvelope>();
        AtomicInteger handoffs = new AtomicInteger();
        var request = UiGenerationRequest.builder().userInput("original").build();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var result = executor
                    .submit(() -> generator.generateStream(request, events::add, (original, reason, sink) -> {
                        assertSame(request, original);
                        assertEquals(GenerationEndReason.TIMEOUT, reason);
                        assertEquals(0, cancelled.getCount());
                        handoffs.incrementAndGet();
                        time.now.addAndGet(TimeUnit.MINUTES.toNanos(1));
                        sink.accept(RenderStreamEnvelope.dsl(1, "markdown page"));
                        return new GenUiGenerationResult("markdown page", original.response());
                    }));
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            time.expire();
            assertEquals(GenerationEndReason.TIMEOUT, result.get(3, TimeUnit.SECONDS).endReason());
            time.fire.run();
            assertEquals(1, handoffs.get());
            assertEquals(List.of("dataModel", "replace", "dsl", "done"),
                    events.stream().map(RenderStreamEnvelope::type).toList());
        }
    }

    static final class ManualTime {
        final AtomicLong now = new AtomicLong();
        final AtomicInteger schedules = new AtomicInteger();
        volatile Runnable fire;
        GenerationTiming timing() {
            return new GenerationTiming(now::get, (action, delay) -> {
                schedules.incrementAndGet();
                assertTrue(delay <= TimeUnit.SECONDS.toNanos(20));
                fire = action;
                return () -> {
                };
            });
        }
        void expire() {
            now.set(TimeUnit.SECONDS.toNanos(20));
            fire.run();
        }
    }
}
