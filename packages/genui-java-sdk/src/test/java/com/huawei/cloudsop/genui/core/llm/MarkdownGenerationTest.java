/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.huawei.cloudsop.genui.core.llm;

import static org.junit.jupiter.api.Assertions.*;

import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.core.llm.transport.InMemoryLlmTransport;

import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

class MarkdownGenerationTest {
    @Test
    void synchronizedHostFieldsAreRetainedAndUnsupportedFiltersAreNotSilentlyIgnored() {
        var request =
                UiGenerationRequest.builder()
                        .apiNameList(List.of("getAlarms"))
                        .ext(Map.of("scenario", "Preview"))
                        .specifiedComponents(List.of("Table"))
                        .build();
        assertEquals(List.of("getAlarms"), request.ext().get("apiNameList"));
        assertEquals(request.ext(), request.asMarkdown().ext());
        var transport =
                new InMemoryLlmTransport() {
                    public String post(String body) {
                        throw new AssertionError("must reject before model invocation");
                    }

                    public InputStream postStream(String body) {
                        throw new AssertionError();
                    }
                };
        var generator = GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), transport);
        var error = assertThrows(IllegalArgumentException.class, () -> generator.generate(request));
        assertTrue(error.getMessage().contains("Component filtering"));
    }

    @Test
    void explicitMarkdownPreservesContextAndWrapsEscapedTextWithoutDslValidation() {
        String markdown = " # Report\n\"quoted\" \\ path \n";
        List<String> requests = new ArrayList<>();
        InMemoryLlmTransport transport =
                new InMemoryLlmTransport() {
                    public String post(String body) {
                        requests.add(body);
                        return Json.stringify(
                                Map.of(
                                        "choices",
                                        List.of(Map.of("message", Map.of("content", markdown)))));
                    }

                    public InputStream postStream(String body) {
                        requests.add(body);
                        String frame =
                                "data: "
                                        + Json.stringify(
                                                Map.of(
                                                        "choices",
                                                        List.of(
                                                                Map.of(
                                                                        "delta",
                                                                        Map.of(
                                                                                "content",
                                                                                markdown)))))
                                        + "\n\n";
                        return new ByteArrayInputStream(
                                (frame + "data: [DONE]\n\n").getBytes(StandardCharsets.UTF_8));
                    }
                };
        var request =
                UiGenerationRequest.builder()
                        .userInput("original request")
                        .response(Map.of("answer", 42))
                        .request(Map.of("source", "host"))
                        .ext(Map.of("scenario", "Canvas"))
                        .locale("zh")
                        .suggestion("Keep detail")
                        .build()
                        .asMarkdown();
        var generator =
                GenUiGenerator.withTransport(
                        GenUiLlmConfig.builder().jsonObjectResponse(true).build(), transport);
        var sync = generator.generate(request);
        List<RenderStreamEnvelope> frames = new ArrayList<>();
        var streaming = generator.generateStream(request, frames::add);
        String rendered =
                frames.stream()
                        .filter(e -> "dsl".equals(e.type()))
                        .map(e -> String.valueOf(e.content()))
                        .reduce("", String::concat);
        assertEquals(sync.dsl(), streaming.dsl());
        assertEquals(sync.dsl(), rendered);
        assertTrue(rendered.startsWith("root = CanvasCard([Stack([MarkDownRenderer(\" # Report"));
        assertTrue(rendered.contains("\\\"quoted\\\" \\\\ path"));
        assertEquals(request.response(), sync.dataModel());
        assertNull(sync.validationStatus());
        assertEquals(2, requests.size());
        for (String body : requests) {
            assertTrue(body.contains("original request"));
            assertTrue(body.contains("42"));
            assertTrue(body.contains("Keep detail"));
            assertFalse(body.contains("response_format"));
            assertFalse(body.contains("valid openui-lang code"));
        }
    }
}
