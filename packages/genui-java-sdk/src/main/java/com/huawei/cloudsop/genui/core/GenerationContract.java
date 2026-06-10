package com.huawei.cloudsop.genui.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record GenerationContract(
    String contractVersion,
    String root,
    Map<String, ComponentPromptSpec> components,
    List<ComponentGroup> componentGroups,
    List<ToolSpec> tools,
    List<String> examples,
    List<String> additionalRules,
    List<BuiltinSpec> builtins) {
  public GenerationContract {
    // Preserve insertion order — component order is significant for prompt byte-alignment,
    // so use an order-preserving unmodifiable LinkedHashMap (NOT Map.copyOf, which reorders).
    components =
        components == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(components));
    componentGroups = componentGroups == null ? List.of() : List.copyOf(componentGroups);
    tools = tools == null ? List.of() : List.copyOf(tools);
    examples = examples == null ? List.of() : List.copyOf(examples);
    additionalRules = additionalRules == null ? List.of() : List.copyOf(additionalRules);
    builtins = builtins == null ? List.of() : List.copyOf(builtins);
  }

  /** Backwards-compatible constructor for contracts assembled without a builtins manifest. */
  public GenerationContract(
      String contractVersion,
      String root,
      Map<String, ComponentPromptSpec> components,
      List<ComponentGroup> componentGroups,
      List<ToolSpec> tools,
      List<String> examples,
      List<String> additionalRules) {
    this(contractVersion, root, components, componentGroups, tools, examples, additionalRules, List.of());
  }
}
