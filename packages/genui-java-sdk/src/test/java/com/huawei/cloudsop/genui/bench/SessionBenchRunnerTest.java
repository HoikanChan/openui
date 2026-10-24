/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.huawei.cloudsop.genui.bench;

import static org.junit.jupiter.api.Assertions.*;

import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.core.contract.GenerationContractLoader;
import com.huawei.cloudsop.genui.core.llm.transport.InMemoryLlmTransport;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

class SessionBenchRunnerTest {
    @Test
    void liveInitialAndIncrementalPatchTravelThroughPublicStreamingSession() {
        var calls = new AtomicInteger();
        InMemoryLlmTransport transport = new InMemoryLlmTransport() {
            public String post(String body) {
                throw new AssertionError("must stream");
            }
            public InputStream postStream(String body) {
                int call = calls.incrementAndGet();
                String dsl = call == 1 ? "root = Stack([UnknownThing()]);" : "root = Stack([TextContent(\"ok\")]);";
                String chunk = Json.stringify(Map.of("choices", List.of(Map.of("delta", Map.of("content", dsl)))));
                return new ByteArrayInputStream(
                        ("data: " + chunk + "\n\ndata: [DONE]\n\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        };
        var benchCase = new RepairCasePack.RepairCase("live", "Show ok", Map.of(), "root = Historical();",
                GenerationContractLoader.loadDefault(), null, "live");
        List<Map<String, Object>> events = new ArrayList<>();
        Map<String, Object> result = new SessionBenchRunner(transport, "scripted").run(benchCase, events::add);
        assertEquals(2, calls.get());
        assertEquals("live-initial", result.get("generationMode"));
        assertEquals("CHECK_PASSED", result.get("endReason"));
        assertEquals(true, result.get("validationPassed"));
        assertEquals(2L, result.get("modelRequestCount"));
        assertEquals("unconfirmed", result.get("qualityCategory"));
        assertEquals(false, result.get("accuracyEligible"));
        String deltas = events.stream().filter(e -> "dsl".equals(e.get("type")))
                .map(e -> String.valueOf(e.get("content"))).reduce("", String::concat);
        assertTrue(deltas.contains("UnknownThing"));
        assertTrue(deltas.contains("TextContent"));
        assertFalse(deltas.contains("Historical"));
        assertEquals(2, ((List<?>) result.get("rounds")).size());
        assertNotNull(result.get("contractSha256"));
        assertFalse(result.containsKey("firstVisibleRenderMs"), "Only browser DOM may measure visible render");
    }
    @Test
    void replayIsExplicitAndHistoricalRequestIsNotCountedAsAModelCall() {
        InMemoryLlmTransport transport = new InMemoryLlmTransport() {
            public String post(String body) {
                throw new AssertionError("must stream");
            }
            public InputStream postStream(String body) {
                String chunk = Json.stringify(Map.of("choices",
                        List.of(Map.of("delta", Map.of("content", "root = Stack([TextContent(\"fixed\")]);")))));
                return new ByteArrayInputStream(
                        ("data: " + chunk + "\n\ndata: [DONE]\n\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        };
        var benchCase = new RepairCasePack.RepairCase("replay", "Show fixed", Map.of(),
                "root = Stack([UnknownThing()]);", GenerationContractLoader.loadDefault(), null, "replay");
        var result = new SessionBenchRunner(transport, "scripted", true).run(benchCase, event -> {
        });
        assertEquals("historical-initial-replay", result.get("generationMode"));
        assertEquals(1L, result.get("modelRequestCount"));
        assertEquals(2, result.get("sessionRequestCount"));
        assertEquals(2, ((List<?>) result.get("rounds")).size());
        assertEquals("CHECK_PASSED", result.get("endReason"));
    }
}
