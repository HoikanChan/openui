/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.llm.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.llm.GenUiLlmConfig;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

class RestfulLlmTransportIT {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
        System.clearProperty("openui.bsp.restclient.baseUrl");
    }

    @Test
    void postReturnsResponseBodyAndSendsExtraHeaders() throws Exception {
        startServer(exchange -> {
            assertEquals("header-value", exchange.getRequestHeaders().getFirst("X-Test"));
            assertEquals("{\"hello\":true}",
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        GenUiLlmConfig config = GenUiLlmConfig.builder().endpoint("/chat").putHeader("X-Test", "header-value").build();

        assertEquals("{\"ok\":true}", new RestfulLlmTransport(config).post("{\"hello\":true}"));
    }

    @Test
    void postStreamSendsStreamHeaderAndReturnsStreamBody() throws Exception {
        startServer(exchange -> {
            assertEquals("true", exchange.getRequestHeaders().getFirst("SUPPORT_STREAM_CONTENT_FOR_SDK"));
            byte[] body = "data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        try (InputStream stream = new RestfulLlmTransport(GenUiLlmConfig.builder().endpoint("/chat").build())
                .postStream("{}")) {
            assertEquals("data: [DONE]\n\n", new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void nonOkStatusThrowsTransportException() throws Exception {
        startServer(exchange -> {
            exchange.sendResponseHeaders(503, 0);
            exchange.close();
        });

        LlmTransportException error = assertThrows(LlmTransportException.class,
                () -> new RestfulLlmTransport(GenUiLlmConfig.builder().endpoint("/chat").build()).post("{}"));

        assertTrue(error.getMessage().contains("503"));
    }

    private void startServer(ThrowingHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat", exchange -> {
            try {
                handler.handle(exchange);
            } catch (Exception error) {
                byte[] body = error.toString().getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(500, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            }
        });
        server.start();
        System.setProperty("openui.bsp.restclient.baseUrl", "http://127.0.0.1:" + server.getAddress().getPort());
    }

    private interface ThrowingHandler {
        void handle(com.sun.net.httpserver.HttpExchange exchange) throws Exception;
    }
}
