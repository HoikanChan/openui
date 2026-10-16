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
import com.huawei.cloudsop.genui.core.validation.ValidationRequest;
import com.huawei.cloudsop.genui.core.validation.ValidationResult;
import com.huawei.cloudsop.genui.core.validation.ValidationStatus;
import com.huawei.cloudsop.genui.core.validation.repair.RepairCoordinator;
import com.huawei.cloudsop.genui.core.validation.repair.RepairPolicy;
import com.huawei.cloudsop.genui.core.validation.stream.GateDecision;
import com.huawei.cloudsop.genui.core.validation.stream.StreamingValidationSession;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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

            // ── FINAL validation gate ─────────────────────────────────────────────
            if (validationConfig.validationMode() != ValidationConfigMode.DISABLED) {
                GenUIPromptRequest promptRequest = toPromptRequest(effectiveRequest);
                GenerationContract merged = sdk.mergedContract(promptRequest);
                Set<String> externalRefs = externalRefsFor(promptRequest);

                ValidationRequest validationRequest = ValidationRequest.builder().dsl(extracted).contract(merged)
                        .rootName(merged.root()).externalRefs(externalRefs).dataModel(dataModelFor(effectiveRequest))
                        .mode(ValidationMode.FINAL).build();

                ValidationResult validationResult = validator.validate(validationRequest);

                if (validationResult.status() == ValidationStatus.INVALID) {
                    // Section 7: FINAL_REPAIR attempts sync whole-DSL repair before throwing; each attempt is
                    // re-validated in FINAL mode. FAIL_FAST_REASK is streaming-only (cannot run in sync) and
                    // NONE never repairs — both fall through to the throw below.
                    RepairPolicy policy = RepairPolicy.from(validationConfig.repairPolicy());
                    if (policy.kind() == RepairPolicyKind.FINAL_REPAIR) {
                        RepairCoordinator.FullRepairOutcome outcome = repairCoordinator(policy, externalRefs,
                                dataModelFor(effectiveRequest)).repairFull(userMessage(effectiveRequest), extracted,
                                        validationResult, merged, merged.root(), requestBody.systemPrompt());
                        if (outcome.repaired()) {
                            return new GenUiGenerationResult(outcome.dsl(), effectiveRequest.response(),
                                    ValidationStatus.VALID, markRepaired(outcome.result()));
                        }
                        // Exhausted / still invalid / timeout / transport error → throw with the last result.
                        ValidationResult last = outcome.result() == null ? validationResult : outcome.result();
                        throw new GenerationValidationException(
                                "Generated DSL failed validation after repair: " + buildIssueSummary(last), last);
                    }
                    String summary = buildIssueSummary(validationResult);
                    throw new GenerationValidationException("Generated DSL failed validation: " + summary,
                            validationResult);
                }

                return new GenUiGenerationResult(extracted, effectiveRequest.response(), validationResult.status(),
                        validationResult);
            }
            // ─────────────────────────────────────────────────────────────────────

            return new GenUiGenerationResult(extracted, effectiveRequest.response());
        } catch (GenerationValidationException e) {
            // Re-throw directly — do not wrap in a generic GenerationSdkException.
            throw e;
        } catch (LlmTransportException error) {
            throw new GenerationSdkException("Failed to invoke LLM: " + error.getMessage(), error);
        }
    }

    /** Build a repair coordinator bound to this generator's transport/validator + a policy. */
    private RepairCoordinator repairCoordinator(RepairPolicy policy, Set<String> externalRefs) {
        return repairCoordinator(policy, externalRefs, null);
    }

    /**
     * Build a repair coordinator that also carries the render data model, so every re-validation attempt runs the same
     * data-path/prop type validation as the initial FINAL gate.
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

    private GenUiGenerationResult generateStreamInternal(UiGenerationRequest request,
            Consumer<RenderStreamEnvelope> sink) {
        Objects.requireNonNull(sink, "sink must not be null");
        UiGenerationRequest effectiveRequest = request == null ? UiGenerationRequest.builder().build() : request;

        if (validationConfig.validationMode() == ValidationConfigMode.STREAMING_GATE) {
            return generateStreamGated(effectiveRequest, sink);
        }
        return generateStreamRaw(effectiveRequest, sink);
    }

    /**
     * DISABLED / FINAL_ONLY path: unchanged low-latency raw-delta streaming. In FINAL_ONLY, one FINAL validation runs
     * at stream end over the accumulated DSL to set {@code validationStatus} — the DSL is already delivered, so an
     * INVALID result does NOT emit a spurious {@code error} frame; it only marks the result so the service layer won't
     * cache it (Section 9).
     */
    private GenUiGenerationResult generateStreamRaw(UiGenerationRequest effectiveRequest,
            Consumer<RenderStreamEnvelope> sink) {
        Map<String, Object> dataModel = effectiveRequest.response();

        // 首帧:dataModel,seq=0。一旦发出,后续 LLM 流错误改为 error envelope 而不再抛给调用方。
        sink.accept(RenderStreamEnvelope.dataModel(dataModel));
        int[] nextSeq = {1};

        String body = buildRequestBody(effectiveRequest, true);
        StringBuilder accumulated = new StringBuilder();
        try (InputStream stream = transport.postStream(body)) {
            SseDeltaParser.parse(stream, delta -> {
                accumulated.append(delta);
                sink.accept(RenderStreamEnvelope.dsl(nextSeq[0]++, delta));
            });
            String extracted = OpenuiCodeExtractor.extract(accumulated.toString());
            sink.accept(RenderStreamEnvelope.done(nextSeq[0]++));

            if (validationConfig.validationMode() == ValidationConfigMode.FINAL_ONLY) {
                ValidationResult finalResult = finalValidate(effectiveRequest, extracted);
                return new GenUiGenerationResult(extracted, dataModel, finalResult.status(), finalResult);
            }
            // DISABLED: no validation status.
            return new GenUiGenerationResult(extracted, dataModel);
        } catch (LlmTransportException | IOException error) {
            sink.accept(RenderStreamEnvelope.error(nextSeq[0]++, "LLM_STREAM_FAILED", error.getMessage(), true));
            sink.accept(RenderStreamEnvelope.done(nextSeq[0]++));
            String extracted = OpenuiCodeExtractor.extract(accumulated.toString());
            // Transport failed mid-stream; content is partial. Do not run a tail validation here — the
            // error frame already signals SDK inability to produce a trustworthy final DSL.
            return new GenUiGenerationResult(extracted, dataModel);
        }
    }

    /**
     * STREAMING_GATE / streamingGateWithReask path: feed each LLM delta through a {@link StreamingValidationSession}.
     * Only SDK-accepted COMPLETE statements are emitted as {@code dsl} envelopes (never raw deltas). A
     * definitively-invalid completed statement is WITHHELD (its text never appears in any frame) and triggers
     * {@code error("VALIDATION_FAILED")} → {@code done}.
     */
    private GenUiGenerationResult generateStreamGated(UiGenerationRequest effectiveRequest,
            Consumer<RenderStreamEnvelope> sink) {
        Map<String, Object> dataModel = effectiveRequest.response();

        // 首帧:dataModel,seq=0。
        sink.accept(RenderStreamEnvelope.dataModel(dataModel));
        int[] nextSeq = {1};

        GenUIPromptRequest promptRequest = toPromptRequest(effectiveRequest);
        GenerationContract merged = sdk.mergedContract(promptRequest);
        Set<String> externalRefs = externalRefsFor(promptRequest);
        StreamingValidationSession session = new StreamingValidationSession(validator, merged, merged.root(),
                externalRefs, dataModelFor(effectiveRequest));
        RepairPolicy policy = RepairPolicy.from(validationConfig.repairPolicy());

        RequestBody requestBody = buildRequest(effectiveRequest, true);
        try (InputStream stream = transport.postStream(requestBody.json())) {
            boolean withheld = consumeGatedStream(stream, session, sink, nextSeq, true);

            if (withheld) {
                stream.close(); // release the previous request before any reask
                // Section 7 Fail-Fast Reask: cancel-then-reask when policy is FAIL_FAST_REASK; otherwise the
                // withheld statement is terminal (error -> done, INVALID). Reask reuses the SAME session/gate
                // continuing from the accepted prefix; only accepted repaired DSL is emitted. seq is shared.
                if (policy.kind() == RepairPolicyKind.FAIL_FAST_REASK && failFastReask(effectiveRequest, merged,
                        externalRefs, session, sink, nextSeq, policy, requestBody.systemPrompt())) {
                    // Reask succeeded: continuation emitted accepted DSL and closed cleanly via onEnd().
                    sink.accept(RenderStreamEnvelope.done(nextSeq[0]++));
                    return new GenUiGenerationResult(session.acceptedDsl(), dataModel, ValidationStatus.VALID,
                            session.latestValidationResult());
                }
                // Terminal failure (no reask, or reask exhausted/failed): emit error -> done, INVALID.
                sink.accept(
                        RenderStreamEnvelope.error(nextSeq[0]++, "VALIDATION_FAILED", withheldMessage(session), false));
                sink.accept(RenderStreamEnvelope.done(nextSeq[0]++));
                return new GenUiGenerationResult(session.acceptedDsl(), dataModel, ValidationStatus.INVALID,
                        session.withheldResult());
            }

            // Clean end: buffered statements were flushed by onEnd(); close the stream.
            sink.accept(RenderStreamEnvelope.done(nextSeq[0]++));
            ValidationResult finalResult = session.latestValidationResult();
            return new GenUiGenerationResult(session.acceptedDsl(), dataModel, ValidationStatus.VALID, finalResult);
        } catch (LlmTransportException | IOException error) {
            sink.accept(RenderStreamEnvelope.error(nextSeq[0]++, "LLM_STREAM_FAILED", error.getMessage(), true));
            sink.accept(RenderStreamEnvelope.done(nextSeq[0]++));
            return new GenUiGenerationResult(session.acceptedDsl(), dataModel, ValidationStatus.INVALID,
                    session.latestValidationResult());
        }
    }

    /**
     * Drive one stream through the gate/session. Consumption STOPS at the first withheld statement (fail-fast): the
     * original stream is not read past that point (a {@link StopStreamSignal} unwinds the SSE parser). EMIT decisions
     * are forwarded as {@code dsl} envelopes; BUFFER stays internal. No {@code error}/{@code done} frames are emitted
     * here — the caller decides terminal vs reask.
     *
     * @param runOnEnd
     *            whether to flush buffered statements via {@link StreamingValidationSession#onEnd} at clean
     *            end-of-stream (the reask continuation runs onEnd via its own driver call).
     * @return {@code true} if a statement was withheld (fail-fast), {@code false} on a clean end.
     */
    private boolean consumeGatedStream(InputStream stream, StreamingValidationSession session,
            Consumer<RenderStreamEnvelope> sink, int[] nextSeq, boolean runOnEnd) throws IOException {
        try {
            SseDeltaParser.parse(stream, delta -> {
                if (applyDecisions(session.onDelta(delta), sink, nextSeq)) {
                    throw new StopStreamSignal(); // withheld — stop reading the original stream now
                }
            });
        } catch (StopStreamSignal stop) {
            return true;
        }
        if (runOnEnd && applyDecisions(session.onEnd(), sink, nextSeq)) {
            return true;
        }
        return false;
    }

    /**
     * Fail-Fast Reask (design Decision #6). Cancel-then-reask: the original stream is already stopped. Reset the
     * session to continue from its accepted prefix, then open ONE new continuation stream (bounded by
     * {@link RepairPolicy#maxAttempts}) and feed it through the SAME gate. The bad statement text (original AND any
     * reask-bad) never enters a {@code dsl} frame. Returns {@code
     * true} only if a reask round completed cleanly (final DSL valid); {@code false} if every round still withheld /
     * the stream failed / the attempt budget was exhausted.
     */
    private boolean failFastReask(UiGenerationRequest effectiveRequest, GenerationContract merged,
            Set<String> externalRefs, StreamingValidationSession session, Consumer<RenderStreamEnvelope> sink,
            int[] nextSeq, RepairPolicy policy, String originalSystemPrompt) {
        RepairCoordinator coordinator = repairCoordinator(policy, externalRefs);
        for (int attempt = 1; attempt <= policy.maxAttempts(); attempt++) {
            String acceptedPrefix = session.acceptedDsl();
            String invalidStatement = session.withheldStatement();
            List<com.huawei.cloudsop.genui.core.validation.ValidationIssue> issues = session.withheldResult() == null
                    ? List.of()
                    : session.withheldResult().issues();

            // Prepare the session to continue from the accepted prefix on the fresh continuation stream.
            session.resetForReask();

            boolean clean = coordinator.reaskStream(userMessage(effectiveRequest), acceptedPrefix, invalidStatement,
                    issues, merged, originalSystemPrompt,
                    newStream -> !consumeGatedStream(newStream, session, sink, nextSeq, true));
            if (clean && !session.hasWithheld()) {
                return true; // continuation produced only valid DSL
            }
            // else: reask still withheld a statement → loop for another attempt (if budget remains).
        }
        return false;
    }

    /** Control-flow signal to unwind the blocking SSE parser at a fail-fast withhold point. */
    private static final class StopStreamSignal extends RuntimeException {
        StopStreamSignal() {
            super(null, null, false, false); // no message, no stack trace — pure control flow
        }
    }

    /**
     * Apply gate decisions to the sink. Forwards EMIT decisions as {@code dsl} envelopes; a WITHHOLD/FAIL (definitively
     * invalid) returns {@code true} to signal the stream must stop — but emits NO frame (the caller chooses terminal
     * error vs reask). BUFFER stays internal (no frame).
     *
     * @return {@code true} if a definitively-invalid statement was encountered (fail-fast)
     */
    private boolean applyDecisions(List<GateDecision> decisions, Consumer<RenderStreamEnvelope> sink, int[] nextSeq) {
        for (GateDecision decision : decisions) {
            switch (decision.kind()) {
                case EMIT -> sink.accept(RenderStreamEnvelope.dsl(nextSeq[0]++, decision.statementText()));
                case BUFFER -> {
                    // held internally — no envelope until flushed
                }
                case WITHHOLD, FAIL -> {
                    return true; // fail-fast trigger; caller decides terminal error vs reask
                }
                default -> {
                    // no-op
                }
            }
        }
        return false;
    }

    private static String withheldMessage(StreamingValidationSession session) {
        ValidationResult result = session.withheldResult();
        if (result == null) {
            return "Generated DSL failed streaming validation";
        }
        return "Generated DSL failed streaming validation: " + buildIssueSummary(result);
    }

    /** Run a FINAL validation over the accumulated DSL (FINAL_ONLY tail check). */
    private ValidationResult finalValidate(UiGenerationRequest request, String dsl) {
        GenUIPromptRequest promptRequest = toPromptRequest(request);
        GenerationContract merged = sdk.mergedContract(promptRequest);
        ValidationRequest validationRequest = ValidationRequest.builder().dsl(dsl).contract(merged)
                .rootName(merged.root()).externalRefs(externalRefsFor(promptRequest)).dataModel(dataModelFor(request))
                .mode(ValidationMode.FINAL).build();
        return validator.validate(validationRequest);
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
