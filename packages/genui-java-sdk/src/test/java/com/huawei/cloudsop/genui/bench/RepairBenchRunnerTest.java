/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.bench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.bench.RepairCasePack.RepairCase;
import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransport;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransportException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link RepairBenchRunner} 全批流程（假 transport）：复现判定、repaired / still-invalid / transport-error / not-reproduced 状态映射、逐
 * attempt codes、主副口径与 issueBuckets 算术（含 transport-error 剔除分母、 分母 0 → rate null）、内部 issues.json 透传。
 *
 * @since 2026
 */
class RepairBenchRunnerTest {

    private static final String CONTRACT_JSON = "{\"contractVersion\":\"v1\",\"root\":\"Stack\",\"components\":{"
            + "\"Stack\":{\"description\":\"Flex container\",\"propsSchema\":{\"type\":\"object\",\"properties\":{"
            + "\"children\":{\"type\":\"array\"}},\"required\":[\"children\"]}},"
            + "\"TextContent\":{\"description\":\"Text block\",\"propsSchema\":{\"type\":\"object\",\"properties\":{"
            + "\"text\":{\"type\":\"string\"}},\"required\":[\"text\"]}}}}";

    private static final String VALID_DSL = "root = Stack([TextContent(\"ok\")])";

    /** 在测试契约中不存在的组件 → 复验必为 unknown-component。 */
    private static final String STILL_BAD_DSL = "root = StillBad()";

    /**
     * 一个会触发"修复后仍无效"的 DSL（测试契约里不存在 {@code StillBad} 组件）， 但它依然是合法的 openui-lang 语法 —— 不会触发 root-mismatch 之外的解析级错误。
     * 注意：不带参数（避免括号引号在 JSON 转义/反转义中变形）。
     */
    private static final String STILL_BAD_DSL_2 = "root = StillBad2()";

    /**
     * 一个会触发"修复后仍无效"的 DSL，第三次尝试用。
     */
    private static final String STILL_BAD_DSL_3 = "root = StillBad3()";

    /** 修复第二轮用的"仍无效但根名正确"的 DSL（根名与契约一致，不会触发 root-not-renderable）。 */
    private static final String STILL_BAD_ROOTED_DSL = "root = Stack([Bogus()])";

    @TempDir
    Path tempDir;

    // ── 假 transport ─────────────────────────────────────────────────────────

    private static String syncResponse(String dsl) {
        return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\""
                + dsl.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"}}]}";
    }

    /** 按 DSL 脚本化的假 transport：从请求体里提取修复提示词携带的无效 DSL，反查脚本队列。 */
    private static final class ScriptedTransport implements LlmTransport {
        private final Map<String, Deque<String>> scriptByDsl = new LinkedHashMap<>();

        private final int[] postCount = {0};

        ScriptedTransport answer(String invalidDsl, String... responses) {
            scriptByDsl.put(invalidDsl, new ArrayDeque<>(List.of(responses)));
            return this;
        }

        int postCount() {
            return postCount[0];
        }

        @Override
        public String post(String body) throws LlmTransportException {
            postCount[0]++;
            Map<String, Object> parsed = Json.asObject(Json.parse(body), "request body");
            List<Object> messages = Json.asList(parsed.get("messages"), "messages");
            StringBuilder allContent = new StringBuilder();
            for (Object message : messages) {
                Map<String, Object> item = Json.asObject(message, "message");
                Object content = item.get("content");
                if (content != null) {
                    allContent.append(content).append('\n');
                }
            }
            String haystack = allContent.toString();
            for (Map.Entry<String, Deque<String>> entry : scriptByDsl.entrySet()) {
                // ReaskPromptBuilder 会把 DSL 原样（含换行/引号）拼进提示词，
                // 但 ChatCompletionRequest.toJson 已把换行转义为 \n、引号转义为 \"，
                // 因此匹配前先把 key 转成同样的 JSON 转义形式。
                String jsonEscapedKey = entry.getKey().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
                if (haystack.contains(jsonEscapedKey) || haystack.contains(entry.getKey())) {
                    Deque<String> queue = entry.getValue();
                    if (queue.isEmpty()) {
                        throw new LlmTransportException("no scripted response for " + entry.getKey());
                    }
                    String next = queue.poll();
                    if ("<boom>".equals(next)) {
                        throw new LlmTransportException("scripted boom");
                    }
                    return syncResponse(next);
                }
            }
            throw new LlmTransportException("no script matches body");
        }

        @Override
        public InputStream postStream(String body) {
            return new ByteArrayInputStream(new byte[0]);
        }
    }

    // ── 跑批辅助 ─────────────────────────────────────────────────────────────

    private static final class BatchResult {
        final List<Map<String, Object>> caseLines;

        final Map<String, Object> summary;

        BatchResult(List<Map<String, Object>> caseLines, Map<String, Object> summary) {
            this.caseLines = caseLines;
            this.summary = summary;
        }

        Map<String, Object> caseLine(String caseId) {
            return caseLines.stream().filter(line -> caseId.equals(line.get("caseId"))).findFirst().orElseThrow();
        }
    }

    private Path packDir() throws Exception {
        Files.writeString(tempDir.resolve("contract.json"), CONTRACT_JSON, StandardCharsets.UTF_8);
        return tempDir;
    }

    private void writeCase(String dirName, String id, String dsl, String extraCaseJson) throws Exception {
        Path dir = Files.createDirectories(tempDir.resolve(dirName));
        String caseJson = "{\"id\":\"" + id + "\"" + (extraCaseJson == null ? "" : "," + extraCaseJson) + "}";
        Files.writeString(dir.resolve("case.json"), caseJson, StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("dsl.txt"), dsl, StandardCharsets.UTF_8);
    }

    private BatchResult run(ScriptedTransport transport, int concurrency) throws Exception {
        List<RepairCase> cases = RepairCasePack.load(packDir(), null);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream print = new PrintStream(buffer, true, StandardCharsets.UTF_8);
        new RepairBenchRunner(transport, "test-model", 3, Duration.ZERO, print).runAll(cases, concurrency);
        print.flush();

        List<Map<String, Object>> caseLines = new ArrayList<>();
        Map<String, Object> summary = null;
        for (String line : buffer.toString(StandardCharsets.UTF_8).split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            Map<String, Object> parsed = Json.asObject(Json.parse(line), "output line");
            if ("summary".equals(parsed.get("type"))) {
                summary = parsed;
            } else {
                caseLines.add(parsed);
            }
        }
        assertNotNull(summary, "summary line must be emitted");
        return new BatchResult(caseLines, summary);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Map<String, Object> parent, String key) {
        return (Map<String, Object>) parent.get(key);
    }

    @SuppressWarnings("unchecked")
    private static List<String> strings(Map<String, Object> parent, String key) {
        return ((List<Object>) parent.get(key)).stream().map(String::valueOf).toList();
    }

    private static long longOf(Object value) {
        return ((Number) value).longValue();
    }

    // ── 单案例流程 ───────────────────────────────────────────────────────────

    @Test
    void repairedOnSecondAttempt() throws Exception {
        String invalidDsl = "root = Bogus(\"x\")";
        writeCase("case-a", "a", invalidDsl, null);
        // 第一次修复返回根名正确但仍含未知组件的 DSL（触发第二轮），第二次返回有效 DSL。
        ScriptedTransport transport = new ScriptedTransport().answer(invalidDsl, STILL_BAD_ROOTED_DSL)
                .answer(STILL_BAD_ROOTED_DSL, VALID_DSL);

        BatchResult result = run(transport, 2);
        Map<String, Object> line = result.caseLine("a");
        assertEquals("repaired", line.get("status"));
        assertEquals(true, line.get("success"));
        assertEquals(true, line.get("reproduced"));
        // Bogus 是 unknown-component（根名 root 与契约 root=Stack 不一致，但 actionable 视图只保留根因）。
        assertEquals(List.of("unknown-component"), strings(line, "initialIssueCodes"));
        assertEquals(2L, longOf(line.get("attemptCount")));

        List<Object> attempts = (List<Object>) line.get("attempts");
        assertEquals(2, attempts.size());
        Map<String, Object> first = (Map<String, Object>) attempts.get(0);
        assertEquals(1L, longOf(first.get("attempt")));
        assertEquals(false, first.get("valid"));
        assertEquals(List.of("unknown-component"), strings(first, "issueCodes"));
        Map<String, Object> second = (Map<String, Object>) attempts.get(1);
        assertEquals(true, second.get("valid"));
        assertEquals(List.of(), strings(second, "issueCodes"));
        assertEquals(VALID_DSL, line.get("finalDsl"));

        Map<String, Object> mainRate = map(result.summary, "mainRate");
        assertEquals(1L, longOf(mainRate.get("success")));
        assertEquals(1L, longOf(mainRate.get("denominator")));
        assertEquals(1.0, ((Number) mainRate.get("rate")).doubleValue(), 1e-9);

        Map<String, Object> bucket = map(map(result.summary, "issueBuckets"), "unknown-component");
        assertEquals(1L, longOf(bucket.get("cases")));
        assertEquals(1L, longOf(bucket.get("repaired")));
    }

    @Test
    void stillInvalidExhaustsAttempts() throws Exception {
        String invalidDsl = "root = Bogus(\"x\")";
        writeCase("case-a", "a", invalidDsl, null);
        // 每一轮修复都返回另一个仍无效的 DSL（逐轮换 key，模拟模型修不动）。
        ScriptedTransport transport = new ScriptedTransport().answer(invalidDsl, STILL_BAD_DSL)
                .answer(STILL_BAD_DSL, STILL_BAD_DSL_2).answer(STILL_BAD_DSL_2, STILL_BAD_DSL_3);

        BatchResult result = run(transport, 1);
        Map<String, Object> line = result.caseLine("a");
        assertEquals("still-invalid", line.get("status"));
        assertEquals(false, line.get("success"));
        assertEquals(3L, longOf(line.get("attemptCount")), "maxAttempts=3 default budget consumed");

        Map<String, Object> mainRate = map(result.summary, "mainRate");
        assertEquals(0L, longOf(mainRate.get("success")));
        assertEquals(1L, longOf(mainRate.get("denominator")));
        assertEquals(0.0, ((Number) mainRate.get("rate")).doubleValue(), 1e-9);
    }

    @Test
    void transportErrorExcludedFromMainDenominator() throws Exception {
        String invalidDsl = "root = Bogus(\"x\")";
        writeCase("case-a", "a", invalidDsl, null);
        ScriptedTransport transport = new ScriptedTransport().answer(invalidDsl, "<boom>");

        BatchResult result = run(transport, 1);
        Map<String, Object> line = result.caseLine("a");
        assertEquals("transport-error", line.get("status"));
        assertEquals(false, line.get("success"));

        // 传输失败的尝试：issueCodes 为 null、valid 为 false。
        List<Object> attempts = (List<Object>) line.get("attempts");
        assertEquals(1, attempts.size());
        Map<String, Object> first = (Map<String, Object>) attempts.get(0);
        assertEquals(false, first.get("valid"));
        assertNull(first.get("issueCodes"), "transport-failed attempt carries null issueCodes");

        // 主口径剔除 transport-error：分母为 0 → rate null；副口径分母含全部案例。
        Map<String, Object> mainRate = map(result.summary, "mainRate");
        assertEquals(0L, longOf(mainRate.get("denominator")));
        assertNull(mainRate.get("rate"), "zero main denominator → null rate");
        Map<String, Object> secondaryRate = map(result.summary, "secondaryRate");
        assertEquals(1L, longOf(secondaryRate.get("denominator")));
        assertEquals(0.0, ((Number) secondaryRate.get("rate")).doubleValue(), 1e-9);

        Map<String, Object> bucket = map(map(result.summary, "issueBuckets"), "unknown-component");
        assertEquals(1L, longOf(bucket.get("transport-error")));
    }

    @Test
    void notReproducedWhenInitialValidationPasses() throws Exception {
        writeCase("case-a", "a", VALID_DSL, null);
        ScriptedTransport transport = new ScriptedTransport();

        BatchResult result = run(transport, 1);
        Map<String, Object> line = result.caseLine("a");
        assertEquals("not-reproduced", line.get("status"));
        assertEquals(false, line.get("success"));
        assertEquals(false, line.get("reproduced"));
        assertEquals(List.of(), strings(line, "initialIssueCodes"));
        assertTrue(!line.containsKey("attempts"), "not-reproduced rows carry no attempts");
        assertEquals(0, transport.postCount(), "no repair LLM call for not-reproduced cases");

        // not-reproduced 不计入 issueBuckets，主口径分母为 0 → null。
        assertEquals(Map.of(), map(result.summary, "issueBuckets"));
        Map<String, Object> mainRate = map(result.summary, "mainRate");
        assertEquals(0L, longOf(mainRate.get("denominator")));
        assertNull(mainRate.get("rate"));
    }

    @Test
    void validationOnlyEmitsInvalidWithoutRepair() throws Exception {
        String invalidDsl = "root = Bogus(\"x\")";
        writeCase("case-a", "a", invalidDsl, null);
        writeCase("case-b", "b", VALID_DSL, null);
        ScriptedTransport transport = new ScriptedTransport();

        List<RepairCase> cases = RepairCasePack.load(packDir(), null);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream print = new PrintStream(buffer, true, StandardCharsets.UTF_8);
        // repair=false：transport 传 null 也必须能跑（validation-only 不碰 transport）。
        new RepairBenchRunner(null, null, 3, Duration.ZERO, print, false).runAll(cases, 1);
        print.flush();

        List<Map<String, Object>> caseLines = new ArrayList<>();
        Map<String, Object> summary = null;
        for (String line : buffer.toString(StandardCharsets.UTF_8).split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            Map<String, Object> parsed = Json.asObject(Json.parse(line), "output line");
            if ("summary".equals(parsed.get("type"))) {
                summary = parsed;
            } else {
                caseLines.add(parsed);
            }
        }
        assertNotNull(summary, "summary line must be emitted");

        Map<String, Object> invalidLine = caseLines.stream().filter(l -> "a".equals(l.get("caseId"))).findFirst()
                .orElseThrow();
        assertEquals("invalid", invalidLine.get("status"));
        assertEquals(false, invalidLine.get("success"));
        assertEquals(true, invalidLine.get("reproduced"));
        assertEquals(List.of("unknown-component"), strings(invalidLine, "initialIssueCodes"));
        assertTrue(!invalidLine.containsKey("attempts"), "validation-only rows carry no attempts");
        assertTrue(!invalidLine.containsKey("finalDsl"), "validation-only rows carry no finalDsl");

        Map<String, Object> validLine = caseLines.stream().filter(l -> "b".equals(l.get("caseId"))).findFirst()
                .orElseThrow();
        assertEquals("not-reproduced", validLine.get("status"));

        assertEquals(0, transport.postCount(), "validation-only must make zero LLM calls");

        Map<String, Object> byStatus = map(summary, "byStatus");
        assertEquals(1L, longOf(byStatus.get("invalid")));
        assertEquals(1L, longOf(byStatus.get("not-reproduced")));
        Map<String, Object> mainRate = map(summary, "mainRate");
        assertEquals(0L, longOf(mainRate.get("denominator")));
        assertNull(mainRate.get("rate"), "no repaired/still-invalid in validation-only → null main rate");
        Map<String, Object> bucket = map(map(summary, "issueBuckets"), "unknown-component");
        assertEquals(1L, longOf(bucket.get("cases")));
        assertEquals(1L, longOf(bucket.get("invalid")));
    }

    @Test
    void internalIssueCodesPassthrough() throws Exception {
        String invalidDsl = "root = Bogus(\"x\")";
        Path dir = Files.createDirectories(tempDir.resolve("case-a"));
        Files.writeString(dir.resolve("case.json"), "{\"id\":\"a\"}", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("dsl.txt"), invalidDsl, StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("issues.json"), "[{\"code\":\"internal-code-1\"}]", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("contract.json"), CONTRACT_JSON, StandardCharsets.UTF_8);
        ScriptedTransport transport = new ScriptedTransport().answer(invalidDsl, VALID_DSL);

        BatchResult result = run(transport, 1);
        assertEquals(List.of("internal-code-1"), strings(result.caseLine("a"), "internalIssueCodes"));
    }

    @Test
    void summaryAggregatesMixedOutcomes() throws Exception {
        String badDslA = "root = Bogus(\"a\")";
        String badDslB = "root = Bogus(\"b\")";
        writeCase("case-a", "a", badDslA, null);
        writeCase("case-b", "b", badDslB, null);
        writeCase("case-c", "c", VALID_DSL, null); // not-reproduced
        writeCase("case-d", "d", badDslA, null); // 与 a 同一 DSL → 共享脚本队列
        // a 消费第一条（修复成功），d 消费第二条（"<boom>" → transport-error）。
        // badDslB 逐轮换 key，三次都返回仍无效 DSL → still-invalid。
        ScriptedTransport transport = new ScriptedTransport().answer(badDslA, VALID_DSL, "<boom>")
                .answer(badDslB, STILL_BAD_DSL).answer(STILL_BAD_DSL, STILL_BAD_DSL_2)
                .answer(STILL_BAD_DSL_2, STILL_BAD_DSL_3);

        BatchResult result = run(transport, 2);

        // 并发下完成序不确定：只按状态聚合，不按 caseId 断言归属。
        Map<String, Object> byStatus = map(result.summary, "byStatus");
        long total = longOf(result.summary.get("total"));
        assertEquals(4L, total);
        long reproducedCount = longOf(byStatus.get("repaired")) + longOf(byStatus.get("still-invalid"))
                + longOf(byStatus.get("transport-error"));
        assertEquals(1L, longOf(byStatus.get("repaired")));
        assertEquals(1L, longOf(byStatus.get("still-invalid")));
        assertEquals(1L, longOf(byStatus.get("transport-error")));
        assertEquals(1L, longOf(byStatus.get("not-reproduced")));
        assertEquals(3L, reproducedCount);

        // 主口径 = repaired / (repaired + still-invalid) = 1/2；副口径 = 1/4。
        Map<String, Object> mainRate = map(result.summary, "mainRate");
        assertEquals(2L, longOf(mainRate.get("denominator")));
        assertEquals(0.5, ((Number) mainRate.get("rate")).doubleValue(), 1e-9);
        Map<String, Object> secondaryRate = map(result.summary, "secondaryRate");
        assertEquals(4L, longOf(secondaryRate.get("denominator")));
        assertEquals(0.25, ((Number) secondaryRate.get("rate")).doubleValue(), 1e-9);

        // issueBuckets：unknown-component 桶含 3 个 reproduced 案例（a/b/d），outcome 分布 1/1/1。
        Map<String, Object> bucket = map(map(result.summary, "issueBuckets"), "unknown-component");
        assertEquals(3L, longOf(bucket.get("cases")));
        assertEquals(1L, longOf(bucket.get("repaired")));
        assertEquals(1L, longOf(bucket.get("still-invalid")));
        assertEquals(1L, longOf(bucket.get("transport-error")));
    }

    @Test
    void summaryIncludesRunParameters() throws Exception {
        writeCase("case-a", "a", VALID_DSL, null);
        ScriptedTransport transport = new ScriptedTransport();
        BatchResult result = run(transport, 2);
        assertEquals("test-model", result.summary.get("model"));
        assertEquals(3L, longOf(result.summary.get("maxAttempts")));
        assertEquals(2L, longOf(result.summary.get("concurrency")));
        assertEquals(0L, longOf(result.summary.get("timeoutMinutes")));
    }

    @Test
    void originalIntentFlowsIntoRepairBody() throws Exception {
        String invalidDsl = "root = Bogus(\"x\")";
        writeCase("case-a", "a", invalidDsl, "\"originalIntent\":\"show the dashboard\"");
        // 用一个捕获型 transport 验证请求体里带着原始意图与温度 0。
        List<String> bodies = new ArrayList<>();
        LlmTransport capturing = new LlmTransport() {
            @Override
            public String post(String body) {
                bodies.add(body);
                return syncResponse(VALID_DSL);
            }

            @Override
            public InputStream postStream(String body) {
                return new ByteArrayInputStream(new byte[0]);
            }
        };
        List<RepairCase> cases = RepairCasePack.load(packDir(), null);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        new RepairBenchRunner(capturing, "test-model", 3, Duration.ZERO,
                new PrintStream(buffer, true, StandardCharsets.UTF_8)).runAll(cases, 1);

        assertEquals(1, bodies.size());
        assertTrue(bodies.get(0).contains("show the dashboard"), "original intent must reach the repair prompt");
        assertTrue(bodies.get(0).contains("\"temperature\":0"), "repair discipline: temperature 0");
        assertTrue(bodies.get(0).contains("\"enable_thinking\":false"), "repair discipline: thinking off");
        assertTrue(bodies.get(0).contains("\"model\":\"test-model\""));
    }
}
