package com.huawei.cloudsop.genui.core.llm;

import com.huawei.cloudsop.genui.core.GenerationSdk;
import com.huawei.cloudsop.genui.core.GenerationSdkException;
import com.huawei.cloudsop.genui.core.GenerationValidationException;
import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.core.contract.DataModelSpec;
import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import com.huawei.cloudsop.genui.core.contract.GenUIExtension;
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
import com.huawei.cloudsop.genui.core.validation.ValidationMode;
import com.huawei.cloudsop.genui.core.validation.ValidationRequest;
import com.huawei.cloudsop.genui.core.validation.ValidationResult;
import com.huawei.cloudsop.genui.core.validation.ValidationStatus;
import com.huawei.cloudsop.genui.core.validation.stream.GateDecision;
import com.huawei.cloudsop.genui.core.validation.stream.StreamingValidationSession;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class GenUiGenerator {
  private final GenerationSdk sdk;
  private final GenUiLlmConfig config;
  private final LlmTransport transport;
  private final GenUiValidationConfig validationConfig;
  private final OpenuiLangValidator validator;

  private GenUiGenerator(
      GenerationSdk sdk,
      GenUiLlmConfig config,
      LlmTransport transport,
      GenUiValidationConfig validationConfig,
      OpenuiLangValidator validator) {
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
    return new GenUiGenerator(
        GenerationSdk.create(), effectiveConfig, new RestfulLlmTransport(effectiveConfig));
  }

  public static GenUiGenerator create(GenUiLlmConfig config, CharacterizationConfig characterization) {
    GenUiLlmConfig effectiveConfig = config == null ? GenUiLlmConfig.defaults() : config;
    GenerationSdk sdk =
        GenerationSdk.builder()
            .characterization(
                characterization == null ? CharacterizationConfig.defaults() : characterization)
            .build();
    return new GenUiGenerator(sdk, effectiveConfig, new RestfulLlmTransport(effectiveConfig));
  }

  public static GenUiGenerator withTransport(GenUiLlmConfig config, LlmTransport transport) {
    return new GenUiGenerator(GenerationSdk.create(), config, transport);
  }

  public static GenUiGenerator withTransport(
      GenUiLlmConfig config, CharacterizationConfig characterization, LlmTransport transport) {
    GenerationSdk sdk =
        GenerationSdk.builder()
            .characterization(
                characterization == null ? CharacterizationConfig.defaults() : characterization)
            .build();
    return new GenUiGenerator(sdk, config, transport);
  }

  /**
   * Create a generator backed by a fake/test transport, a custom validation config, and an optional
   * custom validator. If {@code validationConfig} is null, defaults to {@link
   * GenUiValidationConfig#finalOnly()}. If {@code validator} is null, defaults to
   * {@link DefaultOpenuiLangValidator}.
   */
  public static GenUiGenerator withTransport(
      GenUiLlmConfig config,
      LlmTransport transport,
      GenUiValidationConfig validationConfig,
      OpenuiLangValidator validator) {
    return new GenUiGenerator(
        GenerationSdk.create(), config, transport, validationConfig, validator);
  }

  public GenUiGenerator register(GenUIExtension extension) {
    sdk.register(extension);
    return this;
  }

  public GenUiGenerationResult generate(UiGenerationRequest request) {
    UiGenerationRequest effectiveRequest =
        request == null ? UiGenerationRequest.builder().build() : request;
    try {
      String body = buildRequestBody(effectiveRequest, false);
      String response = transport.post(body);
      String content = ChatCompletionResponse.parse(response).firstContent();
      String extracted = OpenuiCodeExtractor.extract(content);

      // ── FINAL validation gate ─────────────────────────────────────────────
      if (validationConfig.validationMode() != ValidationConfigMode.DISABLED) {
        GenUIPromptRequest promptRequest = toPromptRequest(effectiveRequest);
        GenerationContract merged = sdk.mergedContract(promptRequest);

        ValidationRequest validationRequest = ValidationRequest.builder()
            .dsl(extracted)
            .contract(merged)
            .rootName(merged.root())
            .mode(ValidationMode.FINAL)
            .build();

        ValidationResult validationResult = validator.validate(validationRequest);

        if (validationResult.status() == ValidationStatus.INVALID) {
          // TODO(Section 7): if repairPolicy == FINAL_REPAIR, attempt sync repair here; re-validate;
          // only throw if still INVALID.
          // FAIL_FAST_REASK is streaming-only and cannot execute in sync; NONE never repairs.
          // All INVALID -> throw.
          String summary = buildIssueSummary(validationResult);
          throw new GenerationValidationException(
              "Generated DSL failed validation: " + summary, validationResult);
        }

        return new GenUiGenerationResult(
            extracted, effectiveRequest.response(), validationResult.status(), validationResult);
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

  /** Summarize the first few blocking issues for the exception message. */
  private static String buildIssueSummary(ValidationResult result) {
    return result.issues().stream()
        .filter(i -> i.severity() == com.huawei.cloudsop.genui.core.validation.ValidationSeverity.ERROR)
        .limit(3)
        .map(i -> "[" + i.code() + "] " + i.message())
        .collect(Collectors.joining("; "));
  }

  /**
   * 流式生成。首帧固定发出 {@code dataModel} envelope(seq=0),随后每个模型 delta 发出
   * {@code dsl} envelope(seq 递增),正常结束发 {@code done}。首帧发出后若流读取/传输失败,SDK 不
   * 向调用方抛出该异常,而是发出 {@code error} 再发 {@code done},并返回已累计内容的提取结果。
   */
  public GenUiGenerationResult generateStream(
      UiGenerationRequest request, Consumer<RenderStreamEnvelope> sink) {
    Objects.requireNonNull(sink, "sink must not be null");
    UiGenerationRequest effectiveRequest =
        request == null ? UiGenerationRequest.builder().build() : request;

    if (validationConfig.validationMode() == ValidationConfigMode.STREAMING_GATE) {
      return generateStreamGated(effectiveRequest, sink);
    }
    return generateStreamRaw(effectiveRequest, sink);
  }

  /**
   * DISABLED / FINAL_ONLY path: unchanged low-latency raw-delta streaming. In FINAL_ONLY, one FINAL
   * validation runs at stream end over the accumulated DSL to set {@code validationStatus} — the DSL
   * is already delivered, so an INVALID result does NOT emit a spurious {@code error} frame; it only
   * marks the result so the service layer won't cache it (Section 9).
   */
  private GenUiGenerationResult generateStreamRaw(
      UiGenerationRequest effectiveRequest, Consumer<RenderStreamEnvelope> sink) {
    Map<String, Object> dataModel = effectiveRequest.response();

    // 首帧:dataModel,seq=0。一旦发出,后续 LLM 流错误改为 error envelope 而不再抛给调用方。
    sink.accept(RenderStreamEnvelope.dataModel(dataModel));
    int[] nextSeq = {1};

    String body = buildRequestBody(effectiveRequest, true);
    StringBuilder accumulated = new StringBuilder();
    try (InputStream stream = transport.postStream(body)) {
      SseDeltaParser.parse(
          stream,
          delta -> {
            accumulated.append(delta);
            sink.accept(RenderStreamEnvelope.dsl(nextSeq[0]++, delta));
          });
      String extracted = OpenuiCodeExtractor.extract(accumulated.toString());
      sink.accept(RenderStreamEnvelope.done(nextSeq[0]++));

      if (validationConfig.validationMode() == ValidationConfigMode.FINAL_ONLY) {
        ValidationResult finalResult = finalValidate(effectiveRequest, extracted);
        return new GenUiGenerationResult(
            extracted, dataModel, finalResult.status(), finalResult);
      }
      // DISABLED: no validation status.
      return new GenUiGenerationResult(extracted, dataModel);
    } catch (LlmTransportException | IOException error) {
      sink.accept(
          RenderStreamEnvelope.error(nextSeq[0]++, "LLM_STREAM_FAILED", error.getMessage(), true));
      sink.accept(RenderStreamEnvelope.done(nextSeq[0]++));
      String extracted = OpenuiCodeExtractor.extract(accumulated.toString());
      // Transport failed mid-stream; content is partial. Do not run a tail validation here — the
      // error frame already signals SDK inability to produce a trustworthy final DSL.
      return new GenUiGenerationResult(extracted, dataModel);
    }
  }

  /**
   * STREAMING_GATE / streamingGateWithReask path: feed each LLM delta through a {@link
   * StreamingValidationSession}. Only SDK-accepted COMPLETE statements are emitted as {@code dsl}
   * envelopes (never raw deltas). A definitively-invalid completed statement is WITHHELD (its text
   * never appears in any frame) and triggers {@code error("VALIDATION_FAILED")} → {@code done}.
   */
  private GenUiGenerationResult generateStreamGated(
      UiGenerationRequest effectiveRequest, Consumer<RenderStreamEnvelope> sink) {
    Map<String, Object> dataModel = effectiveRequest.response();

    // 首帧:dataModel,seq=0。
    sink.accept(RenderStreamEnvelope.dataModel(dataModel));
    int[] nextSeq = {1};

    GenerationContract merged = sdk.mergedContract(toPromptRequest(effectiveRequest));
    StreamingValidationSession session =
        new StreamingValidationSession(validator, merged, merged.root());
    boolean[] failed = {false};

    String body = buildRequestBody(effectiveRequest, true);
    try (InputStream stream = transport.postStream(body)) {
      SseDeltaParser.parse(
          stream,
          delta -> {
            if (failed[0]) {
              return; // already failed fast; ignore trailing deltas
            }
            if (applyDecisions(session.onDelta(delta), session, sink, nextSeq)) {
              failed[0] = true;
            }
          });

      if (!failed[0]) {
        if (applyDecisions(session.onEnd(), session, sink, nextSeq)) {
          failed[0] = true;
        }
      }

      if (failed[0]) {
        // Fail path already emitted the error frame; just close.
        sink.accept(RenderStreamEnvelope.done(nextSeq[0]++));
        return new GenUiGenerationResult(
            session.acceptedDsl(), dataModel, ValidationStatus.INVALID, session.withheldResult());
      }

      // Clean end: buffered statements were flushed by onEnd(); close the stream.
      sink.accept(RenderStreamEnvelope.done(nextSeq[0]++));
      ValidationResult finalResult = session.latestValidationResult();
      return new GenUiGenerationResult(
          session.acceptedDsl(), dataModel, ValidationStatus.VALID, finalResult);
    } catch (LlmTransportException | IOException error) {
      sink.accept(
          RenderStreamEnvelope.error(nextSeq[0]++, "LLM_STREAM_FAILED", error.getMessage(), true));
      sink.accept(RenderStreamEnvelope.done(nextSeq[0]++));
      return new GenUiGenerationResult(
          session.acceptedDsl(), dataModel, ValidationStatus.INVALID, session.latestValidationResult());
    }
  }

  /**
   * Apply gate decisions to the sink. Forwards EMIT decisions as {@code dsl} envelopes; on a
   * WITHHOLD/FAIL (definitively invalid) emits {@code error("VALIDATION_FAILED")} and returns {@code
   * true} to signal the stream must stop. BUFFER decisions are held inside the session (no frame).
   *
   * @return {@code true} if a definitively-invalid statement was encountered (fail-fast)
   */
  private boolean applyDecisions(
      List<GateDecision> decisions,
      StreamingValidationSession session,
      Consumer<RenderStreamEnvelope> sink,
      int[] nextSeq) {
    for (GateDecision decision : decisions) {
      switch (decision.kind()) {
        case EMIT ->
            sink.accept(RenderStreamEnvelope.dsl(nextSeq[0]++, decision.statementText()));
        case BUFFER -> {
          // held internally — no envelope until flushed
        }
        case WITHHOLD, FAIL -> {
          // Section 7 seam: when validationConfig.repairPolicy() == FAIL_FAST_REASK, Section 7 will
          // cancel the stream + reask here, feeding session.withheldResult() issues back to the LLM
          // and resuming. For THIS task, FAIL_FAST_REASK behaves identically to the non-reask fail
          // path (error -> done, validationStatus=INVALID).
          // TODO(Section 7): cancel stream + reask here (only when repairPolicy == FAIL_FAST_REASK).
          String message = withheldMessage(session);
          sink.accept(
              RenderStreamEnvelope.error(nextSeq[0]++, "VALIDATION_FAILED", message, false));
          return true;
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
    GenerationContract merged = sdk.mergedContract(toPromptRequest(request));
    ValidationRequest validationRequest =
        ValidationRequest.builder()
            .dsl(dsl)
            .contract(merged)
            .rootName(merged.root())
            .mode(ValidationMode.FINAL)
            .build();
    return validator.validate(validationRequest);
  }

  private String buildRequestBody(UiGenerationRequest request, boolean stream) {
    UiGenerationRequest effectiveRequest =
        request == null ? UiGenerationRequest.builder().build() : request;
    GenUIPromptAssemblyResult assembly = sdk.assemblePrompt(toPromptRequest(effectiveRequest));
    List<ChatMessage> messages =
        List.of(
            ChatMessage.system(assembly.prompt()),
            ChatMessage.user(userMessage(effectiveRequest)));
    return ChatCompletionRequest.of(config, null, messages, stream).toJson();
  }

  private GenUIPromptRequest toPromptRequest(UiGenerationRequest request) {
    List<String> extraRules =
        request.suggestion() == null || request.suggestion().isBlank()
            ? List.of()
            : List.of(request.suggestion());

    DataModelSpec dataModel =
        request.response().isEmpty() ? null : new DataModelSpec("Response data", request.response());
    return new GenUIPromptRequest(
        request.extensionId(),
        dataModel,
        extraRules,
        request.editMode(),
        request.inlineMode(),
        request.toolCalls(),
        request.bindings());
  }

  private String userMessage(UiGenerationRequest request) {
    StringBuilder message = new StringBuilder();
    if (request.userInput() != null) {
      message.append(request.userInput());
    }
    if (!request.request().isEmpty()) {
      if (!message.isEmpty()) message.append("\n\n");
      message.append("## Request Context\n```json\n")
          .append(Json.stringifyPretty(request.request()))
          .append("\n```");
    }
    message.append(" /no_think");
    return message.toString();
  }
}
