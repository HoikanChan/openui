/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.llm.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.huawei.cloudsop.genui.core.GenerationSdkException;

import org.junit.jupiter.api.Test;

class ChatCompletionResponseTest {
    @Test
    void returnsFirstChoiceContent() {
        ChatCompletionResponse response = ChatCompletionResponse.parse("""
                {"choices":[{"message":{"role":"assistant","content":"root = Stack([])"},"finish_reason":"stop"}]}
                """);

        assertEquals("root = Stack([])", response.firstContent());
        assertEquals("stop", response.choices().getFirst().finishReason());
    }

    @Test
    void emptyChoicesThrowsClearException() {
        GenerationSdkException error = assertThrows(GenerationSdkException.class,
                () -> ChatCompletionResponse.parse("{\"choices\":[]}").firstContent());

        assertEquals("Chat completion response choices is empty", error.getMessage());
    }

    @Test
    void missingMessageThrowsClearException() {
        GenerationSdkException error = assertThrows(GenerationSdkException.class,
                () -> ChatCompletionResponse.parse("{\"choices\":[{}]}").firstContent());

        assertEquals("Chat completion response first choice message is missing", error.getMessage());
    }

    @Test
    void invalidJsonThrowsGenerationSdkException() {
        assertThrows(GenerationSdkException.class, () -> ChatCompletionResponse.parse("{"));
    }
}
