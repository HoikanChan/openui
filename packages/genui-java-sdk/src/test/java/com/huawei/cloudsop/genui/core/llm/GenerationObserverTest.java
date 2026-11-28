/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.core.llm.stream.SseDeltaParser;
import com.huawei.cloudsop.genui.core.llm.stream.SseFrames;
import com.huawei.cloudsop.genui.core.llm.transport.InMemoryLlmTransport;
import com.huawei.cloudsop.genui.core.validation.GenUiValidationConfig;
import com.huawei.cloudsop.genui.core.validation.ValidationStatus;
import com.huawei.cloudsop.genui.core.validation.stream.RoundEndResult;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

class GenerationObserverTest {

    @Test
    void defaultConfigOmitsStreamOptionsAndOptInEmitsIncludeUsage() {
        List<String> capturedBodies = new ArrayList<>();
        InMemoryLlmTransport transport =
                new InMemoryLlmTransport() {
                    @Override
                    public String post(String body) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public InputStream postStream(String body) {
                        capturedBodies.add(body);
                        return sseStream("root = Stack([])", null);
                    }
                };

        GenUiGenerator defaultGen =
                GenUiGenerator.withTransport(
                        GenUiLlmConfig.defaults(),
                        transport,
                        GenUiValidationConfig.disabled(),
                        null);
        defaultGen.generateStream(UiGenerationRequest.builder().userInput("hi").build(), e -> {});

        GenUiGenerator usageGen =
                GenUiGenerator.withTransport(
                        GenUiLlmConfig.builder().includeStreamUsage(true).build(),
                        transport,
                        GenUiValidationConfig.disabled(),
                        null);
        usageGen.generateStream(UiGenerationRequest.builder().userInput("hi").build(), e -> {});

        Map<String, Object> first = Json.asObject(Json.parse(capturedBodies.get(0)), "first");
        Map<String, Object> second = Json.asObject(Json.parse(capturedBodies.get(1)), "second");
        assertFalse(first.containsKey("stream_options"));
        assertEquals(Map.of("include_usage", true), second.get("stream_options"));
    }

    @Test
    void sseParserCapturesTrailingUsageFrameAfterStop() throws IOException {
        String raw =
                "data: {\"choices\":[{\"delta\":{\"content\":\"root = \"}}]}\n\n"
                        + "data: {\"choices\":[{\"delta\":{\"content\":\"Stack([])\"},\"finish_reason\":\"stop\"}]}\n\n"
                        + "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":120,\"completion_tokens\":30,\"total_tokens\":150}}\n\n"
                        + SseFrames.done();
        List<String> deltas = new ArrayList<>();
        SseDeltaParser.StreamParseResult result =
                SseDeltaParser.parseWithUsage(
                        new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)),
                        deltas::add);

        assertEquals("root = Stack([])", result.content());
        assertEquals(List.of("root = ", "Stack([])"), deltas);
        assertEquals(new LlmUsage(120, 30, 150), result.usage());
    }

    @Test
    void observerReceivesOrderedRepairEventsWithExactRequestBodiesAndUsage() {
        Deque<InputStream> streams = new ArrayDeque<>();
        streams.add(sseStream("root = UnknownWidget(\"bad\")", new LlmUsage(100, 20, 120)));
        streams.add(sseStream("root = Stack([])", new LlmUsage(140, 15, 155)));

        InMemoryLlmTransport transport =
                new InMemoryLlmTransport() {
                    @Override
                    public String post(String body) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public InputStream postStream(String body) {
                        return streams.removeFirst();
                    }
                };

        RecordingObserver observer = new RecordingObserver();
        GenUiGenerator generator =
                GenUiGenerator.withTransport(
                                GenUiLlmConfig.builder()
                                        .defaultModel("local-qwen")
                                        .includeStreamUsage(true)
                                        .build(),
                                transport,
                                GenUiValidationConfig.enabled(2),
                                null)
                        .withObserver(observer);

        GenUiGenerationResult result =
                generator.generateStream(
                        UiGenerationRequest.builder().userInput("render card").build(), e -> {});

        assertEquals(ValidationStatus.VALID, result.validationStatus());
        assertEquals(
                List.of(
                        "session_start",
                        "llm_start:0:initial",
                        "llm_end:0:initial",
                        "round_validated:0:INVALID",
                        "llm_start:1:repair",
                        "llm_end:1:repair",
                        "round_validated:1:VALID",
                        "session_end"),
                observer.events);
        assertEquals(2, observer.starts.size());
        assertTrue(observer.starts.get(1).requestBodyJson().contains("[REPAIR:INCREMENTAL]"));
        assertEquals(new LlmUsage(100, 20, 120), observer.ends.get(0).usage());
        assertEquals(new LlmUsage(140, 15, 155), observer.ends.get(1).usage());
        assertNull(observer.ends.get(1).error());
        assertNotNull(observer.finalResult);
    }

    @Test
    void observerExceptionsNeverBreakGeneration() {
        InMemoryLlmTransport transport =
                new InMemoryLlmTransport() {
                    @Override
                    public String post(String body) {
                        return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"root = Stack([])\"}}],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5,\"total_tokens\":15}}";
                    }

                    @Override
                    public InputStream postStream(String body) {
                        return sseStream("root = Stack([])", new LlmUsage(10, 5, 15));
                    }
                };

        GenerationObserver failingObserver =
                new GenerationObserver() {
                    @Override
                    public void onSessionStart(
                            UiGenerationRequest request,
                            String model,
                            boolean validationEnabled,
                            long startedAtEpochMs) {
                        throw new IllegalStateException("observer boom");
                    }

                    @Override
                    public void onLlmCallStart(LlmCallStartEvent event) {
                        throw new IllegalStateException("observer boom");
                    }

                    @Override
                    public void onLlmCallEnd(LlmCallEndEvent event) {
                        throw new IllegalStateException("observer boom");
                    }

                    @Override
                    public void onRoundValidated(
                            int roundIndex, RoundEndResult roundResult, long completedAtEpochMs) {
                        throw new IllegalStateException("observer boom");
                    }

                    @Override
                    public void onSessionEnd(
                            GenUiGenerationResult result,
                            long durationMs,
                            long endedAtEpochMs,
                            String error) {
                        throw new IllegalStateException("observer boom");
                    }
                };

        GenUiGenerator generator =
                GenUiGenerator.withTransport(
                                GenUiLlmConfig.defaults(),
                                transport,
                                GenUiValidationConfig.enabled(1),
                                null)
                        .withObserver(failingObserver);

        GenUiGenerationResult syncResult =
                generator.generate(UiGenerationRequest.builder().userInput("test").build());
        GenUiGenerationResult streamResult =
                generator.generateStream(
                        UiGenerationRequest.builder().userInput("test").build(), e -> {});

        assertEquals("root = Stack([])", syncResult.dsl());
        assertEquals(ValidationStatus.VALID, streamResult.validationStatus());
    }

    private static InputStream sseStream(String content, LlmUsage usage) {
        StringBuilder sb = new StringBuilder();
        sb.append("data: {\"choices\":[{\"delta\":{\"content\":")
                .append(Json.stringify(content))
                .append("},\"finish_reason\":\"stop\"}]}\n\n");
        if (usage != null) {
            sb.append("data: {\"choices\":[],\"usage\":{\"prompt_tokens\":")
                    .append(usage.promptTokens())
                    .append(",\"completion_tokens\":")
                    .append(usage.completionTokens())
                    .append(",\"total_tokens\":")
                    .append(usage.totalTokens())
                    .append("}}\n\n");
        }
        sb.append(SseFrames.done());
        return new ByteArrayInputStream(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static final class RecordingObserver implements GenerationObserver {
        private final List<String> events = new ArrayList<>();
        private final List<LlmCallStartEvent> starts = new ArrayList<>();
        private final List<LlmCallEndEvent> ends = new ArrayList<>();
        private GenUiGenerationResult finalResult;

        @Override
        public void onSessionStart(
                UiGenerationRequest request,
                String model,
                boolean validationEnabled,
                long startedAtEpochMs) {
            events.add("session_start");
        }

        @Override
        public void onLlmCallStart(LlmCallStartEvent event) {
            starts.add(event);
            events.add("llm_start:" + event.roundIndex() + ":" + event.kind());
        }

        @Override
        public void onLlmCallEnd(LlmCallEndEvent event) {
            ends.add(event);
            events.add("llm_end:" + event.roundIndex() + ":" + event.kind());
        }

        @Override
        public void onRoundValidated(
                int roundIndex, RoundEndResult roundResult, long completedAtEpochMs) {
            events.add(
                    "round_validated:"
                            + roundIndex
                            + ":"
                            + roundResult.validationResult().status().name());
        }

        @Override
        public void onSessionEnd(
                GenUiGenerationResult result, long durationMs, long endedAtEpochMs, String error) {
            this.finalResult = result;
            events.add("session_end");
        }
    }
}
