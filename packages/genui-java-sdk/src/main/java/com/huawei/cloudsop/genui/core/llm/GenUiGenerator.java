package com.huawei.cloudsop.genui.core.llm;

import com.huawei.cloudsop.genui.core.GenerationSdk;
import com.huawei.cloudsop.genui.core.GenerationSdkException;
import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.core.contract.DataModelSpec;
import com.huawei.cloudsop.genui.core.contract.GenUIGeneration;
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
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class GenUiGenerator {
  private final GenerationSdk sdk;
  private final GenUiLlmConfig config;
  private final LlmTransport transport;

  private GenUiGenerator(GenerationSdk sdk, GenUiLlmConfig config, LlmTransport transport) {
    this.sdk = Objects.requireNonNull(sdk, "sdk must not be null");
    this.config = config == null ? GenUiLlmConfig.defaults() : config;
    this.transport = Objects.requireNonNull(transport, "transport must not be null");
  }

  public static GenUiGenerator create() {
    return create(GenUiLlmConfig.defaults());
  }

  public static GenUiGenerator create(GenUiLlmConfig config) {
    GenUiLlmConfig effectiveConfig = config == null ? GenUiLlmConfig.defaults() : config;
    return new GenUiGenerator(
        GenerationSdk.create(), effectiveConfig, new RestfulLlmTransport(effectiveConfig));
  }

  public static GenUiGenerator withTransport(GenUiLlmConfig config, LlmTransport transport) {
    return new GenUiGenerator(GenerationSdk.create(), config, transport);
  }

  public GenUiGenerator register(GenUIGeneration extension) {
    sdk.register(extension);
    return this;
  }

  public String generate(UiGenerationRequest request) {
    try {
      String body = buildRequestBody(request, false);
      String response = transport.post(body);
      return OpenuiCodeExtractor.extract(ChatCompletionResponse.parse(response).firstContent());
    } catch (LlmTransportException error) {
      throw new GenerationSdkException("Failed to invoke LLM: " + error.getMessage(), error);
    }
  }

  public String generateStream(UiGenerationRequest request, Consumer<String> sink) {
    Objects.requireNonNull(sink, "sink must not be null");
    try (InputStream stream = transport.postStream(buildRequestBody(request, true))) {
      String accumulated = SseDeltaParser.parse(stream, sink);
      return OpenuiCodeExtractor.extract(accumulated);
    } catch (LlmTransportException error) {
      throw new GenerationSdkException("Failed to invoke LLM stream: " + error.getMessage(), error);
    } catch (IOException error) {
      throw new GenerationSdkException("Failed to read LLM stream: " + error.getMessage(), error);
    }
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
    ArrayList<String> extraRules = new ArrayList<>(request.overlayRules());
    if (request.suggestion() != null && !request.suggestion().isBlank()) {
      extraRules.add(request.suggestion());
    }

    DataModelSpec dataModel =
        request.response().isEmpty() ? null : new DataModelSpec("Response data", request.response());
    return new GenUIPromptRequest(
        request.extensionId(),
        dataModel,
        request.overlayTools(),
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
