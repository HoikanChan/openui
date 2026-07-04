/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.llm.protocol;

import com.huawei.cloudsop.genui.core.GenerationSdkException;
import com.huawei.cloudsop.genui.core.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ChatCompletionResponse {
    private final List<Choice> choices;

    private ChatCompletionResponse(List<Choice> choices) {
        this.choices = List.copyOf(choices);
    }

    public static ChatCompletionResponse parse(String json) {
        Object parsed = Json.parse(json);
        Map<String, Object> object = Json.asObject(parsed, "chat completion response");
        Object choicesValue = object.get("choices");
        if (choicesValue == null) {
            return new ChatCompletionResponse(List.of());
        }

        List<Object> rawChoices = Json.asList(choicesValue, "choices");
        ArrayList<Choice> choices = new ArrayList<>(rawChoices.size());
        for (Object rawChoice : rawChoices) {
            Map<String, Object> choice = Json.asObject(rawChoice, "choice");
            Message message = null;
            Object rawMessage = choice.get("message");
            if (rawMessage != null) {
                Map<String, Object> messageObject = Json.asObject(rawMessage, "message");
                message = new Message(asString(messageObject.get("role")), asString(messageObject.get("content")));
            }
            choices.add(new Choice(message, asString(choice.get("finish_reason"))));
        }
        return new ChatCompletionResponse(choices);
    }

    public List<Choice> choices() {
        return choices;
    }

    public String firstContent() {
        if (choices.isEmpty()) {
            throw new GenerationSdkException("Chat completion response choices is empty");
        }
        Message message = choices.getFirst().message();
        if (message == null) {
            throw new GenerationSdkException("Chat completion response first choice message is missing");
        }
        if (message.content() == null) {
            throw new GenerationSdkException("Chat completion response first choice content is missing");
        }
        return message.content();
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    public record Choice(Message message, String finishReason) {
    }

    public record Message(String role, String content) {
    }
}
