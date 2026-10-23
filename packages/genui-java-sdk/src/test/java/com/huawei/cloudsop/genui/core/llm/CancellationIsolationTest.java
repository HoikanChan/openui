/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.huawei.cloudsop.genui.core.llm;

import static org.junit.jupiter.api.Assertions.*;

import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.core.llm.transport.*;
import com.huawei.cloudsop.genui.core.validation.ValidationStatus;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * #90 集成行为:一个请求被超时取消,不影响同一生成器上并发的另一个请求。
 */
class CancellationIsolationTest {

    private static String frame(String dsl) {
        return "data: " + Json.stringify(java.util.Map.of("choices",
                List.of(java.util.Map.of("delta", java.util.Map.of("content", dsl))))) + "\n\n";
    }

    /** 可手动推进的时钟;每个请求各自注册截止动作,按注册顺序保存以便逐个触发。 */
    static final class Deadlines {
        final AtomicLong now = new AtomicLong();
        final List<Runnable> actions = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        GenerationTiming timing() {
            return new GenerationTiming(now::get, (action, delay) -> {
                actions.add(action);
                return () -> {
                };
            });
        }

        void fireAll() {
            now.set(TimeUnit.SECONDS.toNanos(20));
            List.copyOf(actions).forEach(Runnable::run);
        }
    }

    @Test
    void cancellingOneRequestDoesNotDisturbAConcurrentRequest() throws Exception {
        Deadlines deadlines = new Deadlines();
        CountDownLatch reading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean firstCancelled = new AtomicBoolean();
        AtomicBoolean secondCancelled = new AtomicBoolean();
        AtomicBoolean secondCompleted = new AtomicBoolean();
        AtomicInteger calls = new AtomicInteger();

        LlmTransport transport = new LlmTransport() {
            public String post(String body) {
                throw new AssertionError("streaming only");
            }

            public InputStream postStream(String body) {
                throw new AssertionError("streaming only");
            }

            public LlmCall newCall(String body, boolean stream) {
                int call = calls.incrementAndGet();
                if (call == 1) {
                    return new LlmCall() {
                        public InputStream execute() {
                            return new InputStream() {
                                public int read() {
                                    throw new AssertionError("use bulk read");
                                }

                                public int read(byte[] bytes, int offset, int length) {
                                    reading.countDown();
                                    boolean interrupted = false;
                                    while (true) {
                                        try {
                                            release.await();
                                            break;
                                        } catch (InterruptedException error) {
                                            interrupted = true;
                                        }
                                    }
                                    if (interrupted) {
                                        Thread.currentThread().interrupt();
                                    }
                                    return -1;
                                }
                            };
                        }

                        public void cancel() {
                            firstCancelled.set(true);
                            release.countDown();
                        }
                    };
                }
                return new LlmCall() {
                    public InputStream execute() {
                        return new java.io.ByteArrayInputStream(
                                frame("root = Stack([])").getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
                            public synchronized int read(byte[] bytes, int offset, int length) {
                                int count = super.read(bytes, offset, length);
                                if (count == -1) {
                                    secondCompleted.set(true);
                                }
                                return count;
                            }
                        };
                    }

                    public void cancel() {
                        // close() 也走 cancel():正常读完后的关闭不算取消。
                        if (!secondCompleted.get()) {
                            secondCancelled.set(true);
                        }
                    }
                };
            }
        };

        var generator = GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), transport).withTiming(deadlines.timing());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                var blocked = executor.submit(() -> generator
                        .generateStream(UiGenerationRequest.builder().userInput("first").build(), e -> {
                        }));
                assertTrue(reading.await(3, TimeUnit.SECONDS), "first request must be reading");

                GenUiGenerationResult concurrent = generator.generateStream(
                        UiGenerationRequest.builder().userInput("second").build(), e -> {
                        });
                assertEquals(ValidationStatus.VALID, concurrent.validationStatus());
                assertEquals(GenerationEndReason.CHECK_PASSED, concurrent.endReason());
                assertFalse(secondCancelled.get(), "the concurrent request must not be cancelled");

                deadlines.fireAll();
                assertEquals(GenerationEndReason.TIMEOUT, blocked.get(3, TimeUnit.SECONDS).endReason());
                assertTrue(firstCancelled.get(), "only the timed-out request is cancelled");
                assertFalse(secondCancelled.get(), "cancelling the first request must not touch the second");
            } finally {
                deadlines.fireAll();
                release.countDown();
            }
        }
    }
}
