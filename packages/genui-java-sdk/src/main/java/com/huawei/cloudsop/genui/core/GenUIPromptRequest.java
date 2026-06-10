package com.huawei.cloudsop.genui.core;

import java.util.List;

public record GenUIPromptRequest(
    String contextId,
    DataModelSpec dataModel,
    List<ToolSpec> tools,
    List<String> extraRules,
    Boolean editMode,
    Boolean inlineMode,
    Boolean toolCalls,
    Boolean bindings) {
  public GenUIPromptRequest {
    tools = tools == null ? List.of() : List.copyOf(tools);
    extraRules = extraRules == null ? List.of() : List.copyOf(extraRules);
  }
}
