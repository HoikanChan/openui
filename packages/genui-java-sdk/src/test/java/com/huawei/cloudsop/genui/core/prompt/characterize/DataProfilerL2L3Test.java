/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.prompt.characterize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Behavior tests for the deeper-layer (L2 dataset, L3 field) profiling rules.
 *
 * <p>Each test builds a realistic payload, runs {@link DataProfiler#profile(Map)}, and asserts on
 * the observable insight list: which rule won per target path, and what the winning rule recorded
 * in its slots.
 */
class DataProfilerL2L3Test {

    // ---- helpers ----

    private static Map<String, Object> wrap(List<Object> rows) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("rows", rows);
        return raw;
    }

    private static Map<String, Object> row(Object... keyValues) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            row.put((String) keyValues[i], keyValues[i + 1]);
        }
        return row;
    }

    private static DataInsight onlyL2(List<DataInsight> insights) {
        List<DataInsight> l2 =
                insights.stream().filter(i -> i.level() == InsightLevel.L2_DATASET).toList();
        assertEquals(1, l2.size(), "expected exactly one L2 insight");
        return l2.get(0);
    }

    private static List<DataInsight> l3Of(List<DataInsight> insights, String id) {
        return insights.stream()
                .filter(i -> i.level() == InsightLevel.L3_FIELD && i.id().equals(id))
                .toList();
    }

    // ---- L2 competition invariant ----

    @Test
    void atMostOneL2InsightPerTargetPath() {
        List<Object> rows = new ArrayList<>();
        String[] groups = {"小区A", "小区B", "小区C"};
        int[] counts = {35, 33, 32};
        for (int g = 0; g < groups.length; g++) {
            for (int i = 0; i < counts[g]; i++) {
                rows.add(row("community", groups[g], "value", (long) (i + g)));
            }
        }
        List<DataInsight> insights = DataProfiler.profile(wrap(rows));
        Map<String, Long> l2PerPath = new LinkedHashMap<>();
        for (DataInsight insight : insights) {
            if (insight.level() == InsightLevel.L2_DATASET) {
                l2PerPath.merge(insight.targetPath(), 1L, Long::sum);
            }
        }
        for (long count : l2PerPath.values()) {
            assertEquals(1L, count, "more than one L2 insight for a target path");
        }
    }

    // ---- l2-grouped-aggregation ----

    @Test
    void groupedAggregationWinsWithExactEnumCounts() {
        List<Object> rows = new ArrayList<>();
        String[] groups = {"小区A", "小区B", "小区C"};
        int[] counts = {35, 33, 32};
        for (int g = 0; g < groups.length; g++) {
            for (int i = 0; i < counts[g]; i++) {
                rows.add(row("community", groups[g], "value", (long) (i + g)));
            }
        }
        List<DataInsight> insights = DataProfiler.profile(wrap(rows));

        DataInsight l2 = onlyL2(insights);
        assertEquals("l2-grouped-aggregation", l2.id());
        assertEquals("rows", l2.targetPath());
        assertEquals("community", l2.slots().get("groupField"));
        assertEquals("value", l2.slots().get("metricField"));
        @SuppressWarnings("unchecked")
        Map<String, Long> enumCounts = (Map<String, Long>) l2.slots().get("enumCounts");
        assertEquals(35L, enumCounts.get("小区A"));
        assertEquals(33L, enumCounts.get("小区B"));
        assertEquals(32L, enumCounts.get("小区C"));

        // L3 insights coexist: numeric stats on the metric, enum distribution on the group.
        assertFalse(l3Of(insights, "l3-numeric-stats").isEmpty());
        assertFalse(l3Of(insights, "l3-enum-distribution").isEmpty());
    }

    // ---- l2-partitioned-time-series ----

    @Test
    void partitionedTimeSeriesWinsWhenAlignedAscending() {
        List<Object> rows = new ArrayList<>();
        String[] groups = {"A", "B"};
        long base = 1_700_000_000_000L;
        for (int day = 0; day < 5; day++) {
            for (String g : groups) {
                rows.add(
                        row(
                                "timestamp",
                                base + day * 86_400_000L,
                                "group",
                                g,
                                "metric",
                                (long) (day * 10 + g.charAt(0))));
            }
        }
        List<DataInsight> insights = DataProfiler.profile(wrap(rows));
        DataInsight l2 = onlyL2(insights);
        assertEquals("l2-partitioned-time-series", l2.id());
        assertEquals(true, l2.slots().get("aligned"));
        assertEquals("ascending", l2.slots().get("order"));
        assertEquals("timestamp", l2.slots().get("timeField"));
        assertEquals("group", l2.slots().get("groupField"));
    }

    @Test
    void partitionedTimeSeriesWinsWhenAlignedDescending() {
        List<Object> rows = new ArrayList<>();
        long base = 1_700_000_000_000L;
        for (int day = 4; day >= 0; day--) {
            for (String g : new String[] {"A", "B"}) {
                rows.add(
                        row(
                                "timestamp",
                                base + day * 86_400_000L,
                                "group",
                                g,
                                "metric",
                                (long) day));
            }
        }
        List<DataInsight> insights = DataProfiler.profile(wrap(rows));
        DataInsight l2 = onlyL2(insights);
        assertEquals("l2-partitioned-time-series", l2.id());
        assertEquals("descending", l2.slots().get("order"));
    }

    // ---- l2-unaligned-time-scatter ----

    @Test
    void unalignedTimeScatterWinsWhenGroupsNotAligned() {
        List<Object> rows = new ArrayList<>();
        long base = 1_700_000_000_000L;
        // Group A uses days 0..4, group B uses days 100..104: no shared timestamps.
        for (int i = 0; i < 5; i++) {
            rows.add(row("timestamp", base + i * 86_400_000L, "group", "A", "metric", (long) i));
        }
        for (int i = 100; i < 105; i++) {
            rows.add(row("timestamp", base + i * 86_400_000L, "group", "B", "metric", (long) i));
        }
        List<DataInsight> insights = DataProfiler.profile(wrap(rows));
        DataInsight l2 = onlyL2(insights);
        assertEquals("l2-unaligned-time-scatter", l2.id());
        assertEquals(false, l2.slots().get("aligned"));
    }

    @Test
    void unalignedTimeScatterWinsWhenGroupTimestampsOutOfOrder() {
        List<Object> rows = new ArrayList<>();
        long base = 1_700_000_000_000L;
        // Interleave so each group's own timestamps are non-monotonic.
        long[] shuffled = {0L, 200L, 100L, 300L, 50L};
        for (long day : shuffled) {
            for (String g : new String[] {"A", "B"}) {
                rows.add(row("timestamp", base + day * 86_400_000L, "group", g, "metric", day));
            }
        }
        List<DataInsight> insights = DataProfiler.profile(wrap(rows));
        DataInsight l2 = onlyL2(insights);
        assertEquals("l2-unaligned-time-scatter", l2.id());
    }

    // ---- l2-top-n-ranking ----

    @Test
    void topNRankingWinsWithExtremeEntities() {
        List<Object> rows = new ArrayList<>();
        String[] names = {"alpha", "bravo", "charlie", "delta", "echo", "foxtrot"};
        long[] values = {10L, 99L, 55L, 1L, 73L, 40L};
        for (int i = 0; i < names.length; i++) {
            rows.add(row("name", names[i], "score", values[i]));
        }
        List<DataInsight> insights = DataProfiler.profile(wrap(rows));
        DataInsight l2 = onlyL2(insights);
        assertEquals("l2-top-n-ranking", l2.id());
        assertEquals("name", l2.slots().get("labelField"));
        assertEquals("score", l2.slots().get("metricField"));
        assertEquals("bravo", l2.slots().get("maxEntity"));
        assertEquals(99.0, (Double) l2.slots().get("maxValue"), 0.001);
        assertEquals("delta", l2.slots().get("minEntity"));
        assertEquals(1.0, (Double) l2.slots().get("minValue"), 0.001);
    }

    // ---- l2-tuple-series ----

    @Test
    void tupleSeriesWinsForTwoElementNumericPairs() {
        List<Object> rows = new ArrayList<>();
        long base = 1_700_000_000_000L;
        for (int i = 0; i < 6; i++) {
            rows.add(List.of(base + i * 1000L, (long) (i * 5)));
        }
        List<DataInsight> insights = DataProfiler.profile(wrap(rows));
        DataInsight l2 = onlyL2(insights);
        assertEquals("l2-tuple-series", l2.id());
        assertEquals("number", l2.slots().get("xType"));
        assertEquals("number", l2.slots().get("yType"));
        assertEquals(6L, l2.slots().get("rowCount"));
    }

    // ---- l2-timeline-items ----

    @Test
    void timelineItemsWinsForTimestampedEvents() {
        List<Object> rows = new ArrayList<>();
        long base = 1_700_000_000_000L;
        String[] events = {"started", "processed", "completed", "archived"};
        for (int i = 0; i < events.length; i++) {
            rows.add(row("timestamp", base + i * 1000L, "event", events[i], "detail", "step " + i));
        }
        List<DataInsight> insights = DataProfiler.profile(wrap(rows));
        DataInsight l2 = onlyL2(insights);
        assertEquals("l2-timeline-items", l2.id());
        assertEquals("timestamp", l2.slots().get("timeField"));
        assertEquals("event", l2.slots().get("textField"));
    }

    // ---- l2-homogeneous-table ----

    @Test
    void homogeneousTableIsFallbackForPlainRows() {
        List<Object> rows = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            // Two id-like columns and no time/group/metric/label shape: only the fallback fires.
            rows.add(row("id", (long) i, "noteId", (long) (i + 100)));
        }
        List<DataInsight> insights = DataProfiler.profile(wrap(rows));
        DataInsight l2 = onlyL2(insights);
        assertEquals("l2-homogeneous-table", l2.id());
        assertEquals(4L, l2.slots().get("rowCount"));
    }

    @Test
    void singleRowSingleColumnEmitsNoInsights() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("orders", List.of(row("id", 1L)));
        List<DataInsight> insights = DataProfiler.profile(raw);
        assertTrue(insights.isEmpty());
    }

    // ---- L3 time-range ----

    @Test
    void timeRangeRecommendsDateForCrossDaySpan() {
        List<Object> rows = new ArrayList<>();
        long base = 1_700_000_000_000L;
        for (int i = 0; i < 5; i++) {
            rows.add(row("date", base + i * 86_400_000L, "v", (long) i));
        }
        List<DataInsight> insights = DataProfiler.profile(wrap(rows));
        List<DataInsight> time = l3Of(insights, "l3-time-range");
        assertEquals(1, time.size());
        assertEquals("date", time.get(0).slots().get("recommendedFormat"));
        assertEquals("ascending", time.get(0).slots().get("order"));
    }

    @Test
    void timeRangeRecommendsTimeForSameDaySpan() {
        List<Object> rows = new ArrayList<>();
        long base = 1_700_000_000_000L;
        for (int i = 0; i < 5; i++) {
            rows.add(row("time", base + i * 1000L, "v", (long) i));
        }
        List<DataInsight> insights = DataProfiler.profile(wrap(rows));
        List<DataInsight> time = l3Of(insights, "l3-time-range");
        assertEquals(1, time.size());
        assertEquals("time", time.get(0).slots().get("recommendedFormat"));
    }

    @Test
    void timeRangeDetectsDescendingOrder() {
        List<Object> rows = new ArrayList<>();
        long base = 1_700_000_000_000L;
        for (int i = 4; i >= 0; i--) {
            rows.add(row("date", base + i * 86_400_000L, "v", (long) i));
        }
        List<DataInsight> insights = DataProfiler.profile(wrap(rows));
        List<DataInsight> time = l3Of(insights, "l3-time-range");
        assertEquals(1, time.size());
        assertEquals("descending", time.get(0).slots().get("order"));
    }

    // ---- L3 numeric stats with extreme entities ----

    @Test
    void numericStatsReportsMinMaxAvgAndEntities() {
        List<Object> rows = new ArrayList<>();
        String[] names = {"a", "b", "c"};
        long[] values = {5L, 50L, 20L};
        for (int i = 0; i < names.length; i++) {
            rows.add(row("label", names[i], "amount", values[i]));
        }
        List<DataInsight> insights = DataProfiler.profile(wrap(rows));
        List<DataInsight> stats = l3Of(insights, "l3-numeric-stats");
        assertEquals(1, stats.size());
        Map<String, Object> slots = stats.get(0).slots();
        assertEquals(5.0, (Double) slots.get("min"), 0.001);
        assertEquals(50.0, (Double) slots.get("max"), 0.001);
        assertEquals(25.0, (Double) slots.get("avg"), 0.001);
        assertEquals(3L, slots.get("count"));
        assertEquals("a", slots.get("minEntity"));
        assertEquals("b", slots.get("maxEntity"));
    }

    // ---- L3 sample-null protection ----

    @Test
    void sampleNullProtectionFiresWhenPrefixAllNullButRestPopulated() {
        List<Object> rows = new ArrayList<>();
        // First sampleRows (default 3) rows have null extra; later rows populate it.
        for (int i = 0; i < 3; i++) {
            rows.add(row("id", (long) i, "extra", null));
        }
        for (int i = 3; i < 8; i++) {
            rows.add(row("id", (long) i, "extra", "v" + i));
        }
        List<DataInsight> insights = DataProfiler.profile(wrap(rows));
        List<DataInsight> protection = l3Of(insights, "l3-sample-null-non-empty");
        assertEquals(1, protection.size());
        Map<String, Object> slots = protection.get(0).slots();
        assertEquals("extra", slots.get("field"));
        assertEquals(5L, slots.get("nonNullCount"));
    }

    // ---- L3 inline sparkline ----

    @Test
    void inlineSparklineFiresForNumericArrayColumn() {
        List<Object> rows = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            rows.add(row("id", (long) i, "trend", List.of(1L, 2L, 3L, 4L, 5L)));
        }
        List<DataInsight> insights = DataProfiler.profile(wrap(rows));
        List<DataInsight> spark = l3Of(insights, "l3-inline-sparkline");
        assertEquals(1, spark.size());
        assertEquals(5, spark.get(0).slots().get("maxPoints"));
    }

    // ---- L3 unit hints ----

    @Test
    void unitHintFiresForBytesColumn() {
        assertUnitBuiltin("fileSizeBytes", "@FormatBytes");
    }

    @Test
    void unitHintFiresForPercentColumn() {
        assertUnitBuiltin("cpuPercent", "@FormatPercent");
    }

    @Test
    void unitHintFiresForDurationColumn() {
        assertUnitBuiltin("requestDurationMs", "@FormatDuration");
    }

    @Test
    void unitHintFiresForThroughputColumn() {
        assertUnitBuiltin("networkMbps", "unit-label");
    }

    private static void assertUnitBuiltin(String fieldName, String expectedBuiltin) {
        List<Object> rows = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            rows.add(row("id", (long) i, fieldName, (long) (100 + i)));
        }
        List<DataInsight> insights = DataProfiler.profile(wrap(rows));
        List<DataInsight> hints = l3Of(insights, "l3-field-unit-hint");
        assertEquals(1, hints.size(), "expected unit hint for " + fieldName);
        assertEquals(expectedBuiltin, hints.get(0).slots().get("recommendedBuiltin"));
    }

    // ---- deterministic ordering: L2 before L3 ----

    @Test
    void insightsAreSortedByLevelL2BeforeL3() {
        List<Object> rows = new ArrayList<>();
        String[] groups = {"A", "B", "C"};
        for (int g = 0; g < groups.length; g++) {
            for (int i = 0; i < 12; i++) {
                rows.add(row("community", groups[g], "value", (long) (i + g)));
            }
        }
        List<DataInsight> insights = DataProfiler.profile(wrap(rows));
        int lastL2Index = -1;
        int firstL3Index = Integer.MAX_VALUE;
        for (int i = 0; i < insights.size(); i++) {
            if (insights.get(i).level() == InsightLevel.L2_DATASET) {
                lastL2Index = i;
            }
            if (insights.get(i).level() == InsightLevel.L3_FIELD && i < firstL3Index) {
                firstL3Index = i;
            }
        }
        assertNotNull(onlyL2(insights));
        if (lastL2Index >= 0 && firstL3Index < Integer.MAX_VALUE) {
            assertTrue(lastL2Index < firstL3Index, "L2 insights must precede L3 insights");
        }
    }
}
