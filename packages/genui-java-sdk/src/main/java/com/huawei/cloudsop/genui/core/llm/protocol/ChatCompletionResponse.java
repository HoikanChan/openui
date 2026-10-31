/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.llm.protocol;

import com.huawei.cloudsop.genui.core.GenerationSdkException;
import com.huawei.cloudsop.genui.core.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容的 chat completion 响应：候选列表，每个候选携带消息与结束原因。
 *
 * @since 2026
 */
public final class ChatCompletionResponse {
    private final List<Choice> choices;

    private ChatCompletionResponse(List<Choice> choices) {
        this.choices = List.copyOf(choices);
    }

    /**
     * 解析响应 JSON。缺少 choices 字段时返回空列表；某个 choice 缺少 message 时其 message 为 null；非字符串字段经 {@link
     * String#valueOf(Object)} 转换，缺失字段为 null。
     *
     * @param json 响应 JSON
     * @return 解析结果，choices 不为 null
     */
    public static ChatCompletionResponse parse(String json) {
        Object parsed = Json.parse(json);
        Map<String, Object> object = Json.asObject(parsed, "chat completion response");
        Object choicesValue = object.get("choices");
        if (choicesValue == null) {
            return new ChatCompletionResponse(List.of());
        }

        List<Object> rawChoices = Json.asList(choicesValue, "choices");
        ArrayList<Choice> parsedChoices = new ArrayList<>(rawChoices.size());
        for (Object rawChoice : rawChoices) {
            Map<String, Object> choice = Json.asObject(rawChoice, "choice");
            Message message = null;
            Object rawMessage = choice.get("message");
            if (rawMessage != null) {
                Map<String, Object> messageObject = Json.asObject(rawMessage, "message");
                message =
                        new Message(
                                asString(messageObject.get("role")),
                                asString(messageObject.get("content")));
            }
            parsedChoices.add(new Choice(message, asString(choice.get("finish_reason"))));
        }
        return new ChatCompletionResponse(parsedChoices);
    }

    /**
     * 候选列表，顺序与上游一致；返回不可变列表且不会为 null。
     *
     * @return 候选列表
     */
    public List<Choice> choices() {
        return choices;
    }

    /**
     * 取第一个候选的消息正文。
     *
     * @return 消息正文
     * @throws GenerationSdkException choices 为空、首条消息缺失或正文缺失时抛出
     */
    public String firstContent() {
        if (choices.isEmpty()) {
            throw new GenerationSdkException("Chat completion response choices is empty");
        }
        Message message = choices.getFirst().message();
        if (message == null) {
            throw new GenerationSdkException(
                    "Chat completion response first choice message is missing");
        }
        if (message.content() == null) {
            throw new GenerationSdkException(
                    "Chat completion response first choice content is missing");
        }
        return message.content();
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 单个候选。
     *
     * @param message 该候选的消息，可能为 null
     * @param finishReason 结束原因，可能为 null
     * @since 2026
     */
    public record Choice(Message message, String finishReason) {}

    /**
     * 候选中携带的消息。
     *
     * @param role 角色，如 assistant
     * @param content 消息正文，可能为 null
     * @since 2026
     */
    public record Message(String role, String content) {}
}
