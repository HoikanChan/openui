/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.llm.protocol;

import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.core.llm.GenUiLlmConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容的 chat completion 请求体：模型名、消息列表、流式开关与采样参数。
 *
 * @since 2026
 */
public final class ChatCompletionRequest {
    private final String model;
    private final List<ChatMessage> messages;
    private final boolean stream;
    private final double temperature;
    private final boolean enableThinking;
    private final boolean jsonObjectResponse;

    private ChatCompletionRequest(
            String model,
            List<ChatMessage> messages,
            boolean stream,
            double temperature,
            boolean enableThinking,
            boolean jsonObjectResponse) {
        this.model = model;
        this.messages = List.copyOf(messages == null ? List.of() : messages);
        this.stream = stream;
        this.temperature = temperature;
        this.enableThinking = enableThinking;
        this.jsonObjectResponse = jsonObjectResponse;
    }

    /**
     * 按配置创建请求。config 为 null 时使用默认配置；model 为 null 或空白时回退到配置的默认模型； messages 为 null
     * 时按空列表处理；采样参数统一取自配置。
     *
     * @param config LLM 配置，可为 null
     * @param model 模型名，可为 null 或空白
     * @param messages 对话消息
     * @param stream 是否请求流式响应
     * @return 组装完成的请求
     */
    public static ChatCompletionRequest of(
            GenUiLlmConfig config, String model, List<ChatMessage> messages, boolean stream) {
        GenUiLlmConfig effectiveConfig = config == null ? GenUiLlmConfig.defaults() : config;
        String effectiveModel =
                model == null || model.isBlank() ? effectiveConfig.defaultModel() : model;
        return new ChatCompletionRequest(
                effectiveModel,
                messages,
                stream,
                effectiveConfig.temperature(),
                effectiveConfig.enableThinking(),
                effectiveConfig.jsonObjectResponse());
    }

    /**
     * 序列化为 OpenAI 兼容的 JSON 请求体。response_format 仅在配置启用 JSON object 响应时出现。
     *
     * @return JSON 文本
     */
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
