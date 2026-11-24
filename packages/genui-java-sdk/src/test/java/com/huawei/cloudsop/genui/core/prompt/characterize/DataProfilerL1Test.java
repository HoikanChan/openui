/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.prompt.characterize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Behavior tests for {@link InsightLevel#L1_CONTAINER} rules and their cross-layer composition with
 * inner L2/L3 insights.
 */
class DataProfilerL1Test {

    // ---- l1-correlated-object-merge ----

    @Test
    void correlatedMergeFiresForHeterogeneousArrayObjectsSharingDeviceId() {
        Map<String, Object> cpuObj =
                orderedMap(
                        "deviceId", "dev-01", "cpuUsage", 42.5, "cpuCores", 16L, "cpuTemp", 61.0);
        Map<String, Object> gpuObj =
                orderedMap(
                        "deviceId",
                        "dev-01",
                        "gpuUsage",
                        88.0,
                        "gpuMemGb",
                        24L,
                        "gpuPowerW",
                        250.0);
        Map<String, Object> raw = orderedMap("metrics", List.of(cpuObj, gpuObj));

        List<DataInsight> insights = DataProfiler.profile(raw);
        List<DataInsight> l1 = byId(insights, DataProfiler.ID_L1_CORRELATED_OBJECT_MERGE);
        assertEquals(1, l1.size());
        DataInsight insight = l1.get(0);
        assertEquals(InsightLevel.L1_CONTAINER, insight.level());
        assertEquals("metrics", insight.targetPath());
        assertEquals("deviceId", insight.slots().get("joinKey"));
        assertEquals(List.of("dev-01"), insight.slots().get("sharedValues"));
        assertEquals(true, insight.slots().get("aligned"));
        @SuppressWarnings("unchecked")
        List<List<String>> uniqueGroups =
                (List<List<String>>) insight.slots().get("uniqueFieldGroups");
        assertEquals(2, uniqueGroups.size());
        assertTrue(uniqueGroups.get(0).contains("cpuUsage"));
        assertTrue(uniqueGroups.get(1).contains("gpuUsage"));

        // Outer heterogeneous array must not also emit a flat homogeneous-table fallback.
        assertTrue(
                insights.stream()
                        .noneMatch(
                                i ->
                                        i.level() == InsightLevel.L2_DATASET
                                                && "metrics".equals(i.targetPath())));
    }

    @Test
    void correlatedMergeFiresForSiblingHeterogeneousObjectsSharingId() {
        Map<String, Object> cpuInfo =
                orderedMap("deviceId", "dev-01", "cpuUsage", 42L, "cpuLoad", 1.5);
        Map<String, Object> gpuInfo =
                orderedMap("deviceId", "dev-01", "gpuUsage", 88L, "gpuMemUsed", 18L);
        Map<String, Object> raw = orderedMap("cpuInfo", cpuInfo, "gpuInfo", gpuInfo);

        List<DataInsight> insights = DataProfiler.profile(raw);
        List<DataInsight> l1 = byId(insights, DataProfiler.ID_L1_CORRELATED_OBJECT_MERGE);
        assertEquals(1, l1.size());
        assertEquals("deviceId", l1.get(0).slots().get("joinKey"));
        assertEquals(List.of("dev-01"), l1.get(0).slots().get("sharedValues"));
        assertEquals(List.of("cpuInfo", "gpuInfo"), l1.get(0).slots().get("sourceKeys"));
    }

    @Test
    void correlatedMergeFiresForHeterogeneousObjectsSharingAlignedNestedTimestamps() {
        long base = 1_700_000_000_000L;
        List<Object> cpuSeries =
                List.of(
                        orderedMap("timestamp", base, "cpu", 30L),
                        orderedMap("timestamp", base + 60_000L, "cpu", 45L),
                        orderedMap("timestamp", base + 120_000L, "cpu", 50L));
        List<Object> memSeries =
                List.of(
                        orderedMap("timestamp", base, "mem", 60L),
                        orderedMap("timestamp", base + 60_000L, "mem", 62L),
                        orderedMap("timestamp", base + 120_000L, "mem", 65L));
        Map<String, Object> objA = orderedMap("cpuSeries", cpuSeries, "cpuModel", "Xeon");
        Map<String, Object> objB = orderedMap("memSeries", memSeries, "memTotalGb", 128L);
        Map<String, Object> raw = orderedMap("panels", List.of(objA, objB));

        List<DataInsight> insights = DataProfiler.profile(raw);
        List<DataInsight> l1 = byId(insights, DataProfiler.ID_L1_CORRELATED_OBJECT_MERGE);
        assertEquals(1, l1.size());
        assertEquals("timestamp", l1.get(0).slots().get("joinKey"));
        assertEquals(true, l1.get(0).slots().get("aligned"));
    }

    // ---- l1-multi-entity-tabs & cross-layer composition ----

    @Test
    void multiEntityTabsFiresAndComposesWithInnerSubTableL2AndL3() {
        long base = 1_700_000_000_000L;
        List<Object> devices = new ArrayList<>();
        String[] names = {"设备A", "设备B", "设备C"};
        String[] severities = {"critical", "warning", "info"};
        for (int d = 0; d < names.length; d++) {
            List<Object> alarms = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                alarms.add(
                        orderedMap(
                                "timestamp",
                                base + (d * 4L + i) * 3_600_000L,
                                "severity",
                                severities[i % severities.length],
                                "message",
                                names[d] + " alarm " + i,
                                "score",
                                (long) (10 + d * 10 + i)));
            }
            devices.add(orderedMap("deviceName", names[d], "status", "online", "alarms", alarms));
        }
        Map<String, Object> raw = orderedMap("devices", devices);

        List<DataInsight> insights = DataProfiler.profile(raw);

        // Outer L1 multi-entity-tabs fires with discriminator & titles.
        List<DataInsight> l1 = byId(insights, DataProfiler.ID_L1_MULTI_ENTITY_TABS);
        assertEquals(1, l1.size());
        DataInsight tabInsight = l1.get(0);
        assertEquals("devices", tabInsight.targetPath());
        assertEquals("deviceName", tabInsight.slots().get("tabKey"));
        assertEquals(List.of("设备A", "设备B", "设备C"), tabInsight.slots().get("tabTitles"));
        assertEquals(List.of("alarms"), tabInsight.slots().get("nestedArrayFields"));

        // Inner alarms sub-table still produces L2 and L3 insights, composing with outer L1.
        List<DataInsight> innerL2 =
                insights.stream()
                        .filter(
                                i ->
                                        i.level() == InsightLevel.L2_DATASET
                                                && "devices[].alarms".equals(i.targetPath()))
                        .toList();
        assertEquals(1, innerL2.size(), "inner alarms sub-table should emit one L2 winner");

        List<DataInsight> innerL3 =
                insights.stream()
                        .filter(
                                i ->
                                        i.level() == InsightLevel.L3_FIELD
                                                && i.targetPath().startsWith("devices[].alarms."))
                        .toList();
        assertFalse(innerL3.isEmpty(), "inner alarms sub-table should emit L3 field insights");
        assertTrue(
                innerL3.stream().anyMatch(i -> "l3-time-range".equals(i.id())),
                "inner alarms should expose l3-time-range");
        assertTrue(
                innerL3.stream().anyMatch(i -> "l3-enum-distribution".equals(i.id())),
                "inner alarms should expose l3-enum-distribution");

        // Ordering: L1 precedes L2 which precedes L3.
        assertEquals(InsightLevel.L1_CONTAINER, insights.get(0).level());
    }

    // ---- l1-paginated-envelope ----

    @Test
    void paginatedEnvelopeFiresAlongsideInnerTableL2() {
        List<Object> rows = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            rows.add(
                    orderedMap(
                            "id",
                            (long) i,
                            "region",
                            i % 2 == 0 ? "east" : "west",
                            "v",
                            (long) (i * 10)));
        }
        Map<String, Object> raw =
                orderedMap("total", 120L, "pageIndex", 1L, "pageSize", 20L, "list", rows);

        List<DataInsight> insights = DataProfiler.profile(raw);
        List<DataInsight> l1 = byId(insights, DataProfiler.ID_L1_PAGINATED_ENVELOPE);
        assertEquals(1, l1.size());
        assertEquals("data", l1.get(0).targetPath());
        assertEquals("list", l1.get(0).slots().get("listField"));
        assertEquals("list", l1.get(0).slots().get("arrayPath"));
        assertTrue(
                insights.stream()
                        .anyMatch(
                                i ->
                                        i.level() == InsightLevel.L2_DATASET
                                                && "list".equals(i.targetPath())),
                "paginated list should still receive its L2 dataset insight");
    }

    // ---- l1-dynamic-key-map ----

    @Test
    void dynamicKeyMapFiresForHomogeneousKeyedObjects() {
        Map<String, Object> raw =
                orderedMap(
                        "dev-01",
                        orderedMap("cpu", 40L, "mem", 65L, "status", "ok"),
                        "dev-02",
                        orderedMap("cpu", 55L, "mem", 72L, "status", "warn"),
                        "dev-03",
                        orderedMap("cpu", 20L, "mem", 45L, "status", "ok"));

        List<DataInsight> insights = DataProfiler.profile(raw);
        List<DataInsight> l1 = byId(insights, DataProfiler.ID_L1_DYNAMIC_KEY_MAP);
        assertEquals(1, l1.size());
        assertEquals("data", l1.get(0).targetPath());
        assertEquals(3, l1.get(0).slots().get("entryCount"));
        assertEquals(List.of("dev-01", "dev-02", "dev-03"), l1.get(0).slots().get("sampleKeys"));
        assertTrue(l1.get(0).rulePrompt().contains("@ObjectEntries"));
    }

    // ---- l1-single-record-detail ----

    @Test
    void singleRecordDetailFiresForStandaloneRecordAndEnvelopeWrappedRecord() {
        Map<String, Object> flat =
                orderedMap(
                        "deviceName",
                        "SW-Core-01",
                        "ip",
                        "10.0.0.1",
                        "cpuUsage",
                        45L,
                        "status",
                        "online");
        List<DataInsight> flatInsights = DataProfiler.profile(flat);
        List<DataInsight> flatL1 = byId(flatInsights, DataProfiler.ID_L1_SINGLE_RECORD_DETAIL);
        assertEquals(1, flatL1.size());
        assertEquals("data", flatL1.get(0).targetPath());
        assertTrue(flatL1.get(0).rulePrompt().contains("Descriptions"));

        Map<String, Object> wrapped =
                orderedMap(
                        "code",
                        200L,
                        "message",
                        "ok",
                        "data",
                        orderedMap("id", "u-1", "name", "Alice", "role", "admin", "email", "a@b"));
        List<DataInsight> wrappedInsights = DataProfiler.profile(wrapped);
        List<DataInsight> wrappedL1 =
                byId(wrappedInsights, DataProfiler.ID_L1_SINGLE_RECORD_DETAIL);
        assertEquals(1, wrappedL1.size());
        assertEquals("data", wrappedL1.get(0).targetPath());
    }

    // ---- helpers ----

    private static List<DataInsight> byId(List<DataInsight> insights, String id) {
        return insights.stream().filter(i -> id.equals(i.id())).toList();
    }

    private static Map<String, Object> orderedMap(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }
}
