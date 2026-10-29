/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.huawei.cloudsop.genui.core.llm.transport;

import static org.junit.jupiter.api.Assertions.*;

import com.huawei.cloudsop.genui.core.llm.GenUiLlmConfig;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.*;

class HttpClientLlmTransportTest {
    @Test
    void cancelsBeforeResponseHeaders() throws Exception {
        checkCancellation(false);
    }

    @Test
    void cancelsBlockedBodyRead() throws Exception {
        checkCancellation(true);
    }

    private void checkCancellation(boolean headers) throws Exception {
        CountDownLatch received = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/chat",
                exchange -> {
                    try {
                        exchange.getRequestBody().readAllBytes();
                        if (headers) {
                            exchange.sendResponseHeaders(200, 0);
                            exchange.getResponseBody().write('x');
                            exchange.getResponseBody().flush();
                        }
                        received.countDown();
                        release.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        exchange.close();
                    }
                });
        server.start();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var transport =
                    new HttpClientLlmTransport(
                            GenUiLlmConfig.builder()
                                    .endpoint(
                                            "http://127.0.0.1:"
                                                    + server.getAddress().getPort()
                                                    + "/chat")
                                    .build());
            try (LlmCall call = transport.newCall("{}", true)) {
                CountDownLatch reading = new CountDownLatch(1);
                Future<?> blocked =
                        executor.submit(
                                () -> {
                                    try (InputStream body = call.execute()) {
                                        if (headers) {
                                            assertEquals('x', body.read());
                                            reading.countDown();
                                        }
                                        body.read();
                                        fail("cancelled request must not complete normally");
                                    } catch (java.io.IOException | LlmTransportException expected) {
                                        // Cancellation releases the blocked caller before the
                                        // server responds.
                                    }
                                });
                assertTrue(received.await(3, TimeUnit.SECONDS));
                if (headers) assertTrue(reading.await(3, TimeUnit.SECONDS));
                call.cancel();
                call.cancel();
                blocked.get(2, TimeUnit.SECONDS);
            } finally {
                release.countDown();
            }
        } finally {
            release.countDown();
            server.stop(0);
        }
    }
}
