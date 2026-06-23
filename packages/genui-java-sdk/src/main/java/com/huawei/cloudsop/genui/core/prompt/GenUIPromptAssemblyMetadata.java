package com.huawei.cloudsop.genui.core.prompt;

import java.util.List;

public record GenUIPromptAssemblyMetadata(
    String extensionId,
    String baseContractVersion,
    String generationVersion,
    List<String> registeredToolNames) {
  public GenUIPromptAssemblyMetadata {
    registeredToolNames = registeredToolNames == null ? List.of() : List.copyOf(registeredToolNames);
  }
}
