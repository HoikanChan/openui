/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.service.web;

import com.huawei.cloudsop.genui.core.GenerationSdk;
import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.core.contract.DataModelSpec;
import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import com.huawei.cloudsop.genui.core.llm.RenderStreamEnvelope;
import com.huawei.cloudsop.genui.core.prompt.GenUIPromptRequest;
import com.huawei.cloudsop.genui.core.validation.DefaultOpenuiLangValidator;
import com.huawei.cloudsop.genui.core.validation.ValidationStatus;
import com.huawei.cloudsop.genui.core.validation.stream.ProgramRoundSession;
import com.huawei.cloudsop.genui.core.validation.stream.RoundEndResult;
import com.huawei.cloudsop.genui.service.api.model.GenerateRequest;
import com.huawei.cloudsop.genui.service.application.GenerationAppService;
import com.huawei.cloudsop.genui.service.llm.LlmClient;
import com.huawei.cloudsop.genui.service.llm.LlmStream;

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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 手写流式端点:codegen 生成的接口签名(ResponseEntity&lt;String&gt;)无法表达 chunked 流式, 故 /generate
 * 绕过生成接口,但路径与契约保持一致(swagger/genui-service.yaml)。
 *
 * <p>
 * 响应为 Server-Sent Events(text/event-stream),每帧一行 {@code data: <json>\n\n},{@code <json>} 是序列化后的 SDK
 * {@link RenderStreamEnvelope}(type/seq/content)。协议(#56:首次生成实时展示,轮末统一校验): 首帧 {@code dataModel}(seq=0)→ 若干
 * {@code dsl}(模型原始增量,实时转发——不因校验扣留、不逐语句门控)→ 结束 {@code done}。轮末对当前有效程序(同名 last-wins)做一次统一校验,结果只记入日志与
 * {@link RoundEndResult} 记录,不发 VALIDATION_FAILED 帧;流中途失败或 finish_reason 非 stop 时发 {@code error(LLM_STREAM_FAILED)} 后
 * {@code done}。
 *
 * <p>
 * 错误行为(流开始前):拼装 404 / 空 prompt 400 / LLM 连接失败 502 都在响应体开始前抛出,由异常 处理器返回 JSON —— 所以任何 SSE 帧都在 openStream 成功之后,502 契约不变。
 */
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

        // 拼装(404 在此抛出)与 LLM 连接(502 在此抛出)都发生在流式响应开始之前。
        GenUIPromptRequest request = toPromptRequest(body);
        String systemPrompt = resolveSystemPrompt(body, request);
        // 轮末校验用的合并契约必须与 LLM 被 prompt 的契约一致(相同组件名/props)。
        GenerationContract merged = sdk.mergedContract(request);
        LlmStream stream = llmClient.openStream(systemPrompt, body.getPrompt());

        StreamingResponseBody responseBody = out -> streamEnvelopes(out, stream, merged, body.getDataModel());
        return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM).body(responseBody);
    }

    /**
     * 实时转发模型增量为 dsl 帧;同时喂给 {@link ProgramRoundSession} 维护当前程序。流干净结束后执行轮末统一校验 (当前有效定义,FINAL),结果记录日志。异常不外抛(首帧已发出),转为
     * error 帧。
     */
    private void streamEnvelopes(OutputStream out, LlmStream stream, GenerationContract merged,
            Map<String, Object> dataModel) {
        // 有 dataModel 才视 `data` 为外部引用并传入轮末校验(完整 Render Data Model 原样,不以样本替代)。
        boolean hasDataModel = dataModel != null && !dataModel.isEmpty();
        Set<String> externalRefs = hasDataModel ? Collections.singleton("data") : Collections.emptySet();
        ProgramRoundSession session = new ProgramRoundSession(new DefaultOpenuiLangValidator(), merged, merged.root(),
                externalRefs, hasDataModel ? dataModel : null);
        int[] nextSeq = {1};
        ValidationStatus finalStatus = null;
        RoundEndResult round = null;
        try {
            // 首帧:dataModel(seq=0)—— 在 openStream 成功后发出,502 仍先于任何帧。
            writeFrame(out, RenderStreamEnvelope.dataModel(dataModel));

            String finishReason = stream.pipeToConsumer(delta -> {
                // 实时转发:不因校验扣留。轮末统一校验不影响已发出的帧。
                writeFrame(out, RenderStreamEnvelope.dsl(nextSeq[0]++, delta));
                session.onDelta(delta);
            });

            if (!"stop".equals(finishReason)) {
                // finish_reason 非 stop(截断/连接被掐断):发 error(LLM_STREAM_FAILED) 后 done;内容不可信,不运行轮末校验。
                String msg = "incomplete, finish_reason="
                        + (finishReason == null ? "connection dropped" : finishReason);
                log.warn("[generate] stream ended abnormally: {}", msg);
                writeFrame(out, RenderStreamEnvelope.error(nextSeq[0]++, "LLM_STREAM_FAILED", msg, true));
                writeFrame(out, RenderStreamEnvelope.done(nextSeq[0]++));
                finalStatus = ValidationStatus.INVALID;
                return;
            }

            // 干净结束:轮末统一校验当前有效程序(一次 FINAL),结果由日志与 RoundEndResult 承载,不发 error 帧。
            writeFrame(out, RenderStreamEnvelope.done(nextSeq[0]++));
            round = session.onRoundEnd();
            finalStatus = round.validationResult().status();
        } catch (Exception e) {
            // 流中途失败:发 error(LLM_STREAM_FAILED) 后 done(取代旧文本尾巴)。
            log.error("[generate] stream failed mid-flight: {}", e.getMessage());
            writeFrame(out, RenderStreamEnvelope.error(nextSeq[0]++, "LLM_STREAM_FAILED", e.getMessage(), true));
            writeFrame(out, RenderStreamEnvelope.done(nextSeq[0]++));
            finalStatus = ValidationStatus.INVALID;
        } finally {
            closeQuietly(stream);
            // 9.2 缓存决策:本服务不缓存生成的 DSL 结果(GenerationsController/AppService 只记录已注册扩展,
            // 无 DSL 结果缓存),故不新造缓存(YAGNI)。此处记录轮末统一校验状态与轮记录,供未来的缓存消费者
            // 据此只缓存 VALID(或修复后 VALID)的结果。
            logRound(finalStatus, round);
        }
    }

    /** 轮末记录:状态、当前定义规模、耗时与契约版本(#56:为后续修复与评测复用)。 */
    private void logRound(ValidationStatus finalStatus, RoundEndResult round) {
        if (round == null) {
            log.info("[generate] stream complete, validationStatus={} (cache only VALID/REPAIRED)", finalStatus);
            return;
        }
        int statementCount = round.validationResult().metadata() == null
                ? -1
                : round.validationResult().metadata().statementCount();
        log.info("[generate] stream complete, validationStatus={}, contract={}, durationMs={}, statements={}",
                finalStatus, round.contractVersion(), round.durationMs(), statementCount);
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
            LinkedHashMap<String, Object> frame = new LinkedHashMap<>();
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

    private static void closeQuietly(LlmStream stream) {
        try {
            stream.close();
        } catch (IOException ignored) {
            // 关闭失败不影响响应
        }
    }
}
