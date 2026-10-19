/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.huawei.cloudsop.genui.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.core.llm.transport.*;
import com.huawei.cloudsop.genui.service.llm.LlmClient;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@SpringBootTest
@AutoConfigureMockMvc
class GenerateApiTest {
    @Autowired
    MockMvc mvc;
    @MockBean
    LlmClient llmClient;

    @Test
    void streamsRawFragmentsAndOneTerminal() throws Exception {
        when(llmClient.newCall(anyString(), eq(true))).thenReturn(call("root = ", "Stack([])"));
        var frames = perform("{\"prompt\":\"show UI\"}");
        assertEquals(List.of("dataModel", "dsl", "dsl", "done"), types(frames));
        assertEquals("root = ", frames.get(1).get("content"));
        verify(llmClient, times(1)).newCall(anyString(), eq(true));
    }

    @Test
    void realHostHandsOffOnceAndReplacesPageOnFailure() throws Exception {
        LlmCall failed = new LlmCall() {
            public InputStream execute() throws LlmTransportException {
                throw new LlmTransportException("network failed");
            }
            public void cancel() {
            }
        };
        when(llmClient.newCall(anyString(), eq(true))).thenReturn(failed, call("# Answer", "\n42"));
        var frames = perform("{\"prompt\":\"show original\",\"dataModel\":{\"answer\":42}}");
        assertEquals(1, types(frames).stream().filter("replace"::equals).count());
        assertEquals(1, types(frames).stream().filter("done"::equals).count());
        var replace = Json.asObject(frames.get(1).get("content"), "replace");
        assertEquals("page", replace.get("scope"));
        assertEquals("MODEL_REQUEST_FAILED", replace.get("reason"));
        String dsl = frames.stream().filter(f -> "dsl".equals(f.get("type"))).map(f -> String.valueOf(f.get("content")))
                .reduce("", String::concat);
        assertEquals("root = Stack([MarkDownRenderer(\"# Answer\n42\")])", dsl);
        ArgumentCaptor<String> bodies = ArgumentCaptor.forClass(String.class);
        verify(llmClient, times(2)).newCall(bodies.capture(), eq(true));
        String markdown = bodies.getAllValues().get(1);
        assertTrue(markdown.contains("Markdown only"));
        assertTrue(markdown.contains("show original"));
        assertTrue(markdown.contains("42"));
        for (int i = 0; i < frames.size(); i++)
            assertEquals(i, ((Number) frames.get(i).get("seq")).intValue());
    }

    @Test
    void promptOverrideIsUsedOnlyForDsl() throws Exception {
        when(llmClient.newCall(anyString(), eq(true))).thenReturn(call("root = Stack([])"));
        perform("{\"prompt\":\"show\",\"promptOverride\":\"CUSTOM SYSTEM PROMPT\"}");
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(llmClient).newCall(body.capture(), eq(true));
        assertTrue(body.getValue().contains("CUSTOM SYSTEM PROMPT"));
    }

    @Test
    void emptyPromptReturns400() throws Exception {
        mvc.perform(post("/v1/generate").contentType(MediaType.APPLICATION_JSON).content("{\"prompt\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownExtensionReturns404() throws Exception {
        mvc.perform(post("/v1/generate").contentType(MediaType.APPLICATION_JSON)
                .content("{\"prompt\":\"show\",\"extensionId\":\"no-such\"}")).andExpect(status().isNotFound());
    }

    private List<Map<String, Object>> perform(String body) throws Exception {
        var started = mvc.perform(post("/v1/generate").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(request().asyncStarted()).andReturn();
        String raw = mvc.perform(asyncDispatch(started)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        List<Map<String, Object>> frames = new ArrayList<>();
        for (String block : raw.split("\n\n")) {
            if (block.startsWith("data: "))
                frames.add(Json.asObject(Json.parse(block.substring(6)), "frame"));
        }
        return frames;
    }

    private static List<String> types(List<Map<String, Object>> frames) {
        return frames.stream().map(f -> String.valueOf(f.get("type"))).toList();
    }

    private static LlmCall call(String... deltas) {
        StringBuilder sse = new StringBuilder();
        for (String delta : deltas)
            sse.append("data: ")
                    .append(Json.stringify(Map.of("choices", List.of(Map.of("delta", Map.of("content", delta))))))
                    .append("\n\n");
        sse.append("data: [DONE]\n\n");
        return new LlmCall() {
            private boolean cancelled;
            public synchronized InputStream execute() throws LlmTransportException {
                if (cancelled)
                    throw new LlmTransportException("cancelled");
                return new ByteArrayInputStream(sse.toString().getBytes(StandardCharsets.UTF_8));
            }
            public synchronized void cancel() {
                cancelled = true;
            }
        };
    }
}
