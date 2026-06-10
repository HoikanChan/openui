package com.huawei.cloudsop.genui.core;

import java.util.List;

public record GenUIPromptAssemblyMetadata(
    String contextId,
    String baseContractVersion,
    String extensionVersion,
    List<String> registeredToolNames,
    List<String> requestToolNames) {
  public GenUIPromptAssemblyMetadata {
    registeredToolNames = registeredToolNames == null ? List.of() : List.copyOf(registeredToolNames);
    requestToolNames = requestToolNames == null ? List.of() : List.copyOf(requestToolNames);
  }
}
