/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.llm.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.core.llm.GenUiLlmConfig;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class ChatCompletionRequestTest {
    @Test
    void synchronousBodyUsesDefaultsWithoutJsonObjectResponse() {
        Map<String, Object> body = body(ChatCompletionRequest
                .of(GenUiLlmConfig.defaults(), "", List.of(ChatMessage.user("hello")), false).toJson());

        assertEquals(GenUiLlmConfig.DEFAULT_MODEL, body.get("model"));
        assertEquals(false, body.get("stream"));
        assertEquals(0.0, ((Number) body.get("temperature")).doubleValue());
        assertEquals(false, body.get("enable_thinking"));
        assertFalse(body.containsKey("response_format"));
    }

    @Test
    void streamingBodySetsStreamTrue() {
        Map<String, Object> body = body(ChatCompletionRequest
                .of(GenUiLlmConfig.defaults(), null, List.of(ChatMessage.user("hello")), true).toJson());

        assertEquals(true, body.get("stream"));
    }

    @Test
    void serializesMultipleMessagesWithRolesInOrder() {
        Map<String, Object> body = body(ChatCompletionRequest.of(GenUiLlmConfig.defaults(), "model-a",
                List.of(ChatMessage.system("sys"), ChatMessage.user("user")), false).toJson());

        List<Object> messages = Json.asList(body.get("messages"), "messages");
        assertEquals("system", Json.asObject(messages.get(0), "message").get("role"));
        assertEquals("sys", Json.asObject(messages.get(0), "message").get("content"));
        assertEquals("user", Json.asObject(messages.get(1), "message").get("role"));
        assertEquals("user", Json.asObject(messages.get(1), "message").get("content"));
    }

    @Test
    void customTemperatureAndDisabledJsonObjectResponseAreReflected() {
        GenUiLlmConfig config = GenUiLlmConfig.builder().temperature(0.3).jsonObjectResponse(false).build();

        Map<String, Object> body = body(ChatCompletionRequest.of(config, "model-a", List.of(), false).toJson());

        assertEquals(0.3, body.get("temperature"));
        assertFalse(body.containsKey("response_format"));
    }

    @Test
    void enabledJsonObjectResponseIsReflected() {
        GenUiLlmConfig config = GenUiLlmConfig.builder().jsonObjectResponse(true).build();

        Map<String, Object> body = body(ChatCompletionRequest.of(config, "model-a", List.of(), false).toJson());

        assertEquals("json_object", Json.asObject(body.get("response_format"), "response_format").get("type"));
    }

    @Test
    void customModelIsNotReplaced() {
        Map<String, Object> body = body(
                ChatCompletionRequest.of(GenUiLlmConfig.defaults(), "model-b", List.of(), false).toJson());

        assertEquals("model-b", body.get("model"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> body(String json) {
        assertTrue(json.indexOf("\"model\"") < json.indexOf("\"messages\""));
        return (Map<String, Object>) Json.parse(json);
    }
}
