/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.bench;

import com.huawei.cloudsop.genui.core.GenerationSdk;
import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.core.contract.DataModelSpec;
import com.huawei.cloudsop.genui.core.llm.GenUiLlmConfig;
import com.huawei.cloudsop.genui.core.llm.protocol.ChatCompletionRequest;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransport;
import com.huawei.cloudsop.genui.core.prompt.GenUIPromptRequest;
import com.huawei.cloudsop.genui.core.validation.DefaultOpenuiLangValidator;
import com.huawei.cloudsop.genui.core.validation.RepairPolicyKind;
import com.huawei.cloudsop.genui.core.validation.ValidationIssue;
import com.huawei.cloudsop.genui.core.validation.ValidationMode;
import com.huawei.cloudsop.genui.core.validation.ValidationRequest;
import com.huawei.cloudsop.genui.core.validation.ValidationResult;
import com.huawei.cloudsop.genui.core.validation.ValidationStatus;
import com.huawei.cloudsop.genui.core.validation.repair.RepairCoordinator;
import com.huawei.cloudsop.genui.core.validation.repair.RepairCoordinator.FullRepairOutcome;
import com.huawei.cloudsop.genui.core.validation.repair.RepairPolicy;

import java.io.PrintStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * repair-bench 批量执行器。照 {@code JobRunner} 形态：有界固定线程池、按完成顺序逐行 JSONL、
 * {@code synchronized} emit；加载阶段的语料损坏已在 loader 层整批终止（退出码 1），执行阶段单案例异常转
 * {@code error} 行不中断批次。
 *
 * <p>
 * 单案例流程（实现设计 §3）：案例契约现算 originalSystemPrompt → FINAL 复现判定（非 INVALID 即
 * {@code not-reproduced}）→ {@link RepairCoordinator#repairFull} 驱动修复 → outcome kind 映射输出状态。
 * 全链路 issue code 一律取 <b>actionable</b> 视图（与修复提示词所见一致）。
 *
 * @since 2026
 */
final class RepairBenchRunner {

    private final LlmTransport transport;

    private final String model;

    private final int maxAttempts;

    private final Duration timeout;

    private final PrintStream out;

    /** 全批共享的确定性修复配置：温度 0 + 不开 thinking（镜像 GenUiGenerator.repairConfig 的纪律）。 */
    private final GenUiLlmConfig repairConfig;

    RepairBenchRunner(LlmTransport transport, String model, int maxAttempts, Duration timeout, PrintStream out) {
        this.transport = transport;
        this.model = model;
        this.maxAttempts = maxAttempts;
        this.timeout = timeout == null ? Duration.ZERO : timeout;
        this.out = out;
        this.repairConfig = GenUiLlmConfig.builder().defaultModel(model).temperature(0).enableThinking(false).build();
    }

    /**
     * 跑完整批案例：逐案例行按完成顺序输出，批次末尾输出一行汇总。
     *
     * @param cases
     *            已加载的案例（顺序即执行提交顺序）
     * @param concurrency
     *            线程池大小
     * @throws InterruptedException
     *             等待期间被中断
     */
    void runAll(List<RepairCasePack.RepairCase> cases, int concurrency) throws InterruptedException {
        long batchStart = System.currentTimeMillis();
        SummaryAccumulator summary = new SummaryAccumulator();
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(concurrency, Math.max(cases.size(), 1)));
        try {
            List<Future<?>> futures = cases.stream()
                    .map(benchCase -> pool.submit(() -> runOneSafely(benchCase, summary))).<Future<?>>map(f -> f)
                    .toList();
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (java.util.concurrent.ExecutionException error) {
                    // runOneSafely 已把案例级异常转为 error 行；走到这里属于意外，记录后继续等其余案例
                    System.err.println("[repair-bench] unexpected worker failure: " + error.getCause());
                }
            }
        } finally {
            pool.shutdownNow();
        }
        emit(Json.stringify(summary.toLine(model, maxAttempts, concurrency, timeout,
                System.currentTimeMillis() - batchStart)));
    }

    private void runOneSafely(RepairCasePack.RepairCase benchCase, SummaryAccumulator summary) {
        try {
            runOne(benchCase, summary);
        } catch (Exception error) {
            LinkedHashMap<String, Object> line = baseLine(benchCase);
            line.put("status", "error");
            line.put("success", false);
            line.put("reproduced", false);
            line.put("error", String.valueOf(error));
            emit(Json.stringify(line));
            summary.record("error", null);
        }
    }

    private void runOne(RepairCasePack.RepairCase benchCase, SummaryAccumulator summary) {
        long started = System.currentTimeMillis();

        // originalSystemPrompt 现算：案例契约即"当时契约"，assemblePrompt 从同一契约现算与原始生成自洽；
        // 入参带案例自己的 dataModel（镜像 GenUiGenerator.toPromptRequest 的装配）。
        GenerationSdk sdk = GenerationSdk.builder().baseContract(benchCase.contract()).build();
        GenUIPromptRequest promptRequest = new GenUIPromptRequest(null,
                benchCase.dataModel() == null ? null : new DataModelSpec("Response data", benchCase.dataModel()),
                List.of(), null, null, null, null);
        String systemPrompt = sdk.assemblePrompt(promptRequest).prompt();
        Set<String> externalRefs = benchCase.dataModel() == null ? Set.of() : Set.of("data");

        // ── 步骤 1：复现判定（FINAL）──
        ValidationResult v0 = DefaultOpenuiLangValidator.INSTANCE.validate(ValidationRequest.builder()
                .dsl(benchCase.dsl()).contract(benchCase.contract())
                .rootName(benchCase.contract().root() == null ? "root" : benchCase.contract().root())
                .externalRefs(externalRefs).dataModel(benchCase.dataModel()).mode(ValidationMode.FINAL).build());
        List<String> initialCodes = actionableCodes(v0);

        if (v0.status() != ValidationStatus.INVALID) {
            LinkedHashMap<String, Object> line = baseLine(benchCase);
            line.put("status", "not-reproduced");
            line.put("success", false);
            line.put("reproduced", false);
            line.put("initialIssueCodes", List.of());
            putInternalCodes(line, benchCase);
            line.put("durationMs", System.currentTimeMillis() - started);
            emit(Json.stringify(line));
            summary.record("not-reproduced", null);
            return;
        }

        // ── 步骤 2：repairFull ──
        RepairCoordinator coordinator = new RepairCoordinator(transport, DefaultOpenuiLangValidator.INSTANCE,
                new RepairPolicy(RepairPolicyKind.FINAL_REPAIR, maxAttempts, timeout, Duration.ZERO))
                .withBodyBuilder((messages, stream) -> ChatCompletionRequest.of(repairConfig, null, messages, stream)
                        .toJson())
                .withExternalRefs(externalRefs).withDataModel(benchCase.dataModel());
            FullRepairOutcome outcome = coordinator.repairFull(benchCase.originalIntent(), benchCase.dsl(), v0,
                    benchCase.contract(), benchCase.contract().root(), systemPrompt);

        String status = switch (outcome.kind()) {
            case REPAIRED -> "repaired";
            case STILL_INVALID -> "still-invalid";
            case TRANSPORT_ERROR -> "transport-error";
            case TIMED_OUT -> "timeout";
        };

        LinkedHashMap<String, Object> line = baseLine(benchCase);
        line.put("status", status);
        line.put("success", outcome.repaired());
        line.put("reproduced", true);
        line.put("initialIssueCodes", initialCodes);
        putInternalCodes(line, benchCase);
        line.put("attemptCount", outcome.attempts());
        List<Object> attempts = new ArrayList<>();
        for (FullRepairOutcome.AttemptRecord record : outcome.attemptLog()) {
            LinkedHashMap<String, Object> attemptLine = new LinkedHashMap<>();
            attemptLine.put("attempt", record.attempt());
            attemptLine.put("valid", record.valid());
            attemptLine.put("issueCodes",
                    record.issues() == null ? null : actionableCodes(record.issues()));
            attempts.add(attemptLine);
        }
        line.put("attempts", attempts);
        line.put("finalIssueCodes", outcome.result() == null ? List.of() : actionableCodes(outcome.result()));
        line.put("finalDsl", outcome.dsl());
        line.put("durationMs", System.currentTimeMillis() - started);
        emit(Json.stringify(line));
        summary.record(status, initialCodes);
    }

    private static LinkedHashMap<String, Object> baseLine(RepairCasePack.RepairCase benchCase) {
        LinkedHashMap<String, Object> line = new LinkedHashMap<>();
        line.put("type", "case");
        line.put("caseId", benchCase.id());
        return line;
    }

    private static void putInternalCodes(LinkedHashMap<String, Object> line, RepairCasePack.RepairCase benchCase) {
        if (benchCase.internalIssueCodes() != null) {
            line.put("internalIssueCodes", benchCase.internalIssueCodes());
        }
    }

    /** 全链路统一 actionable 视图（修复提示词消费的口径），升序去重。 */
    private static List<String> actionableCodes(ValidationResult result) {
        return actionableCodes(result.actionableIssues());
    }

    private static List<String> actionableCodes(List<ValidationIssue> issues) {
        return issues.stream().map(ValidationIssue::code).distinct().sorted().toList();
    }

    private synchronized void emit(String line) {
        out.println(line);
        out.flush();
    }

    /**
     * 批次汇总累加器（worker 线程共享，全部方法 synchronized）。口径（实现设计 §5.2）：
     *
     * <ul>
     * <li>主口径 = repaired / (repaired + still-invalid)——transport-error 与 timeout 剔除分母（基础设施噪声与
     * 自设预算不算"修不动"，可筛出重跑）；分母为 0 时 rate 为 null。
     * <li>副口径 = repaired / total（not-reproduced 计入惩罚，度量外部环境保真度）。
     * <li>issueBuckets 仅统计 reproduced 案例，按 initialIssueCodes 分桶（一案例可入多桶）。
     * </ul>
     */
    private static final class SummaryAccumulator {
        private final Map<String, int[]> byStatus = new TreeMap<>();

        private final Map<String, Map<String, Integer>> buckets = new TreeMap<>();

        private int total;

        synchronized void record(String status, List<String> initialCodes) {
            total++;
            byStatus.computeIfAbsent(status, key -> new int[1])[0]++;
            if (initialCodes != null) {
                for (String code : initialCodes) {
                    Map<String, Integer> bucket = buckets.computeIfAbsent(code, key -> new TreeMap<>());
                    bucket.put("cases", bucket.getOrDefault("cases", 0) + 1);
                    bucket.put(status, bucket.getOrDefault(status, 0) + 1);
                }
            }
        }

        synchronized Map<String, Object> toLine(String model, int maxAttempts, int concurrency, Duration timeout,
                long durationMs) {
            long repaired = count("repaired");
            long stillInvalid = count("still-invalid");

            LinkedHashMap<String, Object> line = new LinkedHashMap<>();
            line.put("type", "summary");
            line.put("model", model);
            line.put("maxAttempts", maxAttempts);
            line.put("concurrency", concurrency);
            line.put("timeoutMinutes", timeout.toMinutes());
            line.put("total", total);
            LinkedHashMap<String, Object> statusCounts = new LinkedHashMap<>();
            for (Map.Entry<String, int[]> entry : byStatus.entrySet()) {
                statusCounts.put(entry.getKey(), entry.getValue()[0]);
            }
            line.put("byStatus", statusCounts);

            long mainDenominator = repaired + stillInvalid;
            LinkedHashMap<String, Object> mainRate = new LinkedHashMap<>();
            mainRate.put("success", repaired);
            mainRate.put("denominator", mainDenominator);
            mainRate.put("rate", mainDenominator == 0 ? null : (double) repaired / mainDenominator);
            line.put("mainRate", mainRate);

            LinkedHashMap<String, Object> secondaryRate = new LinkedHashMap<>();
            secondaryRate.put("success", repaired);
            secondaryRate.put("denominator", total);
            secondaryRate.put("rate", total == 0 ? null : (double) repaired / total);
            line.put("secondaryRate", secondaryRate);

            LinkedHashMap<String, Object> issueBuckets = new LinkedHashMap<>();
            for (Map.Entry<String, Map<String, Integer>> entry : buckets.entrySet()) {
                LinkedHashMap<String, Object> bucket = new LinkedHashMap<>();
                Map<String, Integer> counts = entry.getValue();
                bucket.put("cases", counts.get("cases"));
                putIfPresent(bucket, counts, "repaired");
                putIfPresent(bucket, counts, "still-invalid");
                putIfPresent(bucket, counts, "transport-error");
                putIfPresent(bucket, counts, "timeout");
                for (Map.Entry<String, Integer> extra : counts.entrySet()) {
                    bucket.putIfAbsent(extra.getKey(), extra.getValue());
                }
                issueBuckets.put(entry.getKey(), bucket);
            }
            line.put("issueBuckets", issueBuckets);
            line.put("durationMs", durationMs);
            return line;
        }

        private static void putIfPresent(Map<String, Object> bucket, Map<String, Integer> counts, String key) {
            if (counts.containsKey(key)) {
                bucket.put(key, counts.get(key));
            }
        }

        private long count(String status) {
            int[] holder = byStatus.get(status);
            return holder == null ? 0 : holder[0];
        }
    }
}
