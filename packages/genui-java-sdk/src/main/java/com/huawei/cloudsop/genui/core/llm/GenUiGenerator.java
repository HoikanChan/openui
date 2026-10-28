/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.llm;

import com.huawei.cloudsop.genui.core.GenerationSdk;
import com.huawei.cloudsop.genui.core.GenerationSdkException;
import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.core.contract.DataModelSpec;
import com.huawei.cloudsop.genui.core.contract.GenUIExtension;
import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import com.huawei.cloudsop.genui.core.llm.extract.OpenuiCodeExtractor;
import com.huawei.cloudsop.genui.core.llm.protocol.ChatCompletionRequest;
import com.huawei.cloudsop.genui.core.llm.protocol.ChatCompletionResponse;
import com.huawei.cloudsop.genui.core.llm.protocol.ChatMessage;
import com.huawei.cloudsop.genui.core.llm.stream.SseDeltaParser;
import com.huawei.cloudsop.genui.core.llm.transport.HttpClientLlmTransport;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransport;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransportException;
import com.huawei.cloudsop.genui.core.prompt.GenUIPromptAssemblyResult;
import com.huawei.cloudsop.genui.core.prompt.GenUIPromptRequest;
import com.huawei.cloudsop.genui.core.prompt.characterize.CharacterizationConfig;
import com.huawei.cloudsop.genui.core.validation.DefaultOpenuiLangValidator;
import com.huawei.cloudsop.genui.core.validation.GenUiValidationConfig;
import com.huawei.cloudsop.genui.core.validation.OpenuiLangValidator;
import com.huawei.cloudsop.genui.core.validation.ValidationConfigMode;
import com.huawei.cloudsop.genui.core.validation.ValidationMetadata;
import com.huawei.cloudsop.genui.core.validation.ValidationResult;
import com.huawei.cloudsop.genui.core.validation.ValidationStatus;
import com.huawei.cloudsop.genui.core.validation.parser.OpenuiPreprocessor;
import com.huawei.cloudsop.genui.core.validation.repair.RepairPromptBuilder;
import com.huawei.cloudsop.genui.core.validation.stream.ProgramRoundSession;
import com.huawei.cloudsop.genui.core.validation.stream.RoundEndResult;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 公开生成入口。同步生成直接返回提取结果;流式生成实时转发,轮末校验当前完整程序, 失败时默认增量修复,修复失败或模型请求失败时整页降级为 Markdown。
 */
public final class GenUiGenerator {
    private final GenerationSdk sdk;
    private final GenUiLlmConfig config;
    private final LlmTransport transport;
    private final GenUiValidationConfig validationConfig;
    private final OpenuiLangValidator validator;
    private GenerationTiming timing = GenerationTiming.system();
    private String systemPromptOverride;

    private GenUiGenerator(GenerationSdk sdk, GenUiLlmConfig config, LlmTransport transport,
            GenUiValidationConfig validationConfig, OpenuiLangValidator validator) {
        this.sdk = Objects.requireNonNull(sdk, "sdk must not be null");
        this.config = config == null ? GenUiLlmConfig.defaults() : config;
        this.transport = Objects.requireNonNull(transport, "transport must not be null");
        this.validationConfig = validationConfig == null ? GenUiValidationConfig.incremental() : validationConfig;
        this.validator = validator == null ? new DefaultOpenuiLangValidator() : validator;
    }

    /** Backwards-compatible private constructor (no validation config). */
    private GenUiGenerator(GenerationSdk sdk, GenUiLlmConfig config, LlmTransport transport) {
        this(sdk, config, transport, null, null);
    }

    public static GenUiGenerator create() {
        return create(GenUiLlmConfig.defaults());
    }

    public static GenUiGenerator create(GenUiLlmConfig config) {
        GenUiLlmConfig effectiveConfig = config == null ? GenUiLlmConfig.defaults() : config;
        return new GenUiGenerator(GenerationSdk.create(), effectiveConfig, new HttpClientLlmTransport(effectiveConfig));
    }

    public static GenUiGenerator create(GenUiLlmConfig config, CharacterizationConfig characterization) {
        GenUiLlmConfig effectiveConfig = config == null ? GenUiLlmConfig.defaults() : config;
        GenerationSdk sdk = GenerationSdk.builder()
                .characterization(characterization == null ? CharacterizationConfig.defaults() : characterization)
                .build();
        return new GenUiGenerator(sdk, effectiveConfig, new HttpClientLlmTransport(effectiveConfig));
    }

    public static GenUiGenerator withTransport(GenUiLlmConfig config, LlmTransport transport) {
        return new GenUiGenerator(GenerationSdk.create(), config, transport);
    }

    public static GenUiGenerator withTransport(GenUiLlmConfig config, CharacterizationConfig characterization,
            LlmTransport transport) {
        GenerationSdk sdk = GenerationSdk.builder()
                .characterization(characterization == null ? CharacterizationConfig.defaults() : characterization)
                .build();
        return new GenUiGenerator(sdk, config, transport);
    }

    /**
     * Create a generator backed by a fake/test transport, a custom validation config, and an optional custom validator.
     * If {@code validationConfig} is null, defaults to {@link GenUiValidationConfig#incremental()}. If
     * {@code validator} is null, defaults to {@link DefaultOpenuiLangValidator}.
     */
    public static GenUiGenerator withTransport(GenUiLlmConfig config, LlmTransport transport,
            GenUiValidationConfig validationConfig, OpenuiLangValidator validator) {
        return new GenUiGenerator(GenerationSdk.create(), config, transport, validationConfig, validator);
    }

    /**
     * 使用调用方自建的 {@link GenerationSdk}(例如注入外部 base contract)与自定义 transport 创建生成器。
     */
    public static GenUiGenerator withTransport(GenerationSdk sdk, GenUiLlmConfig config, LlmTransport transport) {
        return new GenUiGenerator(sdk, config, transport);
    }

    /** 使用调用方自建 SDK 与显式校验/修复配置创建生成器(测试或宿主关闭修复时使用)。 */
    public static GenUiGenerator withTransport(GenerationSdk sdk, GenUiLlmConfig config, LlmTransport transport,
            GenUiValidationConfig validationConfig, OpenuiLangValidator validator) {
        return new GenUiGenerator(sdk, config, transport, validationConfig, validator);
    }

    public GenUiGenerator register(GenUIExtension extension) {
        sdk.register(extension);
        return this;
    }

    /** 同步生成:单次模型请求后直接返回提取的 DSL,不校验、不修复、不自动降级。 */
    public GenUiGenerationResult generate(UiGenerationRequest request) {
        UiGenerationRequest effective = request == null ? UiGenerationRequest.builder().build() : request;
        if ("Markdown".equals(effective.renderComponentsKey()))
            return markdown().generate(effective);
        RequestBody body = buildRequest(toPromptRequest(effective), effective, false);
        try {
            String content = ChatCompletionResponse.parse(transport.post(body.json())).firstContent();
            return new GenUiGenerationResult(OpenuiCodeExtractor.extract(content), effective.response());
        } catch (LlmTransportException error) {
            throw new GenerationSdkException("Model request failed: " + error.getMessage(), error);
        }
    }

    /** Configure before use. Copies share the SDK's registered contracts, not invocation state. */
    public GenUiGenerator withTiming(GenerationTiming value) {
        GenUiGenerator copy = scoped(transport);
        copy.timing = Objects.requireNonNull(value);
        return copy;
    }

    /** Host-supplied system prompt, e.g. the reference service's debug override. */
    public GenUiGenerator withSystemPrompt(String prompt) {
        GenUiGenerator copy = scoped(transport);
        copy.systemPromptOverride = prompt;
        return copy;
    }

    private GenUiGenerator scoped(LlmTransport scopedTransport) {
        GenUiGenerator copy = new GenUiGenerator(sdk, config, scopedTransport, validationConfig, validator);
        copy.timing = timing;
        copy.systemPromptOverride = systemPromptOverride;
        return copy;
    }

    private MarkdownGeneration markdown() {
        return new MarkdownGeneration(config, transport);
    }

    /**
     * 流式修复轮循环:构建增量提示词、重置轮状态、执行一次模型请求、合并并校验;通过或预算耗尽后返回。
     */
    private RoundEndResult runRepair(RepairLoop loop, RoundEndResult current,
            RepairRoundExecutor executor) throws LlmTransportException {
        int attempts = 0;
        int limit = validationConfig.maxIncrementalAttempts();
        while ((attempts == 0 || !loop.budget().expiredNow()) && (limit == 0 || attempts < limit)) {
            attempts++;
            String body = repairBody(loop, current);
            String previousOutput = loop.roundSession().rawOutput();
            loop.state().beginInFlight();
            loop.roundSession().beginRound();
            // A stream failure leaves inFlight set: the caller's partial snapshot records the interrupted round.
            executor.execute(body, previousOutput);
            current = completeRound(loop.roundSession(), loop.rounds(), loop.state());
            if (current.validationResult().status() == ValidationStatus.VALID) {
                break;
            }
        }
        return current;
    }

    /** Build the next repair request from the shared loop inputs and the latest merged program. */
    private String repairBody(RepairLoop loop, RoundEndResult current) {
        List<ChatMessage> messages = RepairPromptBuilder.build(loop.originalSystemPrompt(),
                userContext(loop.request()), loop.merged(), current, history(loop.rounds()));
        return buildBodyFromMessages(messages);
    }

    /** Executes one repair model request; {@code body} is the full chat-completion JSON. */
    @FunctionalInterface
    private interface RepairRoundExecutor {
        void execute(String body, String previousOutput) throws LlmTransportException;
    }

    /** Inputs for the stream incremental-repair loop. */
    private record RepairLoop(String originalSystemPrompt, UiGenerationRequest request,
            ProgramRoundSession roundSession, List<RoundEndResult> rounds, GenerationContract merged,
            GenerationSession budget, RepairLoopState state) {
    }

    private static List<RoundEndResult> history(List<RoundEndResult> rounds) {
        return rounds.size() <= 1 ? List.of() : List.copyOf(rounds.subList(0, rounds.size() - 1));
    }

    /**
     * Snapshot for an abnormal terminal: completed rounds plus, when a request is in flight, the interrupted round (raw
     * output so far, current definitions, no verdict). Not used on the normal path.
     */
    private static GenUiGenerationResult partialResult(UiGenerationRequest request, ProgramRoundSession roundSession,
            RepairLoopState state, List<RoundEndResult> rounds, GenerationEndReason endReason) {
        synchronized (state) {
            List<RoundEndResult> snapshot = new ArrayList<>(rounds);
            if (state.inFlight() && roundSession != null) {
                snapshot.add(new RoundEndResult(roundSession.rawOutput(), roundSession.currentDefinitions(), null,
                        roundSession.elapsedMs(), roundSession.contractVersion(), endReason));
            }
            ValidationResult last = snapshot.isEmpty() ? null : snapshot.get(snapshot.size() - 1).validationResult();
            return new GenUiGenerationResult(roundSession == null ? "" : roundSession.currentDefinitions(),
                    request.response(), last == null ? null : last.status(), last, endReason, snapshot);
        }
    }

    /** Mutable per-invocation markers shared with the failure snapshot; guarded by its own monitor. */
    private static final class RepairLoopState {
        private boolean inFlight;

        synchronized void beginInFlight() {
            inFlight = true;
        }

        synchronized void endInFlight() {
            inFlight = false;
        }

        synchronized boolean inFlight() {
            return inFlight;
        }
    }

    private GenUiLlmConfig repairConfig() {
        return GenUiLlmConfig.builder().endpoint(config.endpoint()).defaultModel(config.defaultModel()).temperature(0)
                .enableThinking(false).jsonObjectResponse(config.jsonObjectResponse())
                .extraHeaders(config.extraHeaders()).build();
    }

    /** Serialize repair chat messages into a transport body, reusing the generation request shape. */
    private String buildBodyFromMessages(List<ChatMessage> messages) {
        return ChatCompletionRequest.of(repairConfig(), null, messages, true).toJson();
    }

    /** Tag a repaired result's metadata with {@code repaired=true} for service-layer logging. */
    private static ValidationResult markRepaired(ValidationResult result) {
        if (result == null) {
            return null;
        }
        ValidationMetadata meta = result.metadata();
        LinkedHashMap<String, String> extra = new LinkedHashMap<>(
                meta == null || meta.extra() == null ? Map.of() : meta.extra());
        extra.put("repaired", "true");
        ValidationMetadata newMeta = new ValidationMetadata(meta == null ? 0 : meta.statementCount(),
                meta == null ? null : meta.rootName(), extra);
        return ValidationResult.valid(result.normalizedDsl(), result.issues(), newMeta);
    }

    /**
     * Stream realtime DSL, allowing the initial generation and first repair to finish. The 20-second budget gates
     * subsequent repairs only. On budget exhaustion with invalid DSL or model failure, emit a whole-page
     * {@code replace} and invoke the independent Markdown path. One {@code done} ends the entire invocation; Markdown
     * failure emits {@code error} before it.
     */
    public GenUiGenerationResult generateStream(UiGenerationRequest request, Consumer<RenderStreamEnvelope> sink) {
        return generateStream(request, sink, (original, reason, output) -> markdown().generateStream(original, output));
    }

    /** The host may hand off to its existing independent Markdown flow at this public seam. */
    public GenUiGenerationResult generateStream(UiGenerationRequest request, Consumer<RenderStreamEnvelope> sink,
            MarkdownFallback fallback) {
        long started = timing.nanoTime().getAsLong();
        Objects.requireNonNull(sink, "sink must not be null");
        Objects.requireNonNull(fallback, "fallback must not be null");
        UiGenerationRequest effective = request == null ? UiGenerationRequest.builder().build() : request;
        if ("Markdown".equals(effective.renderComponentsKey()))
            return markdown().generateStream(effective, sink);
        GenerationSession session = new GenerationSession(transport, timing, started, sink);
        var outcome = session.run(() -> {
            session.ensureInitial(effective.response());
            return scoped(session).generateStreamInternal(effective, session::emit, session);
        });
        session.ensureInitial(effective.response());
        if (outcome.failure() == null) {
            sink.accept(RenderStreamEnvelope.done(session.nextSeq()));
            return outcome.result();
        }
        sink.accept(RenderStreamEnvelope.replace(session.nextSeq(), outcome.failure()));
        try {
            GenUiGenerationResult result = fallback.generate(effective, outcome.failure(), event -> {
                if (!RenderStreamEnvelope.TYPE_DATA_MODEL.equals(event.type())
                        && !RenderStreamEnvelope.TYPE_DONE.equals(event.type())) {
                    sink.accept(new RenderStreamEnvelope(event.type(), session.nextSeq(), event.content()));
                }
            });
            sink.accept(RenderStreamEnvelope.done(session.nextSeq()));
            return result.withEndReason(outcome.failure(), roundsOf(outcome.result()));
        } catch (GenerationSdkException error) {
            sink.accept(
                    RenderStreamEnvelope.error(session.nextSeq(), "MARKDOWN_REQUEST_FAILED", error.getMessage(), true));
            sink.accept(RenderStreamEnvelope.done(session.nextSeq()));
            return new GenUiGenerationResult("", effective.response()).withEndReason(outcome.failure(),
                    roundsOf(outcome.result()));
        }
    }

    private static List<RoundEndResult> roundsOf(GenUiGenerationResult result) {
        return result == null ? List.of() : result.rounds();
    }

    /**
     * 轮内实时转发(#56),轮末统一校验后进入增量修复(#58)。修复片段同样实时转发;每轮请求关闭后才发起下一轮; 模型单轮结束不发 {@code done}。DISABLED 时跳过程序状态与校验。
     */
    private GenUiGenerationResult generateStreamInternal(UiGenerationRequest request,
            Consumer<RenderStreamEnvelope> sink, GenerationSession budget) {
        Objects.requireNonNull(sink, "sink must not be null");
        UiGenerationRequest effectiveRequest = request == null ? UiGenerationRequest.builder().build() : request;
        boolean validate = validationConfig.validationMode() != ValidationConfigMode.DISABLED;
        Map<String, Object> dataModel = effectiveRequest.response();
        GenUIPromptRequest promptRequest = toPromptRequest(effectiveRequest);
        GenerationContract merged = validate ? sdk.mergedContract(promptRequest) : null;
        ProgramRoundSession roundSession = validate ? newRoundSession(promptRequest, merged) : null;
        RepairLoopState state = new RepairLoopState();
        List<RoundEndResult> rounds = new ArrayList<>();
        // StringBuffer: the timeout/failure snapshot reads it from another thread while the stream thread appends.
        StringBuffer accumulated = new StringBuffer();
        if (roundSession != null) {
            budget.partialSnapshot(() -> partialResult(effectiveRequest, roundSession, state, rounds,
                    GenerationEndReason.MODEL_REQUEST_FAILED));
        } else {
            // DISABLED keeps validation off but must not discard partial streamed output on timeout/request failure.
            budget.partialSnapshot(() -> new GenUiGenerationResult(OpenuiCodeExtractor.extract(accumulated.toString()),
                    dataModel, null, null, GenerationEndReason.MODEL_REQUEST_FAILED, List.of()));
        }

        try {
            RequestBody requestBody = buildRequest(promptRequest, effectiveRequest, true);
            state.beginInFlight();
            try (InputStream stream = transport.postStream(requestBody.json())) {
                SseDeltaParser.parse(stream, delta -> {
                    sink.accept(RenderStreamEnvelope.dsl(0, delta)); // seq 由 GenerationSession 重排
                    if (roundSession != null) {
                        roundSession.onDelta(delta);
                    } else {
                        accumulated.append(delta);
                    }
                });
            }

            if (roundSession == null) {
                // DISABLED: raw deltas were forwarded and accumulated; no validation, no round record.
                return new GenUiGenerationResult(OpenuiCodeExtractor.extract(accumulated.toString()), dataModel);
            }

            RoundEndResult current = completeRound(roundSession, rounds, state);
            boolean incremental = validationConfig.validationMode() == ValidationConfigMode.INCREMENTAL;
            if (current.validationResult().status() == ValidationStatus.INVALID && incremental) {
                RepairLoop loop = new RepairLoop(requestBody.systemPrompt(), effectiveRequest,
                        roundSession, rounds, merged, budget, state);
                current = runIncrementalStream(loop, current, sink);
            }

            ValidationStatus status = current.validationResult().status();
            if (status == ValidationStatus.INVALID && incremental) {
                // Incremental repair could not produce a valid program: end unsuccessfully (no done for a failed
                // DSL session); the outer entry emits one replace and hands off to Markdown.
                budget.terminate(
                        budget.expiredNow() ? GenerationEndReason.TIMEOUT : GenerationEndReason.VALIDATION_FAILED);
            }
            boolean repaired = rounds.size() > 1;
            ValidationResult validation = status == ValidationStatus.VALID && repaired
                    ? markRepaired(current.validationResult())
                    : current.validationResult();
            return new GenUiGenerationResult(current.currentDefinitions(), dataModel, status, validation,
                    status == ValidationStatus.VALID
                            ? GenerationEndReason.CHECK_PASSED
                            : GenerationEndReason.VALIDATION_FAILED,
                    List.copyOf(rounds));
        } catch (LlmTransportException | IOException error) {
            // 流/修复请求中途失败:发 error 帧(GenerationSession 拦截 LLM_STREAM_FAILED 转 MODEL_REQUEST_FAILED),
            // 保留已完成轮与中断轮记录;轮末校验不运行(内容不可信)。
            sink.accept(RenderStreamEnvelope.error(0, "LLM_STREAM_FAILED", error.getMessage(), true));
            if (roundSession == null) {
                // DISABLED: no round record; keep the streamed prefix so the interrupted result is not empty.
                return new GenUiGenerationResult(OpenuiCodeExtractor.extract(accumulated.toString()), dataModel, null,
                        null, GenerationEndReason.MODEL_REQUEST_FAILED, List.of());
            }
            return partialResult(effectiveRequest, roundSession, state, rounds,
                    GenerationEndReason.MODEL_REQUEST_FAILED);
        }
    }

    /** 流式增量修复:每轮只转发模型输出的覆盖/新增语句,并合并进同一当前程序。 */
    private RoundEndResult runIncrementalStream(RepairLoop loop, RoundEndResult current,
            Consumer<RenderStreamEnvelope> sink) throws LlmTransportException {
        return runRepair(loop, current, (body, previousOutput) -> {
            // The frontend consumes one appended DSL stream, while each model request has its own
            // parser buffer. Complete the previous request's tail using the same existing auto-close
            // rule as round-end validation, then separate it from the next request with a newline.
            String boundary = OpenuiPreprocessor.streamRoundBoundary(previousOutput);
            if (!boundary.isEmpty()) {
                sink.accept(RenderStreamEnvelope.dsl(0, boundary));
            }
            try (InputStream stream = transport.postStream(body)) {
                SseDeltaParser.parse(stream, delta -> {
                    sink.accept(RenderStreamEnvelope.dsl(0, delta));
                    loop.roundSession().onDelta(delta);
                });
            } catch (IOException error) {
                throw new LlmTransportException(error.getMessage(), error);
            }
        });
    }

    private static RoundEndResult completeRound(ProgramRoundSession roundSession, List<RoundEndResult> rounds,
            RepairLoopState state) {
        synchronized (state) {
            RoundEndResult round = roundSession.onRoundEnd();
            rounds.add(round);
            state.endInFlight();
            return round;
        }
    }

    private RequestBody buildRequest(GenUIPromptRequest promptRequest, UiGenerationRequest request, boolean stream) {
        GenUIPromptAssemblyResult assembly = sdk.assemblePrompt(promptRequest);
        String systemPrompt = systemPromptOverride == null ? assembly.prompt() : systemPromptOverride;
        List<ChatMessage> messages = List.of(ChatMessage.system(systemPrompt), ChatMessage.user(userMessage(request)));
        return new RequestBody(ChatCompletionRequest.of(config, null, messages, stream).toJson(), systemPrompt);
    }

    private record RequestBody(String json, String systemPrompt) {
    }

    /** 每轮创建的轮末校验会话:使用当前请求生效契约 + 完整 Render Data Model,无第二份能力白名单。 */
    private ProgramRoundSession newRoundSession(GenUIPromptRequest promptRequest, GenerationContract merged) {
        return new ProgramRoundSession(validator, merged, merged.root(), externalRefsFor(promptRequest),
                dataModelFor(promptRequest));
    }

    /**
     * External reference names for validation: when the request carries a dataModel, the host binds {@code data} at
     * render time (the prompt teaches the model to use {@code data.<field>}), so the validator must treat it as
     * runtime-resolved. Without a dataModel a {@code data} reference is genuinely unresolved — inject nothing.
     */
    private static Set<String> externalRefsFor(GenUIPromptRequest promptRequest) {
        return promptRequest.dataModel() == null ? Set.of() : Set.of("data");
    }

    /**
     * Render Data Model for validation, or {@code null} when the request carries none. {@code
     * UiGenerationRequest.response()} normalizes absent to an EMPTY map (never null), while {@code
     * ValidationRequest.dataModel} distinguishes null (no model → type validation off, {@code data} unresolved) from an
     * empty map (concrete empty model). Translate at this seam so absent stays "no model".
     */
    private static Map<String, Object> dataModelFor(GenUIPromptRequest promptRequest) {
        DataModelSpec dataModel = promptRequest.dataModel();
        return dataModel == null || dataModel.raw().isEmpty() ? null : dataModel.raw();
    }

    private GenUIPromptRequest toPromptRequest(UiGenerationRequest request) {
        if (request.includeBasic() != null || !request.specifiedComponents().isEmpty()) {
            throw new IllegalArgumentException(
                    "Component filtering (includeBasic/specifiedComponents) is not supported by this SDK version");
        }
        List<String> extraRules = request.suggestion() == null || request.suggestion().isBlank()
                ? List.of()
                : List.of(request.suggestion());

        DataModelSpec dataModel = request.response().isEmpty()
                ? null
                : new DataModelSpec("Response data", request.response());
        return new GenUIPromptRequest(request.extensionId(), dataModel, extraRules, request.editMode(),
                request.inlineMode(), request.toolCalls(), request.bindings());
    }

    private String userMessage(UiGenerationRequest request) {
        return userContext(request) + " /no_think";
    }

    private String userContext(UiGenerationRequest request) {
        StringBuilder message = new StringBuilder();
        if (request.userInput() != null) {
            message.append(request.userInput());
        }
        if (!request.request().isEmpty()) {
            if (!message.isEmpty())
                message.append("\n\n");
            message.append("## Request Context\n```json\n").append(Json.stringifyPretty(request.request()))
                    .append("\n```");
        }
        return message.toString();
    }
}
