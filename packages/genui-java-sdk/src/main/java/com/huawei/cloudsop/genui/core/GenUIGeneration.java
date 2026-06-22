package com.huawei.cloudsop.genui.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record GenUIGeneration(
    String extensionId,
    String version,
    Map<String, ComponentPromptSpec> components,
    List<ComponentGroup> componentGroups,
    List<ToolSpec> tools,
    List<String> examples,
    List<String> additionalRules) {
  public GenUIGeneration {
    components =
        components == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(components));
    componentGroups = componentGroups == null ? List.of() : List.copyOf(componentGroups);
    tools = tools == null ? List.of() : List.copyOf(tools);
    examples = examples == null ? List.of() : List.copyOf(examples);
    additionalRules = additionalRules == null ? List.of() : List.copyOf(additionalRules);
  }
}
