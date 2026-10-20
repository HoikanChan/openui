/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.bench;

import com.huawei.cloudsop.genui.core.Json;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 把 repair-bench 的 JSONL 运行产物渲染成单文件 HTML 报告：数据内嵌、无外部资源，可直接离线打开。
 *
 * <p>
 * 输入是 {@link RepairBenchRunner} 已经写出的完整 JSONL（summary + case 行），原始意图/数据/DSL 则从 {@code --cases}
 * 指向的案例目录读取。报告不修改运行结果，只做视图组装；Markdown 降级占位不会写入报告， 未修复案例展示最后一次校验的 DSL（{@code finalDsl}）。
 *
 * <p>
 * 模板与静态资源位于 {@code src/test/resources/bench-report/}，前端只消费预处理后的 payload，因此同一次运行 产出与历史报告结构一致。
 *
 * @since 2026
 */
final class RepairBenchReport {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final Pattern ISSUE = Pattern.compile(
            "^ValidationIssue\\[code=(.*?), severity=(.*?), source=(.*?), message=(.*?), statementId=(.*?), "
                    + "component=(.*?), path=(.*?), line=(-?\\d+), column=(-?\\d+), hint=(.*?), retryable=(.*?)\\]$",
            Pattern.DOTALL);

    private static final Map<String, Integer> STATUS_RANK = Map.of("error", 0, "timeout", 1, "transport-error", 2,
            "still-invalid", 3, "invalid", 4, "not-reproduced", 5, "repaired", 6);

    private RepairBenchReport() {
    }

    /**
     * 生成报告。
     *
     * @param jsonlLines
     *            本次运行写出的全部 JSONL 行（summary 必定是最后一行）
     * @param casesDir
     *            案例包根目录；用于补回 originalIntent / dataModel / 原始 DSL
     * @param outHtml
     *            报告输出路径（父目录不存在时自动创建）
     * @param sourceLabel
     *            JSONL 数据源展示名，例如文件名或 {@code stdout}
     * @throws IOException
     *             读取案例或写入报告失败
     */
    static void write(List<String> jsonlLines, Path casesDir, Path outHtml, String sourceLabel) throws IOException {
        Objects.requireNonNull(jsonlLines, "jsonlLines");
        Objects.requireNonNull(outHtml, "outHtml");

        Map<String, Object> summary = null;
        List<Map<String, Object>> caseLines = new ArrayList<>();
        for (String line : jsonlLines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            Map<String, Object> parsed = Json.asObject(Json.parse(line), "bench JSONL line");
            Object type = parsed.get("type");
            if ("summary".equals(type)) {
                summary = parsed;
            } else if ("case".equals(type)) {
                caseLines.add(parsed);
            }
        }
        if (summary == null) {
            throw new IOException("bench JSONL has no summary line");
        }

        Map<String, CaseMeta> metaById = loadCaseMeta(casesDir);
        List<Map<String, Object>> cases = new ArrayList<>();
        for (Map<String, Object> line : caseLines) {
            cases.add(toCase(line, metaById));
        }
        cases.sort(Comparator
                .comparingInt((Map<String, Object> item) -> STATUS_RANK.getOrDefault(str(item.get("status")), 9))
                .thenComparing(item -> str(item.get("id"))));

        LinkedHashMap<String, Object> meta = new LinkedHashMap<>(summary);
        meta.remove("type");
        meta.put("sourceFile", sourceLabel);

        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("meta", meta);
        payload.put("generatedAt", LocalDateTime.now().format(TIMESTAMP));
        payload.put("sourcePath", sourceLabel);
        payload.put("cases", cases);

        String model = str(summary.get("model"));
        String html = resource("/bench-report/report.html").replace("__MODEL__", escapeHtml(model))
                .replace("__CSS__", resource("/bench-report/report.css"))
                .replace("__DATA__", escapeForScript(Json.stringify(payload)))
                .replace("__JS__", resource("/bench-report/report.js"));

        Path absolute = outHtml.toAbsolutePath();
        Path parent = absolute.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(absolute, html, StandardCharsets.UTF_8);
    }

    // ── payload ─────────────────────────────────────────────────────────────

    private static Map<String, Object> toCase(Map<String, Object> line, Map<String, CaseMeta> metaById) {
        String id = str(line.get("caseId"));
        CaseMeta meta = metaById.get(id);
        String status = str(line.get("status"));

        LinkedHashMap<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("dir", meta == null ? "（未找到案例目录）" : meta.dirName());
        item.put("intent", meta == null ? "" : meta.intent());
        item.put("dataModel", meta == null ? null : meta.dataModel());
        item.put("rootCause", meta == null ? "" : meta.rootCause());
        item.put("caseSource", meta == null ? Map.of() : meta.source());
        item.put("status", status);
        item.put("success", Boolean.TRUE.equals(line.get("success")));
        item.put("reproduced", Boolean.TRUE.equals(line.get("reproduced")));
        item.put("initialCodes", stringList(line.get("initialIssueCodes")));
        item.put("internalCodes", stringList(line.get("internalIssueCodes")));
        item.put("finalCodes", stringList(line.get("finalIssueCodes")));
        item.put("attemptCount", line.get("attemptCount"));
        item.put("durationMs", line.get("durationMs"));
        item.put("endReason", line.get("endReason"));
        item.put("markdownFallback", line.get("markdownFallback"));
        item.put("error", line.get("error"));

        String output = normText(line.get("outputDsl"));
        String lastValidated = normText(line.get("finalDsl"));
        String finalDsl;
        String finalSource;
        if ("repaired".equals(status) && !output.isEmpty()) {
            finalDsl = output;
            finalSource = "outputDsl";
        } else if (!lastValidated.isEmpty()) {
            finalDsl = lastValidated;
            finalSource = "finalDsl";
        } else {
            finalDsl = "";
            finalSource = "none";
        }
        item.put("before", meta == null ? "" : meta.dsl());
        item.put("finalDsl", finalDsl);
        item.put("finalSource", finalSource);
        // Never embed the Markdown fallback: for failed runs outputDsl is only a placeholder page.
        item.put("fallbackHidden", !"repaired".equals(status) && !output.isEmpty());
        item.put("rounds", rounds(line.get("rounds")));
        return item;
    }

    private static List<Object> rounds(Object raw) {
        List<Object> result = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return result;
        }
        for (int index = 0; index < list.size(); index++) {
            if (!(list.get(index) instanceof Map<?, ?> rawRound)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> round = (Map<String, Object>) rawRound;
            List<Map<String, Object>> issues = issues(round.get("issues"));
            LinkedHashSet<String> codes = new LinkedHashSet<>();
            for (Map<String, Object> issue : issues) {
                codes.add(str(issue.get("code")));
            }

            LinkedHashMap<String, Object> item = new LinkedHashMap<>();
            item.put("n", index);
            item.put("status", round.get("status"));
            item.put("codes", new ArrayList<>(codes));
            item.put("issues", issues);
            item.put("durationMs", round.get("durationMs"));
            item.put("endReason", round.get("endReason"));
            item.put("rawOutput", normText(round.get("rawOutput")));
            item.put("currentDefinitions", normText(round.get("currentDefinitions")));
            result.add(item);
        }
        return result;
    }

    private static List<Map<String, Object>> issues(Object raw) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return result;
        }
        for (Object entry : list) {
            if (entry instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> issue = (Map<String, Object>) map;
                result.add(issue);
            } else if (entry != null) {
                result.add(parseIssue(String.valueOf(entry)));
            }
        }
        return result;
    }

    private static Map<String, Object> parseIssue(String raw) {
        String text = raw == null ? "" : raw.trim();
        Matcher matcher = ISSUE.matcher(text);
        if (matcher.matches()) {
            LinkedHashMap<String, Object> issue = new LinkedHashMap<>();
            issue.put("code", clean(matcher.group(1), "unknown"));
            issue.put("severity", clean(matcher.group(2), ""));
            issue.put("source", clean(matcher.group(3), ""));
            issue.put("message", clean(matcher.group(4), ""));
            issue.put("statementId", clean(matcher.group(5), ""));
            issue.put("component", clean(matcher.group(6), ""));
            issue.put("path", clean(matcher.group(7), ""));
            issue.put("line", Integer.parseInt(matcher.group(8)));
            issue.put("column", Integer.parseInt(matcher.group(9)));
            issue.put("hint", clean(matcher.group(10), ""));
            return issue;
        }
        LinkedHashMap<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("code", "unknown");
        fallback.put("severity", "ERROR");
        fallback.put("source", "");
        fallback.put("message", text);
        fallback.put("statementId", "");
        fallback.put("component", "");
        fallback.put("path", "");
        fallback.put("line", -1);
        fallback.put("column", -1);
        fallback.put("hint", "");
        return fallback;
    }

    // ── case metadata ───────────────────────────────────────────────────────

    private record CaseMeta(String dirName, String intent, Object dataModel, String rootCause,
            Map<String, Object> source, String dsl) {
    }

    private static Map<String, CaseMeta> loadCaseMeta(Path casesDir) throws IOException {
        LinkedHashMap<String, CaseMeta> result = new LinkedHashMap<>();
        if (casesDir == null || !Files.isDirectory(casesDir)) {
            return result;
        }
        List<Path> dirs;
        try (Stream<Path> stream = Files.list(casesDir)) {
            dirs = stream.filter(Files::isDirectory).sorted().toList();
        }
        for (Path dir : dirs) {
            Path caseJson = dir.resolve("case.json");
            if (!Files.isRegularFile(caseJson)) {
                continue;
            }
            Map<String, Object> json;
            try {
                json = Json.asObject(Json.parse(Files.readString(caseJson, StandardCharsets.UTF_8)), "case.json");
            } catch (RuntimeException error) {
                continue;
            }
            String id = str(json.get("id"));
            if (id.isBlank()) {
                continue;
            }
            Path dslPath = dir.resolve("dsl.txt");
            String dsl = Files.isRegularFile(dslPath)
                    ? normText(Files.readString(dslPath, StandardCharsets.UTF_8))
                    : "";
            Map<String, Object> source = json.get("source") instanceof Map<?, ?> rawSource
                    ? toStringMap(rawSource)
                    : Map.of();
            result.put(id, new CaseMeta(dir.getFileName().toString(), str(json.get("originalIntent")),
                    json.get("dataModel"), str(json.get("rootCause")), source, dsl));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toStringMap(Map<?, ?> raw) {
        return (Map<String, Object>) raw;
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static String resource(String name) throws IOException {
        try (InputStream stream = RepairBenchReport.class.getResourceAsStream(name)) {
            if (stream == null) {
                throw new IOException("missing report resource: " + name);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String escapeForScript(String json) {
        return json.replace("&", "\\u0026").replace("<", "\\u003c").replace(">", "\\u003e").replace("\u2028", "\\u2028")
                .replace("\u2029", "\\u2029");
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'",
                "&#39;");
    }

    private static String clean(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || "null".equals(trimmed) || "none".equals(trimmed)) {
            return fallback;
        }
        return trimmed;
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String normText(Object value) {
        String text = str(value).replace("\r\n", "\n").replace('\r', '\n');
        int end = text.length();
        while (end > 0 && text.charAt(end - 1) == '\n') {
            end--;
        }
        return text.substring(0, end);
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item != null) {
                result.add(String.valueOf(item));
            }
        }
        return result;
    }
}
