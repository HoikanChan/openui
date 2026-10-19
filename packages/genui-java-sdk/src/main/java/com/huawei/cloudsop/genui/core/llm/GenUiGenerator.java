/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.llm;

import com.huawei.cloudsop.genui.core.GenerationSdk;
import com.huawei.cloudsop.genui.core.GenerationSdkException;
import com.huawei.cloudsop.genui.core.GenerationValidationException;
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
import com.huawei.cloudsop.genui.core.validation.RepairPolicyKind;
import com.huawei.cloudsop.genui.core.validation.ValidationConfigMode;
import com.huawei.cloudsop.genui.core.validation.ValidationMetadata;
import com.huawei.cloudsop.genui.core.validation.ValidationMode;
import com.huawei.cloudsop.genui.core.validation.ValidationResult;
import com.huawei.cloudsop.genui.core.validation.ValidationStatus;
import com.huawei.cloudsop.genui.core.validation.repair.RepairCoordinator;
import com.huawei.cloudsop.genui.core.validation.repair.RepairPolicy;
import com.huawei.cloudsop.genui.core.validation.stream.ProgramRoundSession;
import com.huawei.cloudsop.genui.core.validation.stream.RoundEndResult;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 公开生成入口(#56:首次生成实时展示,轮末统一校验当前程序)。
 *
 * <p>
 * 生成过程不因校验而扣留内容、不逐语句修复、不增加生成前模型请求;模型本轮输出结束后,对<b>当前有效程序</b>(同名语句
 * last-wins)执行一次统一校验(语法、引用、组件与算子契约、参数、数据路径、静态类型、页面完整性)。检查发现的错误交给 已有失败处理:同步入口抛 {@link GenerationValidationException} 或按
 * FINAL_REPAIR 整份修复;流式入口在返回结果上标记 状态。增量修复循环见 #58(未在本类落地)。
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
        this.validationConfig = validationConfig == null ? GenUiValidationConfig.finalOnly() : validationConfig;
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
     * If {@code validationConfig} is null, defaults to {@link GenUiValidationConfig#finalOnly()}. If {@code validator}
     * is null, defaults to {@link DefaultOpenuiLangValidator}.
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

    public GenUiGenerator register(GenUIExtension extension) {
        sdk.register(extension);
        return this;
    }

    /**
     * 同步生成:一次模型请求,轮末对当前有效程序统一校验。同名覆盖后旧定义及其错误不再生效;结果 dsl 是去重后的当前 有效定义。INVALID 时按已有失败处理:NONE 抛
     * {@link GenerationValidationException},FINAL_REPAIR 走整份修复。
     */
    public GenUiGenerationResult generate(UiGenerationRequest request) {
        long started = timing.nanoTime().getAsLong();
        UiGenerationRequest effective = request == null ? UiGenerationRequest.builder().build() : request;
        if ("Markdown".equals(effective.renderComponentsKey()))
            return markdown().generate(effective);
        GenerationSession session = new GenerationSession(transport, timing, started, null);
        var outcome = session.run(() -> scoped(session).generateInternal(effective));
        if (outcome.failure() == null)
            return outcome.result();
        return markdown().generate(effective).withEndReason(outcome.failure());
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

    private GenUiGenerationResult generateInternal(UiGenerationRequest request) {
        UiGenerationRequest effectiveRequest = request == null ? UiGenerationRequest.builder().build() : request;
        try {
            RequestBody requestBody = buildRequest(effectiveRequest, false);
            String response = transport.post(requestBody.json());
            String content = ChatCompletionResponse.parse(response).firstContent();
            String extracted = OpenuiCodeExtractor.extract(content);

            if (validationConfig.validationMode() == ValidationConfigMode.DISABLED) {
                return new GenUiGenerationResult(extracted, effectiveRequest.response());
            }

            // ── 轮末统一校验:当前程序 = 同名 last-wins 的有效定义 ─────────────
            GenerationContract merged = mergedContract(effectiveRequest);
            ProgramRoundSession session = newRoundSession(effectiveRequest, merged);
            session.onDelta(extracted);
            RoundEndResult round = session.onRoundEnd();
            ValidationResult validationResult = round.validationResult();

            if (validationResult.status() == ValidationStatus.INVALID) {
                // Section 7: FINAL_REPAIR attempts sync whole-DSL repair before throwing; each attempt is
                // re-validated in FINAL mode. NONE never repairs — both fall through to the throw below.
                RepairPolicy policy = RepairPolicy.from(validationConfig.repairPolicy());
                if (policy.kind() == RepairPolicyKind.FINAL_REPAIR) {
                    RepairCoordinator.FullRepairOutcome outcome = repairCoordinator(policy,
                            externalRefsFor(toPromptRequest(effectiveRequest)), dataModelFor(effectiveRequest))
                            .repairFull(userMessage(effectiveRequest), round.currentDefinitions(), validationResult,
                                    merged, merged.root(), requestBody.systemPrompt());
                    if (outcome.repaired()) {
                        return new GenUiGenerationResult(outcome.dsl(), effectiveRequest.response(),
                                ValidationStatus.VALID, markRepaired(outcome.result()), null, List.of(round));
                    }
                    // Exhausted / still invalid / timeout / transport error → throw with the last result.
                    ValidationResult last = outcome.result() == null ? validationResult : outcome.result();
                    throw new GenerationValidationException(
                            "Generated DSL failed validation after repair: " + buildIssueSummary(last), last);
                }
                throw new GenerationValidationException(
                        "Generated DSL failed validation: " + buildIssueSummary(validationResult), validationResult);
            }

            return new GenUiGenerationResult(round.currentDefinitions(), effectiveRequest.response(),
                    validationResult.status(), validationResult, null, List.of(round));
        } catch (GenerationValidationException e) {
            // Re-throw directly — do not wrap in a generic GenerationSdkException.
            throw e;
        } catch (LlmTransportException error) {
            throw new GenerationSdkException("Failed to invoke LLM: " + error.getMessage(), error);
        }
    }

    private GenUiLlmConfig repairConfig() {
        return GenUiLlmConfig.builder().endpoint(config.endpoint()).defaultModel(config.defaultModel()).temperature(0)
                .enableThinking(false).jsonObjectResponse(config.jsonObjectResponse())
                .extraHeaders(config.extraHeaders()).build();
    }

    /** Serialize repair chat messages into a transport body, reusing the generation request shape. */
    private String buildBodyFromMessages(List<ChatMessage> messages, boolean stream) {
        return ChatCompletionRequest.of(repairConfig(), null, messages, stream).toJson();
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
                meta == null ? null : meta.rootName(), meta == null ? ValidationMode.FINAL : meta.mode(), extra);
        return ValidationResult.valid(result.normalizedDsl(), result.issues(), newMeta);
    }

    /** Summarize the first few blocking issues for the exception message. */
    private static String buildIssueSummary(ValidationResult result) {
        return result.issues().stream()
                .filter(i -> i.severity() == com.huawei.cloudsop.genui.core.validation.ValidationSeverity.ERROR)
                .limit(3).map(i -> "[" + i.code() + "] " + i.message()).collect(Collectors.joining("; "));
    }

    /**
     * Stream realtime DSL under one 20-second deadline. On timeout/model failure, cancel the DSL request, emit a
     * whole-page {@code replace}, and invoke the independent Markdown path. One {@code done} ends the entire
     * invocation; Markdown failure emits {@code error} before it.
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
            return scoped(session).generateStreamInternal(effective, session::emit);
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
            return result.withEndReason(outcome.failure());
        } catch (GenerationSdkException error) {
            sink.accept(
                    RenderStreamEnvelope.error(session.nextSeq(), "MARKDOWN_REQUEST_FAILED", error.getMessage(), true));
            sink.accept(RenderStreamEnvelope.done(session.nextSeq()));
            return new GenUiGenerationResult("", effective.response()).withEndReason(outcome.failure());
        }
    }

    /**
     * 轮内实时转发(#56):不因校验扣留、不逐语句修复;流干净结束后由外层 {@code done} 收尾,轮末统一校验结果随 返回值携带。DISABLED 时跳过程序状态与校验。
     */
    private GenUiGenerationResult generateStreamInternal(UiGenerationRequest request,
            Consumer<RenderStreamEnvelope> sink) {
        Objects.requireNonNull(sink, "sink must not be null");
        UiGenerationRequest effectiveRequest = request == null ? UiGenerationRequest.builder().build() : request;
        boolean validate = validationConfig.validationMode() != ValidationConfigMode.DISABLED;
        Map<String, Object> dataModel = effectiveRequest.response();

        String body = buildRequestBody(effectiveRequest, true);
        ProgramRoundSession roundSession = validate
                ? newRoundSession(effectiveRequest, mergedContract(effectiveRequest))
                : null;
        StringBuilder accumulated = new StringBuilder();
        try (InputStream stream = transport.postStream(body)) {
            SseDeltaParser.parse(stream, delta -> {
                sink.accept(RenderStreamEnvelope.dsl(0, delta)); // seq 由 GenerationSession 重排
                if (roundSession != null) {
                    roundSession.onDelta(delta);
                }
            });

            if (roundSession == null) {
                // DISABLED: raw accumulation, no validation, no round record.
                return new GenUiGenerationResult(OpenuiCodeExtractor.extract(accumulated.toString()), dataModel);
            }
            // 轮末统一校验:流已干净收尾,状态由返回结果承载(不发 error 帧);endReason 随校验结论推导。
            RoundEndResult round = roundSession.onRoundEnd();
            ValidationStatus status = round.validationResult().status();
            return new GenUiGenerationResult(round.currentDefinitions(), dataModel, status, round.validationResult(),
                    status == ValidationStatus.VALID
                            ? GenerationEndReason.CHECK_PASSED
                            : GenerationEndReason.VALIDATION_FAILED,
                    List.of(round));
        } catch (LlmTransportException | IOException error) {
            // 流中途失败:发 error 帧(GenerationSession 拦截 LLM_STREAM_FAILED 转 MODEL_REQUEST_FAILED 并触发
            // 整页 Markdown 降级),尽力返回已累计的当前程序;不运行轮末校验(内容不可信)。
            sink.accept(RenderStreamEnvelope.error(0, "LLM_STREAM_FAILED", error.getMessage(), true));
            return new GenUiGenerationResult(roundSession == null
                    ? OpenuiCodeExtractor.extract(accumulated.toString())
                    : roundSession.drainToCurrentDefinitions(), dataModel);
        }
    }

    /** Build a repair coordinator bound to this generator's transport/validator + a policy. */
    private RepairCoordinator repairCoordinator(RepairPolicy policy, Set<String> externalRefs) {
        return repairCoordinator(policy, externalRefs, null);
    }

    /**
     * Build a repair coordinator that also carries the render data model, so every re-validation attempt runs the same
     * data-path/prop type validation as the initial round-end check.
     */
    private RepairCoordinator repairCoordinator(RepairPolicy policy, Set<String> externalRefs,
            Map<String, Object> dataModel) {
        return new RepairCoordinator(transport, validator, policy).withBodyBuilder(this::buildBodyFromMessages)
                .withExternalRefs(externalRefs).withDataModel(dataModel);
    }

    private String buildRequestBody(UiGenerationRequest request, boolean stream) {
        return buildRequest(request, stream).json();
    }

    private RequestBody buildRequest(UiGenerationRequest request, boolean stream) {
        UiGenerationRequest effectiveRequest = request == null ? UiGenerationRequest.builder().build() : request;
        GenUIPromptAssemblyResult assembly = sdk.assemblePrompt(toPromptRequest(effectiveRequest));
        String systemPrompt = systemPromptOverride == null ? assembly.prompt() : systemPromptOverride;
        List<ChatMessage> messages = List.of(ChatMessage.system(systemPrompt),
                ChatMessage.user(userMessage(effectiveRequest)));
        return new RequestBody(ChatCompletionRequest.of(config, null, messages, stream).toJson(), systemPrompt);
    }

    private record RequestBody(String json, String systemPrompt) {
    }

    /** 每轮创建的轮末校验会话:使用当前请求生效契约 + 完整 Render Data Model,无第二份能力白名单。 */
    private ProgramRoundSession newRoundSession(UiGenerationRequest request, GenerationContract merged) {
        return new ProgramRoundSession(validator, merged, merged.root(), externalRefsFor(toPromptRequest(request)),
                dataModelFor(request));
    }

    private GenerationContract mergedContract(UiGenerationRequest request) {
        return sdk.mergedContract(toPromptRequest(request));
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
    private static Map<String, Object> dataModelFor(UiGenerationRequest request) {
        return request.response().isEmpty() ? null : request.response();
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
        message.append(" /no_think");
        return message.toString();
    }
}
