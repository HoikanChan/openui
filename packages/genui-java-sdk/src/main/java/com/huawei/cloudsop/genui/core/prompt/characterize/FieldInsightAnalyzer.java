/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.prompt.characterize;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Derives {@link InsightLevel#L3_FIELD} insights from one array's per-column accumulators.
 *
 * <p>Every rule here reads only the statistics already gathered during the single scan; no rule
 * re-inspects the raw payload. Rules that compare across rows (time range, numeric stats, enum
 * distribution, sparkline, unit hint, sample-null protection) require at least two rows, so a
 * single-row array emits nothing.
 *
 * @since 2026
 */
final class FieldInsightAnalyzer {
    /** Minimum share of non-null values that must parse as timestamps for a time column. */
    private static final double TIME_RATIO_THRESHOLD = 0.8;

    /** Minimum share of non-null values that must be numeric arrays for a sparkline column. */
    private static final double SPARKLINE_RATIO_THRESHOLD = 0.8;

    /** Milliseconds in one day, used to classify the recommended time format granularity. */
    private static final long MILLIS_PER_DAY = 86_400_000L;

    /** Span at or above which a date-only granularity is always recommended. */
    private static final long DATE_FORMAT_SPAN_MS = 3L * MILLIS_PER_DAY;

    /** Priority of the time-range rule. */
    private static final int PRIORITY_TIME_RANGE = 60;

    /** Priority of the numeric-stats rule. */
    private static final int PRIORITY_NUMERIC_STATS = 50;

    /** Priority of the enum-distribution rule. */
    private static final int PRIORITY_ENUM_DISTRIBUTION = 55;

    /** Priority of the sample-null-protection rule. */
    private static final int PRIORITY_SAMPLE_NULL = 70;

    /** Priority of the inline-sparkline rule. */
    private static final int PRIORITY_SPARKLINE = 65;

    /** Priority of the field-unit-hint rule. */
    private static final int PRIORITY_UNIT_HINT = 45;

    /** Minimum rows required before an enum-distribution insight can fire. */
    private static final long MIN_ENUM_DISTRIBUTION_ROWS = 3L;

    private FieldInsightAnalyzer() {}

    /**
     * Evaluates every L3 field rule against one array's columns and returns the fired insights.
     *
     * @param profile the per-array profile holding the column accumulators
     * @param cfg the characterization configuration
     * @return the fired L3 insights in rule-evaluation order
     */
    static List<DataInsight> analyze(ArrayProfile profile, CharacterizationConfig cfg) {
        List<DataInsight> insights = new ArrayList<>();
        if (profile.rowCount() < 2 || !profile.allObjectRows()) {
            return insights;
        }
        String labelField = findLabelField(profile);
        for (Map.Entry<String, ColumnAccumulator> entry : profile.columns().entrySet()) {
            String field = entry.getKey();
            ColumnAccumulator column = entry.getValue();
            long nonNull = column.occurrences() - column.nullCount();
            if (nonNull <= 0) {
                continue;
            }
            insights.addAll(evaluateTimeRange(profile, field, column, nonNull));
            insights.addAll(evaluateNumericStats(profile, field, column, labelField));
            insights.addAll(evaluateEnumDistribution(profile, field, column, nonNull, cfg));
            insights.addAll(evaluateSampleNull(profile, field, column, nonNull, cfg));
            insights.addAll(evaluateSparkline(profile, field, column, nonNull));
            insights.addAll(evaluateUnitHint(profile, field, column));
        }
        return insights;
    }

    private static List<DataInsight> evaluateTimeRange(
            ArrayProfile profile, String field, ColumnAccumulator column, long nonNull) {
        if (column.timeCount() < 2
                || column.timeCount() / (double) nonNull < TIME_RATIO_THRESHOLD) {
            return List.of();
        }
        long spanMs = column.maxEpochMs() - column.minEpochMs();
        String order =
                column.ascending() ? "ascending" : column.descending() ? "descending" : "unordered";
        String format = recommendTimeFormat(column, spanMs);
        Map<String, Object> slots = new LinkedHashMap<>();
        slots.put("field", field);
        slots.put("arrayPath", profile.arrayPath());
        slots.put("minTime", column.minTimeRaw());
        slots.put("maxTime", column.maxTimeRaw());
        slots.put("spanMs", spanMs);
        slots.put("recommendedFormat", format);
        slots.put("order", order);
        String prompt =
                "时间字段 "
                        + field
                        + " 覆盖 "
                        + column.minTimeRaw()
                        + " 至 "
                        + column.maxTimeRaw()
                        + "，跨度 "
                        + spanMs
                        + "ms，排序 "
                        + order
                        + "。推荐使用 @FormatDate(..., \""
                        + format
                        + "\") 按该粒度格式化。";
        return List.of(
                build(
                        "l3-time-range",
                        PRIORITY_TIME_RANGE,
                        profile.arrayPath() + "." + field,
                        Set.of("l3-time-range", "time-field", "date-format"),
                        slots,
                        prompt));
    }

    private static String recommendTimeFormat(ColumnAccumulator column, long spanMs) {
        if (column.allDateOnly() || spanMs >= DATE_FORMAT_SPAN_MS) {
            return "date";
        }
        if (spanMs >= MILLIS_PER_DAY) {
            return "dateTime";
        }
        return "time";
    }

    private static List<DataInsight> evaluateNumericStats(
            ArrayProfile profile, String field, ColumnAccumulator column, String labelField) {
        boolean idColumn = isIdColumn(field);
        boolean timeColumn =
                column.timeCount() >= 2
                        && TimestampParser.isTimeName(TimestampParser.normalizeName(field));
        if (column.numericCount() < 2 || idColumn || timeColumn) {
            return List.of();
        }
        if (column.maxNumeric() <= column.minNumeric()) {
            return List.of();
        }
        double avg = column.sumNumeric() / column.numericCount();
        Map<String, Object> slots = new LinkedHashMap<>();
        slots.put("field", field);
        slots.put("arrayPath", profile.arrayPath());
        slots.put("min", column.minNumeric());
        slots.put("max", column.maxNumeric());
        slots.put("avg", avg);
        slots.put("count", column.numericCount());
        StringBuilder prompt =
                new StringBuilder("数值字段 ")
                        .append(field)
                        .append("：min=")
                        .append(column.minNumeric())
                        .append("，max=")
                        .append(column.maxNumeric())
                        .append("，avg=")
                        .append(avg)
                        .append("。");
        if (labelField != null && column.minRowLabel() != null && column.maxRowLabel() != null) {
            slots.put("minEntity", column.minRowLabel());
            slots.put("maxEntity", column.maxRowLabel());
            prompt.append("极值实体：max 对应 ")
                    .append(column.maxRowLabel())
                    .append("，min 对应 ")
                    .append(column.minRowLabel())
                    .append("。");
        }
        return List.of(
                build(
                        "l3-numeric-stats",
                        PRIORITY_NUMERIC_STATS,
                        profile.arrayPath() + "." + field,
                        Set.of("l3-numeric-stats", "numeric-field", "stats"),
                        slots,
                        prompt.toString()));
    }

    private static List<DataInsight> evaluateEnumDistribution(
            ArrayProfile profile,
            String field,
            ColumnAccumulator column,
            long nonNull,
            CharacterizationConfig cfg) {
        if (profile.rowCount() < MIN_ENUM_DISTRIBUTION_ROWS || column.distinctOverflowed()) {
            return List.of();
        }
        long distinct = column.distinctStringCounts().size();
        if (distinct < 2 || distinct > cfg.enumMaxDistinct()) {
            return List.of();
        }
        if (distinct / (double) nonNull > cfg.enumMaxRatio()) {
            return List.of();
        }
        Map<String, Object> slots = new LinkedHashMap<>();
        slots.put("field", field);
        slots.put("arrayPath", profile.arrayPath());
        slots.put("enumCounts", new LinkedHashMap<>(column.distinctStringCounts()));
        slots.put("enumValues", new ArrayList<>(column.distinctStringCounts().keySet()));
        slots.put("distinctCount", distinct);
        StringBuilder prompt = new StringBuilder("枚举字段 ").append(field).append(" 取值分布：");
        for (Map.Entry<String, Long> count : column.distinctStringCounts().entrySet()) {
            prompt.append(count.getKey()).append("=").append(count.getValue()).append("，");
        }
        prompt.append("按完整枚举域渲染筛选/分组。");
        return List.of(
                build(
                        "l3-enum-distribution",
                        PRIORITY_ENUM_DISTRIBUTION,
                        profile.arrayPath() + "." + field,
                        Set.of("l3-enum-distribution", "enum-field", "categorical"),
                        slots,
                        prompt.toString()));
    }

    private static List<DataInsight> evaluateSampleNull(
            ArrayProfile profile,
            String field,
            ColumnAccumulator column,
            long nonNull,
            CharacterizationConfig cfg) {
        boolean hiddenInSample =
                profile.rowCount() > cfg.sampleRows()
                        && column.sampleNonNullCount() == 0
                        && nonNull > 0;
        if (!hiddenInSample) {
            return List.of();
        }
        Map<String, Object> slots = new LinkedHashMap<>();
        slots.put("field", field);
        slots.put("arrayPath", profile.arrayPath());
        slots.put("sampleRows", cfg.sampleRows());
        slots.put("nonNullCount", nonNull);
        slots.put("totalRows", profile.rowCount());
        String prompt =
                "字段 "
                        + field
                        + " 在前 "
                        + cfg.sampleRows()
                        + " 行样本中全部为 null/缺失，但在完整数据集中有 "
                        + nonNull
                        + " 个非空值。不要因样本为空而丢弃该列。";
        return List.of(
                build(
                        "l3-sample-null-non-empty",
                        PRIORITY_SAMPLE_NULL,
                        profile.arrayPath() + "." + field,
                        Set.of("l3-sample-null-non-empty", "null-protection", "sparse-sample"),
                        slots,
                        prompt));
    }

    private static List<DataInsight> evaluateSparkline(
            ArrayProfile profile, String field, ColumnAccumulator column, long nonNull) {
        if (column.numericArrayCount() < 1
                || column.numericArrayCount() / (double) nonNull < SPARKLINE_RATIO_THRESHOLD) {
            return List.of();
        }
        Map<String, Object> slots = new LinkedHashMap<>();
        slots.put("field", field);
        slots.put("arrayPath", profile.arrayPath());
        slots.put("maxPoints", column.maxNumericArrayLen());
        String prompt =
                "字段 "
                        + field
                        + " 是内联数字数组（最多 "
                        + column.maxNumericArrayLen()
                        + " 个点），推荐用 MiniChart 渲染行内趋势。";
        return List.of(
                build(
                        "l3-inline-sparkline",
                        PRIORITY_SPARKLINE,
                        profile.arrayPath() + "." + field,
                        Set.of("l3-inline-sparkline", "number-array", "mini-chart"),
                        slots,
                        prompt));
    }

    private static List<DataInsight> evaluateUnitHint(
            ArrayProfile profile, String field, ColumnAccumulator column) {
        if (column.numericCount() < 2) {
            return List.of();
        }
        String normalized = TimestampParser.normalizeName(field);
        String unitKind = unitKindOf(normalized);
        if (unitKind == null) {
            return List.of();
        }
        String builtin = builtinFor(unitKind);
        Map<String, Object> slots = new LinkedHashMap<>();
        slots.put("field", field);
        slots.put("arrayPath", profile.arrayPath());
        slots.put("unitKind", unitKind);
        slots.put("recommendedBuiltin", builtin);
        Set<String> tags = new LinkedHashSet<>();
        tags.add("l3-field-unit-hint");
        tags.add("unit-format");
        tags.add(unitKind);
        String prompt = "数值字段 " + field + " 疑似 " + unitKind + " 单位，推荐使用 " + builtin + " 格式化。";
        return List.of(
                build(
                        "l3-field-unit-hint",
                        PRIORITY_UNIT_HINT,
                        profile.arrayPath() + "." + field,
                        tags,
                        slots,
                        prompt));
    }

    private static String unitKindOf(String normalized) {
        // Duration and bytes are checked before percent/throughput because their keywords can
        // appear as substrings of one another ("duration" contains "ratio", "durationms" ends
        // with "ms"). More specific kinds win first.
        if (matchesAny(
                normalized,
                "duration",
                "durationms",
                "latency",
                "latencyms",
                "elapsedms",
                "uptime")) {
            return "duration";
        }
        if (matchesAny(
                normalized, "bytes", "byte", "memorybytes", "diskbytes", "filesize", "memsize")) {
            return "bytes";
        }
        if (matchesAny(
                normalized,
                "bps",
                "kbps",
                "mbps",
                "gbps",
                "bandwidth",
                "throughput",
                "qps",
                "tps")) {
            return "throughput";
        }
        if (matchesAny(normalized, "percent", "pct", "usage", "utilization")
                || normalized.endsWith("ratio")
                || normalized.endsWith("rate")) {
            return "percent";
        }
        return null;
    }

    private static boolean matchesAny(String normalized, String... keywords) {
        for (String keyword : keywords) {
            if (normalized.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static String builtinFor(String unitKind) {
        switch (unitKind) {
            case "bytes":
                return "@FormatBytes";
            case "percent":
                return "@FormatPercent";
            case "duration":
                return "@FormatDuration";
            default:
                return "unit-label";
        }
    }

    private static boolean isIdColumn(String field) {
        String normalized = TimestampParser.normalizeName(field);
        return "id".equals(normalized) || normalized.endsWith("id");
    }

    private static String findLabelField(ArrayProfile profile) {
        for (Map.Entry<String, ColumnAccumulator> entry : profile.columns().entrySet()) {
            String field = entry.getKey();
            ColumnAccumulator column = entry.getValue();
            if (isIdColumn(field)) {
                continue;
            }
            if (!column.distinctStringCounts().isEmpty()
                    && column.scalarTypesSeen().contains(ScalarType.STRING)) {
                return field;
            }
        }
        return null;
    }

    private static DataInsight build(
            String id,
            int priority,
            String targetPath,
            Set<String> tags,
            Map<String, Object> slots,
            String prompt) {
        return new DataInsight(
                id, InsightLevel.L3_FIELD, targetPath, priority, tags, slots, prompt);
    }
}
