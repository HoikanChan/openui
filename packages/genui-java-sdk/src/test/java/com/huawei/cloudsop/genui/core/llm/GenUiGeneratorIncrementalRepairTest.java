/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.core.llm.stream.SseFrames;
import com.huawei.cloudsop.genui.core.llm.transport.InMemoryLlmTransport;
import com.huawei.cloudsop.genui.core.llm.transport.LlmCall;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransport;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransportException;
import com.huawei.cloudsop.genui.core.validation.ValidationStatus;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * #58 增量修复:通过公开入口 {@link GenUiGenerator#generate} / {@link #generateStream} 验证。
 *
 * <p>
 * 首轮输出结束后才校验;失败时模型只输出覆盖/新增语句,片段实时转发;后端合并当前程序后再检查;所有轮共享同一截止时间; 失败/超时保留完整轮与中断轮记录。
 */
class GenUiGeneratorIncrementalRepairTest {

    private static UiGenerationRequest req() {
        return UiGenerationRequest.builder().userInput("build a page").build();
    }

    private static String syncResponse(String dsl) {
        return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":" + Json.stringify(dsl) + "}}]}";
    }

    private static String frame(String content) {
        return "data: {\"choices\":[{\"delta\":{\"content\":" + Json.stringify(content) + "}}]}\n\n";
    }

    private static List<String> dslContents(List<RenderStreamEnvelope> envelopes) {
        return envelopes.stream().filter(e -> RenderStreamEnvelope.TYPE_DSL.equals(e.type()))
                .map(e -> String.valueOf(e.content())).toList();
    }

    private static void assertSeqStrictlyIncreasing(List<RenderStreamEnvelope> envelopes) {
        for (int i = 1; i < envelopes.size(); i++) {
            assertTrue(envelopes.get(i).seq() > envelopes.get(i - 1).seq(), "seq must strictly increase at index " + i);
        }
    }

    // ── 同步:默认配置启用增量修复 ─────────────────────────────────────────

    @Test
    void syncFirstRoundPassReportsCheckPassed() {
        ScriptedTransport transport = ScriptedTransport.sync(syncResponse("root = Stack([])"));

        GenUiGenerationResult result = GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), transport)
                .generate(req());

        assertEquals(ValidationStatus.VALID, result.validationStatus());
        assertEquals(GenerationEndReason.CHECK_PASSED, result.endReason(), "a validated first round is CHECK_PASSED");
        assertEquals(1, transport.postCount());
    }

    @Test
    void syncRepairsWithPatchOnlyAndKeepsUnrelatedDefinitions() {
        ScriptedTransport transport = ScriptedTransport.sync(
                syncResponse("root = Bogus(\"x\")\nkeep = TextContent(\"k\")"), syncResponse("root = Stack([keep])"));

        GenUiGenerationResult result = GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), transport)
                .generate(req());

        assertEquals(ValidationStatus.VALID, result.validationStatus());
        assertEquals(GenerationEndReason.CHECK_PASSED, result.endReason(), "a successful repair round is CHECK_PASSED");
        assertEquals("root = Stack([keep])\nkeep = TextContent(\"k\")", result.dsl(),
                "only the patched statement is replaced; unrelated definitions survive");
        assertEquals(2, transport.postCount(), "one initial round + one incremental repair round");
        assertEquals(2, result.rounds().size());
        assertTrue(transport.bodies.get(1).contains("[REPAIR:INCREMENTAL]"),
                "repair request carries the incremental repair prompt");
        assertTrue(transport.bodies.get(1).contains("root = Bogus"),
                "repair request carries the current effective program");
    }

    @Test
    void syncKeepsNewHelperDefinitionFromPatch() {
        ScriptedTransport transport = ScriptedTransport.sync(syncResponse("root = Table(data.rows)"),
                syncResponse("rows = @Each(data.rows, row, TextContent(row.name))\nroot = Stack([rows])"));

        GenUiGenerationResult result = GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), transport).generate(
                UiGenerationRequest.builder().userInput("build").response(java.util.Map.of("rows", List.of())).build());

        assertEquals(ValidationStatus.VALID, result.validationStatus(), String.valueOf(result.validationResult()));
        assertTrue(result.dsl().contains("TextContent(row.name)"), "new helper definition is retained");
    }

    @Test
    void streamRepairsEachArrayShapeUpstreamAndPreservesOtherContent() throws IOException {
        String initial = "root = Stack([keep, table])\nkeep = TextContent(\"unchanged heading\")\n"
                + "rows = @Each(data.groups, group, group.rows)\n" + "table = Table([Col(\"Name\", \"name\")], rows)\n";
        String patch = "rows = @Flatten(@Each(data.groups, group, group.rows))\n";
        ScriptedTransport transport = ScriptedTransport.streams(frame(initial) + SseFrames.done(),
                frame(patch) + SseFrames.done());
        Map<String, Object> data = Map.of("groups", List.of(Map.of("rows", List.of(Map.of("name", "Alice"))),
                Map.of("rows", List.of(Map.of("name", "Bob")))));
        String originalData = Json.stringify(data);
        ArrayList<RenderStreamEnvelope> events = new ArrayList<>();
        GenUiGenerationResult result = GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), transport)
                .generateStream(UiGenerationRequest.builder().userInput("Show all names and keep the heading")
                        .response(data).build(), events::add);
        assertEquals(ValidationStatus.VALID, result.validationStatus(), String.valueOf(result.validationResult()));
        assertEquals(2, result.rounds().size());
        assertTrue(result.rounds().get(0).validationResult().issues().stream()
                .anyMatch(issue -> "table".equals(issue.statementId()) && issue.code().contains("row-shape")));
        assertTrue(transport.bodies.get(1).contains("@Each returns one result per input"),
                "shape guidance must identify upstream flattening");
        assertTrue(transport.bodies.get(1).contains("`table`: upstream/downstream related statements: `rows`, `root`"),
                "repair context includes upstream and users");
        assertEquals(List.of(initial, patch), dslContents(events), "upstream override is forwarded live");
        assertTrue(result.dsl().contains("keep = TextContent(\"unchanged heading\")"));
        assertTrue(result.dsl().contains("table = Table([Col(\"Name\", \"name\")], rows)"));
        assertEquals(originalData, Json.stringify(data), "Render Data Model is unchanged");
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/upstream-consumer-evidence.json"),
                Json.stringify(Map.of("chunks", dslContents(events), "roundDefinitions",
                        result.rounds().stream().map(round -> round.currentDefinitions()).toList(), "dsl", result.dsl(),
                        "dataModel", data)));
    }

    @Test
    void repairsObjectEntriesArgumentThroughPublicGeneration() throws IOException {
        String initial = "root = Stack([keep, table])\nkeep = TextContent(\"unchanged heading\")\n"
                + "entries = @ObjectEntries(data.rows)\n"
                + "table = Table([Col(\"Key\", \"key\"), Col(\"Value\", \"value\")], entries)\n";
        String patch = "entries = @ObjectEntries(data.counts)\n";
        ScriptedTransport transport = ScriptedTransport.streams(frame(initial) + SseFrames.done(),
                frame(patch) + SseFrames.done());
        Map<String, Object> data = Map.of("rows", List.of(Map.of("name", "Alice")), "counts",
                Map.of("switches", 2, "routers", 3));
        String original = Json.stringify(data);
        ArrayList<RenderStreamEnvelope> events = new ArrayList<>();
        GenUiGenerationResult result = GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), transport)
                .generateStream(
                        UiGenerationRequest.builder().userInput("Show counts by device kind").response(data).build(),
                        events::add);
        assertEquals(ValidationStatus.VALID, result.validationStatus(), String.valueOf(result.validationResult()));
        assertEquals(2, result.rounds().size());
        assertTrue(result.rounds().get(0).validationResult().issues().stream()
                .anyMatch(issue -> "entries".equals(issue.statementId())
                        && "builtin-argument-type-mismatch".equals(issue.code())));
        assertTrue(
                transport.bodies.get(1).contains("`entries`: upstream/downstream related statements: `table`, `root`"));
        assertEquals(original, Json.stringify(data));
        assertTrue(result.dsl().contains("keep = TextContent(\"unchanged heading\")"));
        Files.writeString(Path.of("target/object-entries-consumer-evidence.json"),
                Json.stringify(Map.of("chunks", dslContents(events), "roundDefinitions",
                        result.rounds().stream().map(round -> round.currentDefinitions()).toList(), "dsl", result.dsl(),
                        "dataModel", data)));
    }

    @Test
    void upstreamPatchRechecksOtherConsumersBeforeFinishing() {
        String initial = "root = Stack([keep, primary, secondary])\nkeep = TextContent(\"unchanged\")\n"
                + "rows = data.rows\nprimary = Table([Col(\"Label\", \"label\")], rows)\n"
                + "secondary = Table([Col(\"Name\", \"name\")], rows)";
        String upstreamPatch = "rows = @Each(data.rows, row, {label: row.name})";
        String consumerPatch = "secondary = Table([Col(\"Name\", \"label\")], rows)";
        ScriptedTransport transport = ScriptedTransport.sync(syncResponse(initial), syncResponse(upstreamPatch),
                syncResponse(consumerPatch));
        GenUiGenerationResult result = GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), transport)
                .generate(UiGenerationRequest.builder().userInput("Show rows in two views")
                        .response(Map.of("rows", List.of(Map.of("name", "Alice")))).build());
        assertEquals(ValidationStatus.VALID, result.validationStatus(), String.valueOf(result.validationResult()));
        assertEquals(3, result.rounds().size(), "valid patch cannot bypass full-program recheck");
        assertTrue(result.rounds().get(1).validationResult().issues().stream().anyMatch(
                issue -> "secondary".equals(issue.statementId()) && "type-table-column-missing".equals(issue.code())));
        assertTrue(transport.bodies.get(2).contains("secondary"));
        assertTrue(result.dsl().contains("keep = TextContent(\"unchanged\")"));
    }

    // ── #75: 能力边界越界（如 @GroupBy）通过增量覆盖补丁收敛为 capabilityNotice + Table ──

    @Test
    void syncCapabilityGapGroupByConvergesInOneIncrementalPatchWithCapabilityNoticeAndTable() {
        String initialDsl = "grouped = @GroupBy(data.boards, \"model\")\nroot = Stack([grouped])";
        String patchDsl = "grouped = @Sort(data.boards, \"model\", \"asc\")\n"
                + "capabilityNotice = TextCallout(\"info\", \"部分需求暂不支持\", \"暂不支持分组展示，以下按原始数据展示\")\n"
                + "table = Table([Col(\"型号\", \"model\"), Col(\"槽位\", \"slot\")], grouped)\n"
                + "root = Stack([capabilityNotice, table])";
        ScriptedTransport transport = ScriptedTransport.sync(syncResponse(initialDsl), syncResponse(patchDsl));

        UiGenerationRequest request = UiGenerationRequest.builder().userInput("按板卡型号分组展示")
                .response(Map.of("boards", List.of(Map.of("model", "MPU", "slot", "1")))).build();
        GenUiGenerationResult result = GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), transport)
                .generate(request);

        assertEquals(ValidationStatus.VALID, result.validationStatus(), String.valueOf(result.validationResult()));
        assertEquals(GenerationEndReason.CHECK_PASSED, result.endReason());
        assertEquals(2, transport.postCount());
        assertEquals(2, result.rounds().size());
        assertTrue(transport.bodies.get(1).contains("## Capability boundary (overrides the original request)"),
                "repair request must carry the capability boundary override section");
        assertTrue(transport.bodies.get(1).contains("## Original request (for context only)"),
                "repair request must mark Original request as for context only");
        assertFalse(result.dsl().contains("GroupBy"), "invalid @GroupBy definition must be overridden by last-wins");
        assertTrue(result.dsl().contains("capabilityNotice = TextCallout(\"info\", \"部分需求暂不支持\""),
                "converged DSL must include capabilityNotice");
        assertTrue(result.dsl().contains("root = Stack([capabilityNotice, table])"),
                "root must place capabilityNotice as its first child");
    }

    @Test
    void streamCapabilityGapGroupByConvergesInOneIncrementalPatchWithCapabilityNoticeAndTable() {
        String initialDsl = "grouped = @GroupBy(data.boards, \"model\")\nroot = Stack([grouped])\n";
        String patchDsl = "grouped = @Sort(data.boards, \"model\", \"asc\")\n"
                + "capabilityNotice = TextCallout(\"info\", \"部分需求暂不支持\", \"暂不支持分组展示，以下按原始数据展示\")\n"
                + "table = Table([Col(\"型号\", \"model\"), Col(\"槽位\", \"slot\")], grouped)\n"
                + "root = Stack([capabilityNotice, table])\n";
        ScriptedTransport transport = ScriptedTransport.streams(frame(initialDsl) + SseFrames.done(),
                frame(patchDsl) + SseFrames.done());
        ArrayList<RenderStreamEnvelope> envelopes = new ArrayList<>();

        UiGenerationRequest request = UiGenerationRequest.builder().userInput("按板卡型号分组展示")
                .response(Map.of("boards", List.of(Map.of("model", "MPU", "slot", "1")))).build();
        GenUiGenerationResult result = GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), transport)
                .generateStream(request, envelopes::add);

        assertEquals(ValidationStatus.VALID, result.validationStatus(), String.valueOf(result.validationResult()));
        assertEquals(GenerationEndReason.CHECK_PASSED, result.endReason());
        assertEquals(2, result.rounds().size());
        assertTrue(transport.bodies.get(1).contains("## Capability boundary (overrides the original request)"));
        assertTrue(transport.bodies.get(1).contains("## Original request (for context only)"));
        assertFalse(result.dsl().contains("GroupBy"));
        assertTrue(result.dsl().contains("capabilityNotice = TextCallout(\"info\", \"部分需求暂不支持\""));
        assertSeqStrictlyIncreasing(envelopes);
    }

    // ── 修复轮异常:空 content 不穿透,走 Markdown 降级并保留轮记录 ─────────────

    @Test
    void syncInitialEmptyContentHandsOffToMarkdown() {
        // The very first response has no usable content (refusal / tool call only); the Markdown handoff follows.
        ScriptedTransport transport = ScriptedTransport.sync(
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null}}]}",
                syncResponse("# Markdown fallback"));

        GenUiGenerationResult result = GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), transport)
                .generate(req());

        assertTrue(result.dsl().contains("MarkDownRenderer") && result.dsl().contains("# Markdown fallback"),
                "an empty initial response hands off to the Markdown wrapper instead of escaping; got: "
                        + result.dsl());
        assertEquals(GenerationEndReason.MODEL_REQUEST_FAILED, result.endReason());
        assertEquals(2, transport.postCount(), "initial + markdown; no repair round is possible");
        assertEquals(1, result.rounds().size(), "the interrupted initial request is recorded as an interrupted round");
        assertNull(result.rounds().get(0).validationResult(), "no verdict for an interrupted initial round");
        assertEquals(GenerationEndReason.MODEL_REQUEST_FAILED, result.rounds().get(0).endReason());
    }

    @Test
    void syncRepairEmptyContentHandsOffToMarkdownAndKeepsRounds() {
        // Round 1: INVALID DSL. Round 2 (repair): first choice has null content (e.g. tool-call only response).
        // Round 3 (Markdown handoff): plain markdown text.
        ScriptedTransport transport = ScriptedTransport.sync(syncResponse("root = Bogus(\"x\")"),
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null}}]}",
                syncResponse("# Markdown fallback"));

        GenUiGenerationResult result = GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), transport)
                .generate(req());

        assertEquals("root = Stack([MarkDownRenderer(\"# Markdown fallback\")])", result.dsl(),
                "empty repair content hands off to Markdown, not a throw");
        assertEquals(GenerationEndReason.MODEL_REQUEST_FAILED, result.endReason(),
                "the session terminates as MODEL_REQUEST_FAILED, not as a runtime exception");
        assertEquals(3, transport.postCount(), "initial + repair + markdown");
        assertEquals(2, result.rounds().size(), "the completed first round survives; the repair round is recorded");
        assertNull(result.rounds().get(0).endReason(), "first round completed normally");
        assertEquals(GenerationEndReason.MODEL_REQUEST_FAILED, result.rounds().get(1).endReason(),
                "the empty repair round is recorded as interrupted by request failure");
    }

    // ── 流式:修复片段实时转发,不额外 done ────────────────────────────────

    @Test
    void streamForwardsRepairPatchLiveAndEndsWithOneDone() {
        ScriptedTransport transport = ScriptedTransport.streams(frame("root = Bogus(\"x\")\n") + SseFrames.done(),
                frame("root = Stack([])\n") + SseFrames.done());
        ArrayList<RenderStreamEnvelope> envelopes = new ArrayList<>();

        GenUiGenerationResult result = GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), transport)
                .generateStream(req(), envelopes::add);

        assertEquals(ValidationStatus.VALID, result.validationStatus());
        assertEquals(GenerationEndReason.CHECK_PASSED, result.endReason(), "a successful repair round is CHECK_PASSED");
        assertEquals("root = Stack([])", result.dsl());
        assertEquals(2, result.rounds().size());
        assertEquals(List.of("root = Bogus(\"x\")\n", "root = Stack([])\n"), dslContents(envelopes));
        assertEquals(1, envelopes.stream().filter(e -> RenderStreamEnvelope.TYPE_DONE.equals(e.type())).count(),
                "a model round ending is not a session terminal");
        assertEquals(RenderStreamEnvelope.TYPE_DONE, envelopes.get(envelopes.size() - 1).type());
        assertSeqStrictlyIncreasing(envelopes);
    }

    @Test
    void streamChecksMergedProgramSoPatchCanFixDownstreamStatement() {
        // 初始:上游 rows 未知组件,root 依赖它;修复只覆盖上游 rows,root 不重发。
        ScriptedTransport transport = ScriptedTransport.streams(
                frame("rows = Bogus()\nroot = Stack([rows])\n") + SseFrames.done(),
                frame("rows = TextContent(\"fixed\")\n") + SseFrames.done());
        ArrayList<RenderStreamEnvelope> envelopes = new ArrayList<>();

        GenUiGenerationResult result = GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), transport)
                .generateStream(req(), envelopes::add);

        assertEquals(ValidationStatus.VALID, result.validationStatus(), String.valueOf(result.validationResult()));
        assertEquals("rows = TextContent(\"fixed\")\nroot = Stack([rows])", result.dsl(),
                "the patch only replaces the upstream statement; the downstream reference is re-checked");
        assertEquals(2, result.rounds().size());
        assertTrue(dslContents(envelopes).get(1).contains("TextContent"), "patch is forwarded live");
        assertSeqStrictlyIncreasing(envelopes);
    }

    @Test
    void streamRepairStartsFreshAfterDrainingLateFragment() {
        // 首轮在语句中途结束:轮末 drain 自动闭合 late;Bogus 触发修复轮。
        // 修复请求必须从空缓冲区开始,不得把补丁拼接到上一请求的半句上。
        ScriptedTransport transport = ScriptedTransport.streams(
                frame("root = Stack([late])\nlate = Bogus(") + SseFrames.done(),
                frame("late = CardHeader(\"fixed\")\n") + SseFrames.done());
        ArrayList<RenderStreamEnvelope> envelopes = new ArrayList<>();

        GenUiGenerationResult result = GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), transport)
                .generateStream(req(), envelopes::add);

        assertEquals(ValidationStatus.VALID, result.validationStatus(), String.valueOf(result.validationResult()));
        assertEquals(GenerationEndReason.CHECK_PASSED, result.endReason());
        assertEquals("root = Stack([late])\nlate = CardHeader(\"fixed\")", result.dsl(),
                "the late fragment is auto-closed in round 0; the patch is parsed from a fresh buffer");
        assertEquals(2, result.rounds().size());
        assertFalse(result.rounds().get(1).rawOutput().contains("Bogus"),
                "repair round raw output only contains the new request, not the previous request's tail");
    }

    // ── 反复失败:不提前退出,到期一次降级并保留轮记录 ─────────────────────

    @Test
    void repeatedInvalidRoundsContinueUntilDeadlineAndHandOffOnce() {
        ManualTime time = new ManualTime();
        ScriptedTransport transport = ScriptedTransport.streams(frame("root = Bogus(\"x\")\n") + SseFrames.done(),
                frame("root = StillBogus()\n") + SseFrames.done(),
                // The final marker has no trailing blank line: EOF advances the clock before its handling,
                // so the round completes and the loop then observes the exhausted budget.
                frame("root = AlsoBogus()\n") + "data: [DONE]");
        transport.advanceAfterStream(time, 3, 20);
        ArrayList<RenderStreamEnvelope> envelopes = new ArrayList<>();
        AtomicInteger handoffs = new AtomicInteger();

        GenUiGenerationResult result = GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), transport)
                .withTiming(time.timing()).generateStream(req(), envelopes::add, (original, reason, sink) -> {
                    handoffs.incrementAndGet();
                    return new GenUiGenerationResult("fallback", original.response());
                });

        assertEquals(GenerationEndReason.TIMEOUT, result.endReason());
        assertEquals(1, handoffs.get(), "one handoff regardless of repeated failures");
        assertEquals(1, envelopes.stream().filter(e -> RenderStreamEnvelope.TYPE_REPLACE.equals(e.type())).count());
        assertEquals(1, envelopes.stream().filter(e -> RenderStreamEnvelope.TYPE_DONE.equals(e.type())).count());
        assertEquals(3, result.rounds().size(), "all completed rounds survive the fallback");
        assertEquals("root = AlsoBogus()", result.rounds().get(2).currentDefinitions());
        assertTrue(result.rounds().stream().allMatch(round -> round.endReason() == null),
                "completed rounds leave endReason null; the session reason is on the result");
    }

    // ── 修复中断:保留已完成轮与中断轮 ────────────────────────────────────

    @Test
    void repairStreamFailureKeepsCompletedAndInterruptedRounds() {
        ScriptedTransport transport = ScriptedTransport.streams(frame("root = Bogus(\"x\")\n") + SseFrames.done(),
                frame("root = Stack([") + "data: {\"broken\":true}\n\n");
        ArrayList<RenderStreamEnvelope> envelopes = new ArrayList<>();
        AtomicInteger handoffs = new AtomicInteger();

        GenUiGenerationResult result = GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), transport)
                .generateStream(req(), envelopes::add, (original, reason, sink) -> {
                    handoffs.incrementAndGet();
                    return new GenUiGenerationResult("fallback", original.response());
                });

        assertEquals(GenerationEndReason.MODEL_REQUEST_FAILED, result.endReason());
        assertEquals(1, handoffs.get());
        assertEquals(2, result.rounds().size(), "completed first round + interrupted repair round");
        assertNull(result.rounds().get(0).endReason(), "a completed round keeps its verdict in validationResult");
        assertEquals(GenerationEndReason.MODEL_REQUEST_FAILED, result.rounds().get(1).endReason(),
                "the interrupted round records the session terminal reason");
        assertTrue(result.rounds().get(1).rawOutput().contains("root = Stack(["));
        assertEquals(1, envelopes.stream().filter(e -> RenderStreamEnvelope.TYPE_DONE.equals(e.type())).count());
    }

    @Test
    void streamSeparatesAnUnterminatedRoundFromItsRepair() {
        ScriptedTransport transport = ScriptedTransport.streams(
                frame("root = Stack([late])\nlate = Bogus(") + SseFrames.done(),
                frame("late = TextContent(\"fixed\")") + SseFrames.done());
        ArrayList<RenderStreamEnvelope> events = new ArrayList<>();

        GenUiGenerationResult result = GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), transport)
                .generateStream(req(), events::add);

        assertEquals(ValidationStatus.VALID, result.validationStatus());
        assertEquals("root = Stack([late])\nlate = Bogus()\nlate = TextContent(\"fixed\")",
                String.join("", dslContents(events)), "the frontend receives separate complete statements");
        assertEquals("root = Stack([late])\nlate = Bogus(", result.rounds().get(0).rawOutput(),
                "normalization must not change the recorded model output");
        assertEquals("late = TextContent(\"fixed\")", result.rounds().get(1).rawOutput());
    }

    @Test
    void emptyAndRepeatedPatchesKeepRepairingUntilTheOriginalDeadline() {
        ManualTime time = new ManualTime();
        ScriptedTransport transport = ScriptedTransport.streams(frame("root = Bogus()\n") + SseFrames.done(),
                SseFrames.done(), frame("root = Bogus()\n") + SseFrames.done(), "data: [DONE]");
        transport.advanceAfterStream(time, 4, 20);
        AtomicInteger handoffs = new AtomicInteger();

        GenUiGenerationResult result = GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), transport)
                .withTiming(time.timing()).generateStream(req(), event -> {
                }, (original, reason, sink) -> {
                    handoffs.incrementAndGet();
                    return new GenUiGenerationResult("fallback", original.response());
                });

        assertEquals(GenerationEndReason.TIMEOUT, result.endReason());
        assertEquals(4, transport.streamCount, "neither empty output nor unchanged errors end the session");
        assertEquals(0, time.schedules.get(), "repair budget must not install a cancellation timer");
        assertEquals(1, handoffs.get());
        assertEquals("", result.rounds().get(1).rawOutput());
    }

    @Test
    void recordsCrossRoundConsumerEvidenceWithIncompleteInitialAndRepairOutputs() throws Exception {
        ScriptedTransport transport = ScriptedTransport.streams(
                frame("root = Stack([late])\nlate = Bogus(") + SseFrames.done(),
                frame("late = StillBogus(") + SseFrames.done(),
                frame("helper = TextContent(\"repair helper visible\")\nlate = Stack([helper])") + SseFrames.done());
        ArrayList<RenderStreamEnvelope> events = new ArrayList<>();

        GenUiGenerationResult result = GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), transport)
                .generateStream(req(), events::add);

        assertEquals(ValidationStatus.VALID, result.validationStatus());
        assertEquals(3, result.rounds().size());
        assertEquals("root = Stack([late])\nlate = Stack([helper])\nhelper = TextContent(\"repair helper visible\")",
                result.dsl());
        assertEquals(
                List.of("root = Stack([late])\nlate = Bogus(", "late = StillBogus(",
                        "helper = TextContent(\"repair helper visible\")\nlate = Stack([helper])"),
                result.rounds().stream().map(round -> round.rawOutput()).toList());
        assertEquals(1, events.stream().filter(event -> RenderStreamEnvelope.TYPE_DONE.equals(event.type())).count());
        // Consumable evidence for the real frontend harness; only build output is written.
        Path target = Path.of("target", "incremental-consumer-evidence.json");
        Files.createDirectories(target.getParent());
        Files.writeString(target, Json.stringify(Map.of("chunks", dslContents(events), "dsl", result.dsl(),
                "roundDefinitions", result.rounds().stream().map(round -> round.currentDefinitions()).toList())));
    }

    @Test
    void blockedFirstRepairCanCompleteAfterBudgetExpires() throws Exception {
        GenerationDeadlineTest.ManualTime time = new GenerationDeadlineTest.ManualTime();
        CountDownLatch repairing = new CountDownLatch(1);
        CountDownLatch cancelled = new CountDownLatch(1);
        CountDownLatch lateRead = new CountDownLatch(1);
        AtomicBoolean firstReleased = new AtomicBoolean();
        AtomicInteger calls = new AtomicInteger();
        LlmTransport transport = new LlmTransport() {
            public String post(String body) {
                throw new AssertionError();
            }
            public InputStream postStream(String body) {
                throw new AssertionError();
            }
            public LlmCall newCall(String body, boolean stream) {
                int number = calls.incrementAndGet();
                if (number > 1)
                    assertTrue(firstReleased.get(), "release the old source before repair");
                return new LlmCall() {
                    public InputStream execute() {
                        if (number == 1)
                            return new ByteArrayInputStream(
                                    (frame("root = Bogus()\n") + SseFrames.done()).getBytes(StandardCharsets.UTF_8));
                        repairing.countDown();
                        while (cancelled.getCount() != 0) {
                            try {
                                cancelled.await();
                            } catch (InterruptedException ignored) {
                            }
                        }
                        return new ByteArrayInputStream(
                                (frame("root = Stack([])\n") + SseFrames.done()).getBytes(StandardCharsets.UTF_8)) {
                            public synchronized int read(byte[] b, int offset, int length) {
                                lateRead.countDown();
                                return super.read(b, offset, length);
                            }
                        };
                    }
                    public void cancel() {
                        if (number == 1)
                            firstReleased.set(true);
                        else
                            cancelled.countDown();
                    }
                };
            }
        };
        var events = new CopyOnWriteArrayList<RenderStreamEnvelope>();
        AtomicInteger handoffs = new AtomicInteger();
        GenUiGenerator generator = GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), transport)
                .withTiming(time.timing());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var future = executor
                    .submit(() -> generator.generateStream(req(), events::add, (original, reason, sink) -> {
                        handoffs.incrementAndGet();
                        assertEquals(0, cancelled.getCount());
                        return new GenUiGenerationResult("fallback", original.response());
                    }));
            try {
                assertTrue(repairing.await(3, TimeUnit.SECONDS));
                time.expire();
                assertFalse(future.isDone(), "elapsed budget must not interrupt an in-flight repair");
            } finally {
                cancelled.countDown();
            }
            GenUiGenerationResult result = future.get(3, TimeUnit.SECONDS);
            assertTrue(lateRead.await(3, TimeUnit.SECONDS));
            assertEquals(GenerationEndReason.CHECK_PASSED, result.endReason());
            assertEquals(List.of("root = Bogus()\n", "root = Stack([])\n"), dslContents(events));
            assertEquals(List.of("dataModel", "dsl", "dsl", "done"),
                    events.stream().map(RenderStreamEnvelope::type).toList());
            assertEquals(0, handoffs.get());
            assertEquals(0, time.schedules.get());
            assertEquals(2, result.rounds().size());
            assertNull(result.rounds().get(1).endReason());
        }
    }

    @Test
    void rawCommentsAndStringWhitespaceCannotSwallowTheNextRepair() throws Exception {
        List<String> rawOutputs = List.of("root = Bogus( // (", "root = Bogus( # (", "root = Bogus(\"abc\\   ");
        List<String> boundaries = List.of("\n)\n", "\n)\n", "\")\n");
        List<Map<String, Object>> evidence = new ArrayList<>();
        for (int i = 0; i < rawOutputs.size(); i++) {
            String raw = rawOutputs.get(i);
            String patch = "root = TextContent(\"repair helper visible\")";
            ScriptedTransport transport = ScriptedTransport.streams(frame(raw) + SseFrames.done(),
                    frame(patch) + SseFrames.done());
            List<RenderStreamEnvelope> events = new ArrayList<>();
            GenUiGenerationResult result = GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), transport)
                    .generateStream(req(), events::add);
            assertEquals(ValidationStatus.VALID, result.validationStatus());
            assertEquals(List.of(raw, boundaries.get(i), patch), dslContents(events), raw);
            evidence.add(Map.of("chunks", dslContents(events), "dsl", result.dsl(), "roundDefinitions",
                    result.rounds().stream().map(round -> round.currentDefinitions()).toList()));
        }
        Files.writeString(Path.of("target", "incremental-boundary-regressions.json"), Json.stringify(evidence));
    }

    // ── fake transports ─────────────────────────────────────────────────────

    static final class ScriptedTransport implements InMemoryLlmTransport {
        private final Deque<String> posts = new ArrayDeque<>();
        private final Deque<String> streams = new ArrayDeque<>();
        final List<String> bodies = new ArrayList<>();
        private int postCount;
        int streamCount;
        private ManualTime time;
        private int advanceOnStream = -1;
        private long advanceToNanos = -1;

        static ScriptedTransport sync(String... responses) {
            ScriptedTransport transport = new ScriptedTransport();
            transport.posts.addAll(List.of(responses));
            return transport;
        }

        static ScriptedTransport streams(String... scripts) {
            ScriptedTransport transport = new ScriptedTransport();
            transport.streams.addAll(List.of(scripts));
            return transport;
        }

        void advanceAfterStream(ManualTime time, int streamNumber, int seconds) {
            this.time = time;
            this.advanceOnStream = streamNumber;
            this.advanceToNanos = TimeUnit.SECONDS.toNanos(seconds);
        }

        int postCount() {
            return postCount;
        }

        @Override
        public String post(String body) throws LlmTransportException {
            postCount++;
            bodies.add(body);
            if (posts.isEmpty()) {
                throw new LlmTransportException("no scripted sync response");
            }
            return posts.poll();
        }

        @Override
        public InputStream postStream(String body) throws LlmTransportException {
            streamCount++;
            bodies.add(body);
            String script = streams.isEmpty() ? "" : streams.poll();
            if (script.contains("\"broken\"")) {
                return failingAfter(script);
            }
            if (time != null && streamCount == advanceOnStream) {
                return advancingAtEnd(script, time, advanceToNanos);
            }
            return new ByteArrayInputStream(script.getBytes(StandardCharsets.UTF_8));
        }

        private static InputStream advancingAtEnd(String script, ManualTime time, long atNanos) {
            return new java.io.ByteArrayInputStream(script.getBytes(StandardCharsets.UTF_8)) {
                @Override
                public synchronized int read(byte[] buffer, int offset, int length) {
                    int count = super.read(buffer, offset, length);
                    if (count == -1) {
                        time.now.set(atNanos);
                    }
                    return count;
                }
            };
        }

        private static InputStream failingAfter(String script) {
            byte[] bytes = script.getBytes(StandardCharsets.UTF_8);
            return new java.io.FilterInputStream(new ByteArrayInputStream(bytes)) {
                @Override
                public int read() throws IOException {
                    int value = super.read();
                    if (value == -1) {
                        throw new IOException("connection dropped");
                    }
                    return value;
                }

                @Override
                public int read(byte[] buffer, int offset, int length) throws IOException {
                    int count = super.read(buffer, offset, length);
                    if (count == -1) {
                        throw new IOException("connection dropped");
                    }
                    return count;
                }
            };
        }
    }

    static final class ManualTime {
        final AtomicLong now = new AtomicLong();
        final AtomicInteger schedules = new AtomicInteger();

        GenerationTiming timing() {
            return new GenerationTiming(now::get, (action, delay) -> {
                schedules.incrementAndGet();
                return () -> {
                };
            });
        }
    }
}
