/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.core.llm.stream.SseFrames;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransport;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransportException;
import com.huawei.cloudsop.genui.core.validation.GenUiValidationConfig;
import com.huawei.cloudsop.genui.core.validation.ValidationStatus;
import com.huawei.cloudsop.genui.core.validation.stream.RoundEndResult;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * #56 首次生成实时展示 + 轮末统一校验(经公开入口 {@link GenUiGenerator#generateStream})。
 *
 * <p>
 * 断言:生成过程中不因校验扣留 DSL(增量实时转发为 dsl 帧)、不增加生成前模型请求(一次 postStream);轮结束后对当前
 * 有效程序做一次统一检查;同名覆盖后旧错误消失;分片无关;轮记录(原始输出/当前定义/诊断/耗时/契约版本)随结果返回。
 */
class GenUiGeneratorRoundEndTest {

    private static GenUiGenerator generator(FakeStream transport) {
        return GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), transport, GenUiValidationConfig.finalOnly(),
                null);
    }

    private static UiGenerationRequest req() {
        return UiGenerationRequest.builder().userInput("build").build();
    }

    private static List<RenderStreamEnvelope> run(GenUiGenerator generator, UiGenerationRequest request) {
        ArrayList<RenderStreamEnvelope> envelopes = new ArrayList<>();
        generator.generateStream(request, envelopes::add);
        return envelopes;
    }

    private static List<String> dslContents(List<RenderStreamEnvelope> envelopes) {
        List<String> out = new ArrayList<>();
        for (RenderStreamEnvelope e : envelopes) {
            if (e.type().equals(RenderStreamEnvelope.TYPE_DSL)) {
                out.add(String.valueOf(e.content()));
            }
        }
        return out;
    }

    private static List<String> types(List<RenderStreamEnvelope> envelopes) {
        List<String> out = new ArrayList<>();
        for (RenderStreamEnvelope e : envelopes) {
            out.add(e.type());
        }
        return out;
    }

    private static void assertSeqStrictlyIncreasing(List<RenderStreamEnvelope> envelopes) {
        for (int i = 1; i < envelopes.size(); i++) {
            assertTrue(envelopes.get(i).seq() > envelopes.get(i - 1).seq(), "seq must strictly increase at index " + i);
        }
    }

    // ── 实时展示:不扣留、不逐语句修复、不加生成前请求 ─────────────────────

    @Test
    void deltasAreForwardedLiveWithoutGatingAndNoExtraModelRequest() {
        // 未知组件 Bogus 也实时转发(轮末才统一校验);整轮只有一次 postStream(无生成前预判请求)。
        FakeStream t = FakeStream.of(frame("root = Bogus(\"x\")\n"), SseFrames.done());
        List<RenderStreamEnvelope> envelopes = run(generator(t), req());

        assertEquals(1, t.streamCount(), "首次生成只发起一次模型请求");
        assertEquals("dataModel", envelopes.get(0).type());
        assertEquals(0, envelopes.get(0).seq());
        List<String> dsl = dslContents(envelopes);
        assertEquals(List.of("root = Bogus(\"x\")\n"), dsl, "原始增量实时转发,不因校验扣留");
        assertEquals("done", envelopes.get(envelopes.size() - 1).type());
        assertSeqStrictlyIncreasing(envelopes);
    }

    @Test
    void noValidationErrorFrameWhenRoundEndFindsIssues() {
        // 轮末 INVALID 不发 error 帧(检查交给已有失败处理:结果标记 INVALID),流以 done 结束。
        FakeStream t = FakeStream.of(frame("root = Bogus(\"x\")\n"), SseFrames.done());
        ArrayList<RenderStreamEnvelope> envelopes = new ArrayList<>();
        GenUiGenerationResult result = generator(t).generateStream(req(), envelopes::add);

        assertFalse(types(envelopes).contains(RenderStreamEnvelope.TYPE_ERROR),
                "轮末统一检查不对外发 error 帧: " + types(envelopes));
        assertEquals("done", envelopes.get(envelopes.size() - 1).type());
        assertEquals(ValidationStatus.INVALID, result.validationStatus());
    }

    // ── 同名覆盖:前后端一致 ────────────────────────────────────────────────

    @Test
    void sameNameOverrideOldErrorDisappearsInRoundEndCheck() {
        // 先输出错误定义(Bogus 未知组件),再输出同名正确定义(root=Stack)。
        FakeStream t = FakeStream.of(frame("root = Bogus(\"x\")\n"), frame("root = Stack([CardHeader(\"A\")])\n"),
                SseFrames.done());
        ArrayList<RenderStreamEnvelope> envelopes = new ArrayList<>();
        GenUiGenerationResult result = generator(t).generateStream(req(), envelopes::add);

        // 前端视角:两条增量都实时到达(前端按同名覆盖消费)。
        List<String> dsl = dslContents(envelopes);
        assertEquals(2, dsl.size());
        assertTrue(dsl.get(0).contains("Bogus"));
        assertTrue(dsl.get(1).contains("Stack"));

        // 后端视角:轮末校验只看当前程序 → VALID;结果 dsl 是去重后的当前定义。
        assertEquals(ValidationStatus.VALID, result.validationStatus());
        assertEquals("root = Stack([CardHeader(\"A\")])", result.dsl(), "结果携带当前有效定义(同名 last-wins)");
        assertFalse(result.validationResult().hasBlockingIssues());
    }

    @Test
    void redefinitionCausingDownstreamMismatchIsCaughtAtRoundEnd() {
        // a 先是合法组件,后被覆盖为未定义引用 missing → root 引用 a 时轮末仍能发现下游问题。
        FakeStream t = FakeStream.of(frame("a = CardHeader(\"A\")\n"), frame("root = Stack([a])\n"),
                frame("a = missing\n"), SseFrames.done());
        GenUiGenerationResult result = generator(t).generateStream(req(), e -> {
        });

        assertEquals(ValidationStatus.INVALID, result.validationStatus());
        assertTrue(
                result.validationResult().issues().stream()
                        .anyMatch(i -> "unresolved-ref".equals(i.code()) && "a".equals(i.statementId())),
                "覆盖后的当前定义造成下游错误必须被发现: " + result.validationResult().issues());
    }

    @Test
    void helperDefinitionAfterUseIsValidAtRoundEnd() {
        // 轮内后置定义(root 先引用 row,row 后到)不提前报错;轮末已定义 → VALID。
        FakeStream t = FakeStream.of(frame("root = Stack([row])\n"), frame("row = CardHeader(\"R\")\n"),
                SseFrames.done());
        GenUiGenerationResult result = generator(t).generateStream(req(), e -> {
        });

        assertEquals(ValidationStatus.VALID, result.validationStatus());
        assertTrue(result.dsl().contains("CardHeader"));
    }

    // ── 任意分片、跨行传输一致 ─────────────────────────────────────────────

    @Test
    void arbitraryChunkingAcrossLinesYieldsSameProgramAndVerdict() {
        // 同一份输出按 3 种切法传输:逐字、按行、整块。当前程序与校验结论一致。
        String output = "root = Stack([\n  CardHeader(\"A\"),\n  TextContent(\"C\")\n])\n";
        GenUiGenerationResult whole = generator(FakeStream.of(frame(output), SseFrames.done())).generateStream(req(),
                e -> {
                });

        StringBuilder chars = new StringBuilder();
        for (char c : output.toCharArray()) {
            chars.append(frame(String.valueOf(c)));
        }
        GenUiGenerationResult byChar = generator(FakeStream.of(chars + SseFrames.done())).generateStream(req(), e -> {
        });
        GenUiGenerationResult byLine = generator(FakeStream.of(frame("root = Stack([\n"),
                frame("  CardHeader(\"A\"),\n"), frame("  TextContent(\"C\")\n"), frame("])\n"), SseFrames.done()))
                .generateStream(req(), e -> {
                });

        assertEquals(whole.dsl(), byChar.dsl(), "逐字符分片的当前程序一致");
        assertEquals(whole.validationStatus(), byChar.validationStatus());
        assertEquals(whole.dsl(), byLine.dsl(), "跨行分片的当前程序一致");
        assertEquals(whole.validationStatus(), byLine.validationStatus());
        assertEquals(ValidationStatus.VALID, whole.validationStatus());
    }

    // ── 数据路径:完整 Render Data Model ────────────────────────────────────

    @Test
    void dataRefRoundEndCheckUsesRenderDataModel() {
        FakeStream t = FakeStream.of(frame("root = Stack([TextContent(data.name)])\n"), SseFrames.done());
        GenUiGenerationResult with = generator(t).generateStream(
                UiGenerationRequest.builder().userInput("show device").response(Map.of("name", "CloudEngine")).build(),
                e -> {
                });

        assertEquals(ValidationStatus.VALID, with.validationStatus(), "dataModel 存在时 data.* 按外部引用参与轮末统一校验");

        FakeStream t2 = FakeStream.of(frame("root = Stack([TextContent(data.name)])\n"), SseFrames.done());
        GenUiGenerationResult without = generator(t2)
                .generateStream(UiGenerationRequest.builder().userInput("show device").build(), e -> {
                });

        assertEquals(ValidationStatus.INVALID, without.validationStatus(), "无 dataModel 时 data 仍未解析");
        assertTrue(without.validationResult().issues().stream().anyMatch(i -> "unresolved-ref".equals(i.code())));
    }

    @Test
    void wrongDataPathIsReportedAtRoundEnd() {
        // 数据路径错误(完整 Render Data Model 里没有 device.displayName)在轮末被报告。
        FakeStream t = FakeStream.of(frame("root = CardHeader(data.device.displayName)\n"), SseFrames.done());
        GenUiGenerationResult result = generator(t).generateStream(UiGenerationRequest.builder()
                .userInput("show device").response(Map.of("device", Map.of("name", "leaf-1"))).build(), e -> {
                });

        assertEquals(ValidationStatus.INVALID, result.validationStatus());
        assertTrue(
                result.validationResult().issues().stream().anyMatch(i -> "type-data-path-missing".equals(i.code())));
    }

    // ── 轮记录 ──────────────────────────────────────────────────────────────

    @Test
    void resultCarriesRoundRecordWithRawCurrentDiagnosticsTimingAndContract() {
        FakeStream t = FakeStream.of(frame("root = Bogus(\"x\")\n"), SseFrames.done());
        GenUiGenerationResult result = generator(t).generateStream(req(), e -> {
        });

        List<RoundEndResult> rounds = result.rounds();
        assertEquals(1, rounds.size(), "首次生成恰为一轮");
        RoundEndResult round = rounds.get(0);
        assertTrue(round.rawOutput().contains("Bogus"), "记录本轮原始输出");
        assertEquals("root = Bogus(\"x\")", round.currentDefinitions(), "记录当前定义");
        assertNotNull(round.validationResult(), "记录诊断");
        assertTrue(round.durationMs() >= 0, "记录耗时");
        assertNotNull(round.contractVersion(), "记录契约版本");
    }

    @Test
    void fenceWrappedOutputStillProducesCleanProgram() {
        FakeStream t = FakeStream.of(frame("```openui\n"), frame("root = Stack([CardHeader(\"A\")])\n"), frame("```"),
                SseFrames.done());
        GenUiGenerationResult result = generator(t).generateStream(req(), e -> {
        });

        assertEquals("root = Stack([CardHeader(\"A\")])", result.dsl(), "当前程序是围栏内的预处理语句");
        assertEquals(ValidationStatus.VALID, result.validationStatus());
    }

    @Test
    void emptyOutputRoundEndFailsValidation() {
        FakeStream t = FakeStream.of(SseFrames.done());
        GenUiGenerationResult result = generator(t).generateStream(req(), e -> {
        });

        assertEquals(ValidationStatus.INVALID, result.validationStatus(), "空程序轮末 root-missing");
        assertEquals("", result.dsl());
        assertNull(result.validationResult().metadata().rootName());
    }

    // ── 流失败:已有失败处理不变 ─────────────────────────────────────────────

    @Test
    void midStreamFailureEmitsErrorThenDoneWithoutRoundEndCheck() {
        FakeStream t = FakeStream.failAfterFrames(frame("root = Stack([])\n"));
        ArrayList<RenderStreamEnvelope> envelopes = new ArrayList<>();
        GenUiGenerationResult result = generator(t).generateStream(req(), envelopes::add);

        assertEquals("error", envelopes.get(envelopes.size() - 2).type());
        Map<String, Object> payload = Json.asObject(envelopes.get(envelopes.size() - 2).content(), "error");
        assertEquals("LLM_STREAM_FAILED", payload.get("code"));
        assertEquals("done", envelopes.get(envelopes.size() - 1).type());
        assertNull(result.validationStatus(), "流失败后不运行轮末校验(内容不可信)");
        assertTrue(result.rounds().isEmpty(), "流失败不产生轮记录");
        assertSeqStrictlyIncreasing(envelopes);
    }

    // ── DISABLED:行为不变 ──────────────────────────────────────────────────

    @Test
    void disabledModeForwardsDeltasWithoutAnyValidation() {
        FakeStream t = FakeStream.of(frame("root = Bogus(\"x\")\n"), SseFrames.done());
        GenUiGenerator g = GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), t, GenUiValidationConfig.disabled(),
                null);
        ArrayList<RenderStreamEnvelope> envelopes = new ArrayList<>();
        GenUiGenerationResult result = g.generateStream(req(), envelopes::add);

        assertEquals(1, dslContents(envelopes).size());
        assertNull(result.validationStatus());
        assertNull(result.validationResult());
        assertTrue(result.rounds().isEmpty());
    }

    // ── fake transport ──────────────────────────────────────────────────────

    private static String frame(String content) {
        return "data: {\"choices\":[{\"delta\":{\"content\":" + Json.stringify(content) + "}}]}\n\n";
    }

    private static final class FakeStream implements LlmTransport {
        private final String stream;
        private final boolean failAfterFrames;
        private int streamCount = 0;

        private FakeStream(String stream, boolean failAfterFrames) {
            this.stream = stream;
            this.failAfterFrames = failAfterFrames;
        }

        static FakeStream of(String... frames) {
            return new FakeStream(String.join("", frames), false);
        }

        static FakeStream failAfterFrames(String... frames) {
            return new FakeStream(String.join("", frames), true);
        }

        int streamCount() {
            return streamCount;
        }

        @Override
        public String post(String body) {
            throw new UnsupportedOperationException("sync not used");
        }

        @Override
        public InputStream postStream(String body) throws LlmTransportException {
            streamCount++;
            InputStream frames = new ByteArrayInputStream(stream.getBytes(StandardCharsets.UTF_8));
            return failAfterFrames ? new FrameThenFailStream(frames) : frames;
        }
    }

    /** 先吐出全部帧字节,再在下一次 read 时抛 IOException,模拟流中途断开。 */
    private static final class FrameThenFailStream extends InputStream {
        private final InputStream delegate;

        private FrameThenFailStream(InputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public int read() throws java.io.IOException {
            int value = delegate.read();
            if (value == -1) {
                throw new java.io.IOException("connection dropped");
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int off, int len) throws java.io.IOException {
            int read = delegate.read(buffer, off, len);
            if (read == -1) {
                throw new java.io.IOException("connection dropped");
            }
            return read;
        }
    }
}
