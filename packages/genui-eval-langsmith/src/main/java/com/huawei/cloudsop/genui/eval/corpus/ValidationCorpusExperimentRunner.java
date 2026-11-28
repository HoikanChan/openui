/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.eval.corpus;

import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import com.huawei.cloudsop.genui.core.contract.GenerationContractLoader;
import com.huawei.cloudsop.genui.core.validation.DefaultOpenuiLangValidator;
import com.huawei.cloudsop.genui.core.validation.ValidationIssue;
import com.huawei.cloudsop.genui.core.validation.ValidationRequest;
import com.huawei.cloudsop.genui.core.validation.ValidationResult;
import com.huawei.cloudsop.genui.eval.dataset.CorpusLoaders.LoadedValidationTestCase;
import com.huawei.cloudsop.genui.eval.langsmith.BatchRunBuffer;
import com.huawei.cloudsop.genui.eval.langsmith.LangSmithClient;
import com.huawei.cloudsop.genui.eval.trace.LangSmithGenerationObserver;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 人工标注验证语料（{@code validation-test/} 49 例）的确定性 LangSmith 实验执行器。 精确复现 {@code
 * ValidationCorpusReportGenerator} 的 {@code DETECTED / MISSED / IGNORED / UNLABELED} 覆盖率统计，
 * 并将每条诊断和实验级覆盖率写入 LangSmith。
 *
 * @since 2026
 */
public final class ValidationCorpusExperimentRunner {
    private static final String IGNORED_ANNOTATION = "不用关注";
    private static final int FEEDBACK_THREADS = 8;

    private final LangSmithClient langSmithClient;
    private final String sessionId;
    private final Map<String, String> exampleIdsByCaseId;

    /**
     * 语料验证汇总输出。
     *
     * @param total 总用例数
     * @param statusCounts 状态计数（INVALID / VALID）
     * @param rawIssueCounts 原始诊断按 code 计数
     * @param actionableIssueCounts 可操作诊断按 code 计数
     * @param coverageCounts 标注覆盖计数（DETECTED / MISSED / IGNORED / UNLABELED）
     * @param summaryScores 写入 LangSmith 的汇总数值指标
     * @param caseResults 逐案例明细（供离线 HTML 报告使用）
     * @since 2026
     */
    public record CorpusRunOutput(
            int total,
            Map<String, Integer> statusCounts,
            Map<String, Integer> rawIssueCounts,
            Map<String, Integer> actionableIssueCounts,
            Map<String, Integer> coverageCounts,
            Map<String, Double> summaryScores,
            List<Map<String, Object>> caseResults) {}

    /**
     * 创建语料验证执行器。
     *
     * @param langSmithClient 可选 LangSmith 客户端（离线时为 null）
     * @param sessionId 实验会话 ID，可为 null
     * @param exampleIdsByCaseId {@code caseId -> exampleId} 映射
     */
    public ValidationCorpusExperimentRunner(
            LangSmithClient langSmithClient,
            String sessionId,
            Map<String, String> exampleIdsByCaseId) {
        this.langSmithClient = langSmithClient;
        this.sessionId = sessionId;
        this.exampleIdsByCaseId =
                exampleIdsByCaseId == null ? Map.of() : Map.copyOf(exampleIdsByCaseId);
    }

    /**
     * 顺序执行全部人工标注用例的 FINAL 校验并上报 LangSmith。
     *
     * @param cases 已加载用例列表
     * @return 汇总统计与逐案例明细
     */
    public CorpusRunOutput runAll(List<LoadedValidationTestCase> cases) {
        GenerationContract contract = GenerationContractLoader.loadDefault();
        Map<String, Integer> statusCounts = new LinkedHashMap<>();
        Map<String, Integer> rawIssueCounts = new TreeMap<>();
        Map<String, Integer> actionableIssueCounts = new TreeMap<>();
        Map<String, Integer> coverageCounts = new LinkedHashMap<>();
        List<Map<String, Object>> caseResults = new ArrayList<>(cases.size());

        CorpusCounters counters =
                new CorpusCounters(
                        statusCounts, rawIssueCounts, actionableIssueCounts, coverageCounts);
        BatchRunBuffer buffer =
                langSmithClient == null ? null : new BatchRunBuffer(langSmithClient);
        ExecutorService feedbackPool =
                langSmithClient == null ? null : Executors.newFixedThreadPool(FEEDBACK_THREADS);
        List<Future<?>> feedbackFutures = new ArrayList<>();
        FeedbackContext fbCtx = new FeedbackContext(buffer, feedbackPool, feedbackFutures);
        try {
            for (LoadedValidationTestCase testCase : cases) {
                caseResults.add(evaluateOne(testCase, contract, counters, fbCtx));
            }
            awaitFeedback(feedbackFutures);
        } finally {
            if (feedbackPool != null) {
                feedbackPool.shutdownNow();
            }
            if (buffer != null) {
                buffer.close();
            }
        }

        Map<String, Double> summaryScores =
                computeSummaryScores(cases.size(), statusCounts, coverageCounts);
        publishSummary(summaryScores);
        return new CorpusRunOutput(
                cases.size(),
                statusCounts,
                rawIssueCounts,
                actionableIssueCounts,
                coverageCounts,
                summaryScores,
                caseResults);
    }

    private record CorpusCounters(
            Map<String, Integer> statusCounts,
            Map<String, Integer> rawIssueCounts,
            Map<String, Integer> actionableIssueCounts,
            Map<String, Integer> coverageCounts) {}

    private record FeedbackContext(
            BatchRunBuffer buffer, ExecutorService pool, List<Future<?>> futures) {}

    private Map<String, Object> evaluateOne(
            LoadedValidationTestCase testCase,
            GenerationContract contract,
            CorpusCounters counters,
            FeedbackContext fbCtx) {
        Instant start = Instant.now();
        ValidationResult validation =
                DefaultOpenuiLangValidator.INSTANCE.validate(
                        ValidationRequest.builder()
                                .dsl(testCase.dsl())
                                .contract(contract)
                                .dataModel(testCase.dataModel())
                                .build());
        Instant end = Instant.now();
        String coverage = classifyCoverage(testCase.annotation(), validation);
        counters.statusCounts().merge(validation.status().name(), 1, Integer::sum);
        validation
                .issues()
                .forEach(issue -> counters.rawIssueCounts().merge(issue.code(), 1, Integer::sum));
        validation
                .actionableIssues()
                .forEach(
                        issue ->
                                counters.actionableIssueCounts()
                                        .merge(issue.code(), 1, Integer::sum));
        counters.coverageCounts().merge(coverage, 1, Integer::sum);
        String runId = recordRun(testCase, validation, coverage, start, end, fbCtx);
        return buildCaseRecord(testCase, validation, coverage, runId);
    }

    private static void awaitFeedback(List<Future<?>> futures) {
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                break;
            } catch (java.util.concurrent.ExecutionException error) {
                System.err.println(
                        "[langsmith] corpus feedback failed: " + error.getCause().getMessage());
            }
        }
    }

    private void publishSummary(Map<String, Double> summaryScores) {
        if (langSmithClient == null || sessionId == null) {
            return;
        }
        for (Map.Entry<String, Double> entry : summaryScores.entrySet()) {
            langSmithClient.createSummaryFeedback(
                    sessionId, entry.getKey(), entry.getValue(), null);
        }
        langSmithClient.finishSession(sessionId, Instant.now(), null);
    }

    /**
     * 按 {@code ValidationCorpusReportGenerator} 相同口径计算单条案例的标注覆盖状态。
     *
     * @param annotation {@code error_detail.txt} 标注文本，可为 null
     * @param validation 校验结果
     * @return {@code DETECTED}、{@code MISSED}、{@code IGNORED} 或 {@code UNLABELED}
     */
    public static String classifyCoverage(String annotation, ValidationResult validation) {
        if (annotation == null || annotation.isBlank()) {
            return "UNLABELED";
        }
        if (annotation.contains(IGNORED_ANNOTATION)) {
            return "IGNORED";
        }
        return validation.hasBlockingIssues() ? "DETECTED" : "MISSED";
    }

    private String recordRun(
            LoadedValidationTestCase testCase,
            ValidationResult validation,
            String coverage,
            Instant start,
            Instant end,
            FeedbackContext fbCtx) {
        LangSmithGenerationObserver observer =
                new LangSmithGenerationObserver(
                        fbCtx.buffer(),
                        sessionId,
                        exampleIdsByCaseId.get(testCase.id()),
                        testCase.id(),
                        "corpus_validate_" + testCase.id(),
                        Map.of(
                                "caseId",
                                testCase.id(),
                                "sourceFile",
                                testCase.sourceFile(),
                                "hasDataModel",
                                testCase.dataModel() != null),
                        Map.of("track", "validation-corpus"));
        observer.recordStandaloneValidation(
                "openui_validator", testCase.dsl(), validation, start, end);
        observer.finishRoot(
                Map.of(
                        "status",
                        validation.status().name(),
                        "coverage",
                        coverage,
                        "rawIssueCount",
                        validation.issues().size(),
                        "actionableIssueCount",
                        validation.actionableIssues().size()),
                null,
                end);
        if (langSmithClient != null && fbCtx.pool() != null) {
            String runId = observer.rootRunId();
            fbCtx.futures()
                    .add(
                            fbCtx.pool()
                                    .submit(
                                            () ->
                                                    submitCaseFeedback(
                                                            runId,
                                                            validation,
                                                            coverage,
                                                            testCase.annotation())));
        }
        return observer.rootRunId();
    }

    private void submitCaseFeedback(
            String runId, ValidationResult validation, String coverage, String annotation) {
        langSmithClient.createRunValueFeedback(
                runId, sessionId, "validation_status", validation.status().name(), null);
        langSmithClient.createRunValueFeedback(
                runId, sessionId, "annotation_coverage", coverage, annotation);
        langSmithClient.createRunScoreFeedback(
                runId, sessionId, "raw_issue_count", validation.issues().size(), null);
        langSmithClient.createRunScoreFeedback(
                runId,
                sessionId,
                "actionable_issue_count",
                validation.actionableIssues().size(),
                null);
    }

    private static Map<String, Object> buildCaseRecord(
            LoadedValidationTestCase testCase,
            ValidationResult validation,
            String coverage,
            String runId) {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("id", testCase.id());
        row.put("sourceFile", testCase.sourceFile());
        row.put("status", validation.status().name());
        row.put("coverage", coverage);
        row.put("annotation", testCase.annotation());
        row.put("rawIssueCount", validation.issues().size());
        row.put("actionableIssueCount", validation.actionableIssues().size());
        row.put(
                "actionableCodes",
                validation.actionableIssues().stream()
                        .map(ValidationIssue::code)
                        .distinct()
                        .sorted()
                        .toList());
        row.put("langsmithRunId", runId);
        return row;
    }

    private static Map<String, Double> computeSummaryScores(
            int total, Map<String, Integer> statusCounts, Map<String, Integer> coverageCounts) {
        LinkedHashMap<String, Double> scores = new LinkedHashMap<>();
        int detected = coverageCounts.getOrDefault("DETECTED", 0);
        int missed = coverageCounts.getOrDefault("MISSED", 0);
        int invalid = statusCounts.getOrDefault("INVALID", 0);
        if (detected + missed > 0) {
            scores.put("detection_recall", (double) detected / (detected + missed));
        }
        if (total > 0) {
            scores.put("invalid_rate", (double) invalid / total);
        }
        scores.put("detected_count", (double) detected);
        scores.put("missed_count", (double) missed);
        return scores;
    }
}
