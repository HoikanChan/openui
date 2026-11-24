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
 * Derives {@link InsightLevel#L2_DATASET} insights from one array's per-column accumulators.
 *
 * <p>Rules read only statistics gathered during the single scan (plus the captured group-timestamp
 * series for the alignment check). Each rule targets the array's own path; per-path competition in
 * {@link DataProfiler} keeps only the highest-priority survivor. A single-row array emits nothing,
 * preserving the invariant that {@code {orders: [{id: 1}]}} yields no insights.
 *
 * @since 2026
 */
final class DatasetInsightAnalyzer {
    /** Minimum rows for a partitioned / unaligned time-series rule. */
    private static final long MIN_TIME_SERIES_ROWS = 4L;

    /** Minimum rows for a grouped-aggregation rule. */
    private static final long MIN_GROUP_ROWS = 6L;

    /** Minimum rows for a top-N ranking rule. */
    private static final long MIN_RANKING_ROWS = 3L;

    /** Minimum distinct values for a categorical group column. */
    private static final long MIN_GROUP_DISTINCT = 2L;

    /** Maximum distinct values for a categorical group column. */
    private static final long MAX_GROUP_DISTINCT = 12L;

    /** Alignment ratio at or above which group timestamps count as aligned. */
    private static final double ALIGNMENT_THRESHOLD = 0.7;

    /** Minimum average rows per group for a grouped-aggregation rule. */
    private static final double MIN_ROWS_PER_GROUP = 2.0;

    /** Distinct/non-null ratio at or above which a string column counts as an entity label. */
    private static final double LABEL_CARDINALITY_RATIO = 0.7;

    /** Minimum share of non-null values that must parse as timestamps for a time column. */
    private static final double TIME_RATIO_THRESHOLD = 0.8;

    /** Priority of the partitioned-time-series rule. */
    private static final int PRIORITY_PARTITIONED_TIME_SERIES = 90;

    /** Priority of the unaligned-time-scatter rule. */
    private static final int PRIORITY_UNALIGNED_TIME_SCATTER = 88;

    /** Priority of the grouped-aggregation rule. */
    private static final int PRIORITY_GROUPED_AGGREGATION = 80;

    /** Priority of the tuple-series rule. */
    private static final int PRIORITY_TUPLE_SERIES = 75;

    /** Priority of the timeline-items rule. */
    private static final int PRIORITY_TIMELINE_ITEMS = 70;

    /** Priority of the top-N-ranking rule. */
    private static final int PRIORITY_TOP_N_RANKING = 65;

    /** Priority of the homogeneous-table fallback rule. */
    private static final int PRIORITY_HOMOGENEOUS_TABLE = 20;

    private DatasetInsightAnalyzer() {}

    /**
     * Evaluates every L2 dataset rule against one array and returns the fired insights.
     *
     * @param profile the per-array profile holding the column accumulators
     * @param cfg the characterization configuration
     * @return the fired L2 insights in rule-evaluation order
     */
    static List<DataInsight> analyze(ArrayProfile profile, CharacterizationConfig cfg) {
        List<DataInsight> insights = new ArrayList<>();
        if (profile.rowCount() < 2) {
            return insights;
        }
        if (!profile.allObjectRows()) {
            evaluateTupleSeries(profile, insights);
            return insights;
        }
        evaluateTimeSeries(profile, cfg, insights);
        evaluateGroupedAggregation(profile, cfg, insights);
        evaluateTimelineItems(profile, insights);
        evaluateTopNRanking(profile, insights);
        evaluateHomogeneousTable(profile, insights);
        return insights;
    }

    private static void evaluateTupleSeries(ArrayProfile profile, List<DataInsight> out) {
        if (!profile.allTupleRows() || profile.tupleXValues().isEmpty()) {
            return;
        }
        Map<String, Object> slots = new LinkedHashMap<>();
        slots.put("arrayPath", profile.arrayPath());
        slots.put("rowCount", profile.rowCount());
        slots.put("xType", profile.tupleXNumeric() ? "number" : "string");
        slots.put("yType", "number");
        String prompt =
                "数组 "
                        + profile.arrayPath()
                        + " 是 "
                        + profile.rowCount()
                        + " 个二元组序列，x 类型 "
                        + slots.get("xType")
                        + "，y 类型 number。按序列渲染为趋势图。";
        out.add(
                build(
                        "l2-tuple-series",
                        PRIORITY_TUPLE_SERIES,
                        profile.arrayPath(),
                        Set.of("l2-tuple-series", "tuple-series", "series"),
                        slots,
                        prompt));
    }

    private static void evaluateTimeSeries(
            ArrayProfile profile, CharacterizationConfig cfg, List<DataInsight> out) {
        if (profile.rowCount() < MIN_TIME_SERIES_ROWS) {
            return;
        }
        String timeField = findTimeField(profile);
        String groupField = findGroupField(profile, cfg, timeField);
        String metricField = findMetricField(profile, timeField);
        if (timeField == null || groupField == null || metricField == null) {
            return;
        }
        AlignmentResult alignment = checkAlignment(profile, timeField, groupField);
        ColumnAccumulator timeColumn = profile.columns().get(timeField);
        String order =
                timeColumn.ascending()
                        ? "ascending"
                        : timeColumn.descending() ? "descending" : "unordered";
        ColumnAccumulator groupColumn = profile.columns().get(groupField);
        Map<String, Object> slots = new LinkedHashMap<>();
        slots.put("arrayPath", profile.arrayPath());
        slots.put("timeField", timeField);
        slots.put("groupField", groupField);
        slots.put("metricField", metricField);
        slots.put("enumValues", new ArrayList<>(groupColumn.distinctStringCounts().keySet()));
        slots.put("enumCounts", new LinkedHashMap<>(groupColumn.distinctStringCounts()));
        slots.put("aligned", alignment.aligned);
        slots.put("order", order);
        slots.put("sampleColumns", sampleColumns(profile, cfg));
        if (alignment.aligned && alignment.monotonic) {
            addPartitionedTimeSeries(profile, timeField, groupField, metricField, slots, out);
        } else {
            addUnalignedTimeScatter(profile, timeField, groupField, slots, out);
        }
    }

    private static void addPartitionedTimeSeries(
            ArrayProfile profile,
            String timeField,
            String groupField,
            String metricField,
            Map<String, Object> slots,
            List<DataInsight> out) {
        out.add(
                build(
                        "l2-partitioned-time-series",
                        PRIORITY_PARTITIONED_TIME_SERIES,
                        profile.arrayPath(),
                        Set.of("l2-partitioned-time-series", "time-series", "partitioned-chart"),
                        slots,
                        "数组 "
                                + profile.arrayPath()
                                + " 是按 "
                                + groupField
                                + " 分组、按 "
                                + timeField
                                + " 对齐的时间序列，指标 "
                                + metricField
                                + "。各组时间戳对齐且单调，可共享 X 轴渲染多序列折线图。"));
    }

    private static void addUnalignedTimeScatter(
            ArrayProfile profile,
            String timeField,
            String groupField,
            Map<String, Object> slots,
            List<DataInsight> out) {
        slots.put("aligned", false);
        out.add(
                build(
                        "l2-unaligned-time-scatter",
                        PRIORITY_UNALIGNED_TIME_SCATTER,
                        profile.arrayPath(),
                        Set.of("l2-unaligned-time-scatter", "time-series", "unaligned-time"),
                        slots,
                        "数组 "
                                + profile.arrayPath()
                                + " 按 "
                                + groupField
                                + " 分组、按 "
                                + timeField
                                + " 的时间戳未对齐或乱序。不要把所有组强行画到同一共享 X 轴折线图上；先排序或分组后再渲染。"));
    }

    private static void evaluateGroupedAggregation(
            ArrayProfile profile, CharacterizationConfig cfg, List<DataInsight> out) {
        if (profile.rowCount() < MIN_GROUP_ROWS) {
            return;
        }
        String groupField = findGroupField(profile, cfg, null);
        String metricField = findMetricField(profile, null);
        if (groupField == null || metricField == null) {
            return;
        }
        ColumnAccumulator groupColumn = profile.columns().get(groupField);
        long distinct = groupColumn.distinctStringCounts().size();
        double rowsPerGroup = profile.rowCount() / (double) distinct;
        if (rowsPerGroup < MIN_ROWS_PER_GROUP) {
            return;
        }
        Map<String, Object> slots = new LinkedHashMap<>();
        slots.put("arrayPath", profile.arrayPath());
        slots.put("groupField", groupField);
        slots.put("enumCounts", new LinkedHashMap<>(groupColumn.distinctStringCounts()));
        slots.put("enumValues", new ArrayList<>(groupColumn.distinctStringCounts().keySet()));
        slots.put("metricField", metricField);
        slots.put("rowCount", profile.rowCount());
        slots.put("sampleColumns", sampleColumns(profile, cfg));
        StringBuilder prompt =
                new StringBuilder("数组 ")
                        .append(profile.arrayPath())
                        .append(" 可按 ")
                        .append(groupField)
                        .append(" 分组聚合 ")
                        .append(metricField)
                        .append("。各组精确行数：");
        for (Map.Entry<String, Long> count : groupColumn.distinctStringCounts().entrySet()) {
            prompt.append(count.getKey()).append("=").append(count.getValue()).append("，");
        }
        prompt.append("使用 @Filter 在完整枚举域上分组汇总。");
        out.add(
                build(
                        "l2-grouped-aggregation",
                        PRIORITY_GROUPED_AGGREGATION,
                        profile.arrayPath(),
                        Set.of("l2-grouped-aggregation", "grouped-aggregation", "group-by"),
                        slots,
                        prompt.toString()));
    }

    private static void evaluateTimelineItems(ArrayProfile profile, List<DataInsight> out) {
        String timeField = findTimeField(profile);
        String textField = findNarrativeField(profile);
        if (timeField == null || textField == null) {
            return;
        }
        ColumnAccumulator timeColumn = profile.columns().get(timeField);
        String order =
                timeColumn.ascending()
                        ? "ascending"
                        : timeColumn.descending() ? "descending" : "unordered";
        Map<String, Object> slots = new LinkedHashMap<>();
        slots.put("arrayPath", profile.arrayPath());
        slots.put("timeField", timeField);
        slots.put("textField", textField);
        slots.put("order", order);
        slots.put("rowCount", profile.rowCount());
        String prompt =
                "数组 "
                        + profile.arrayPath()
                        + " 是时间线条目，时间字段 "
                        + timeField
                        + "，事件文本字段 "
                        + textField
                        + "。按时间线渲染事件列表。";
        out.add(
                build(
                        "l2-timeline-items",
                        PRIORITY_TIMELINE_ITEMS,
                        profile.arrayPath(),
                        Set.of("l2-timeline-items", "timeline", "events"),
                        slots,
                        prompt));
    }

    private static void evaluateTopNRanking(ArrayProfile profile, List<DataInsight> out) {
        if (profile.rowCount() < MIN_RANKING_ROWS) {
            return;
        }
        String labelField = findEntityLabelField(profile);
        String metricField = findMetricField(profile, null);
        if (labelField == null || metricField == null) {
            return;
        }
        ColumnAccumulator metricColumn = profile.columns().get(metricField);
        if (metricColumn.maxNumeric() <= metricColumn.minNumeric()) {
            return;
        }
        Map<String, Object> slots = new LinkedHashMap<>();
        slots.put("arrayPath", profile.arrayPath());
        slots.put("labelField", labelField);
        slots.put("metricField", metricField);
        slots.put("maxEntity", metricColumn.maxRowLabel());
        slots.put("maxValue", metricColumn.maxNumeric());
        slots.put("minEntity", metricColumn.minRowLabel());
        slots.put("minValue", metricColumn.minNumeric());
        slots.put("rowCount", profile.rowCount());
        String prompt =
                "数组 "
                        + profile.arrayPath()
                        + " 是 "
                        + labelField
                        + " 对 "
                        + metricField
                        + " 的排名。最大值实体 "
                        + metricColumn.maxRowLabel()
                        + "="
                        + metricColumn.maxNumeric()
                        + "，最小值实体 "
                        + metricColumn.minRowLabel()
                        + "="
                        + metricColumn.minNumeric()
                        + "。建议渲染排序对比视图。";
        out.add(
                build(
                        "l2-top-n-ranking",
                        PRIORITY_TOP_N_RANKING,
                        profile.arrayPath(),
                        Set.of("l2-top-n-ranking", "top-n", "ranking"),
                        slots,
                        prompt));
    }

    private static void evaluateHomogeneousTable(ArrayProfile profile, List<DataInsight> out) {
        if (profile.columns().size() < 2) {
            return;
        }
        Map<String, Object> slots = new LinkedHashMap<>();
        slots.put("arrayPath", profile.arrayPath());
        slots.put("rowCount", profile.rowCount());
        slots.put("columns", new ArrayList<>(profile.columns().keySet()));
        String prompt =
                "数组 "
                        + profile.arrayPath()
                        + " 是 "
                        + profile.rowCount()
                        + " 行、"
                        + profile.columns().size()
                        + " 列的同质表格。按表格渲染。";
        out.add(
                build(
                        "l2-homogeneous-table",
                        PRIORITY_HOMOGENEOUS_TABLE,
                        profile.arrayPath(),
                        Set.of("l2-homogeneous-table", "table", "homogeneous-table"),
                        slots,
                        prompt));
    }

    private static String findTimeField(ArrayProfile profile) {
        String best = null;
        long bestCount = 0L;
        for (Map.Entry<String, ColumnAccumulator> entry : profile.columns().entrySet()) {
            ColumnAccumulator column = entry.getValue();
            long nonNull = column.occurrences() - column.nullCount();
            if (column.timeCount() >= 2
                    && nonNull > 0
                    && column.timeCount() / (double) nonNull >= TIME_RATIO_THRESHOLD
                    && column.timeCount() > bestCount) {
                best = entry.getKey();
                bestCount = column.timeCount();
            }
        }
        return best;
    }

    private static String findGroupField(
            ArrayProfile profile, CharacterizationConfig cfg, String excludeField) {
        for (Map.Entry<String, ColumnAccumulator> entry : profile.columns().entrySet()) {
            String field = entry.getKey();
            if (field.equals(excludeField)) {
                continue;
            }
            ColumnAccumulator column = entry.getValue();
            if (column.distinctOverflowed()) {
                continue;
            }
            long distinct = column.distinctStringCounts().size();
            if (distinct < MIN_GROUP_DISTINCT || distinct > MAX_GROUP_DISTINCT) {
                continue;
            }
            if (!column.scalarTypesSeen().contains(ScalarType.STRING)) {
                continue;
            }
            return field;
        }
        return null;
    }

    private static String findMetricField(ArrayProfile profile, String excludeField) {
        for (Map.Entry<String, ColumnAccumulator> entry : profile.columns().entrySet()) {
            String field = entry.getKey();
            if (field.equals(excludeField)) {
                continue;
            }
            ColumnAccumulator column = entry.getValue();
            if (column.numericCount() < 2 || isIdColumn(field)) {
                continue;
            }
            if (TimestampParser.isTimeName(TimestampParser.normalizeName(field))
                    && column.timeCount() >= 2) {
                continue;
            }
            return field;
        }
        return null;
    }

    private static String findEntityLabelField(ArrayProfile profile) {
        for (Map.Entry<String, ColumnAccumulator> entry : profile.columns().entrySet()) {
            String field = entry.getKey();
            ColumnAccumulator column = entry.getValue();
            if (isIdColumn(field) || !column.scalarTypesSeen().contains(ScalarType.STRING)) {
                continue;
            }
            if (TimestampParser.isTimeName(TimestampParser.normalizeName(field))) {
                continue;
            }
            long nonNull = column.occurrences() - column.nullCount();
            if (nonNull <= 0) {
                continue;
            }
            long distinct = column.distinctStringCounts().size();
            if (distinct / (double) nonNull >= LABEL_CARDINALITY_RATIO) {
                return field;
            }
        }
        return null;
    }

    private static String findNarrativeField(ArrayProfile profile) {
        Set<String> narrativeNames =
                Set.of(
                        "event",
                        "title",
                        "message",
                        "msg",
                        "description",
                        "desc",
                        "content",
                        "action",
                        "summary",
                        "log",
                        "detail");
        for (Map.Entry<String, ColumnAccumulator> entry : profile.columns().entrySet()) {
            String normalized = TimestampParser.normalizeName(entry.getKey());
            if (narrativeNames.contains(normalized)
                    && entry.getValue().scalarTypesSeen().contains(ScalarType.STRING)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static AlignmentResult checkAlignment(
            ArrayProfile profile, String timeField, String groupField) {
        Map<String, List<long[]>> groups =
                profile.pairTimestamps().get(ArrayProfile.pairKey(timeField, groupField));
        if (groups == null || groups.size() < 2) {
            return new AlignmentResult(false, false);
        }
        Set<Long> union = new LinkedHashSet<>();
        boolean monotonic = true;
        for (List<long[]> series : groups.values()) {
            long previous = Long.MIN_VALUE;
            boolean ascending = true;
            boolean descending = true;
            for (long[] point : series) {
                long epochMs = point[0];
                union.add(epochMs);
                if (previous != Long.MIN_VALUE) {
                    if (epochMs < previous) {
                        ascending = false;
                    }
                    if (epochMs > previous) {
                        descending = false;
                    }
                }
                previous = epochMs;
            }
            if (!ascending && !descending) {
                monotonic = false;
            }
        }
        if (union.isEmpty()) {
            return new AlignmentResult(false, monotonic);
        }
        double totalOverlap = 0.0;
        for (List<long[]> series : groups.values()) {
            Set<Long> groupSet = new LinkedHashSet<>();
            for (long[] point : series) {
                groupSet.add(point[0]);
            }
            totalOverlap += groupSet.size() / (double) union.size();
        }
        double alignmentRatio = totalOverlap / groups.size();
        return new AlignmentResult(alignmentRatio >= ALIGNMENT_THRESHOLD, monotonic);
    }

    private static List<String> sampleColumns(ArrayProfile profile, CharacterizationConfig cfg) {
        List<String> sample = new ArrayList<>();
        int limit = Math.min(cfg.sampleRows(), profile.columns().size());
        int index = 0;
        for (String column : profile.columns().keySet()) {
            if (index >= limit) {
                break;
            }
            sample.add(column);
            index++;
        }
        return sample;
    }

    private static boolean isIdColumn(String field) {
        String normalized = TimestampParser.normalizeName(field);
        return "id".equals(normalized) || normalized.endsWith("id");
    }

    private static DataInsight build(
            String id,
            int priority,
            String targetPath,
            Set<String> tags,
            Map<String, Object> slots,
            String prompt) {
        return new DataInsight(
                id, InsightLevel.L2_DATASET, targetPath, priority, tags, slots, prompt);
    }

    /**
     * Result of the group-timestamp alignment check.
     *
     * @since 2026
     */
    private static final class AlignmentResult {
        private final boolean aligned;
        private final boolean monotonic;

        AlignmentResult(boolean aligned, boolean monotonic) {
            this.aligned = aligned;
            this.monotonic = monotonic;
        }
    }
}
