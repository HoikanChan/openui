/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.service.web;

import com.huawei.cloudsop.genui.core.GenerationSdk;
import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.core.contract.DataModelSpec;
import com.huawei.cloudsop.genui.core.llm.GenUiGenerator;
import com.huawei.cloudsop.genui.core.llm.RenderStreamEnvelope;
import com.huawei.cloudsop.genui.core.llm.UiGenerationRequest;
import com.huawei.cloudsop.genui.core.prompt.GenUIPromptRequest;
import com.huawei.cloudsop.genui.service.api.model.GenerateRequest;
import com.huawei.cloudsop.genui.service.application.GenerationAppService;
import com.huawei.cloudsop.genui.service.llm.LlmClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Streams raw SDK envelopes. On DSL failure, replace the whole page using an independent Markdown request. */
@RestController
public class GenerateController {
    private static final Logger log = LoggerFactory.getLogger(GenerateController.class);

    private final GenerationAppService appService;
    private final GenerationSdk sdk;
    private final LlmClient llmClient;

    public GenerateController(GenerationAppService appService, GenerationSdk sdk, LlmClient llmClient) {
        this.appService = appService;
        this.sdk = sdk;
        this.llmClient = llmClient;
    }

    @PostMapping(value = "/v1/generate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StreamingResponseBody> generate(@RequestBody GenerateRequest body) {
        if (body.getPrompt() == null || body.getPrompt().trim().isEmpty()) {
            throw new IllegalArgumentException("prompt is required");
        }

        GenUIPromptRequest request = toPromptRequest(body);
        String systemPrompt = resolveSystemPrompt(body, request);
        var generator = GenUiGenerator.withTransport(sdk, llmClient.generationConfig(), llmClient)
                .withSystemPrompt(systemPrompt);
        var generationRequest = UiGenerationRequest.builder().extensionId(body.getExtensionId())
                .userInput(body.getPrompt()).response(body.getDataModel())
                .suggestion(body.getExtraRules() == null ? null : String.join("\n", body.getExtraRules())).build();
        StreamingResponseBody responseBody = out -> {
            var result = generator.generateStream(generationRequest, frame -> writeFrame(out, frame),
                    (original, reason, sink) -> {
                        log.info("[generate] replacing whole page with Markdown, reason={}", reason);
                        return generator.generateStream(original.asMarkdown(), sink);
                    });
            log.info("[generate] complete, reason={}, validationStatus={}", result.endReason(),
                    result.validationStatus());
        };
        return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM).body(responseBody);
    }

    private GenUIPromptRequest toPromptRequest(GenerateRequest body) {
        DataModelSpec dataModel = body.getDataModel() == null || body.getDataModel().isEmpty()
                ? null
                : new DataModelSpec(null, body.getDataModel());
        return new GenUIPromptRequest(body.getExtensionId(), dataModel, body.getExtraRules(), null, null, null, null);
    }

    private String resolveSystemPrompt(GenerateRequest body, GenUIPromptRequest request) {
        // Prompt Override(debug-only):整段替换拼装产物,绕过 Generation。
        if (body.getPromptOverride() != null && !body.getPromptOverride().trim().isEmpty()) {
            return body.getPromptOverride();
        }
        return appService.assemble(request).prompt();
    }

    /**
     * 序列化 envelope 为一行 SSE 帧 {@code data: <json>\n\n}。SDK 的 {@link Json#stringify} 面向 Map/List/标量,不会把 record 序列化成 JSON
     * 对象,故先转成 {@code {type, seq, content}} map 再序列化。 {@code done} 帧的 content 为 null,按契约省略 content 字段。
     */
    private static void writeFrame(OutputStream out, RenderStreamEnvelope envelope) {
        try {
            java.util.LinkedHashMap<String, Object> frame = new java.util.LinkedHashMap<>();
            frame.put("type", envelope.type());
            frame.put("seq", envelope.seq());
            if (envelope.content() != null) {
                frame.put("content", envelope.content());
            }
            String json = Json.stringify(frame);
            out.write(("data: " + json + "\n\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException ignored) {
            // 客户端已断开,帧无处可写
        }
    }

}
