/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.eval.ab;

import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.core.llm.GenUiGenerationResult;
import com.huawei.cloudsop.genui.core.llm.GenUiGenerator;
import com.huawei.cloudsop.genui.core.llm.GenUiLlmConfig;
import com.huawei.cloudsop.genui.core.llm.RenderStreamEnvelope;
import com.huawei.cloudsop.genui.core.llm.UiGenerationRequest;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransport;
import com.huawei.cloudsop.genui.core.validation.GenUiValidationConfig;
import com.huawei.cloudsop.genui.core.validation.ValidationStatus;
import com.huawei.cloudsop.genui.eval.dataset.CorpusLoaders.LoadedAbFixture;
import com.huawei.cloudsop.genui.eval.langsmith.BatchRunBuffer;
import com.huawei.cloudsop.genui.eval.langsmith.LangSmithClient;
import com.huawei.cloudsop.genui.eval.trace.LangSmithGenerationObserver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 端到端 A/B 生成验证实验执行器（替代 {@code ValidationBenchmarkDriver}）。 对同一数据集分别跑 {@code arm=off} （关闭校验/修复）与
 * {@code arm=on}（开启流式轮末校验与增量修复）， 在 LangSmith Experiments 中可直接做并排 diff。
 *
 * @since 2026
 */
public final class GenerationAbExperimentRunner {
    private static final long NANOS_PER_MILLI = 1_000_000L;

    private final LlmTransport transport;
    private final String arm;
    private final GenUiLlmConfig llmConfig;
    private final LangSmithClient langSmithClient;
    private final String sessionId;
    private final Map<String, String> exampleIdsByCaseId;
    private final Path outDir;

    /**
     * A/B 实验执行参数。
     *
     * @param transport 真实 LLM 传输
     * @param arm 对照组名称（{@code off} 或 {@code on}）
     * @param llmConfig 模型配置
     * @param langSmithClient 可选 LangSmith 客户端
     * @param sessionId 实验会话 ID，可为 null
     * @param exampleIdsByCaseId {@code caseId -> exampleId} 映射
     * @param outDir 可选本地落盘目录（写 {@code *.dsl} 与 {@code metrics-{arm}.json}），可为 null
     * @since 2026
     */
    public record Options(
            LlmTransport transport,
            String arm,
            GenUiLlmConfig llmConfig,
            LangSmithClient langSmithClient,
            String sessionId,
            Map<String, String> exampleIdsByCaseId,
            Path outDir) {}

    /**
     * A/B 实验批次汇总。
     *
     * @param metrics 逐夹具指标列表
     * @param summaryScores 上传到 LangSmith 的实验级汇总分数
     * @since 2026
     */
    public record AbRunOutput(
            List<Map<String, Object>> metrics, Map<String, Double> summaryScores) {}

    /**
     * 创建 A/B 生成实验执行器。
     *
     * @param options 执行配置
     * @throws IllegalArgumentException arm 不是 {@code off} 或 {@code on} 时抛出
     */
    public GenerationAbExperimentRunner(Options options) {
        if (!"off".equals(options.arm()) && !"on".equals(options.arm())) {
            throw new IllegalArgumentException("arm must be off or on, got: " + options.arm());
        }
        this.transport = options.transport();
        this.arm = options.arm();
        this.llmConfig = options.llmConfig();
        this.langSmithClient = options.langSmithClient();
        this.sessionId = options.sessionId();
        this.exampleIdsByCaseId =
                options.exampleIdsByCaseId() == null
                        ? Map.of()
                        : Map.copyOf(options.exampleIdsByCaseId());
        this.outDir = options.outDir();
    }

    /**
     * 并发执行全部夹具，记录 {@code first_dsl_ms}、{@code duration_ms}、{@code llm_calls}、{@code reasks} 与
     * {@code validation_status}。
     *
     * @param fixtures 已加载夹具
     * @param concurrency 并发数
     * @return 实验指标与汇总分数
     * @throws InterruptedException 线程中断
     * @throws IOException 落盘失败
     * @throws IllegalStateException worker 异常退出时抛出
     */
    public AbRunOutput runAll(List<LoadedAbFixture> fixtures, int concurrency)
            throws InterruptedException, IOException {
        if (outDir != null) {
            Files.createDirectories(outDir);
        }
        long wallStart = System.nanoTime();
        BatchRunBuffer buffer =
                langSmithClient == null ? null : new BatchRunBuffer(langSmithClient);
        List<Map<String, Object>> metrics = new ArrayList<>();
        ExecutorService pool =
                Executors.newFixedThreadPool(Math.min(concurrency, Math.max(fixtures.size(), 1)));
        try {
            List<Future<Map<String, Object>>> futures =
                    fixtures.stream().map(f -> pool.submit(() -> runFixture(f, buffer))).toList();
            for (Future<Map<String, Object>> future : futures) {
                try {
                    metrics.add(future.get());
                } catch (ExecutionException error) {
                    throw new IllegalStateException("AB fixture worker failed", error.getCause());
                }
            }
        } finally {
            pool.shutdownNow();
            if (buffer != null) {
                buffer.close();
            }
        }
        metrics.sort(Comparator.comparing(m -> String.valueOf(m.get("id"))));
        Map<String, Double> summaryScores = computeSummaryScores(metrics);
        publishSummary(summaryScores);
        writeMetricsJson(metrics, wallStart);
        return new AbRunOutput(metrics, summaryScores);
    }

    private record RowFeedback(
            String runId,
            String status,
            boolean valid,
            long durationMs,
            Long firstDslMs,
            int llmCalls,
            int reasks,
            int dslChars) {}

    private Map<String, Object> runFixture(LoadedAbFixture fixture, BatchRunBuffer buffer) {
        LinkedHashMap<String, Object> metric = new LinkedHashMap<>();
        metric.put("id", fixture.id());
        metric.put("arm", arm);
        if (fixture.skipReason() != null) {
            metric.put("status", "SKIPPED");
            metric.put("skipReason", fixture.skipReason());
            return metric;
        }
        LangSmithGenerationObserver observer = createObserver(fixture, buffer);
        GenUiGenerator generator =
                GenUiGenerator.withTransport(
                                llmConfig,
                                transport,
                                "on".equals(arm)
                                        ? GenUiValidationConfig.enabled()
                                        : GenUiValidationConfig.disabled(),
                                null)
                        .withObserver(observer);
        UiGenerationRequest request =
                UiGenerationRequest.builder()
                        .userInput(fixture.prompt())
                        .response(fixture.dataModel())
                        .build();

        long[] firstDslNanos = {-1L};
        List<String> errorFrames = new ArrayList<>();
        long start = System.nanoTime();
        try {
            GenUiGenerationResult result =
                    generator.generateStream(
                            request, envelope -> trackFrame(envelope, firstDslNanos, errorFrames));
            recordFixtureSuccess(
                    fixture, result, observer, start, firstDslNanos[0], errorFrames, metric);
        } catch (RuntimeException | IOException error) {
            recordFixtureFailure(error, observer, start, metric);
        }
        return metric;
    }

    private static void trackFrame(
            RenderStreamEnvelope envelope, long[] firstDslNanos, List<String> errorFrames) {
        if (RenderStreamEnvelope.TYPE_DSL.equals(envelope.type()) && firstDslNanos[0] < 0) {
            firstDslNanos[0] = System.nanoTime();
        }
        if (RenderStreamEnvelope.TYPE_ERROR.equals(envelope.type())) {
            errorFrames.add(String.valueOf(envelope.content()));
        }
    }

    private void recordFixtureSuccess(
            LoadedAbFixture fixture,
            GenUiGenerationResult result,
            LangSmithGenerationObserver observer,
            long startNanos,
            long firstDslNanos,
            List<String> errorFrames,
            LinkedHashMap<String, Object> metric)
            throws IOException {
        long durationMs = (System.nanoTime() - startNanos) / NANOS_PER_MILLI;
        Long firstDslMs = firstDslNanos < 0 ? null : (firstDslNanos - startNanos) / NANOS_PER_MILLI;
        String dsl = result.dsl() == null ? "" : result.dsl();
        int llmCalls = observer.capturedCalls().size();
        int reasks = Math.max(0, llmCalls - 1);
        String status =
                result.validationStatus() == null ? "NONE" : result.validationStatus().name();
        if (outDir != null) {
            Files.writeString(outDir.resolve(fixture.id() + ".dsl"), dsl, StandardCharsets.UTF_8);
        }
        metric.put("status", status);
        metric.put("durationMs", durationMs);
        metric.put("firstDslMs", firstDslMs);
        metric.put("llmCalls", llmCalls);
        metric.put("reasks", reasks);
        metric.put("dslChars", dsl.length());
        metric.put("langsmithRunId", observer.rootRunId());
        if (!errorFrames.isEmpty()) {
            metric.put("errorFrames", errorFrames);
        }
        observer.finishRoot(
                Map.of("status", status, "dsl", dsl, "llmCalls", llmCalls, "reasks", reasks),
                null,
                Instant.now());
        submitRowFeedback(
                new RowFeedback(
                        observer.rootRunId(),
                        status,
                        result.validationStatus() == ValidationStatus.VALID,
                        durationMs,
                        firstDslMs,
                        llmCalls,
                        reasks,
                        dsl.length()));
    }

    private void recordFixtureFailure(
            Exception error,
            LangSmithGenerationObserver observer,
            long startNanos,
            LinkedHashMap<String, Object> metric) {
        long durationMs = (System.nanoTime() - startNanos) / NANOS_PER_MILLI;
        metric.put("status", "EXCEPTION");
        metric.put("durationMs", durationMs);
        metric.put("llmCalls", observer.capturedCalls().size());
        metric.put("error", error.getMessage());
        observer.finishRoot(Map.of("status", "EXCEPTION"), error.getMessage(), Instant.now());
        submitRowFeedback(
                new RowFeedback(
                        observer.rootRunId(),
                        "EXCEPTION",
                        false,
                        durationMs,
                        null,
                        observer.capturedCalls().size(),
                        0,
                        0));
    }

    private void submitRowFeedback(RowFeedback row) {
        if (langSmithClient == null) {
            return;
        }
        langSmithClient.createRunValueFeedback(
                row.runId(), sessionId, "validation_status", row.status(), null);
        langSmithClient.createRunScoreFeedback(
                row.runId(), sessionId, "valid", row.valid() ? 1.0 : 0.0, null);
        langSmithClient.createRunScoreFeedback(
                row.runId(), sessionId, "duration_ms", row.durationMs(), null);
        if (row.firstDslMs() != null) {
            langSmithClient.createRunScoreFeedback(
                    row.runId(), sessionId, "first_dsl_ms", row.firstDslMs(), null);
        }
        langSmithClient.createRunScoreFeedback(
                row.runId(), sessionId, "llm_calls", row.llmCalls(), null);
        langSmithClient.createRunScoreFeedback(
                row.runId(), sessionId, "reasks", row.reasks(), null);
        langSmithClient.createRunScoreFeedback(
                row.runId(), sessionId, "dsl_chars", row.dslChars(), null);
    }

    private Map<String, Double> computeSummaryScores(List<Map<String, Object>> metrics) {
        int active = 0;
        int valid = 0;
        long totalDuration = 0L;
        int totalReasks = 0;
        for (Map<String, Object> m : metrics) {
            String status = String.valueOf(m.get("status"));
            if ("SKIPPED".equals(status)) {
                continue;
            }
            active++;
            if ("VALID".equals(status)) {
                valid++;
            }
            Object dur = m.get("durationMs");
            if (dur instanceof Number n) {
                totalDuration += n.longValue();
            }
            Object re = m.get("reasks");
            if (re instanceof Number n) {
                totalReasks += n.intValue();
            }
        }
        LinkedHashMap<String, Double> scores = new LinkedHashMap<>();
        if (active > 0) {
            scores.put("valid_rate", (double) valid / active);
            scores.put("avg_duration_ms", (double) totalDuration / active);
            scores.put("avg_reasks", (double) totalReasks / active);
        }
        return scores;
    }

    private void publishSummary(Map<String, Double> scores) {
        if (langSmithClient == null || sessionId == null) {
            return;
        }
        for (Map.Entry<String, Double> entry : scores.entrySet()) {
            langSmithClient.createSummaryFeedback(
                    sessionId, entry.getKey(), entry.getValue(), null);
        }
        langSmithClient.finishSession(sessionId, Instant.now(), null);
    }

    private void writeMetricsJson(List<Map<String, Object>> metrics, long wallStart)
            throws IOException {
        if (outDir == null) {
            return;
        }
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("arm", arm);
        summary.put("model", llmConfig.defaultModel());
        summary.put("langsmithSessionId", sessionId);
        summary.put("totalWallMs", (System.nanoTime() - wallStart) / NANOS_PER_MILLI);
        summary.put("fixtures", metrics);
        Files.writeString(
                outDir.resolve("metrics-" + arm + ".json"),
                Json.stringifyPretty(summary),
                StandardCharsets.UTF_8);
    }

    private LangSmithGenerationObserver createObserver(
            LoadedAbFixture fixture, BatchRunBuffer buffer) {
        LinkedHashMap<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("caseId", fixture.id());
        inputs.put("prompt", fixture.prompt());
        inputs.put("hasDataModel", fixture.dataModel() != null);
        return new LangSmithGenerationObserver(
                buffer,
                sessionId,
                exampleIdsByCaseId.get(fixture.id()),
                fixture.id(),
                "ab_" + arm + "_" + fixture.id(),
                inputs,
                Map.of("arm", arm, "model", llmConfig.defaultModel()));
    }
}
