/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.eval.langsmith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.Json;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

class LangSmithClientContractTest {
    private HttpServer server;
    private String baseUrl;
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    private final AtomicInteger failBatchCount = new AtomicInteger();

    private record RecordedRequest(String method, String path, String apiKey, String body) {}

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/datasets", this::handleDatasets);
        server.createContext("/api/v1/examples", this::handleExamples);
        server.createContext("/api/v1/sessions", this::handleSessions);
        server.createContext("/api/v1/runs/batch", this::handleRunsBatch);
        server.createContext("/api/v1/feedback", this::handleFeedback);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void performsDatasetSessionBatchRunsAndSummaryFeedbackAgainstRestApiV1() {
        LangSmithConfig config =
                new LangSmithConfig(
                        baseUrl, "ls-test-key", "ws-1", null, null, Duration.ofSeconds(5), 2);
        LangSmithClient client = new LangSmithClient(config);

        LangSmithClient.DatasetRecord ds =
                client.upsertDataset("genui-repair", "desc", Map.of("contract", "1.1.6"));
        assertEquals("ds-1", ds.id());

        LangSmithClient.ExampleRecord ex =
                client.createExample(
                        ds.id(),
                        Map.of("caseId", "GEN-001", "hasDataModel", false),
                        Map.of("expectedStatus", "INVALID"),
                        Map.of("contentSha256", "abc"));
        assertEquals("ex-1", ex.id());

        LangSmithClient.SessionRecord session =
                client.createExperimentSession(
                        "repair-exp-1", ds.id(), Map.of("model", "local-qwen"));
        assertEquals("sess-1", session.id());

        String rootOrder = DottedOrder.root(Instant.parse("2026-11-28T01:02:03.123456Z"), "run-1");
        String childOrder =
                DottedOrder.child(rootOrder, Instant.parse("2026-11-28T01:02:04.000001Z"), "run-2");
        assertTrue(childOrder.startsWith(rootOrder + "."));
        assertEquals("20261128T010203123456Zrun-1", rootOrder);

        failBatchCount.set(1);
        try (BatchRunBuffer buffer = new BatchRunBuffer(client, 10, 50L)) {
            buffer.enqueuePost(
                    Map.of(
                            "id",
                            "run-1",
                            "trace_id",
                            "run-1",
                            "dotted_order",
                            rootOrder,
                            "session_id",
                            session.id(),
                            "reference_example_id",
                            ex.id()));
            buffer.enqueuePatch(Map.of("id", "run-1", "outputs", Map.of("status", "repaired")));
        }

        client.createRunScoreFeedback("run-1", session.id(), "repaired", 1.0, "passed");
        client.createSummaryFeedback(
                session.id(), "main_repair_rate", 44.0 / 49.0, "repaired/valid");
        client.finishSession(session.id(), Instant.now(), Map.of("done", true));

        assertTrue(requests.stream().allMatch(r -> "ls-test-key".equals(r.apiKey())));
        List<RecordedRequest> feedbackCalls =
                requests.stream().filter(r -> r.path().startsWith("/api/v1/feedback")).toList();
        assertEquals(2, feedbackCalls.size());
        Map<String, Object> summaryPayload =
                Json.asObject(Json.parse(feedbackCalls.get(1).body()), "summary feedback");
        assertFalse(summaryPayload.containsKey("run_id"));
        assertEquals("sess-1", summaryPayload.get("session_id"));
        assertEquals("main_repair_rate", summaryPayload.get("key"));
        assertEquals(0.898, ((Number) summaryPayload.get("score")).doubleValue(), 1e-6);
    }

    @Test
    void dumpsFailedBatchToDiskWhenRetriesExhausted(@TempDir Path failedDir) throws IOException {
        failBatchCount.set(5);
        LangSmithConfig config =
                new LangSmithConfig(
                        baseUrl, "ls-test-key", null, null, failedDir, Duration.ofSeconds(5), 0);
        LangSmithClient client = new LangSmithClient(config);

        try (BatchRunBuffer buffer = new BatchRunBuffer(client, 5, 20L)) {
            buffer.enqueuePost(Map.of("id", "run-fail"));
        }

        List<Path> files = new ArrayList<>();
        try (var stream = Files.list(failedDir)) {
            stream.forEach(files::add);
        }
        assertEquals(1, files.size());
        assertTrue(Files.readString(files.getFirst()).contains("run-fail"));
    }

    @Test
    void sharedOpenAiTransportHandlesLocalLoopbackAndOptionalApiKey() {
        SharedOpenAiTransport local =
                new SharedOpenAiTransport(
                        "http://localhost:11434/v1/", null, "http://proxy.corp:8080");
        assertEquals("http://localhost:11434/v1/chat/completions", local.endpointUrl());
        assertTrue(SharedOpenAiTransport.isLoopbackHost("localhost"));
        assertTrue(SharedOpenAiTransport.isLoopbackHost("127.0.0.1"));
        assertFalse(SharedOpenAiTransport.isLoopbackHost("api.smith.langchain.com"));
    }

    private void handleDatasets(HttpExchange exchange) throws IOException {
        record(exchange);
        if ("GET".equals(exchange.getRequestMethod())) {
            respond(exchange, 200, "[]");
        } else {
            respond(exchange, 200, "{\"id\":\"ds-1\",\"name\":\"genui-repair\"}");
        }
    }

    private void handleExamples(HttpExchange exchange) throws IOException {
        record(exchange);
        if ("GET".equals(exchange.getRequestMethod())) {
            respond(exchange, 200, "[]");
        } else {
            respond(
                    exchange,
                    200,
                    "{\"id\":\"ex-1\",\"dataset_id\":\"ds-1\",\"inputs\":{\"caseId\":\"GEN-001\"}}");
        }
    }

    private void handleSessions(HttpExchange exchange) throws IOException {
        record(exchange);
        respond(exchange, 200, "{\"id\":\"sess-1\",\"name\":\"repair-exp-1\"}");
    }

    private void handleRunsBatch(HttpExchange exchange) throws IOException {
        record(exchange);
        if (failBatchCount.getAndDecrement() > 0) {
            respond(exchange, 429, "{\"detail\":\"rate limited\"}");
            return;
        }
        respond(exchange, 202, "{\"status\":\"accepted\"}");
    }

    private void handleFeedback(HttpExchange exchange) throws IOException {
        record(exchange);
        respond(exchange, 200, "{\"id\":\"fb-1\"}");
    }

    private void record(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String path = exchange.getRequestURI().toString();
        String apiKey = exchange.getRequestHeaders().getFirst("x-api-key");
        requests.add(new RecordedRequest(exchange.getRequestMethod(), path, apiKey, body));
    }

    private static void respond(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
