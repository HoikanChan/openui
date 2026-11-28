/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.eval.repair;

import com.huawei.cloudsop.genui.core.llm.GenerationTiming;
import com.huawei.cloudsop.genui.eval.langsmith.LangSmithClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/**
 * 纯函数汇总计算器：精确镜像 {@code RepairBenchRunner.SummaryAccumulator} 的全部口径（主口径、端到端口径、
 * 副口径、分桶修复率与能力边界合规率），同时产出 JSONL summary 行与 LangSmith Session-level Summary Feedback。
 *
 * @since 2026
 */
public final class SummaryFeedbackCalculator {
    private final Map<String, int[]> byStatus = new TreeMap<>();
    private final Map<String, Map<String, Integer>> buckets = new TreeMap<>();
    private int total;
    private int capabilityGapCases;
    private int capabilityGapRepaired;
    private int capabilityNoticeCount;
    private int casesWithRemainingGapCalls;
    private int consecutiveUnmatchedCases;
    private int consecutiveUnmatchedTotal;

    /**
     * 累加单条案例的评测结果（线程安全）。
     *
     * @param status 案例最终状态（repaired / still-invalid / invalid / not-reproduced / timeout /
     *     transport-error / error）
     * @param initialCodes 初始 actionable 错误码列表，未复现时为 null
     * @param initialHasCapabilityGap 初始诊断是否命中能力缺口提示
     * @param capabilityNoticePresent 最终可达语句是否包含 {@code capabilityNotice}
     * @param remainingGapAliases 最终可达语句中残留的能力缺口别名
     * @param consecutiveUnmatched 连续两轮未匹配的未知组件名
     */
    public synchronized void record(
            String status,
            List<String> initialCodes,
            boolean initialHasCapabilityGap,
            boolean capabilityNoticePresent,
            List<String> remainingGapAliases,
            List<String> consecutiveUnmatched) {
        total++;
        byStatus.computeIfAbsent(status, key -> new int[1])[0]++;
        if (initialCodes != null) {
            for (String code : initialCodes) {
                Map<String, Integer> bucket = buckets.computeIfAbsent(code, key -> new TreeMap<>());
                bucket.put("cases", bucket.getOrDefault("cases", 0) + 1);
                bucket.put(status, bucket.getOrDefault(status, 0) + 1);
            }
        }
        if (initialHasCapabilityGap) {
            capabilityGapCases++;
            if ("repaired".equals(status)) {
                capabilityGapRepaired++;
                if (capabilityNoticePresent) {
                    capabilityNoticeCount++;
                }
            }
        }
        if (remainingGapAliases != null && !remainingGapAliases.isEmpty()) {
            casesWithRemainingGapCalls++;
        }
        if (consecutiveUnmatched != null && !consecutiveUnmatched.isEmpty()) {
            consecutiveUnmatchedCases++;
            consecutiveUnmatchedTotal += consecutiveUnmatched.size();
        }
    }

    /**
     * 构造与离线 HTML 报告完全兼容的 summary JSON 行。
     *
     * @param model 模型名，validation-only 时为 null
     * @param maxAttempts 最大修复轮数
     * @param concurrency 并发度
     * @param timeout 墙钟超时
     * @param durationMs 批次总耗时（毫秒）
     * @return summary 对象
     */
    public synchronized Map<String, Object> toSummaryLine(
            String model, int maxAttempts, int concurrency, Duration timeout, long durationMs) {
        long repaired = count("repaired");
        long stillInvalid = count("still-invalid");

        LinkedHashMap<String, Object> line = new LinkedHashMap<>();
        line.put("type", "summary");
        line.put("model", model);
        line.put("maxAttempts", maxAttempts);
        line.put("concurrency", concurrency);
        line.put("timeoutMinutes", timeout == null ? 0L : timeout.toMinutes());
        line.put("total", total);

        LinkedHashMap<String, Object> statusCounts = new LinkedHashMap<>();
        for (Map.Entry<String, int[]> entry : byStatus.entrySet()) {
            statusCounts.put(entry.getKey(), entry.getValue()[0]);
        }
        line.put("byStatus", statusCounts);

        long mainDenominator = repaired + stillInvalid;
        line.put("mainRate", rateBlock(repaired, mainDenominator));
        line.put(
                "mainRateExcludes",
                List.of("timeout", "transport-error", "error", "not-reproduced"));

        long endToEndDenominator =
                repaired
                        + stillInvalid
                        + count("timeout")
                        + count("transport-error")
                        + count("error");
        line.put("endToEndRate", rateBlock(repaired, endToEndDenominator));
        line.put(
                "sessionBudgetSeconds",
                TimeUnit.NANOSECONDS.toSeconds(GenerationTiming.BUDGET_NANOS));
        line.put("secondaryRate", rateBlock(repaired, total));
        line.put("issueBuckets", buildIssueBuckets());
        line.put("capabilityBoundary", buildCapabilityBoundary());
        line.put("durationMs", durationMs);
        return line;
    }

    /**
     * 计算全部需要提交到 LangSmith 实验级（Session Summary）的数值指标。
     *
     * @return {@code feedbackKey -> score} 有序映射
     */
    public synchronized Map<String, Double> toLangSmithSummaryScores() {
        LinkedHashMap<String, Double> scores = new LinkedHashMap<>();
        long repaired = count("repaired");
        long stillInvalid = count("still-invalid");
        long notReproduced = count("not-reproduced");
        long mainDenominator = repaired + stillInvalid;
        long endToEndDenominator =
                repaired
                        + stillInvalid
                        + count("timeout")
                        + count("transport-error")
                        + count("error");

        if (mainDenominator > 0) {
            scores.put("main_repair_rate", (double) repaired / mainDenominator);
        }
        if (endToEndDenominator > 0) {
            scores.put("end_to_end_repair_rate", (double) repaired / endToEndDenominator);
        }
        if (total > 0) {
            scores.put("secondary_repair_rate", (double) repaired / total);
            scores.put("not_reproduced_rate", (double) notReproduced / total);
        }
        if (capabilityGapRepaired > 0) {
            scores.put(
                    "capability_notice_rate",
                    (double) capabilityNoticeCount / capabilityGapRepaired);
        }
        scores.put("cases_with_remaining_gap_calls", (double) casesWithRemainingGapCalls);
        scores.put("consecutive_unmatched_unknown_cases", (double) consecutiveUnmatchedCases);

        for (Map.Entry<String, Map<String, Integer>> entry : buckets.entrySet()) {
            Map<String, Integer> counts = entry.getValue();
            int bRepaired = counts.getOrDefault("repaired", 0);
            int bStillInvalid = counts.getOrDefault("still-invalid", 0);
            int denom = bRepaired + bStillInvalid;
            if (denom > 0) {
                scores.put(
                        "bucket::" + entry.getKey() + "::repair_rate", (double) bRepaired / denom);
            }
        }
        return scores;
    }

    /**
     * 将全部汇总口径写入 LangSmith 实验 Session Feedback。
     *
     * @param client LangSmith 客户端
     * @param sessionId 实验会话 ID
     */
    public void publishSummaryFeedback(LangSmithClient client, String sessionId) {
        if (client == null || sessionId == null) {
            return;
        }
        Map<String, Double> scores = toLangSmithSummaryScores();
        for (Map.Entry<String, Double> entry : scores.entrySet()) {
            client.createSummaryFeedback(sessionId, entry.getKey(), entry.getValue(), null);
        }
    }

    private Map<String, Object> buildIssueBuckets() {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
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
            result.put(entry.getKey(), bucket);
        }
        return result;
    }

    private Map<String, Object> buildCapabilityBoundary() {
        LinkedHashMap<String, Object> boundary = new LinkedHashMap<>();
        boundary.put("capabilityGapCases", capabilityGapCases);
        boundary.put("capabilityGapRepaired", capabilityGapRepaired);
        boundary.put("capabilityNoticeCount", capabilityNoticeCount);
        boundary.put(
                "capabilityNoticeRate",
                capabilityGapRepaired == 0
                        ? null
                        : (double) capabilityNoticeCount / capabilityGapRepaired);
        boundary.put("casesWithRemainingGapCalls", casesWithRemainingGapCalls);
        boundary.put("consecutiveUnmatchedUnknownComponentCases", consecutiveUnmatchedCases);
        boundary.put("consecutiveUnmatchedUnknownComponentCount", consecutiveUnmatchedTotal);
        return boundary;
    }

    private static Map<String, Object> rateBlock(long success, long denominator) {
        LinkedHashMap<String, Object> block = new LinkedHashMap<>();
        block.put("success", success);
        block.put("denominator", denominator);
        block.put("rate", denominator == 0 ? null : (double) success / denominator);
        return block;
    }

    private static void putIfPresent(
            Map<String, Object> bucket, Map<String, Integer> counts, String key) {
        if (counts.containsKey(key)) {
            bucket.put(key, counts.get(key));
        }
    }

    private long count(String status) {
        int[] holder = byStatus.get(status);
        return holder == null ? 0 : holder[0];
    }
}
