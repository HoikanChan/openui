package com.huawei.cloudsop.genui.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record GenUIContextExtension(
    String contextId,
    String version,
    Map<String, ComponentPromptSpec> components,
    List<ComponentGroup> componentGroups,
    List<ToolSpec> tools,
    List<String> examples,
    List<String> additionalRules) {
  public GenUIContextExtension {
    // Preserve component insertion order — significant for prompt byte-alignment (NOT Map.copyOf).
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
