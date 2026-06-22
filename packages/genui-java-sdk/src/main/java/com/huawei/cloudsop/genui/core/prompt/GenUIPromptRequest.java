package com.huawei.cloudsop.genui.core.prompt;

import com.huawei.cloudsop.genui.core.contract.DataModelSpec;
import com.huawei.cloudsop.genui.core.contract.ToolSpec;
import java.util.List;

public record GenUIPromptRequest(
    String extensionId,
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
