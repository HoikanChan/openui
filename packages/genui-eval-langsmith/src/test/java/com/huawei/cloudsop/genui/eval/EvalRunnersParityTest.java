/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.core.contract.GenerationContractLoader;
import com.huawei.cloudsop.genui.core.llm.GenUiLlmConfig;
import com.huawei.cloudsop.genui.core.llm.stream.SseFrames;
import com.huawei.cloudsop.genui.core.llm.transport.LlmCall;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransport;
import com.huawei.cloudsop.genui.eval.ab.GenerationAbExperimentRunner;
import com.huawei.cloudsop.genui.eval.cli.LangSmithEvalCli;
import com.huawei.cloudsop.genui.eval.corpus.ValidationCorpusExperimentRunner;
import com.huawei.cloudsop.genui.eval.dataset.CorpusLoaders;
import com.huawei.cloudsop.genui.eval.langsmith.LangSmithClient;
import com.huawei.cloudsop.genui.eval.langsmith.LangSmithConfig;
import com.huawei.cloudsop.genui.eval.repair.RepairExperimentRunner;
import com.huawei.cloudsop.genui.eval.repair.SummaryFeedbackCalculator;
import com.huawei.cloudsop.genui.eval.report.OfflineHtmlReporter;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

class EvalRunnersParityTest {
    private HttpServer server;
    private String baseUrl;
    private final List<Map<String, Object>> uploadedFeedback = new CopyOnWriteArrayList<>();
    private final List<Map<String, Object>> uploadedBatches = new CopyOnWriteArrayList<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/datasets", this::handleSimpleJson);
        server.createContext("/api/v1/examples", this::handleExamples);
        server.createContext("/api/v1/sessions", this::handleSessions);
        server.createContext("/api/v1/runs/batch", this::handleBatch);
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
    void summaryCalculatorMatchesMainEndToEndSecondaryAndBucketRates() {
        SummaryFeedbackCalculator calc = new SummaryFeedbackCalculator();
        calc.record("repaired", List.of("unknown-component"), true, true, List.of(), List.of());
        calc.record(
                "still-invalid",
                List.of("unknown-component"),
                true,
                false,
                List.of("Map"),
                List.of("Foo"));
        calc.record("timeout", List.of("syntax-error"), false, false, List.of(), List.of());
        calc.record("not-reproduced", null, false, false, List.of(), List.of());

        Map<String, Double> scores = calc.toLangSmithSummaryScores();
        assertEquals(0.5, scores.get("main_repair_rate"), 1e-9);
        assertEquals(1.0 / 3.0, scores.get("end_to_end_repair_rate"), 1e-9);
        assertEquals(0.25, scores.get("secondary_repair_rate"), 1e-9);
        assertEquals(0.25, scores.get("not_reproduced_rate"), 1e-9);
        assertEquals(1.0, scores.get("capability_notice_rate"), 1e-9);
        assertEquals(0.5, scores.get("bucket::unknown-component::repair_rate"), 1e-9);
    }

    @Test
    void repairExperimentUploadsNestedTraceSummaryFeedbackAndOfflineHtml(@TempDir Path tempDir)
            throws Exception {
        LangSmithClient client =
                new LangSmithClient(
                        new LangSmithConfig(
                                baseUrl, "ls-key", null, null, null, Duration.ofSeconds(5), 1));

        List<CorpusLoaders.LoadedRepairCase> cases =
                List.of(
                        new CorpusLoaders.LoadedRepairCase(
                                "CASE-1",
                                "show card",
                                Map.of("name", "leaf1"),
                                "root = UnknownWidget(\"bad\")",
                                GenerationContractLoader.loadDefault(),
                                null,
                                CorpusLoaders.defaultContractSha256(),
                                List.of("unknown-component"),
                                "unknown widget",
                                "case-01"),
                        new CorpusLoaders.LoadedRepairCase(
                                "CASE-2",
                                "valid",
                                Map.of(),
                                "root = Stack([])",
                                GenerationContractLoader.loadDefault(),
                                null,
                                CorpusLoaders.defaultContractSha256(),
                                null,
                                null,
                                "case-02"));

        LlmTransport repairTransport =
                new LlmTransport() {
                    @Override
                    public LlmCall newCall(String body, boolean stream) {
                        return new LlmCall() {
                            @Override
                            public InputStream execute() {
                                return postStream(body);
                            }

                            @Override
                            public void cancel() {}
                        };
                    }

                    @Override
                    public String post(String body) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public InputStream postStream(String body) {
                        String sse =
                                "data: {\"choices\":[{\"delta\":{\"content\":\"root = Stack([])\"},\"finish_reason\":\"stop\"}]}\n\n"
                                        + "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":80,\"completion_tokens\":10,\"total_tokens\":90}}\n\n"
                                        + SseFrames.done();
                        return new ByteArrayInputStream(sse.getBytes(StandardCharsets.UTF_8));
                    }
                };

        ByteArrayOutputStream jsonlBytes = new ByteArrayOutputStream();
        RepairExperimentRunner runner =
                new RepairExperimentRunner(
                        new RepairExperimentRunner.Options(
                                repairTransport,
                                "local-qwen",
                                2,
                                Duration.ZERO,
                                true,
                                true,
                                client,
                                "sess-repair-1",
                                Map.of("CASE-1", "ex-1", "CASE-2", "ex-2"),
                                new PrintStream(jsonlBytes, true, StandardCharsets.UTF_8)));

        RepairExperimentRunner.RunOutput output = runner.runAll(cases, 2);

        assertEquals(1.0, output.summaryScores().get("main_repair_rate"), 1e-9);
        assertEquals(0.5, output.summaryScores().get("secondary_repair_rate"), 1e-9);
        assertEquals(0.5, output.summaryScores().get("not_reproduced_rate"), 1e-9);
        assertTrue(!uploadedBatches.isEmpty());
        boolean sawUsageMetadata =
                uploadedBatches.stream()
                        .flatMap(
                                batch -> {
                                    Object patches = batch.get("patch");
                                    return patches instanceof List<?> list
                                            ? list.stream()
                                            : java.util.stream.Stream.empty();
                                })
                        .anyMatch(
                                item -> {
                                    if (!(item instanceof Map<?, ?> patch)) {
                                        return false;
                                    }
                                    Object outputs = patch.get("outputs");
                                    if (!(outputs instanceof Map<?, ?> outMap)) {
                                        return false;
                                    }
                                    Object meta = outMap.get("usage_metadata");
                                    if (!(meta instanceof Map<?, ?> usageMap)) {
                                        return false;
                                    }
                                    return Long.valueOf(80L).equals(usageMap.get("input_tokens"))
                                            && Long.valueOf(10L)
                                                    .equals(usageMap.get("output_tokens"))
                                            && Long.valueOf(90L)
                                                    .equals(usageMap.get("total_tokens"));
                                });
        assertTrue(sawUsageMetadata, "expected outputs.usage_metadata in LangSmith LLM patch");
        assertTrue(
                uploadedFeedback.stream()
                        .anyMatch(
                                f ->
                                        !f.containsKey("run_id")
                                                && "main_repair_rate".equals(f.get("key"))));

        Path html = tempDir.resolve("repair.html");
        OfflineHtmlReporter.writeRepairReport(output.emittedLines(), cases, html, "test.jsonl");
        String rendered = Files.readString(html, StandardCharsets.UTF_8);
        assertTrue(rendered.contains("CASE-1"));
        assertTrue(rendered.contains("local-qwen"));
    }

    @Test
    void validationCorpusExperimentMatchesCheckedInBaselineCounts(@TempDir Path tempDir)
            throws IOException {
        Path repoRoot = Path.of("..").toAbsolutePath().normalize().getParent();
        List<CorpusLoaders.LoadedValidationTestCase> cases =
                CorpusLoaders.loadValidationTestCases(repoRoot.resolve("validation-test"));
        ValidationCorpusExperimentRunner runner =
                new ValidationCorpusExperimentRunner(null, null, Map.of());

        ValidationCorpusExperimentRunner.CorpusRunOutput output = runner.runAll(cases);

        assertEquals(49, output.total());
        int invalidCount = output.statusCounts().getOrDefault("INVALID", 0);
        int validCount = output.statusCounts().getOrDefault("VALID", 0);
        assertEquals(49, invalidCount + validCount);
        assertTrue(invalidCount >= 26, "expected at least 26 INVALID on current contract");
        int coverageTotal =
                output.coverageCounts().values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(49, coverageTotal);
        assertEquals(23, output.coverageCounts().get("IGNORED"));
        assertEquals(1, output.coverageCounts().get("UNLABELED"));
        assertEquals(
                25,
                output.coverageCounts().getOrDefault("DETECTED", 0)
                        + output.coverageCounts().getOrDefault("MISSED", 0));

        Path html = tempDir.resolve("corpus.html");
        OfflineHtmlReporter.writeTableReport(
                "Corpus", output.coverageCounts(), output.caseResults(), html);
        assertTrue(Files.readString(html).contains("DETECTED"));
    }

    @Test
    void generationAbRunnerExecutesBothArmsAndWritesMetrics(@TempDir Path tempDir)
            throws Exception {
        LlmTransport transport =
                new LlmTransport() {
                    @Override
                    public LlmCall newCall(String body, boolean stream) {
                        return new LlmCall() {
                            @Override
                            public InputStream execute() {
                                return postStream(body);
                            }

                            @Override
                            public void cancel() {}
                        };
                    }

                    @Override
                    public String post(String body) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public InputStream postStream(String body) {
                        String sse =
                                "data: {\"choices\":[{\"delta\":{\"content\":\"root = Stack([])\"},\"finish_reason\":\"stop\"}]}\n\n"
                                        + SseFrames.done();
                        return new ByteArrayInputStream(sse.getBytes(StandardCharsets.UTF_8));
                    }
                };

        List<CorpusLoaders.LoadedAbFixture> fixtures =
                List.of(
                        new CorpusLoaders.LoadedAbFixture(
                                "fx-1", "show stack", Map.of("a", 1), null, List.of(), List.of()));
        GenerationAbExperimentRunner runner =
                new GenerationAbExperimentRunner(
                        new GenerationAbExperimentRunner.Options(
                                transport,
                                "on",
                                GenUiLlmConfig.builder().defaultModel("local-llm").build(),
                                null,
                                null,
                                Map.of(),
                                tempDir));

        GenerationAbExperimentRunner.AbRunOutput output = runner.runAll(fixtures, 1);
        assertEquals(1.0, output.summaryScores().get("valid_rate"), 1e-9);
        assertTrue(Files.isRegularFile(tempDir.resolve("fx-1.dsl")));
        assertTrue(Files.isRegularFile(tempDir.resolve("metrics-on.json")));
    }

    @Test
    void cliRunsOfflineValidationOnlyRepairAndGeneratesHtml(@TempDir Path tempDir)
            throws Exception {
        Path repoRoot = Path.of("..").toAbsolutePath().normalize().getParent();
        Path html = tempDir.resolve("offline-repair.html");
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        LangSmithEvalCli.run(
                new String[] {
                    "eval-repair",
                    "--cases=" + repoRoot.resolve("packages/genui-java-sdk/repair-cases"),
                    "--limit=3",
                    "--offline",
                    "--offline-report=" + html
                },
                Map.of(),
                new PrintStream(outBytes, true, StandardCharsets.UTF_8));
        assertTrue(Files.isRegularFile(html));
        assertNotNull(outBytes.toString(StandardCharsets.UTF_8));
        assertTrue(outBytes.toString(StandardCharsets.UTF_8).contains("\"type\":\"summary\""));
    }

    private void handleSimpleJson(HttpExchange exchange) throws IOException {
        respond(exchange, 200, "[]");
    }

    private void handleExamples(HttpExchange exchange) throws IOException {
        respond(exchange, 200, "[]");
    }

    private void handleSessions(HttpExchange exchange) throws IOException {
        respond(exchange, 200, "{\"id\":\"sess-repair-1\",\"name\":\"exp\"}");
    }

    private void handleBatch(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        uploadedBatches.add(Json.asObject(Json.parse(body), "batch"));
        respond(exchange, 202, "{\"status\":\"accepted\"}");
    }

    private void handleFeedback(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        uploadedFeedback.add(Json.asObject(Json.parse(body), "feedback"));
        respond(exchange, 200, "{\"id\":\"fb-1\"}");
    }

    private static void respond(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
