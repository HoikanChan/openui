/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.bench;

import com.huawei.cloudsop.genui.core.GenerationSdk;
import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.core.llm.GenUiGenerationResult;
import com.huawei.cloudsop.genui.core.llm.GenUiGenerator;
import com.huawei.cloudsop.genui.core.llm.GenUiLlmConfig;
import com.huawei.cloudsop.genui.core.llm.GenerationEndReason;
import com.huawei.cloudsop.genui.core.llm.UiGenerationRequest;
import com.huawei.cloudsop.genui.core.llm.transport.InMemoryLlmTransport;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransport;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransportException;
import com.huawei.cloudsop.genui.core.validation.DefaultOpenuiLangValidator;
import com.huawei.cloudsop.genui.core.validation.GenUiValidationConfig;
import com.huawei.cloudsop.genui.core.validation.ValidationIssue;
import com.huawei.cloudsop.genui.core.validation.ValidationMode;
import com.huawei.cloudsop.genui.core.validation.ValidationRequest;
import com.huawei.cloudsop.genui.core.validation.ValidationResult;
import com.huawei.cloudsop.genui.core.validation.ValidationStatus;
import com.huawei.cloudsop.genui.core.validation.repair.IncrementalRepairPromptBuilder;
import com.huawei.cloudsop.genui.core.validation.stream.RoundEndResult;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * repair-bench 批量执行器。照 {@code JobRunner} 形态：有界固定线程池、按完成顺序逐行 JSONL、 {@code synchronized} emit；加载阶段的语料损坏已在 loader
 * 层整批终止（退出码 1），执行阶段单案例异常转 {@code error} 行不中断批次。
 *
 * <p>
 * 单案例流程（实现设计 §3）：案例契约现算 initial FINAL 校验 → 复现判定（非 INVALID 即 {@code not-reproduced}）→ 用公开 {@link GenUiGenerator}
 * 跑一次真实生成会话：transport 把案例 DSL 当作"历史初次生成"返回，生成会话随后做轮末统一校验与增量修复 → 由轮记录映射输出状态。 全链路 issue code 一律取 <b>actionable</b>
 * 视图（与修复提示词所见一致）。
 *
 * <p>
 * 每个案例独立构建 {@link GenerationSdk}（案例契约）与生成器，避免跨案例共享契约/会话状态。
 *
 * @since 2026
 */
final class RepairBenchRunner {

    private final LlmTransport transport;

    private final String model;

    private final int maxAttempts;

    private final Duration timeout;

    private final PrintStream out;

    /** 全批共享的确定性生成/修复配置：温度 0 + 不开 thinking（镜像 GenUiGenerator.repairConfig 的纪律）。 */
    private final GenUiLlmConfig repairConfig;

    /** 是否跑修复：false = 每案例只做 FINAL 校验（validation-only，不初始化 transport、零 LLM 调用）。 */
    private final boolean repair;

    RepairBenchRunner(LlmTransport transport, String model, int maxAttempts, Duration timeout, PrintStream out) {
        this(transport, model, maxAttempts, timeout, out, true);
    }

    RepairBenchRunner(LlmTransport transport, String model, int maxAttempts, Duration timeout, PrintStream out,
            boolean repair) {
        this.transport = transport;
        this.model = model;
        this.maxAttempts = maxAttempts;
        this.timeout = timeout == null ? Duration.ZERO : timeout;
        this.out = out;
        this.repair = repair;
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
        emit(Json.stringify(
                summary.toLine(model, maxAttempts, concurrency, timeout, System.currentTimeMillis() - batchStart)));
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

        // 与 ValidateCli 一致固定 data:bench 案例 DSL 全部引用 data(提示词契约根名),dataModel 为 null 只关
        // 类型校验;若随之清空 externalRefs,这些案例会被整批误报 unresolved-ref。
        Set<String> externalRefs = Set.of("data");

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

        // ── 步骤 1.5：validation-only（--repair 未开）时到此为止，不碰 transport ──
        if (!repair) {
            LinkedHashMap<String, Object> line = baseLine(benchCase);
            line.put("status", "invalid");
            line.put("success", false);
            line.put("reproduced", true);
            line.put("initialIssueCodes", initialCodes);
            putInternalCodes(line, benchCase);
            line.put("durationMs", System.currentTimeMillis() - started);
            emit(Json.stringify(line));
            summary.record("invalid", initialCodes);
            return;
        }

        // ── 步骤 2：真实生成会话（transport 重放历史 DSL 作为初次生成，轮末校验 + 增量修复）──
        GenerationSdk sdk = GenerationSdk.builder().baseContract(benchCase.contract()).build();
        CaseReplayTransport replay = new CaseReplayTransport(transport, benchCase.dsl(), maxAttempts, timeout);
        GenUiGenerator generator = GenUiGenerator.withTransport(sdk, repairConfig, replay,
                GenUiValidationConfig.incremental(), null);
        UiGenerationRequest request = UiGenerationRequest.builder().userInput(benchCase.originalIntent())
                .response(benchCase.dataModel()).build();
        GenUiGenerationResult generated = generator.generate(request);

        List<RoundEndResult> rounds = generated.rounds();
        RoundEndResult lastValidated = null;
        for (RoundEndResult round : rounds) {
            if (round.validationResult() != null) {
                lastValidated = round;
            }
        }
        boolean repaired = lastValidated != null && lastValidated.validationResult().status() == ValidationStatus.VALID;

        String status;
        if (repaired) {
            status = "repaired";
        } else if (replay.timedOut()) {
            status = "timeout";
        } else if (replay.attemptBudgetExhausted()) {
            status = "still-invalid";
        } else if (generated.endReason() == GenerationEndReason.TIMEOUT) {
            status = "timeout";
        } else if (generated.endReason() == GenerationEndReason.MODEL_REQUEST_FAILED) {
            status = "transport-error";
        } else {
            status = "still-invalid";
        }

        LinkedHashMap<String, Object> line = baseLine(benchCase);
        line.put("status", status);
        line.put("success", repaired);
        line.put("reproduced", true);
        line.put("initialIssueCodes", initialCodes);
        putInternalCodes(line, benchCase);
        List<Object> attempts = attempts(rounds, replay, generated);
        line.put("attemptCount", attempts.size());
        line.put("attempts", attempts);
        line.put("finalIssueCodes",
                lastValidated == null ? List.of() : actionableCodes(lastValidated.validationResult()));
        line.put("finalDsl", lastValidated == null ? null : lastValidated.currentDefinitions());
        line.put("durationMs", System.currentTimeMillis() - started);
        emit(Json.stringify(line));
        summary.record(status, initialCodes);
    }

    /**
     * 把生成会话的轮记录映射为旧的逐 attempt 输出：跳过初次生成轮；被中断/预算拒绝的请求没有校验结论，除 传输失败外不计入 attempt。
     */
    private static List<Object> attempts(List<RoundEndResult> rounds, CaseReplayTransport replay,
            GenUiGenerationResult generated) {
        List<Object> attempts = new ArrayList<>();
        for (int index = 1; index < rounds.size(); index++) {
            RoundEndResult round = rounds.get(index);
            if (round.validationResult() == null) {
                continue;
            }
            attempts.add(attemptLine(attempts.size() + 1, round.validationResult().isValid(),
                    actionableCodes(round.validationResult())));
        }
        boolean interruptedTransportFailure = generated.endReason() == GenerationEndReason.MODEL_REQUEST_FAILED
                && !replay.attemptBudgetExhausted() && !replay.timedOut() && !rounds.isEmpty()
                && rounds.get(rounds.size() - 1).validationResult() == null;
        if (interruptedTransportFailure) {
            // 传输失败的请求没有轮末结论：旧输出的 attempt 行 valid=false、issueCodes=null。
            attempts.add(attemptLine(attempts.size() + 1, false, null));
        }
        return attempts;
    }

    private static LinkedHashMap<String, Object> attemptLine(int attempt, boolean valid, List<String> issueCodes) {
        LinkedHashMap<String, Object> attemptLine = new LinkedHashMap<>();
        attemptLine.put("attempt", attempt);
        attemptLine.put("valid", valid);
        attemptLine.put("issueCodes", issueCodes);
        return attemptLine;
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
     * 单案例 transport 包装：初次生成请求直接返回案例 DSL（bench 复现历史失败，不打模型），增量修复请求转交真实 transport，整页 Markdown 降级请求返回桩（修复失败不应额外消耗一次模型调用）。
     *
     * <p>
     * 修复请求超过 {@code maxAttempts} 或超过 {@code timeout} 时抛 {@link LlmTransportException} 并以标记记录原因：生成会话会把
     * 它当作模型请求失败结束，但轮记录仍保留，调用方据此映射为 {@code still-invalid} / {@code timeout}。
     */
    private static final class CaseReplayTransport implements InMemoryLlmTransport {

        /** 与 {@code MarkdownGeneration} 的固定 system 文案对齐，用于识别整页降级请求。 */
        private static final String MARKDOWN_SYSTEM_HINT = "Answer the user's request in Markdown only.";

        private final LlmTransport delegate;

        private final String initialDsl;

        private final int maxAttempts;

        private final Duration timeout;

        private final long repairStartedNanos = System.nanoTime();

        private final AtomicInteger repairCalls = new AtomicInteger();

        private volatile boolean attemptBudgetExhausted;

        private volatile boolean timedOut;

        CaseReplayTransport(LlmTransport delegate, String initialDsl, int maxAttempts, Duration timeout) {
            this.delegate = delegate;
            this.initialDsl = initialDsl;
            this.maxAttempts = maxAttempts;
            this.timeout = timeout == null ? Duration.ZERO : timeout;
        }

        boolean attemptBudgetExhausted() {
            return attemptBudgetExhausted;
        }

        boolean timedOut() {
            return timedOut;
        }

        @Override
        public String post(String body) throws LlmTransportException {
            return switch (classify(body)) {
                case INITIAL -> syncResponse(initialDsl);
                case MARKDOWN -> syncResponse("Incremental repair did not produce a valid page.");
                case REPAIR -> repair(body);
            };
        }

        @Override
        public InputStream postStream(String body) throws LlmTransportException {
            return switch (classify(body)) {
                case INITIAL, MARKDOWN -> new ByteArrayInputStream(new byte[0]);
                case REPAIR -> delegate.postStream(body);
            };
        }

        private String repair(String body) throws LlmTransportException {
            if (!timeout.isZero() && System.nanoTime() - repairStartedNanos >= timeout.toNanos()) {
                timedOut = true;
                throw new LlmTransportException("repair timeout after " + repairCalls.get() + " attempt(s)");
            }
            if (repairCalls.get() >= maxAttempts) {
                attemptBudgetExhausted = true;
                throw new LlmTransportException("repair attempt budget exhausted (" + maxAttempts + ")");
            }
            repairCalls.incrementAndGet();
            return delegate.post(body);
        }

        private static RequestKind classify(String body) {
            try {
                Map<String, Object> parsed = Json.asObject(Json.parse(body), "request body");
                List<Object> messages = Json.asList(parsed.get("messages"), "messages");
                StringBuilder system = new StringBuilder();
                for (Object item : messages) {
                    Map<String, Object> message = Json.asObject(item, "message");
                    if ("system".equals(message.get("role")) && message.get("content") != null) {
                        system.append(message.get("content"));
                    }
                }
                String text = system.toString();
                if (text.contains(IncrementalRepairPromptBuilder.INCREMENTAL_REPAIR_MARKER)) {
                    return RequestKind.REPAIR;
                }
                if (text.contains(MARKDOWN_SYSTEM_HINT)) {
                    return RequestKind.MARKDOWN;
                }
                return RequestKind.INITIAL;
            } catch (RuntimeException error) {
                // 无法解析的请求按修复处理：交给真实 transport 暴露问题，而不是伪造初次生成。
                return RequestKind.REPAIR;
            }
        }

        private static String syncResponse(String dsl) {
            return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\""
                    + dsl.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"}}]}";
        }

        private enum RequestKind {
            INITIAL, REPAIR, MARKDOWN
        }
    }

    /**
     * 批次汇总累加器（worker 线程共享，全部方法 synchronized）。口径（实现设计 §5.2）：
     *
     * <ul>
     * <li>主口径 = repaired / (repaired + still-invalid)——transport-error 与 timeout 剔除分母（基础设施噪声与 自设预算不算"修不动"，可筛出重跑）；分母为 0
     * 时 rate 为 null。
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
