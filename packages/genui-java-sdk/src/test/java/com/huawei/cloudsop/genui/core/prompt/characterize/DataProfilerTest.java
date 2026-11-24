/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.prompt.characterize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class DataProfilerTest {

    private static final int HUGE_TRIGGER_BYTES = 10_000_000;
    private static final int TINY_TRIGGER_BYTES = 1;

    // ---- characterize: parity with the original Characterizer gate ----

    @Test
    void characterizePassesThroughOnNullRaw() {
        CharacterizationConfig cfg = CharacterizationConfig.defaults();
        Characterized result = DataProfiler.characterize(null, cfg);
        assertNull(result.sample());
        assertNull(result.shape());
    }

    @Test
    void characterizePassesThroughOnEmptyRaw() {
        CharacterizationConfig cfg = CharacterizationConfig.defaults();
        Map<String, Object> raw = new LinkedHashMap<>();
        Characterized result = DataProfiler.characterize(raw, cfg);
        assertSame(raw, result.sample());
        assertNull(result.shape());
    }

    @Test
    void characterizePassesThroughWhenDisabled() {
        CharacterizationConfig cfg =
                CharacterizationConfig.builder()
                        .enabled(false)
                        .triggerBytes(TINY_TRIGGER_BYTES)
                        .build();
        Map<String, Object> raw = largePayload();
        Characterized result = DataProfiler.characterize(raw, cfg);
        assertSame(raw, result.sample());
        assertNull(result.shape());
    }

    @Test
    void characterizePassesThroughBelowTriggerBytes() {
        CharacterizationConfig cfg =
                CharacterizationConfig.builder().triggerBytes(HUGE_TRIGGER_BYTES).build();
        Map<String, Object> raw = largePayload();
        Characterized result = DataProfiler.characterize(raw, cfg);
        assertSame(raw, result.sample());
        assertNull(result.shape());
    }

    @Test
    void characterizeWalksAboveTriggerBytes() {
        CharacterizationConfig cfg =
                CharacterizationConfig.builder()
                        .triggerBytes(TINY_TRIGGER_BYTES)
                        .sampleRows(2)
                        .build();
        Map<String, Object> raw = largePayload();
        Characterized result = DataProfiler.characterize(raw, cfg);
        assertNotNull(result.shape());
        assertTrue(result.sample() instanceof Map);
    }

    @Test
    void characterizerDelegatesToDataProfiler() {
        CharacterizationConfig cfg =
                CharacterizationConfig.builder().triggerBytes(TINY_TRIGGER_BYTES).build();
        Map<String, Object> raw = largePayload();
        Characterized viaCharacterizer = Characterizer.characterize(raw, cfg);
        Characterized viaProfiler = DataProfiler.characterize(raw, cfg);
        assertNotNull(viaCharacterizer.shape());
        assertNotNull(viaProfiler.shape());
    }

    // ---- profile: l0-empty-data ----

    @Test
    void profileFiresEmptyDataOnNullRaw() {
        List<DataInsight> insights = DataProfiler.profile(null);
        assertEquals(1, insights.size());
        DataInsight insight = insights.get(0);
        assertEquals(DataProfiler.ID_L0_EMPTY_DATA, insight.id());
        assertEquals(InsightLevel.L0_GATE, insight.level());
        assertEquals("null_or_empty_object", insight.slots().get("reason"));
    }

    @Test
    void profileFiresEmptyDataOnEmptyObject() {
        List<DataInsight> insights = DataProfiler.profile(new LinkedHashMap<>());
        assertEquals(1, insights.size());
        assertEquals(DataProfiler.ID_L0_EMPTY_DATA, insights.get(0).id());
        assertEquals("null_or_empty_object", insights.get(0).slots().get("reason"));
    }

    @Test
    void profileFiresEmptyDataOnAllNullLeaves() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("a", null);
        raw.put("b", null);
        List<DataInsight> insights = DataProfiler.profile(raw);
        assertEquals(1, insights.size());
        DataInsight insight = insights.get(0);
        assertEquals(DataProfiler.ID_L0_EMPTY_DATA, insight.id());
        assertEquals("all_null_leaves", insight.slots().get("reason"));
    }

    @Test
    void profileFiresEmptyDataOnEmptyListWithTotal() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("list", List.of());
        raw.put("total", 0L);
        List<DataInsight> insights = DataProfiler.profile(raw);
        assertEquals(1, insights.size());
        assertEquals(DataProfiler.ID_L0_EMPTY_DATA, insights.get(0).id());
        assertEquals("empty_array", insights.get(0).slots().get("reason"));
    }

    @Test
    void profileFiresEmptyDataOnEmptyRowsWithPagination() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("rows", List.of());
        raw.put("total", 0L);
        raw.put("pageIndex", 1L);
        raw.put("pageSize", 20L);
        List<DataInsight> insights = DataProfiler.profile(raw);
        assertEquals(1, insights.size());
        assertEquals(DataProfiler.ID_L0_EMPTY_DATA, insights.get(0).id());
    }

    @Test
    void profileFiresEmptyDataOnCodeMessageEmptyDataArray() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("code", 200L);
        raw.put("message", "ok");
        raw.put("data", List.of());
        List<DataInsight> insights = DataProfiler.profile(raw);
        assertEquals(1, insights.size());
        assertEquals(DataProfiler.ID_L0_EMPTY_DATA, insights.get(0).id());
    }

    // ---- profile: l0-status-ack-only ----

    @Test
    void profileFiresStatusAckOnCodePlusMessage() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("code", 200L);
        raw.put("message", "success");
        List<DataInsight> insights = DataProfiler.profile(raw);
        assertEquals(1, insights.size());
        DataInsight insight = insights.get(0);
        assertEquals(DataProfiler.ID_L0_STATUS_ACK_ONLY, insight.id());
        assertEquals("success", insight.slots().get("outcome"));
        assertEquals("code", insight.slots().get("statusField"));
        assertEquals("message", insight.slots().get("messageField"));
    }

    @Test
    void profileFiresStatusAckOnStatusPlusError() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("status", 500L);
        raw.put("error", "timeout");
        List<DataInsight> insights = DataProfiler.profile(raw);
        assertEquals(1, insights.size());
        DataInsight insight = insights.get(0);
        assertEquals(DataProfiler.ID_L0_STATUS_ACK_ONLY, insight.id());
        assertEquals("error", insight.slots().get("outcome"));
    }

    @Test
    void profileFiresStatusAckOnOkTrue() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("ok", true);
        raw.put("msg", "done");
        raw.put("requestId", "r1");
        List<DataInsight> insights = DataProfiler.profile(raw);
        assertEquals(1, insights.size());
        DataInsight insight = insights.get(0);
        assertEquals(DataProfiler.ID_L0_STATUS_ACK_ONLY, insight.id());
        assertEquals("success", insight.slots().get("outcome"));
    }

    @Test
    void profileFiresStatusAckOnCodeMessageNullData() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("code", 200L);
        raw.put("message", "ok");
        raw.put("data", null);
        List<DataInsight> insights = DataProfiler.profile(raw);
        assertEquals(1, insights.size());
        assertEquals(DataProfiler.ID_L0_STATUS_ACK_ONLY, insights.get(0).id());
    }

    // ---- profile: non-L0 business payloads stay empty ----

    @Test
    void profileDoesNotFireOnEnvelopeWithBusinessRows() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 1L);
        row.put("name", "dev-1");
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("code", 200L);
        raw.put("data", List.of(row));
        List<DataInsight> insights = DataProfiler.profile(raw);
        assertTrue(insights.isEmpty());
    }

    @Test
    void profileDoesNotFireOnPlainOrdersList() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 1L);
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("orders", List.of(row));
        List<DataInsight> insights = DataProfiler.profile(raw);
        assertTrue(insights.isEmpty());
    }

    @Test
    void profileDoesNotFireOnBusinessScalars() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("deviceName", "SW-1");
        raw.put("cpu", 45L);
        List<DataInsight> insights = DataProfiler.profile(raw);
        assertTrue(insights.isEmpty());
    }

    // ---- helpers ----

    private static Map<String, Object> largePayload() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("name", "device-1");
        raw.put("value", 42L);
        return raw;
    }
}
