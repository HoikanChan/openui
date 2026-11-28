/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.eval.dataset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.eval.langsmith.LangSmithClient;
import com.huawei.cloudsop.genui.eval.langsmith.LangSmithConfig;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

class DatasetSyncerTest {
    private HttpServer server;
    private String baseUrl;
    private final Map<String, Map<String, Object>> datasetsByName = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> examplesById = new ConcurrentHashMap<>();
    private final AtomicInteger seq = new AtomicInteger();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/datasets", this::handleDatasets);
        server.createContext("/api/v1/examples", this::handleExamples);
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
    void loadsRealRepairCasesAndValidationTestWithExactCountsAndDataModelSemantics(
            @TempDir Path tempPack) throws IOException {
        Path repoRoot = Path.of("..").toAbsolutePath().normalize().getParent();
        List<CorpusLoaders.LoadedRepairCase> repairCases =
                CorpusLoaders.loadRepairCases(
                        repoRoot.resolve("packages/genui-java-sdk/repair-cases"), null);
        assertEquals(25, repairCases.size());

        CorpusLoaders.LoadedRepairCase withDm =
                repairCases.stream()
                        .filter(c -> "GEN-00109".equals(c.id()))
                        .findFirst()
                        .orElseThrow();
        assertTrue(withDm.dataModel() != null && !withDm.dataModel().isEmpty());
        assertEquals(true, withDm.toDatasetCase().inputs().get("hasDataModel"));

        Path nullCase = Files.createDirectories(tempPack.resolve("case-01-null"));
        Files.writeString(nullCase.resolve("case.json"), "{\"id\":\"null-dm\"}");
        Files.writeString(nullCase.resolve("dsl.txt"), "root = Stack([])");

        Path emptyCase = Files.createDirectories(tempPack.resolve("case-02-empty"));
        Files.writeString(emptyCase.resolve("case.json"), "{\"id\":\"empty-dm\",\"dataModel\":{}}");
        Files.writeString(emptyCase.resolve("dsl.txt"), "root = Stack([])");

        List<CorpusLoaders.LoadedRepairCase> synthetic =
                CorpusLoaders.loadRepairCases(tempPack, null);
        assertNull(synthetic.get(0).dataModel());
        assertEquals(false, synthetic.get(0).toDatasetCase().inputs().get("hasDataModel"));
        assertEquals(Map.of(), synthetic.get(1).dataModel());
        assertEquals(true, synthetic.get(1).toDatasetCase().inputs().get("hasDataModel"));

        List<CorpusLoaders.LoadedValidationTestCase> validationCases =
                CorpusLoaders.loadValidationTestCases(repoRoot.resolve("validation-test"));
        assertEquals(49, validationCases.size());
    }

    @Test
    void syncIsIdempotentAndUpdatesOnlyModifiedExamples() {
        LangSmithClient client =
                new LangSmithClient(
                        new LangSmithConfig(
                                baseUrl, "ls-key", null, null, null, Duration.ofSeconds(5), 1));
        DatasetSyncer syncer = new DatasetSyncer(client);

        List<DatasetCase> firstWave =
                List.of(
                        DatasetCase.of(
                                "c1",
                                Map.of("caseId", "c1", "hasDataModel", false),
                                null,
                                Map.of("corpus_type", "repair-cases")),
                        DatasetCase.of(
                                "c2",
                                Map.of("caseId", "c2", "hasDataModel", true, "dataModel", Map.of()),
                                Map.of("rootCause", "test"),
                                Map.of("corpus_type", "repair-cases")));

        DatasetSyncer.SyncSummary run1 =
                syncer.sync("genui-repair-test", "desc", Map.of(), firstWave);
        assertEquals(2, run1.created());
        assertEquals(0, run1.updated());
        assertEquals(0, run1.unchanged());

        DatasetSyncer.SyncSummary run2 =
                syncer.sync("genui-repair-test", "desc", Map.of(), firstWave);
        assertEquals(0, run2.created());
        assertEquals(0, run2.updated());
        assertEquals(2, run2.unchanged());
        assertEquals(run1.exampleIdByCaseId(), run2.exampleIdByCaseId());

        List<DatasetCase> modifiedWave =
                List.of(
                        firstWave.get(0),
                        DatasetCase.of(
                                "c2",
                                Map.of(
                                        "caseId",
                                        "c2",
                                        "hasDataModel",
                                        true,
                                        "dataModel",
                                        Map.of("k", 1)),
                                Map.of("rootCause", "test"),
                                Map.of("corpus_type", "repair-cases")));

        DatasetSyncer.SyncSummary run3 =
                syncer.sync("genui-repair-test", "desc", Map.of(), modifiedWave);
        assertEquals(0, run3.created());
        assertEquals(1, run3.updated());
        assertEquals(1, run3.unchanged());
        assertFalse(run3.exampleIdByCaseId().isEmpty());
    }

    private void handleDatasets(HttpExchange exchange) throws IOException {
        if ("GET".equals(exchange.getRequestMethod())) {
            respond(exchange, 200, Json.stringify(datasetsByName.values().stream().toList()));
            return;
        }
        Map<String, Object> body =
                Json.asObject(
                        Json.parse(
                                new String(
                                        exchange.getRequestBody().readAllBytes(),
                                        StandardCharsets.UTF_8)),
                        "dataset");
        LinkedHashMap<String, Object> created = new LinkedHashMap<>();
        created.put("id", "ds-" + seq.incrementAndGet());
        created.put("name", body.get("name"));
        datasetsByName.put(String.valueOf(body.get("name")), created);
        respond(exchange, 200, Json.stringify(created));
    }

    private void handleExamples(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        if ("GET".equals(method)) {
            respond(exchange, 200, Json.stringify(examplesById.values().stream().toList()));
            return;
        }
        Map<String, Object> body =
                Json.asObject(
                        Json.parse(
                                new String(
                                        exchange.getRequestBody().readAllBytes(),
                                        StandardCharsets.UTF_8)),
                        "example");
        if ("POST".equals(method)) {
            String id = "ex-" + seq.incrementAndGet();
            LinkedHashMap<String, Object> stored = new LinkedHashMap<>(body);
            stored.put("id", id);
            examplesById.put(id, stored);
            respond(exchange, 200, Json.stringify(stored));
            return;
        }
        String id = path.substring("/api/v1/examples/".length());
        Map<String, Object> existing = examplesById.get(id);
        LinkedHashMap<String, Object> merged = new LinkedHashMap<>(existing);
        merged.putAll(body);
        examplesById.put(id, merged);
        respond(exchange, 200, Json.stringify(merged));
    }

    private static void respond(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
