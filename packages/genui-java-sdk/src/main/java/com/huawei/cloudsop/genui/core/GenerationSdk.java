package com.huawei.cloudsop.genui.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GenerationSdk {
  private final GenerationContract baseContract;
  private final Map<String, GenUIContextExtension> extensions = new LinkedHashMap<>();

  private GenerationSdk(GenerationContract baseContract) {
    if (baseContract == null) throw new GenerationSdkException("baseContract is required");
    if (baseContract.contractVersion() == null || baseContract.contractVersion().isBlank()) {
      throw new GenerationSdkException("baseContract.contractVersion is required");
    }
    this.baseContract = baseContract;
    validateComponentGroups(baseContract.componentGroups(), baseContract.components().keySet(), "base contract");
  }

  public static GenerationSdk create() {
    return builder().build();
  }

  public static Builder builder() {
    return new Builder();
  }

  public GenerationContract baseContract() {
    return baseContract;
  }

  public void register(GenUIContextExtension extension) {
    if (extension == null) throw new GenerationSdkException("extension is required");
    if (extension.contextId() == null || extension.contextId().isBlank()) {
      throw new GenerationSdkException("extension.contextId is required");
    }

    Set<String> baseComponentNames = baseContract.components().keySet();
    Set<String> extensionComponentNames = extension.components().keySet();
    Set<String> componentCollisions = intersection(baseComponentNames, extensionComponentNames);
    if (!componentCollisions.isEmpty()) {
      throw new GenerationSdkException("Component name collision: " + String.join(", ", componentCollisions));
    }

    LinkedHashSet<String> finalComponentNames = new LinkedHashSet<>(baseComponentNames);
    finalComponentNames.addAll(extensionComponentNames);
    validateComponentGroups(extension.componentGroups(), finalComponentNames, "extension " + extension.contextId());

    Set<String> baseToolNames = toolNames(baseContract.tools());
    Set<String> extensionToolNames = toolNames(extension.tools());
    Set<String> toolCollisions = intersection(baseToolNames, extensionToolNames);
    if (!toolCollisions.isEmpty()) {
      throw new GenerationSdkException("Tool name collision: " + String.join(", ", toolCollisions));
    }

    extensions.put(extension.contextId(), extension);
  }

  public GenUIPromptAssemblyResult assemblePrompt(GenUIPromptRequest request) {
    GenUIPromptRequest effectiveRequest =
        request == null ? new GenUIPromptRequest(null, null, List.of(), List.of(), null, null, null, null) : request;
    GenUIContextExtension extension =
        effectiveRequest.contextId() == null ? null : extensions.get(effectiveRequest.contextId());

    LinkedHashMap<String, ComponentPromptSpec> components = new LinkedHashMap<>();
    components.putAll(baseContract.components());
    if (extension != null) components.putAll(extension.components());

    ArrayList<ComponentGroup> componentGroups = new ArrayList<>(baseContract.componentGroups());
    if (extension != null) componentGroups.addAll(extension.componentGroups());
    validateComponentGroups(componentGroups, components.keySet(), "assembled context");

    ArrayList<ToolSpec> registeredTools = new ArrayList<>(baseContract.tools());
    if (extension != null) registeredTools.addAll(extension.tools());
    Set<String> registeredToolNames = toolNames(registeredTools);
    Set<String> requestToolNames = toolNames(effectiveRequest.tools());
    Set<String> requestCollisions = intersection(registeredToolNames, requestToolNames);
    if (!requestCollisions.isEmpty()) {
      throw new GenerationSdkException("Tool name collision: " + String.join(", ", requestCollisions));
    }

    ArrayList<ToolSpec> allTools = new ArrayList<>(registeredTools);
    allTools.addAll(effectiveRequest.tools());

    ArrayList<String> examples = new ArrayList<>(baseContract.examples());
    if (extension != null) examples.addAll(extension.examples());

    ArrayList<String> additionalRules = new ArrayList<>(baseContract.additionalRules());
    if (extension != null) additionalRules.addAll(extension.additionalRules());
    additionalRules.addAll(effectiveRequest.extraRules());

    String prompt =
        PromptAssembler.assemble(
            new PromptAssembler.PromptInput(
                null, // preamble — the SDK always uses the default openui-lang preamble
                baseContract.root(),
                components,
                componentGroups,
                effectiveRequest.dataModel(),
                allTools,
                examples,
                additionalRules,
                effectiveRequest.editMode(),
                effectiveRequest.inlineMode(),
                effectiveRequest.toolCalls(),
                effectiveRequest.bindings()),
            baseContract.builtins());

    GenUIPromptAssemblyMetadata metadata =
        new GenUIPromptAssemblyMetadata(
            effectiveRequest.contextId(),
            baseContract.contractVersion(),
            extension == null ? null : extension.version(),
            new ArrayList<>(registeredToolNames),
            new ArrayList<>(requestToolNames));
    return new GenUIPromptAssemblyResult(prompt, metadata);
  }

  private static void validateComponentGroups(
      List<ComponentGroup> groups, Set<String> knownComponentNames, String scope) {
    LinkedHashSet<String> missing = new LinkedHashSet<>();
    for (ComponentGroup group : groups) {
      for (String componentName : group.components()) {
        if (!knownComponentNames.contains(componentName)) missing.add(componentName);
      }
    }
    if (!missing.isEmpty()) {
      throw new GenerationSdkException(
          "Component group references missing component(s) in " + scope + ": " + String.join(", ", missing));
    }
  }

  private static Set<String> toolNames(List<ToolSpec> tools) {
    LinkedHashSet<String> names = new LinkedHashSet<>();
    LinkedHashSet<String> duplicates = new LinkedHashSet<>();
    for (ToolSpec tool : tools) {
      if (tool.name() == null || tool.name().isBlank()) {
        throw new GenerationSdkException("Tool name is required");
      }
      if (!names.add(tool.name())) duplicates.add(tool.name());
    }
    if (!duplicates.isEmpty()) {
      throw new GenerationSdkException("Tool name collision: " + String.join(", ", duplicates));
    }
    return names;
  }

  private static Set<String> intersection(Set<String> left, Set<String> right) {
    LinkedHashSet<String> result = new LinkedHashSet<>(left);
    result.retainAll(right);
    return result;
  }

  public static final class Builder {
    private GenerationContract baseContract;

    private Builder() {}

    public Builder baseContract(GenerationContract baseContract) {
      this.baseContract = baseContract;
      return this;
    }

    public GenerationSdk build() {
      return new GenerationSdk(baseContract == null ? GenerationContractLoader.loadDefault() : baseContract);
    }
  }
}
