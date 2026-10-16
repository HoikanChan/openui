/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.huawei.cloudsop.genui.core.llm;

import com.huawei.cloudsop.genui.core.GenerationSdkException;
import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.core.llm.protocol.ChatCompletionRequest;
import com.huawei.cloudsop.genui.core.llm.protocol.ChatCompletionResponse;
import com.huawei.cloudsop.genui.core.llm.protocol.ChatMessage;
import com.huawei.cloudsop.genui.core.llm.stream.SseDeltaParser;
import com.huawei.cloudsop.genui.core.llm.transport.LlmCall;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransport;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransportException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

/** Explicit Markdown request path; no DSL validation, repair or inherited DSL deadline. */
final class MarkdownGeneration {
    private final GenUiLlmConfig config;
    private final LlmTransport transport;

    MarkdownGeneration(GenUiLlmConfig config, LlmTransport transport) {
        this.config = config;
        this.transport = transport;
    }

    GenUiGenerationResult generate(UiGenerationRequest request) {
        try (LlmCall call = transport.newCall(body(request, false), false); InputStream input = call.execute()) {
            String response = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            return new GenUiGenerationResult(
                    MarkdownRenderer.wrap(request, ChatCompletionResponse.parse(response).firstContent()),
                    request.response());
        } catch (IOException | LlmTransportException error) {
            throw new GenerationSdkException("Markdown request failed: " + error.getMessage(), error);
        }
    }

    GenUiGenerationResult generateStream(UiGenerationRequest request, Consumer<RenderStreamEnvelope> sink) {
        sink.accept(RenderStreamEnvelope.dataModel(request.response()));
        int[] seq = {1};
        StringBuilder markdown = new StringBuilder();
        try (LlmCall call = transport.newCall(body(request, true), true); InputStream input = call.execute()) {
            sink.accept(RenderStreamEnvelope.dsl(seq[0]++, MarkdownRenderer.prefix(request)));
            SseDeltaParser.parse(input, delta -> {
                markdown.append(delta);
                sink.accept(RenderStreamEnvelope.dsl(seq[0]++, MarkdownRenderer.escapeDelta(delta)));
            });
            sink.accept(RenderStreamEnvelope.dsl(seq[0]++, MarkdownRenderer.suffix(request)));
            sink.accept(RenderStreamEnvelope.done(seq[0]++));
            return new GenUiGenerationResult(MarkdownRenderer.wrap(request, markdown.toString()), request.response());
        } catch (IOException | LlmTransportException error) {
            throw new GenerationSdkException("Markdown request failed: " + error.getMessage(), error);
        }
    }

    private String body(UiGenerationRequest request, boolean stream) {
        String system = "Answer the user's request in Markdown only. Do not output openui-lang or wrap the answer in a code fence. "
                + "Use only the supplied data; do not invent facts or computed results. Clearly state any limitations."
                + (request.locale() == null ? "" : " Answer locale: " + request.locale())
                + (request.suggestion() == null ? "" : "\nAdditional instructions: " + request.suggestion());
        String user = (request.userInput() == null ? "" : request.userInput()) + "\n\n## Request context\n"
                + Json.stringify(request.request()) + "\n\n## Response data\n" + Json.stringify(request.response());
        // Markdown is text, even if the DSL gateway configuration requests JSON-object responses.
        GenUiLlmConfig markdownConfig = GenUiLlmConfig.builder().defaultModel(config.defaultModel())
                .temperature(config.temperature()).enableThinking(config.enableThinking()).build();
        return ChatCompletionRequest
                .of(markdownConfig, null, List.of(ChatMessage.system(system), ChatMessage.user(user)), stream).toJson();
    }
}
