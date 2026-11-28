/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.eval.report;

import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.eval.dataset.CorpusLoaders.LoadedRepairCase;

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

/**
 * 自包含离线 HTML 报告生成器。修复基准沿用既有 {@code /bench-report/report.{html,css,js}} 模板， 端到端 A/B
 * 和人工标注语料验证则输出简洁自包含 HTML 汇总页（零外链，离线直接打开）。
 *
 * @since 2026
 */
public final class OfflineHtmlReporter {
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Pattern ISSUE =
            Pattern.compile(
                    "^ValidationIssue\\[code=(.*?), severity=(.*?), source=(.*?), message=(.*?), statementId=(.*?), "
                            + "component=(.*?), path=(.*?), line=(-?\\d+), column=(-?\\d+), hint=(.*?), retryable=(.*?)\\]$",
                    Pattern.DOTALL);
    private static final Map<String, Integer> STATUS_RANK =
            Map.of(
                    "error",
                    0,
                    "timeout",
                    1,
                    "transport-error",
                    2,
                    "still-invalid",
                    3,
                    "invalid",
                    4,
                    "not-reproduced",
                    5,
                    "repaired",
                    6);
    private static final int DEFAULT_RANK = 9;
    private static final int SOURCE_GROUP = 3;
    private static final int MESSAGE_GROUP = 4;
    private static final int STATEMENT_ID_GROUP = 5;
    private static final int COMPONENT_GROUP = 6;
    private static final int PATH_GROUP = 7;
    private static final int LINE_GROUP = 8;
    private static final int COLUMN_GROUP = 9;
    private static final int HINT_GROUP = 10;

    private OfflineHtmlReporter() {}

    /**
     * 渲染修复基准自包含 HTML 报告（与原 {@code RepairBenchReport} 视图完全兼容）。
     *
     * @param jsonlLines 本次运行全部 JSONL 行（含末尾 summary）
     * @param loadedCases 已加载的修复案例列表
     * @param outHtml 输出 HTML 文件路径
     * @param sourceLabel 数据源展示标签
     * @throws IOException 写入失败
     */
    public static void writeRepairReport(
            List<String> jsonlLines,
            List<LoadedRepairCase> loadedCases,
            Path outHtml,
            String sourceLabel)
            throws IOException {
        Objects.requireNonNull(jsonlLines, "jsonlLines");
        Objects.requireNonNull(outHtml, "outHtml");
        ParsedJsonl parsed = parseJsonl(jsonlLines);
        Map<String, LoadedRepairCase> byId = indexCases(loadedCases);
        List<Map<String, Object>> cases = buildSortedCases(parsed.caseLines(), byId);

        LinkedHashMap<String, Object> meta = new LinkedHashMap<>(parsed.summary());
        meta.remove("type");
        meta.put("sourceFile", sourceLabel);

        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("meta", meta);
        payload.put("generatedAt", LocalDateTime.now().format(TIMESTAMP));
        payload.put("sourcePath", sourceLabel);
        payload.put("cases", cases);

        String model = str(parsed.summary().get("model"));
        String html =
                resource("/bench-report/report.html")
                        .replace("__MODEL__", escapeHtml(model == null ? "validation-only" : model))
                        .replace("__CSS__", resource("/bench-report/report.css"))
                        .replace("__DATA__", escapeForScript(Json.stringify(payload)))
                        .replace("__JS__", resource("/bench-report/report.js"));
        writeText(outHtml, html);
    }

    private record ParsedJsonl(Map<String, Object> summary, List<Map<String, Object>> caseLines) {}

    private static ParsedJsonl parseJsonl(List<String> jsonlLines) throws IOException {
        Map<String, Object> summary = null;
        List<Map<String, Object>> caseLines = new ArrayList<>();
        for (String line : jsonlLines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            Map<String, Object> parsed = Json.asObject(Json.parse(line), "JSONL line");
            if ("summary".equals(parsed.get("type"))) {
                summary = parsed;
            } else if ("case".equals(parsed.get("type"))) {
                caseLines.add(parsed);
            }
        }
        if (summary == null) {
            throw new IOException("No summary line found in JSONL output");
        }
        return new ParsedJsonl(summary, caseLines);
    }

    private static Map<String, LoadedRepairCase> indexCases(List<LoadedRepairCase> loadedCases) {
        Map<String, LoadedRepairCase> byId = new LinkedHashMap<>();
        if (loadedCases != null) {
            for (LoadedRepairCase item : loadedCases) {
                byId.put(item.id(), item);
            }
        }
        return byId;
    }

    private static List<Map<String, Object>> buildSortedCases(
            List<Map<String, Object>> caseLines, Map<String, LoadedRepairCase> byId) {
        List<Map<String, Object>> cases = new ArrayList<>();
        for (Map<String, Object> line : caseLines) {
            cases.add(toCasePayload(line, byId.get(str(line.get("caseId")))));
        }
        cases.sort(
                Comparator.comparingInt(
                                (Map<String, Object> item) ->
                                        STATUS_RANK.getOrDefault(
                                                str(item.get("status")), DEFAULT_RANK))
                        .thenComparing(item -> str(item.get("id"))));
        return cases;
    }

    private static final String TABLE_TEMPLATE =
            "<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\">"
                    + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                    + "<title>GenUI Evaluation Report</title><style>"
                    + "body{font-family:system-ui,-apple-system,sans-serif;margin:24px;color:#111;background:#fafafa;}"
                    + "h1{font-size:20px;margin:0 0 12px;}.cards{display:flex;flex-wrap:wrap;gap:12px;margin-bottom:20px;}"
                    + ".card{background:#fff;border:1px solid #e5e7eb;border-radius:8px;padding:10px 14px;min-width:140px;}"
                    + ".k{font-size:12px;color:#666;}.v{font-size:18px;font-weight:600;margin-top:4px;}"
                    + "table{width:100%;border-collapse:collapse;background:#fff;border:1px solid #e5e7eb;font-size:13px;}"
                    + "th,td{border:1px solid #e5e7eb;padding:8px 10px;text-align:left;vertical-align:top;}"
                    + "th{background:#f3f4f6;}</style></head><body>"
                    + "<h1 id=\"t\"></h1><div id=\"cards\" class=\"cards\"></div><div id=\"tbl\"></div>"
                    + "<script>const DATA=__DATA__;"
                    + "document.title=DATA.title;document.getElementById('t').textContent=DATA.title;"
                    + "const c=document.getElementById('cards');"
                    + "Object.entries(DATA.summary||{}).forEach(([k,v])=>{"
                    + "const d=document.createElement('div');d.className='card';"
                    + "const dk=document.createElement('div');dk.className='k';dk.textContent=k;"
                    + "const dv=document.createElement('div');dv.className='v';dv.textContent=typeof v==='object'?JSON.stringify(v):String(v);"
                    + "d.append(dk,dv);c.append(d);});"
                    + "const rows=DATA.rows||[];if(rows.length){const cols=[...new Set(rows.flatMap(r=>Object.keys(r)))];"
                    + "const t=document.createElement('table');const th=document.createElement('thead');const tr=document.createElement('tr');"
                    + "cols.forEach(col=>{const e=document.createElement('th');e.textContent=col;tr.append(e);});th.append(tr);t.append(th);"
                    + "const tb=document.createElement('tbody');rows.forEach(r=>{const rr=document.createElement('tr');"
                    + "cols.forEach(col=>{const td=document.createElement('td');const v=r[col];"
                    + "td.textContent=v==null?'':(typeof v==='object'?JSON.stringify(v):String(v));rr.append(td);});tb.append(rr);});"
                    + "t.append(tb);document.getElementById('tbl').append(t);}</script></body></html>";

    /**
     * 渲染通用的自包含表格汇总 HTML（用于 A/B 生成对照和人工标注语料验证），数据通过安全 JSON 内嵌并由 DOM {@code textContent} 渲染。
     *
     * @param title 页面标题
     * @param summaryMetrics 顶部汇总指标键值
     * @param rows 逐案例行数据
     * @param outHtml 输出路径
     * @throws IOException 写入失败
     */
    public static void writeTableReport(
            String title,
            Map<String, ?> summaryMetrics,
            List<Map<String, Object>> rows,
            Path outHtml)
            throws IOException {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", title == null ? "GenUI Evaluation Report" : title);
        payload.put("summary", summaryMetrics == null ? Map.of() : summaryMetrics);
        payload.put("rows", rows == null ? List.of() : rows);
        String html = TABLE_TEMPLATE.replace("__DATA__", escapeForScript(Json.stringify(payload)));
        writeText(outHtml, html);
    }

    private static Map<String, Object> toCasePayload(
            Map<String, Object> line, LoadedRepairCase meta) {
        String id = str(line.get("caseId"));
        String status = str(line.get("status"));
        LinkedHashMap<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("dir", meta == null ? "(unknown)" : meta.dirName());
        item.put(
                "intent",
                meta == null || meta.originalIntent() == null ? "" : meta.originalIntent());
        item.put("dataModel", meta == null ? null : meta.dataModel());
        item.put("rootCause", meta == null || meta.rootCause() == null ? "" : meta.rootCause());
        item.put("caseSource", Map.of());
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
        item.put(
                "capabilityNoticePresent",
                Boolean.TRUE.equals(line.get("capabilityNoticePresent")));
        item.put(
                "remainingCapabilityGapAliases",
                stringList(line.get("remainingCapabilityGapAliases")));
        item.put(
                "consecutiveUnmatchedUnknownComponents",
                stringList(line.get("consecutiveUnmatchedUnknownComponents")));
        item.put("error", line.get("error"));
        item.put("before", meta == null ? "" : meta.dsl());
        putFinalDsl(item, line, status);
        item.put("rounds", normalizeRounds(line.get("rounds")));
        return item;
    }

    private static void putFinalDsl(
            Map<String, Object> item, Map<String, Object> line, String status) {
        String output = normText(line.get("outputDsl"));
        String lastValidated = normText(line.get("finalDsl"));
        if ("repaired".equals(status) && !output.isEmpty()) {
            item.put("finalDsl", output);
            item.put("finalSource", "outputDsl");
        } else if (!lastValidated.isEmpty()) {
            item.put("finalDsl", lastValidated);
            item.put("finalSource", "finalDsl");
        } else {
            item.put("finalDsl", "");
            item.put("finalSource", "none");
        }
        item.put("fallbackHidden", !"repaired".equals(status) && !output.isEmpty());
    }

    private static List<Object> normalizeRounds(Object raw) {
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
            List<Map<String, Object>> issues = normalizeIssues(round.get("issues"));
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

    private static List<Map<String, Object>> normalizeIssues(Object raw) {
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
        Matcher matcher = ISSUE.matcher(raw == null ? "" : raw.trim());
        LinkedHashMap<String, Object> issue = new LinkedHashMap<>();
        if (matcher.matches()) {
            issue.put("code", clean(matcher.group(1), "unknown"));
            issue.put("severity", clean(matcher.group(2), ""));
            issue.put("source", clean(matcher.group(SOURCE_GROUP), ""));
            issue.put("message", clean(matcher.group(MESSAGE_GROUP), ""));
            issue.put("statementId", clean(matcher.group(STATEMENT_ID_GROUP), ""));
            issue.put("component", clean(matcher.group(COMPONENT_GROUP), ""));
            issue.put("path", clean(matcher.group(PATH_GROUP), ""));
            issue.put("line", Integer.parseInt(matcher.group(LINE_GROUP)));
            issue.put("column", Integer.parseInt(matcher.group(COLUMN_GROUP)));
            issue.put("hint", clean(matcher.group(HINT_GROUP), ""));
            return issue;
        }
        issue.put("code", "unknown");
        issue.put("message", raw == null ? "" : raw);
        return issue;
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = OfflineHtmlReporter.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("Missing report template resource: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void writeText(Path target, String content) throws IOException {
        Path absolute = target.toAbsolutePath();
        Path parent = absolute.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(absolute, content, StandardCharsets.UTF_8);
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String escapeForScript(String json) {
        return json.replace("</", "<\\/")
                .replace("<!--", "<\\!--")
                .replace("\u2028", "\\u2028")
                .replace("\u2029", "\\u2029");
    }

    private static String clean(String value, String fallback) {
        return value == null || "null".equals(value) ? fallback : value;
    }

    private static String normText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static List<String> stringList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item != null) {
                out.add(String.valueOf(item));
            }
        }
        return out;
    }
}
