/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.huawei.cloudsop.genui.core.llm;

import static org.junit.jupiter.api.Assertions.*;

import com.huawei.cloudsop.genui.core.GenerationSdkException;
import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.core.llm.transport.*;
import com.huawei.cloudsop.genui.core.validation.ValidationStatus;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * #90/#102 取消隔离:一个调用方被中断而取消自己的在途请求,不影响同一生成器上并发的另一个请求。
 */
class CancellationIsolationTest {

    @Test
    void interruptingOneCallerDoesNotCancelAConcurrentRequest() throws Exception {
        CountDownLatch reading = new CountDownLatch(1);
        CountDownLatch firstCancelled = new CountDownLatch(1);
        AtomicBoolean secondCancelled = new AtomicBoolean();
        AtomicBoolean secondFinished = new AtomicBoolean();
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
                        public InputStream execute() throws LlmTransportException {
                            reading.countDown();
                            try {
                                firstCancelled.await();
                            } catch (InterruptedException error) {
                                Thread.currentThread().interrupt();
                            }
                            throw new LlmTransportException("cancelled");
                        }

                        public void cancel() {
                            firstCancelled.countDown();
                        }
                    };
                }
                return new LlmCall() {
                    public InputStream execute() {
                        return new ByteArrayInputStream(
                                (frame("root = Stack([])") + "data: [DONE]").getBytes(StandardCharsets.UTF_8)) {
                            public synchronized int read(byte[] bytes, int offset, int length) {
                                int count = super.read(bytes, offset, length);
                                if (count == -1) {
                                    secondFinished.set(true);
                                }
                                return count;
                            }
                        };
                    }

                    public void cancel() {
                        // close() 也会走 cancel():正常读完后的关闭不算取消。
                        if (!secondFinished.get()) {
                            secondCancelled.set(true);
                        }
                    }
                };
            }
        };
        GenUiGenerator generator = GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), transport);
        AtomicReference<Throwable> callerFailure = new AtomicReference<>();
        Thread firstCaller = Thread.ofVirtual().unstarted(() -> {
            try {
                generator.generateStream(request("first"), event -> {
                });
            } catch (Throwable error) {
                callerFailure.set(error);
            }
        });

        firstCaller.start();
        try {
            assertTrue(reading.await(3, TimeUnit.SECONDS), "first request must be reading");
            firstCaller.interrupt();
            firstCaller.join(TimeUnit.SECONDS.toMillis(3));
            assertFalse(firstCaller.isAlive(), "the interrupted caller must return");
            assertInstanceOf(GenerationSdkException.class, callerFailure.get());
            assertTrue(firstCancelled.await(3, TimeUnit.SECONDS),
                    "the interrupted caller's in-flight request is cancelled");

            GenUiGenerationResult concurrent = generator.generateStream(request("second"), event -> {
            });
            assertEquals(ValidationStatus.VALID, concurrent.validationStatus());
            assertEquals(GenerationEndReason.CHECK_PASSED, concurrent.endReason());
            assertFalse(secondCancelled.get(), "the concurrent request must not be cancelled");
        } finally {
            firstCancelled.countDown();
            firstCaller.join(TimeUnit.SECONDS.toMillis(3));
        }
    }

    private static UiGenerationRequest request(String input) {
        return UiGenerationRequest.builder().userInput(input).build();
    }

    private static String frame(String dsl) {
        return "data: " + Json.stringify(Map.of("choices", List.of(Map.of("delta", Map.of("content", dsl))))) + "\n\n";
    }
}
