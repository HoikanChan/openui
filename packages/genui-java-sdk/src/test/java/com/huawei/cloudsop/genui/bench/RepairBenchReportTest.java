/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.bench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.Json;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link RepairBenchReport}：JSONL + 案例目录 → 单文件 HTML。
 *
 * @since 2026
 */
class RepairBenchReportTest {

    @TempDir
    Path temp;

    @Test
    void writesSelfContainedReportAndOmitsMarkdownFallback() throws Exception {
        Path cases = temp.resolve("repair-cases");
        writeCase(cases.resolve("case-01"), "CASE-1", "按设备类型统计", "root = Stack([])");
        writeCase(cases.resolve("case-02"), "CASE-2", "展示光模块指标", "root = Stack([table])");

        List<String> lines = List.of(Json.stringify(caseLine("CASE-1", "still-invalid")),
                Json.stringify(caseLine("CASE-2", "repaired")), Json.stringify(summary()));
        Path html = temp.resolve("out/run.html");

        RepairBenchReport.write(lines, cases, html, "run.jsonl");

        assertTrue(Files.isRegularFile(html));
        String page = Files.readString(html, StandardCharsets.UTF_8);
        assertTrue(page.contains("<html lang=\"zh-CN\">"));
        assertTrue(page.contains("原始意图"));
        assertTrue(page.contains("反思 Prompt"));
        assertFalse(page.contains("__DATA__") || page.contains("__JS__") || page.contains("__CSS__"));
        assertFalse(page.contains("Incremental repair did not produce a valid page"),
                "Markdown fallback must never be embedded");
        assertFalse(page.contains("http://") || page.contains("https://"), "report must stay offline");

        String json = page.substring(page.indexOf("id=\"bench-data\">") + "id=\"bench-data\">".length(),
                page.indexOf("</script>", page.indexOf("id=\"bench-data\">")));
        Map<String, Object> payload = Json.asObject(Json.parse(json), "payload");
        List<Object> casesOut = Json.asList(payload.get("cases"), "cases");
        assertEquals(2, casesOut.size());
        Map<String, Object> stillInvalid = Json.asObject(casesOut.get(0), "case[0]");
        assertEquals("CASE-1", stillInvalid.get("id"), "still-invalid sorts before repaired");
        assertEquals("root = Stack([])", stillInvalid.get("finalDsl"));
        assertEquals("run.jsonl", Json.asObject(payload.get("meta"), "meta").get("sourceFile"));
        assertTrue(String.valueOf(stillInvalid.get("finalSource")).equals("finalDsl"));
    }

    private static Map<String, Object> caseLine(String id, String status) {
        LinkedHashMap<String, Object> line = new LinkedHashMap<>();
        line.put("type", "case");
        line.put("caseId", id);
        line.put("status", status);
        line.put("success", "repaired".equals(status));
        line.put("reproduced", true);
        line.put("initialIssueCodes", List.of("unknown-component"));
        line.put("internalIssueCodes", List.of("internal-code"));
        line.put("attemptCount", 1);
        line.put("durationMs", 120L);
        line.put("endReason", "repaired".equals(status) ? "CHECK_PASSED" : "VALIDATION_FAILED");
        line.put("markdownFallback", !"repaired".equals(status));
        line.put("outputDsl",
                "repaired".equals(status)
                        ? "root = Stack([table])"
                        : "root = Stack([MarkDownRenderer(\"Incremental repair did not produce a valid page.\")])");
        line.put("finalDsl", "root = Stack([])");
        line.put("finalIssueCodes", "repaired".equals(status) ? List.of() : List.of("unknown-component"));
        LinkedHashMap<String, Object> round = new LinkedHashMap<>();
        round.put("rawOutput", "root = Stack([])");
        round.put("currentDefinitions", "root = Stack([])");
        round.put("durationMs", 120L);
        round.put("endReason", "VALIDATION_FAILED");
        round.put("status", "INVALID");
        round.put("issues",
                List.of("ValidationIssue[code=unknown-component, severity=ERROR, source=semantic, "
                        + "message=Unknown component \"Nope\", statementId=root, component=Nope, path=null, line=2, "
                        + "column=1, hint=use a known component, retryable=true]"));
        line.put("rounds", List.of(round));
        return line;
    }

    private static Map<String, Object> summary() {
        LinkedHashMap<String, Object> line = new LinkedHashMap<>();
        line.put("type", "summary");
        line.put("model", "test-model");
        line.put("maxAttempts", 3);
        line.put("concurrency", 2);
        line.put("timeoutMinutes", 0L);
        line.put("total", 2);
        line.put("byStatus", Map.of("still-invalid", 1, "repaired", 1));
        line.put("mainRate", Map.of("success", 1, "denominator", 2));
        line.put("endToEndRate", Map.of("success", 1, "denominator", 2));
        line.put("secondaryRate", Map.of("success", 1, "denominator", 2));
        line.put("sessionBudgetSeconds", 20L);
        line.put("issueBuckets", Map.of("unknown-component", Map.of("cases", 2, "repaired", 1, "still-invalid", 1)));
        line.put("durationMs", 999L);
        return line;
    }

    private static void writeCase(Path dir, String id, String intent, String dsl) throws Exception {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("case.json"),
                Json.stringify(Map.of("id", id, "originalIntent", intent, "dataModel",
                        Map.of("title", "示例", "rows", List.of(Map.of("name", "A"))), "rootCause", "误用 @GroupBy",
                        "source", Map.of("file", "case.md", "run", "run-1"))),
                StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("dsl.txt"), dsl, StandardCharsets.UTF_8);
    }
}
