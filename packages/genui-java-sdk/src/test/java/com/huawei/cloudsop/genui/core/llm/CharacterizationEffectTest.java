/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransport;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransportException;
import com.huawei.cloudsop.genui.core.prompt.characterize.ArrayShape;
import com.huawei.cloudsop.genui.core.prompt.characterize.CharacterizationConfig;
import com.huawei.cloudsop.genui.core.prompt.characterize.Characterized;
import com.huawei.cloudsop.genui.core.prompt.characterize.Characterizer;
import com.huawei.cloudsop.genui.core.prompt.characterize.EnumShape;
import com.huawei.cloudsop.genui.core.prompt.characterize.FieldShape;
import com.huawei.cloudsop.genui.core.prompt.characterize.ObjectShape;
import com.huawei.cloudsop.genui.core.prompt.characterize.ScalarShape;
import com.huawei.cloudsop.genui.core.prompt.characterize.ScalarType;
import com.huawei.cloudsop.genui.core.prompt.characterize.TsTypeRenderer;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Effect validation for the Characterization gate (spec 7) — measures, on representative large fixtures, that the
 * prompt copy shrinks dramatically (7.2), that the inferred shape is lossless where it matters (7.3: complete enum
 * domains, true counts, all field paths present), and an end-to-end proxy (7.4) proving the assembled prompt carries
 * everything a model needs to avoid dropping enum cases.
 *
 * <p>
 * <b>Scope note on "end-to-end" (7.4):</b> this environment has no live LLM — only a stub {@link FakeTransport}.
 * "End-to-end" is therefore realized as a <i>prompt-content proxy</i>: we assert that the assembled system prompt fed
 * to the (fake) transport contains the COMPLETE enum domain (as a TS union) and the true array count, i.e. the model is
 * <i>given</i> everything required to cover every {@code @Switch} case. We do not call a real model or assert on
 * generated DSL output.
 *
 * <p>
 * Tests run in declared order so the compression-ratio measurement (which also produces the metrics artifact) runs
 * first and other tests can rely on its console output ordering; this is a readability aid only — each test
 * independently builds and releases its own fixtures.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CharacterizationEffectTest {

    private static final int ROW_COUNT = 10_000;
    private static final int POINTS_COUNT = 10_000;
    private static final String[] STATUSES = {"open", "closed", "pending"};

    // ─── 7.1 fixture builders ──────────────────────────────────────────────

    /**
     * Fixture A: a wide object table — 10,000 rows of {id, name, status, revenue, note}.
     *
     * <ul>
     * <li>{@code status} is low-cardinality (3 values: open/closed/pending) but distributed so that NOT all 3 values
     * appear within the first {@code sampleRows} (default 3) rows — the first rows are all {@code "open"}.
     * <li>{@code name} is high-cardinality (unique per row) so it must stay {@code string}, never become an enum.
     * <li>{@code note} is {@code null} on a subset of rows (nullable).
     * </ul>
     */
    private static Map<String, Object> buildFixtureA() {
        List<Object> rows = new ArrayList<>(ROW_COUNT);
        for (int i = 0; i < ROW_COUNT; i++) {
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("id", (long) i);
            row.put("name", "user-" + i);
            // First 10 rows are all "open" so the sampled prefix (sampleRows=3) omits the other two
            // enum values; the full 10,000-row scan must still recover the complete domain.
            row.put("status", i < 10 ? "open" : STATUSES[i % STATUSES.length]);
            row.put("revenue", (double) (i * 1.5));
            row.put("note", i % 4 == 0 ? null : "note for row " + i);
            rows.add(row);
        }
        LinkedHashMap<String, Object> root = new LinkedHashMap<>();
        root.put("rows", rows);
        return root;
    }

    /** Fixture B: a numeric series — {@code {points: [..10000 numbers..]}}. */
    private static Map<String, Object> buildFixtureB() {
        List<Object> points = new ArrayList<>(POINTS_COUNT);
        for (int i = 0; i < POINTS_COUNT; i++) {
            points.add((double) Math.sin(i) * 1000);
        }
        LinkedHashMap<String, Object> root = new LinkedHashMap<>();
        root.put("points", points);
        return root;
    }

    /**
     * Fixture C: a deep nested object, large enough to trip the trigger gate, with a nested array of objects a couple
     * of levels down (exercises {@code deepScanLimit}-bounded deep schema and nested field paths).
     *
     * <p>
     * Shape: {@code root.dashboard.section.widgets[].metrics[].{metricId, value, label}} plus enough sibling padding
     * fields to exceed {@code triggerBytes}.
     */
    private static final int WIDGET_COUNT = 50;
    private static final int METRICS_PER_WIDGET = 40;

    private static Map<String, Object> buildFixtureC() {
        List<Object> widgets = new ArrayList<>(WIDGET_COUNT);
        for (int w = 0; w < WIDGET_COUNT; w++) {
            List<Object> metrics = new ArrayList<>(METRICS_PER_WIDGET);
            for (int m = 0; m < METRICS_PER_WIDGET; m++) {
                LinkedHashMap<String, Object> metric = new LinkedHashMap<>();
                metric.put("metricId", "widget-" + w + "-metric-" + m);
                metric.put("value", (double) (w * METRICS_PER_WIDGET + m));
                metric.put("label", "Metric label number " + m + " for widget " + w);
                metrics.add(metric);
            }
            LinkedHashMap<String, Object> widget = new LinkedHashMap<>();
            widget.put("widgetId", "widget-" + w);
            widget.put("title", "Widget title padding text to add bulk " + w);
            widget.put("metrics", metrics);
            widgets.add(widget);
        }
        LinkedHashMap<String, Object> section = new LinkedHashMap<>();
        section.put("name", "primary-section");
        section.put("widgets", widgets);

        LinkedHashMap<String, Object> dashboard = new LinkedHashMap<>();
        dashboard.put("dashboardId", "dash-001");
        dashboard.put("section", section);

        LinkedHashMap<String, Object> root = new LinkedHashMap<>();
        root.put("dashboard", dashboard);
        return root;
    }

    // ─── 7.2 compression-ratio measurement + metrics artifact ──────────────

    @Test
    @Order(1)
    void compressionRatioMeetsFloorsForAllFixturesAndEmitsMetricsArtifact() throws IOException {
        CharacterizationConfig cfg = CharacterizationConfig.defaults();
        List<String[]> rowsForArtifact = new ArrayList<>();
        double[] ratios = new double[3];

        ratios[0] = measureAndRecord("A: wide object table (10k rows)", buildFixtureA(), cfg, rowsForArtifact);
        ratios[1] = measureAndRecord("B: numeric series (10k numbers)", buildFixtureB(), cfg, rowsForArtifact);
        ratios[2] = measureAndRecord("C: deep nested object (50x40 metrics)", buildFixtureC(), cfg, rowsForArtifact);

        // Conservative floors per spec 7.2.
        assertTrue(ratios[0] <= 0.10, "Fixture A compression ratio should be <= 10%, was " + ratios[0]);
        assertTrue(ratios[1] <= 0.10, "Fixture B compression ratio should be <= 10%, was " + ratios[1]);
        assertTrue(ratios[2] < 1.0, "Fixture C should shrink at all, ratio was " + ratios[2]);
        // Defensible stricter threshold for C: nested object/array reduction should still beat 50%.
        assertTrue(ratios[2] <= 0.50, "Fixture C compression ratio should be <= 50%, was " + ratios[2]);

        writeAndPrintMetricsArtifact(rowsForArtifact);
    }

    /** Computes fullBytes/compressedBytes/ratio for one fixture, appends a row, returns the ratio. */
    private static double measureAndRecord(String label, Map<String, Object> fixture, CharacterizationConfig cfg,
            List<String[]> rows) {
        int fullBytes = Json.stringify(fixture).length();

        Characterized characterized = Characterizer.characterize(fixture, cfg);
        assertTrue(characterized.shape() != null, label + ": expected the gate to trigger");
        String sampleJson = Json.stringify(characterized.sample());
        String sidecar = TsTypeRenderer.render(characterized.shape(), cfg.sampleRows());
        int compressedBytes = sampleJson.length() + sidecar.length();

        double ratio = compressedBytes / (double) fullBytes;
        long fullTokens = Math.round(fullBytes / 4.0);
        long compressedTokens = Math.round(compressedBytes / 4.0);

        rows.add(new String[]{label, Integer.toString(fullBytes), Integer.toString(compressedBytes),
                String.format("%.2f%%", ratio * 100), Long.toString(fullTokens), Long.toString(compressedTokens)});
        return ratio;
    }

    private static void writeAndPrintMetricsArtifact(List<String[]> rows) throws IOException {
        StringBuilder out = new StringBuilder();
        out.append("# Characterization effect metrics\n\n");
        out.append("| Fixture | fullBytes | compressedBytes | ratio | ~full tokens | ~compressed tokens |\n");
        out.append("|---|---|---|---|---|---|\n");
        for (String[] row : rows) {
            out.append("| ").append(row[0]).append(" | ").append(row[1]).append(" | ").append(row[2]).append(" | ")
                    .append(row[3]).append(" | ").append(row[4]).append(" | ").append(row[5]).append(" |\n");
        }
        String table = out.toString();
        System.out.println(table);

        Path target = Paths.get("target");
        Files.createDirectories(target);
        Files.writeString(target.resolve("characterization-effect-metrics.md"), table, StandardCharsets.UTF_8);
    }

    // ─── 7.3 shape-fidelity assertions (losslessness where it matters) ─────

    @Test
    @Order(2)
    void enumDomainMatchesFullDistinctSetIncludingValuesAbsentFromSample() {
        Map<String, Object> fixtureA = buildFixtureA();
        CharacterizationConfig cfg = CharacterizationConfig.defaults();

        // Ground truth computed independently from the FULL data, not from the sample.
        TreeSet<String> expectedDomain = new TreeSet<>();
        @SuppressWarnings("unchecked")
        List<Object> rows = (List<Object>) fixtureA.get("rows");
        for (Object rawRow : rows) {
            @SuppressWarnings("unchecked")
            Map<String, Object> row = (Map<String, Object>) rawRow;
            expectedDomain.add((String) row.get("status"));
        }
        assertEquals(List.of("closed", "open", "pending"), new ArrayList<>(expectedDomain));

        Characterized characterized = Characterizer.characterize(fixtureA, cfg);
        ArrayShape rowsShape = (ArrayShape) ((ObjectShape) characterized.shape()).fields().get("rows").node();
        ObjectShape rowShape = (ObjectShape) rowsShape.element();
        FieldShape statusField = rowShape.fields().get("status");
        EnumShape statusEnum = assertInstanceOf(EnumShape.class, statusField.node());
        assertEquals(new ArrayList<>(expectedDomain), statusEnum.domain());

        // High-cardinality "name" must NOT collapse to an enum.
        FieldShape nameField = rowShape.fields().get("name");
        assertInstanceOf(ScalarShape.class, nameField.node());
        assertEquals(ScalarType.STRING, ((ScalarShape) nameField.node()).type());
    }

    @Test
    @Order(3)
    void arrayCountsAreTrueNotSampledForFixturesAAndB() {
        CharacterizationConfig cfg = CharacterizationConfig.defaults();

        Characterized a = Characterizer.characterize(buildFixtureA(), cfg);
        ArrayShape rowsShape = (ArrayShape) ((ObjectShape) a.shape()).fields().get("rows").node();
        assertEquals(ROW_COUNT, rowsShape.count());
        assertTrue(rowsShape.truncated());

        Characterized b = Characterizer.characterize(buildFixtureB(), cfg);
        ArrayShape pointsShape = (ArrayShape) ((ObjectShape) b.shape()).fields().get("points").node();
        assertEquals(POINTS_COUNT, pointsShape.count());
        assertTrue(pointsShape.truncated());
    }

    @Test
    @Order(4)
    void sidecarRenderingIncludesEveryFieldPathFromFullData() {
        CharacterizationConfig cfg = CharacterizationConfig.defaults();

        Characterized a = Characterizer.characterize(buildFixtureA(), cfg);
        String sidecarA = TsTypeRenderer.render(a.shape(), cfg.sampleRows());
        for (String key : List.of("id", "name", "status", "revenue", "note")) {
            assertTrue(sidecarA.contains(key), "Fixture A sidecar missing field: " + key);
        }

        Characterized c = Characterizer.characterize(buildFixtureC(), cfg);
        String sidecarC = TsTypeRenderer.render(c.shape(), cfg.sampleRows());
        for (String key : List.of("dashboard", "dashboardId", "section", "name", "widgets", "widgetId", "title",
                "metrics", "metricId", "value", "label")) {
            assertTrue(sidecarC.contains(key), "Fixture C sidecar missing field: " + key);
        }
    }

    // ─── 7.4 end-to-end prompt-content proxy ───────────────────────────────

    /**
     * Prompt-content proxy for "end-to-end, no dropped @Switch case" (see class javadoc): there is no live LLM here, so
     * we assert directly on the assembled system prompt that (a) the complete enum union is present, (b) the true array
     * count annotation is present, and (c) the embedded JSON sample is far smaller than the full 10,000-row dataset.
     */
    @Test
    @Order(5)
    void assembledPromptCarriesCompleteEnumUnionAndTrueCountForLargeDataset() {
        FakeTransport transport = FakeTransport
                .sync("{\"choices\":[{\"message\":{\"content\":\"```openui\\nroot = Stack([])\\n```\"}}]}");
        Map<String, Object> fixtureA = buildFixtureA();
        GenUiGenerator generator = GenUiGenerator.withTransport(GenUiLlmConfig.defaults(),
                CharacterizationConfig.defaults(), transport);

        generator.generate(UiGenerationRequest.builder().userInput("show rows").response(fixtureA).build());

        String prompt = systemPrompt(transport.lastBody);

        // (a) Complete enum union present — no case hidden from the model despite the sampled JSON
        // prefix being all "open".
        assertTrue(prompt.contains("\"closed\" | \"open\" | \"pending\""),
                "prompt should contain the complete enum union");

        // (b) True array count annotation present.
        assertTrue(prompt.contains("10000 items"), "prompt should annotate the true array count");

        // (c) Embedded JSON sample is small relative to the full 10,000-row dataset.
        String jsonBlock = jsonBlock(prompt);
        int sampledRowCount = countOccurrences(jsonBlock, "\"id\":");
        assertTrue(sampledRowCount < 100,
                "embedded JSON sample should be far smaller than 10,000 rows, was " + sampledRowCount);
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private static String systemPrompt(String body) {
        Map<String, Object> parsed = Json.asObject(Json.parse(body), "request");
        List<Object> messages = Json.asList(parsed.get("messages"), "messages");
        Map<String, Object> system = Json.asObject(messages.get(0), "system");
        return String.valueOf(system.get("content"));
    }

    private static String jsonBlock(String prompt) {
        int start = prompt.indexOf("```json");
        int end = prompt.indexOf("```", start + 7);
        return prompt.substring(start + 7, end);
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) != -1) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static final class FakeTransport implements LlmTransport {
        private final String response;
        private String lastBody;

        private FakeTransport(String response) {
            this.response = response;
        }

        static FakeTransport sync(String response) {
            return new FakeTransport(response);
        }

        @Override
        public String post(String body) throws LlmTransportException {
            lastBody = body;
            return response;
        }

        @Override
        public InputStream postStream(String body) throws LlmTransportException {
            lastBody = body;
            throw new UncheckedIOException(new IOException("not used by this test"));
        }
    }
}
