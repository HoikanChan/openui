/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.llm.protocol;

import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.core.llm.GenUiLlmConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ChatCompletionRequest {
    private final String model;
    private final List<ChatMessage> messages;
    private final boolean stream;
    private final double temperature;
    private final boolean enableThinking;
    private final boolean jsonObjectResponse;

    private ChatCompletionRequest(String model, List<ChatMessage> messages, boolean stream, double temperature,
            boolean enableThinking, boolean jsonObjectResponse) {
        this.model = model;
        this.messages = List.copyOf(messages == null ? List.of() : messages);
        this.stream = stream;
        this.temperature = temperature;
        this.enableThinking = enableThinking;
        this.jsonObjectResponse = jsonObjectResponse;
    }

    public static ChatCompletionRequest of(GenUiLlmConfig config, String model, List<ChatMessage> messages,
            boolean stream) {
        GenUiLlmConfig effectiveConfig = config == null ? GenUiLlmConfig.defaults() : config;
        String effectiveModel = model == null || model.isBlank() ? effectiveConfig.defaultModel() : model;
        return new ChatCompletionRequest(effectiveModel, messages, stream, effectiveConfig.temperature(),
                effectiveConfig.enableThinking(), effectiveConfig.jsonObjectResponse());
    }

    public String toJson() {
        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("stream", stream);
        body.put("temperature", temperature);
        body.put("enable_thinking", enableThinking);
        if (jsonObjectResponse) {
            body.put("response_format", Map.of("type", "json_object"));
        }
        body.put("messages", serializeMessages());
        return Json.stringify(body);
    }

    private List<Map<String, Object>> serializeMessages() {
        ArrayList<Map<String, Object>> serialized = new ArrayList<>(messages.size());
        for (ChatMessage message : messages) {
            LinkedHashMap<String, Object> item = new LinkedHashMap<>();
            item.put("role", message.role());
            item.put("content", message.content());
            serialized.add(item);
        }
        return serialized;
    }
}
