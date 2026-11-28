/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.eval.repair;

import com.huawei.cloudsop.genui.core.GenerationSdk;
import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.core.llm.GenUiGenerationResult;
import com.huawei.cloudsop.genui.core.llm.GenUiGenerator;
import com.huawei.cloudsop.genui.core.llm.GenUiLlmConfig;
import com.huawei.cloudsop.genui.core.llm.GenerationEndReason;
import com.huawei.cloudsop.genui.core.llm.LlmUsage;
import com.huawei.cloudsop.genui.core.llm.UiGenerationRequest;
import com.huawei.cloudsop.genui.core.llm.stream.SseFrames;
import com.huawei.cloudsop.genui.core.llm.transport.LlmCall;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransport;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransportException;
import com.huawei.cloudsop.genui.core.validation.DefaultOpenuiLangValidator;
import com.huawei.cloudsop.genui.core.validation.GenUiValidationConfig;
import com.huawei.cloudsop.genui.core.validation.ValidationIssue;
import com.huawei.cloudsop.genui.core.validation.ValidationRequest;
import com.huawei.cloudsop.genui.core.validation.ValidationResult;
import com.huawei.cloudsop.genui.core.validation.ValidationStatus;
import com.huawei.cloudsop.genui.core.validation.repair.RepairLoop;
import com.huawei.cloudsop.genui.core.validation.stream.RoundEndResult;
import com.huawei.cloudsop.genui.eval.dataset.CorpusLoaders.LoadedRepairCase;
import com.huawei.cloudsop.genui.eval.langsmith.BatchRunBuffer;
import com.huawei.cloudsop.genui.eval.langsmith.LangSmithClient;
import com.huawei.cloudsop.genui.eval.trace.LangSmithGenerationObserver;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * LangSmith 化的 {@code repair-bench} 批量执行器。
 *
 * <p>保留原有全部口径（主口径/端到端口径/副口径/分桶/能力边界）与离线 JSONL/HTML 输出， 同时将每个案例映射为 LangSmith Experiment 下的嵌套 Run
 * 树（含精确 prompt、raw output、可选 token 用量与逐轮诊断）及行级/实验级 Summary Feedback。
 *
 * @since 2026
 */
public final class RepairExperimentRunner {
    private static final Set<String> EXTERNAL_REFS = Set.of("data");

    private final LlmTransport transport;
    private final String model;
    private final int maxAttempts;
    private final Duration timeout;
    private final boolean repair;
    private final boolean includeUsage;
    private final LangSmithClient langSmithClient;
    private final String sessionId;
    private final Map<String, String> exampleIdsByCaseId;
    private final PrintStream out;
    private final List<String> emitted = java.util.Collections.synchronizedList(new ArrayList<>());

    /**
     * 构造执行器配置。
     *
     * @param transport 真实 LLM 传输（validation-only 时可为 null）
     * @param model 模型名（validation-only 时可为 null）
     * @param maxAttempts 单案例最大修复轮数
     * @param timeout 尝试间墙钟超时（0 表示不限）
     * @param repair 是否执行流式增量修复
     * @param includeUsage 是否开启 {@code stream_options.include_usage}
     * @param langSmithClient 可选 LangSmith 客户端（离线跑法时为 null）
     * @param sessionId LangSmith 实验会话 ID，可为 null
     * @param exampleIdsByCaseId {@code caseId -> exampleId} 映射，可为 null
     * @param out JSONL 输出流
     * @since 2026
     */
    public record Options(
            LlmTransport transport,
            String model,
            int maxAttempts,
            Duration timeout,
            boolean repair,
            boolean includeUsage,
            LangSmithClient langSmithClient,
            String sessionId,
            Map<String, String> exampleIdsByCaseId,
            PrintStream out) {}

    /**
     * 批次运行返回值。
     *
     * @param emittedLines 全量 JSONL 行（含末尾 summary 行，可直接交离线 HTML 渲染器）
     * @param summaryScores 上传到 LangSmith 的实验级汇总分数
     * @param sessionId LangSmith 实验会话 ID（离线时为 null）
     * @since 2026
     */
    public record RunOutput(
            List<String> emittedLines, Map<String, Double> summaryScores, String sessionId) {}

    /**
     * 创建修复实验执行器。
     *
     * @param options 执行配置
     */
    public RepairExperimentRunner(Options options) {
        this.transport = options.transport();
        this.model = options.model();
        this.maxAttempts = options.maxAttempts();
        this.timeout = options.timeout() == null ? Duration.ZERO : options.timeout();
        this.repair = options.repair();
        this.includeUsage = options.includeUsage();
        this.langSmithClient = options.langSmithClient();
        this.sessionId = options.sessionId();
        this.exampleIdsByCaseId =
                options.exampleIdsByCaseId() == null
                        ? Map.of()
                        : Map.copyOf(options.exampleIdsByCaseId());
        this.out = options.out();
    }

    /**
     * 并发执行全部修复案例，提交行级与实验级 Feedback，并返回 JSONL 产物供离线 HTML 报告复用。
     *
     * @param cases 已加载的修复案例
     * @param concurrency 有界并发度
     * @return 运行结果
     * @throws InterruptedException 线程被中断
     */
    public RunOutput runAll(List<LoadedRepairCase> cases, int concurrency)
            throws InterruptedException {
        long batchStart = System.currentTimeMillis();
        SummaryFeedbackCalculator summary = new SummaryFeedbackCalculator();
        BatchRunBuffer buffer =
                langSmithClient == null ? null : new BatchRunBuffer(langSmithClient);
        ExecutorService pool =
                Executors.newFixedThreadPool(Math.min(concurrency, Math.max(cases.size(), 1)));
        try {
            List<Future<?>> futures =
                    cases.stream()
                            .map(c -> pool.submit(() -> runOneSafely(c, summary, buffer)))
                            .<Future<?>>map(f -> f)
                            .toList();
            waitAll(futures);
        } finally {
            pool.shutdownNow();
            if (buffer != null) {
                buffer.close();
            }
        }
        long elapsedMs = System.currentTimeMillis() - batchStart;
        Map<String, Object> summaryLine =
                summary.toSummaryLine(model, maxAttempts, concurrency, timeout, elapsedMs);
        if (sessionId != null) {
            summaryLine.put("langsmithSessionId", sessionId);
        }
        emit(Json.stringify(summaryLine));
        summary.publishSummaryFeedback(langSmithClient, sessionId);
        if (langSmithClient != null && sessionId != null) {
            langSmithClient.finishSession(sessionId, Instant.now(), null);
        }
        return new RunOutput(emittedSnapshot(), summary.toLangSmithSummaryScores(), sessionId);
    }

    private void runOneSafely(
            LoadedRepairCase benchCase, SummaryFeedbackCalculator summary, BatchRunBuffer buffer) {
        try {
            runOne(benchCase, summary, buffer);
        } catch (Exception error) {
            LinkedHashMap<String, Object> line = baseLine(benchCase);
            line.put("status", "error");
            line.put("success", false);
            line.put("reproduced", false);
            line.put("error", String.valueOf(error));
            emit(Json.stringify(line));
            summary.record("error", null, false, false, List.of(), List.of());
        }
    }

    private void runOne(
            LoadedRepairCase benchCase, SummaryFeedbackCalculator summary, BatchRunBuffer buffer) {
        long started = System.currentTimeMillis();
        LangSmithGenerationObserver observer = createObserver(benchCase, buffer);

        Instant v0Start = Instant.now();
        ValidationResult v0 = validateInitial(benchCase);
        observer.recordStandaloneValidation(
                "initial_validation", benchCase.dsl(), v0, v0Start, Instant.now());
        List<String> initialCodes = actionableCodes(v0);

        if (v0.status() != ValidationStatus.INVALID) {
            finishNonRepair(
                    benchCase, "not-reproduced", false, List.of(), started, summary, observer);
            return;
        }
        if (!repair) {
            finishNonRepair(benchCase, "invalid", true, initialCodes, started, summary, observer);
            return;
        }
        runStreamingRepair(benchCase, v0, initialCodes, started, summary, observer);
    }

    private void finishNonRepair(
            LoadedRepairCase benchCase,
            String status,
            boolean reproduced,
            List<String> initialCodes,
            long started,
            SummaryFeedbackCalculator summary,
            LangSmithGenerationObserver observer) {
        LinkedHashMap<String, Object> line = baseLine(benchCase);
        line.put("status", status);
        line.put("success", false);
        line.put("reproduced", reproduced);
        line.put("initialIssueCodes", initialCodes);
        putInternalCodes(line, benchCase);
        line.put("langsmithRunId", observer.rootRunId());
        line.put("durationMs", System.currentTimeMillis() - started);
        emit(Json.stringify(line));
        summary.record(
                status, reproduced ? initialCodes : null, false, false, List.of(), List.of());

        observer.finishRoot(
                Map.of(
                        "status",
                        status,
                        "reproduced",
                        reproduced,
                        "initialIssueCodes",
                        initialCodes),
                null,
                Instant.now());
        submitRowFeedback(
                new RowFeedback(
                        observer.rootRunId(),
                        status,
                        reproduced,
                        false,
                        0,
                        initialCodes.size(),
                        initialCodes.size(),
                        false,
                        false,
                        0,
                        0));
    }

    private record RepairVerdict(
            String status,
            boolean repaired,
            List<String> initialCodes,
            List<String> finalCodes,
            String finalDsl,
            GenUiGenerationResult generated,
            List<Object> attempts,
            List<RoundEndResult> rounds,
            CapabilityInspector.InspectResult inspect,
            List<String> consecutiveUnmatched) {}

    private record RowFeedback(
            String runId,
            String status,
            boolean reproduced,
            boolean repaired,
            int attemptCount,
            int initialIssueCount,
            int finalIssueCount,
            boolean markdownFallback,
            boolean capabilityNoticePresent,
            int remainingGapAliasCount,
            int consecutiveUnmatchedCount) {}

    private void runStreamingRepair(
            LoadedRepairCase benchCase,
            ValidationResult v0,
            List<String> initialCodes,
            long started,
            SummaryFeedbackCalculator summary,
            LangSmithGenerationObserver observer) {
        CaseReplayTransport replay = new CaseReplayTransport(transport, benchCase.dsl(), timeout);
        GenUiGenerator generator = buildRepairGenerator(benchCase, replay, observer);
        UiGenerationRequest request =
                UiGenerationRequest.builder()
                        .userInput(benchCase.originalIntent())
                        .response(benchCase.dataModel())
                        .build();
        GenUiGenerationResult generated = generator.generateStream(request, event -> {});
        RepairVerdict verdict =
                evaluateRepairVerdict(benchCase, initialCodes, generated, replay.timedOut());
        emitAndPublishRepair(benchCase, v0, verdict, observer, started, summary);
    }

    private GenUiGenerator buildRepairGenerator(
            LoadedRepairCase benchCase,
            CaseReplayTransport replay,
            LangSmithGenerationObserver observer) {
        GenUiLlmConfig llmConfig =
                GenUiLlmConfig.builder()
                        .defaultModel(model)
                        .temperature(0)
                        .enableThinking(false)
                        .includeStreamUsage(includeUsage)
                        .build();
        GenerationSdk sdk = GenerationSdk.builder().baseContract(benchCase.contract()).build();
        return GenUiGenerator.withTransport(
                        sdk, llmConfig, replay, GenUiValidationConfig.enabled(maxAttempts), null)
                .withObserver(observer);
    }

    private RepairVerdict evaluateRepairVerdict(
            LoadedRepairCase benchCase,
            List<String> initialCodes,
            GenUiGenerationResult generated,
            boolean timedOut) {
        List<RoundEndResult> rounds = generated.rounds();
        RoundEndResult lastValidated = lastValidatedRound(rounds);
        boolean repaired = isStrictlyRepaired(generated, lastValidated);
        String status = classifyStatus(repaired, timedOut, generated.endReason());
        String finalDsl = lastValidated == null ? null : lastValidated.currentDefinitions();
        List<String> finalCodes =
                lastValidated == null
                        ? List.of()
                        : actionableCodes(lastValidated.validationResult());
        CapabilityInspector.InspectResult inspect =
                CapabilityInspector.inspectFinalDsl(finalDsl, benchCase.contract(), EXTERNAL_REFS);
        List<String> consecutiveUnmatched =
                CapabilityInspector.consecutiveUnmatchedUnknownComponents(rounds);
        return new RepairVerdict(
                status,
                repaired,
                initialCodes,
                finalCodes,
                finalDsl,
                generated,
                attempts(rounds),
                rounds,
                inspect,
                consecutiveUnmatched);
    }

    private void emitAndPublishRepair(
            LoadedRepairCase benchCase,
            ValidationResult v0,
            RepairVerdict verdict,
            LangSmithGenerationObserver observer,
            long started,
            SummaryFeedbackCalculator summary) {
        emit(Json.stringify(buildRepairLine(benchCase, verdict, observer, started)));
        summary.record(
                verdict.status(),
                verdict.initialCodes(),
                CapabilityInspector.hasCapabilityGapIssue(v0),
                verdict.inspect().capabilityNoticePresent(),
                verdict.inspect().remainingGapAliases(),
                verdict.consecutiveUnmatched());
        LinkedHashMap<String, Object> rootOut = new LinkedHashMap<>();
        rootOut.put("status", verdict.status());
        rootOut.put("repaired", verdict.repaired());
        rootOut.put("attemptCount", verdict.attempts().size());
        rootOut.put("initialIssueCodes", verdict.initialCodes());
        rootOut.put("finalIssueCodes", verdict.finalCodes());
        rootOut.put("finalDsl", verdict.finalDsl());
        observer.finishRoot(rootOut, null, Instant.now());
        boolean markdownFallback =
                verdict.generated().endReason() != GenerationEndReason.CHECK_PASSED;
        submitRowFeedback(
                new RowFeedback(
                        observer.rootRunId(),
                        verdict.status(),
                        true,
                        verdict.repaired(),
                        verdict.attempts().size(),
                        verdict.initialCodes().size(),
                        verdict.finalCodes().size(),
                        markdownFallback,
                        verdict.inspect().capabilityNoticePresent(),
                        verdict.inspect().remainingGapAliases().size(),
                        verdict.consecutiveUnmatched().size()));
    }

    private LinkedHashMap<String, Object> buildRepairLine(
            LoadedRepairCase benchCase,
            RepairVerdict verdict,
            LangSmithGenerationObserver observer,
            long started) {
        LinkedHashMap<String, Object> line = baseLine(benchCase);
        line.put("status", verdict.status());
        line.put("success", verdict.repaired());
        line.put("reproduced", true);
        line.put("initialIssueCodes", verdict.initialCodes());
        putInternalCodes(line, benchCase);
        line.put("attemptCount", verdict.attempts().size());
        line.put("attempts", verdict.attempts());
        line.put(
                "endReason",
                verdict.generated().endReason() == null
                        ? null
                        : verdict.generated().endReason().name());
        line.put(
                "markdownFallback",
                verdict.generated().endReason() != GenerationEndReason.CHECK_PASSED);
        line.put("outputDsl", verdict.generated().dsl());
        line.put("rounds", serializeRounds(verdict.rounds()));
        line.put("finalIssueCodes", verdict.finalCodes());
        line.put("finalDsl", verdict.finalDsl());
        line.put("capabilityNoticePresent", verdict.inspect().capabilityNoticePresent());
        line.put("remainingCapabilityGapAliases", verdict.inspect().remainingGapAliases());
        line.put("consecutiveUnmatchedUnknownComponents", verdict.consecutiveUnmatched());
        line.put("langsmithRunId", observer.rootRunId());
        line.put("capturedPromptCount", observer.capturedCalls().size());
        LlmUsage usage = observer.totalUsage();
        if (usage != null) {
            line.put("promptTokens", usage.promptTokens());
            line.put("completionTokens", usage.completionTokens());
            line.put("totalTokens", usage.totalTokens());
        }
        line.put("durationMs", System.currentTimeMillis() - started);
        return line;
    }

    private void submitRowFeedback(RowFeedback row) {
        if (langSmithClient == null) {
            return;
        }
        langSmithClient.createRunValueFeedback(
                row.runId(), sessionId, "status", row.status(), null);
        langSmithClient.createRunScoreFeedback(
                row.runId(), sessionId, "reproduced", row.reproduced() ? 1.0 : 0.0, null);
        langSmithClient.createRunScoreFeedback(
                row.runId(), sessionId, "repaired", row.repaired() ? 1.0 : 0.0, null);
        langSmithClient.createRunScoreFeedback(
                row.runId(), sessionId, "attempt_count", row.attemptCount(), null);
        langSmithClient.createRunScoreFeedback(
                row.runId(), sessionId, "initial_issue_count", row.initialIssueCount(), null);
        langSmithClient.createRunScoreFeedback(
                row.runId(), sessionId, "final_issue_count", row.finalIssueCount(), null);
        langSmithClient.createRunScoreFeedback(
                row.runId(),
                sessionId,
                "markdown_fallback",
                row.markdownFallback() ? 1.0 : 0.0,
                null);
        langSmithClient.createRunScoreFeedback(
                row.runId(),
                sessionId,
                "capability_notice_present",
                row.capabilityNoticePresent() ? 1.0 : 0.0,
                null);
        langSmithClient.createRunScoreFeedback(
                row.runId(),
                sessionId,
                "remaining_gap_alias_count",
                row.remainingGapAliasCount(),
                null);
        langSmithClient.createRunScoreFeedback(
                row.runId(),
                sessionId,
                "consecutive_unmatched_unknown_count",
                row.consecutiveUnmatchedCount(),
                null);
    }

    private LangSmithGenerationObserver createObserver(
            LoadedRepairCase benchCase, BatchRunBuffer buffer) {
        LinkedHashMap<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("caseId", benchCase.id());
        inputs.put("originalIntent", benchCase.originalIntent());
        inputs.put("hasDataModel", benchCase.dataModel() != null);
        inputs.put("dsl", benchCase.dsl());
        LinkedHashMap<String, Object> meta = new LinkedHashMap<>();
        meta.put("track", repair ? "repair" : "validation-only");
        meta.put("contract_version", benchCase.contract().contractVersion());
        meta.put("contract_sha256", benchCase.contractSha256());
        return new LangSmithGenerationObserver(
                buffer,
                sessionId,
                exampleIdsByCaseId.get(benchCase.id()),
                benchCase.id(),
                "repair_case_" + benchCase.id(),
                inputs,
                meta);
    }

    private static ValidationResult validateInitial(LoadedRepairCase benchCase) {
        String root = benchCase.contract().root() == null ? "root" : benchCase.contract().root();
        return DefaultOpenuiLangValidator.INSTANCE.validate(
                ValidationRequest.builder()
                        .dsl(benchCase.dsl())
                        .contract(benchCase.contract())
                        .rootName(root)
                        .externalRefs(EXTERNAL_REFS)
                        .dataModel(benchCase.dataModel())
                        .build());
    }

    private static RoundEndResult lastValidatedRound(List<RoundEndResult> rounds) {
        RoundEndResult last = null;
        for (RoundEndResult round : rounds) {
            if (round.validationResult() != null) {
                last = round;
            }
        }
        return last;
    }

    private static boolean isStrictlyRepaired(
            GenUiGenerationResult generated, RoundEndResult lastValidated) {
        return generated.endReason() == GenerationEndReason.CHECK_PASSED
                && generated.validationStatus() == ValidationStatus.VALID
                && lastValidated != null
                && lastValidated.validationResult().status() == ValidationStatus.VALID
                && generated.dsl().equals(lastValidated.currentDefinitions());
    }

    private static String classifyStatus(
            boolean repaired, boolean timedOut, GenerationEndReason endReason) {
        if (repaired) {
            return "repaired";
        }
        if (timedOut || endReason == GenerationEndReason.TIMEOUT) {
            return "timeout";
        }
        if (endReason == GenerationEndReason.MODEL_REQUEST_FAILED) {
            return "transport-error";
        }
        return "still-invalid";
    }

    private static List<Object> serializeRounds(List<RoundEndResult> rounds) {
        return rounds.stream()
                .map(
                        round -> {
                            LinkedHashMap<String, Object> record = new LinkedHashMap<>();
                            record.put("rawOutput", round.rawOutput());
                            record.put("currentDefinitions", round.currentDefinitions());
                            record.put("durationMs", round.durationMs());
                            record.put(
                                    "endReason",
                                    round.endReason() == null ? null : round.endReason().name());
                            record.put(
                                    "status",
                                    round.validationResult() == null
                                            ? null
                                            : round.validationResult().status().name());
                            record.put(
                                    "issues",
                                    round.validationResult() == null
                                            ? List.of()
                                            : round.validationResult().issues());
                            return (Object) record;
                        })
                .toList();
    }

    private static List<Object> attempts(List<RoundEndResult> rounds) {
        List<Object> list = new ArrayList<>();
        for (int i = 1; i < rounds.size(); i++) {
            RoundEndResult round = rounds.get(i);
            if (round.validationResult() == null) {
                continue;
            }
            list.add(
                    attemptLine(
                            list.size() + 1,
                            round.validationResult().isValid(),
                            actionableCodes(round.validationResult())));
        }
        if (rounds.size() > 1 && rounds.getLast().validationResult() == null) {
            list.add(attemptLine(list.size() + 1, false, null));
        }
        return list;
    }

    private static Map<String, Object> attemptLine(
            int attempt, boolean valid, List<String> issueCodes) {
        LinkedHashMap<String, Object> line = new LinkedHashMap<>();
        line.put("attempt", attempt);
        line.put("valid", valid);
        line.put("issueCodes", issueCodes);
        return line;
    }

    private static LinkedHashMap<String, Object> baseLine(LoadedRepairCase benchCase) {
        LinkedHashMap<String, Object> line = new LinkedHashMap<>();
        line.put("type", "case");
        line.put("caseId", benchCase.id());
        return line;
    }

    private static void putInternalCodes(
            LinkedHashMap<String, Object> line, LoadedRepairCase benchCase) {
        if (benchCase.internalIssueCodes() != null) {
            line.put("internalIssueCodes", benchCase.internalIssueCodes());
        }
    }

    private static List<String> actionableCodes(ValidationResult result) {
        return result.actionableIssues().stream()
                .map(ValidationIssue::code)
                .distinct()
                .sorted()
                .toList();
    }

    private synchronized void emit(String line) {
        emitted.add(line);
        if (out != null) {
            out.println(line);
            out.flush();
        }
    }

    private List<String> emittedSnapshot() {
        synchronized (emitted) {
            return List.copyOf(emitted);
        }
    }

    private static void waitAll(List<Future<?>> futures) throws InterruptedException {
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (ExecutionException error) {
                System.err.println(
                        "[genui-eval-langsmith] unexpected worker failure: " + error.getCause());
            }
        }
    }

    private static final class CaseReplayTransport implements LlmTransport {
        private static final String MARKDOWN_SYSTEM_HINT =
                "Answer the user's request in Markdown only.";
        private static final String MARKDOWN_STUB =
                "Incremental repair did not produce a valid page.";

        private final LlmTransport delegate;
        private final String initialDsl;
        private final Duration timeout;
        private final long repairStartedNanos = System.nanoTime();
        private final AtomicInteger repairCalls = new AtomicInteger();
        private volatile boolean timedOut;

        CaseReplayTransport(LlmTransport delegate, String initialDsl, Duration timeout) {
            this.delegate = delegate;
            this.initialDsl = initialDsl;
            this.timeout = timeout == null ? Duration.ZERO : timeout;
        }

        boolean timedOut() {
            return timedOut;
        }

        @Override
        public LlmCall newCall(String body, boolean stream) {
            return new LlmCall() {
                @Override
                public InputStream execute() throws LlmTransportException {
                    return postStream(body);
                }

                @Override
                public void cancel() {}
            };
        }

        @Override
        public String post(String body) {
            throw new UnsupportedOperationException("repair experiment is streaming-only");
        }

        @Override
        public InputStream postStream(String body) throws LlmTransportException {
            return switch (classify(body)) {
                case INITIAL -> sseResponse(initialDsl);
                case MARKDOWN -> sseResponse(MARKDOWN_STUB);
                case REPAIR -> repairStream(body);
            };
        }

        private InputStream repairStream(String body) throws LlmTransportException {
            if (!timeout.isZero() && System.nanoTime() - repairStartedNanos >= timeout.toNanos()) {
                timedOut = true;
                throw new LlmTransportException(
                        "repair timeout after " + repairCalls.get() + " attempt(s)");
            }
            repairCalls.incrementAndGet();
            return delegate.postStream(body);
        }

        private static RequestKind classify(String body) {
            try {
                Map<String, Object> parsed = Json.asObject(Json.parse(body), "request body");
                List<Object> messages = Json.asList(parsed.get("messages"), "messages");
                StringBuilder system = new StringBuilder();
                for (Object item : messages) {
                    Map<String, Object> msg = Json.asObject(item, "message");
                    if ("system".equals(msg.get("role")) && msg.get("content") != null) {
                        system.append(msg.get("content"));
                    }
                }
                String text = system.toString();
                if (text.contains(RepairLoop.INCREMENTAL_REPAIR_MARKER)) {
                    return RequestKind.REPAIR;
                }
                if (text.contains(MARKDOWN_SYSTEM_HINT)) {
                    return RequestKind.MARKDOWN;
                }
                return RequestKind.INITIAL;
            } catch (RuntimeException error) {
                return RequestKind.REPAIR;
            }
        }

        private static InputStream sseResponse(String content) {
            String delta =
                    "data: {\"choices\":[{\"delta\":{\"content\":"
                            + Json.stringify(content)
                            + "}}]}\n\n";
            return new ByteArrayInputStream(
                    (delta + SseFrames.done()).getBytes(StandardCharsets.UTF_8));
        }

        private enum RequestKind {
            INITIAL,
            REPAIR,
            MARKDOWN
        }
    }
}
