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
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransport;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransportException;
import com.huawei.cloudsop.genui.core.llm.transport.RestfulLlmTransport;
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
        return new GenUiGenerator(GenerationSdk.create(), effectiveConfig, new RestfulLlmTransport(effectiveConfig));
    }

    public static GenUiGenerator create(GenUiLlmConfig config, CharacterizationConfig characterization) {
        GenUiLlmConfig effectiveConfig = config == null ? GenUiLlmConfig.defaults() : config;
        GenerationSdk sdk = GenerationSdk.builder()
                .characterization(characterization == null ? CharacterizationConfig.defaults() : characterization)
                .build();
        return new GenUiGenerator(sdk, effectiveConfig, new RestfulLlmTransport(effectiveConfig));
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
                                ValidationStatus.VALID, markRepaired(outcome.result()), List.of(round));
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
                    validationResult.status(), validationResult, List.of(round));
        } catch (GenerationValidationException e) {
            // Re-throw directly — do not wrap in a generic GenerationSdkException.
            throw e;
        } catch (LlmTransportException error) {
            throw new GenerationSdkException("Failed to invoke LLM: " + error.getMessage(), error);
        }
    }

    /**
     * 流式生成(#56 实时展示)。首帧固定发出 {@code dataModel} envelope(seq=0),随后每个模型 delta 实时转发为 {@code dsl} envelope(seq
     * 递增)——不因校验扣留、不做逐语句门控、不增加生成前请求。模型本轮输出结束并干净收尾后, 在返回结果上携带轮末统一校验(当前有效程序)与轮记录;校验失败不发 error 帧(状态由结果承载,错误交给已有失败处理)。
     * 首帧发出后若流读取/传输失败,SDK 不向调用方抛出该异常,而是发出 {@code error} 再发 {@code done},并返回已累计内容 的当前定义提取结果(不运行轮末校验——内容不可信)。
     */
    public GenUiGenerationResult generateStream(UiGenerationRequest request, Consumer<RenderStreamEnvelope> sink) {
        Objects.requireNonNull(sink, "sink must not be null");
        UiGenerationRequest effectiveRequest = request == null ? UiGenerationRequest.builder().build() : request;
        boolean validate = validationConfig.validationMode() != ValidationConfigMode.DISABLED;
        Map<String, Object> dataModel = effectiveRequest.response();

        // 首帧:dataModel,seq=0。一旦发出,后续 LLM 流错误改为 error envelope 而不再抛给调用方。
        sink.accept(RenderStreamEnvelope.dataModel(dataModel));
        int[] nextSeq = {1};

        String body = buildRequestBody(effectiveRequest, true);
        ProgramRoundSession session = validate
                ? newRoundSession(effectiveRequest, mergedContract(effectiveRequest))
                : null;
        StringBuilder accumulated = new StringBuilder();
        try (InputStream stream = transport.postStream(body)) {
            SseDeltaParser.parse(stream, delta -> {
                accumulated.append(delta);
                sink.accept(RenderStreamEnvelope.dsl(nextSeq[0]++, delta)); // 实时转发,不扣留
                if (session != null) {
                    session.onDelta(delta);
                }
            });
            sink.accept(RenderStreamEnvelope.done(nextSeq[0]++));

            if (session == null) {
                // DISABLED: raw accumulation, no validation, no round record.
                return new GenUiGenerationResult(OpenuiCodeExtractor.extract(accumulated.toString()), dataModel);
            }
            // 轮末统一校验:流已收尾(done 帧已发),状态由返回结果承载。
            RoundEndResult round = session.onRoundEnd();
            return new GenUiGenerationResult(round.currentDefinitions(), dataModel, round.validationResult().status(),
                    round.validationResult(), List.of(round));
        } catch (LlmTransportException | IOException error) {
            sink.accept(RenderStreamEnvelope.error(nextSeq[0]++, "LLM_STREAM_FAILED", error.getMessage(), true));
            sink.accept(RenderStreamEnvelope.done(nextSeq[0]++));
            // Transport failed mid-stream; content is partial. Do not run a round-end validation — the
            // error frame already signals SDK inability to produce a trustworthy final DSL. The returned
            // DSL is the best-effort current program (drained, unvalidated).
            return new GenUiGenerationResult(session == null
                    ? OpenuiCodeExtractor.extract(accumulated.toString())
                    : session.drainToCurrentDefinitions(), dataModel);
        }
    }

    /** 每轮创建的轮末校验会话:使用当前请求生效契约 + 完整 Render Data Model,无第二份能力白名单。 */
    private ProgramRoundSession newRoundSession(UiGenerationRequest request, GenerationContract merged) {
        return new ProgramRoundSession(validator, merged, merged.root(), externalRefsFor(toPromptRequest(request)),
                dataModelFor(request));
    }

    private GenerationContract mergedContract(UiGenerationRequest request) {
        return sdk.mergedContract(toPromptRequest(request));
    }

    /** Build a repair coordinator bound to this generator's transport/validator + a policy. */
    private RepairCoordinator repairCoordinator(RepairPolicy policy, Set<String> externalRefs) {
        return repairCoordinator(policy, externalRefs, null);
    }

    /**
     * Build a repair coordinator that also carries the render data model, so every re-validation attempt runs the same
     * data-path/prop type validation as the initial round-end gate.
     */
    private RepairCoordinator repairCoordinator(RepairPolicy policy, Set<String> externalRefs,
            Map<String, Object> dataModel) {
        return new RepairCoordinator(transport, validator, policy).withBodyBuilder(this::buildBodyFromMessages)
                .withExternalRefs(externalRefs).withDataModel(dataModel);
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

    /** Serialize repair chat messages into a transport body, reusing the generation request shape. */
    private String buildBodyFromMessages(List<ChatMessage> messages, boolean stream) {
        return ChatCompletionRequest.of(repairConfig(), null, messages, stream).toJson();
    }

    private GenUiLlmConfig repairConfig() {
        return GenUiLlmConfig.builder().endpoint(config.endpoint()).defaultModel(config.defaultModel()).temperature(0)
                .enableThinking(false).jsonObjectResponse(config.jsonObjectResponse())
                .extraHeaders(config.extraHeaders()).build();
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

    private String buildRequestBody(UiGenerationRequest request, boolean stream) {
        return buildRequest(request, stream).json();
    }

    private RequestBody buildRequest(UiGenerationRequest request, boolean stream) {
        UiGenerationRequest effectiveRequest = request == null ? UiGenerationRequest.builder().build() : request;
        GenUIPromptAssemblyResult assembly = sdk.assemblePrompt(toPromptRequest(effectiveRequest));
        List<ChatMessage> messages = List.of(ChatMessage.system(assembly.prompt()),
                ChatMessage.user(userMessage(effectiveRequest)));
        return new RequestBody(ChatCompletionRequest.of(config, null, messages, stream).toJson(), assembly.prompt());
    }

    private record RequestBody(String json, String systemPrompt) {
    }

    private GenUIPromptRequest toPromptRequest(UiGenerationRequest request) {
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
