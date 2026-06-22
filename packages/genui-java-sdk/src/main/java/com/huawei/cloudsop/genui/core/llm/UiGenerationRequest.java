package com.huawei.cloudsop.genui.core.llm;

import com.huawei.cloudsop.genui.core.contract.ToolSpec;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record UiGenerationRequest(
    String extensionId,
    String userInput,
    Map<String, Object> request,
    Map<String, Object> response,
    String suggestion,
    List<ToolSpec> overlayTools,
    List<String> overlayRules,
    Boolean editMode,
    Boolean inlineMode,
    Boolean toolCalls,
    Boolean bindings) {
  public UiGenerationRequest {
    request = immutableOrderedMap(request);
    response = immutableOrderedMap(response);
    overlayTools = overlayTools == null ? List.of() : List.copyOf(overlayTools);
    overlayRules = overlayRules == null ? List.of() : List.copyOf(overlayRules);
  }

  private static Map<String, Object> immutableOrderedMap(Map<String, Object> value) {
    return value == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(value));
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private String extensionId;
    private String userInput;
    private Map<String, Object> request;
    private Map<String, Object> response;
    private String suggestion;
    private List<ToolSpec> overlayTools;
    private List<String> overlayRules;
    private Boolean editMode;
    private Boolean inlineMode;
    private Boolean toolCalls;
    private Boolean bindings;

    private Builder() {}

    public Builder extensionId(String extensionId) {
      this.extensionId = extensionId;
      return this;
    }

    public Builder userInput(String userInput) {
      this.userInput = userInput;
      return this;
    }

    public Builder request(Map<String, Object> request) {
      this.request = request;
      return this;
    }

    public Builder response(Map<String, Object> response) {
      this.response = response;
      return this;
    }

    public Builder suggestion(String suggestion) {
      this.suggestion = suggestion;
      return this;
    }

    public Builder overlayTools(List<ToolSpec> overlayTools) {
      this.overlayTools = overlayTools;
      return this;
    }

    public Builder overlayRules(List<String> overlayRules) {
      this.overlayRules = overlayRules;
      return this;
    }

    public Builder editMode(Boolean editMode) {
      this.editMode = editMode;
      return this;
    }

    public Builder inlineMode(Boolean inlineMode) {
      this.inlineMode = inlineMode;
      return this;
    }

    public Builder toolCalls(Boolean toolCalls) {
      this.toolCalls = toolCalls;
      return this;
    }

    public Builder bindings(Boolean bindings) {
      this.bindings = bindings;
      return this;
    }

    public UiGenerationRequest build() {
      return new UiGenerationRequest(
          extensionId,
          userInput,
          request,
          response,
          suggestion,
          overlayTools,
          overlayRules,
          editMode,
          inlineMode,
          toolCalls,
          bindings);
    }
  }
}
