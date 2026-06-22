package com.huawei.cloudsop.genui.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GenerationSdk {
  private final GenerationContract baseContract;
  private final Map<String, GenUIGeneration> generations = new LinkedHashMap<>();

  private GenerationSdk(GenerationContract baseContract) {
    if (baseContract == null) throw new GenerationSdkException("baseContract is required");
    if (baseContract.contractVersion() == null || baseContract.contractVersion().isBlank()) {
      throw new GenerationSdkException("baseContract.contractVersion is required");
    }
    this.baseContract = baseContract;
    validateComponents(baseContract.components(), "base contract");
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

  public void register(GenUIGeneration generation) {
    if (generation == null) throw new GenerationSdkException("generation is required");
    if (generation.generationId() == null || generation.generationId().isBlank()) {
      throw new GenerationSdkException("generation.generationId is required");
    }
    validateComponents(generation.components(), "generation " + generation.generationId());

    Set<String> baseComponentNames = baseContract.components().keySet();
    Set<String> generationComponentNames = generation.components().keySet();
    Set<String> componentCollisions = intersection(baseComponentNames, generationComponentNames);
    if (!componentCollisions.isEmpty()) {
      throw new GenerationSdkException("Component name collision: " + String.join(", ", componentCollisions));
    }

    LinkedHashSet<String> finalComponentNames = new LinkedHashSet<>(baseComponentNames);
    finalComponentNames.addAll(generationComponentNames);
    validateComponentGroups(generation.componentGroups(), finalComponentNames, "generation " + generation.generationId());

    Set<String> baseToolNames = toolNames(baseContract.tools());
    Set<String> generationToolNames = toolNames(generation.tools());
    Set<String> toolCollisions = intersection(baseToolNames, generationToolNames);
    if (!toolCollisions.isEmpty()) {
      throw new GenerationSdkException("Tool name collision: " + String.join(", ", toolCollisions));
    }

    generations.put(generation.generationId(), generation);
  }

  public GenUIPromptAssemblyResult assemblePrompt(GenUIPromptRequest request) {
    GenUIPromptRequest effectiveRequest =
        request == null ? new GenUIPromptRequest(null, null, List.of(), List.of(), null, null, null, null) : request;
    GenUIGeneration generation =
        effectiveRequest.generationId() == null ? null : generations.get(effectiveRequest.generationId());

    LinkedHashMap<String, ComponentPromptSpec> components = new LinkedHashMap<>();
    components.putAll(baseContract.components());
    if (generation != null) components.putAll(generation.components());

    ArrayList<ComponentGroup> componentGroups = new ArrayList<>(baseContract.componentGroups());
    if (generation != null) componentGroups.addAll(generation.componentGroups());
    validateComponentGroups(componentGroups, components.keySet(), "assembled context");

    ArrayList<ToolSpec> registeredTools = new ArrayList<>(baseContract.tools());
    if (generation != null) registeredTools.addAll(generation.tools());
    Set<String> registeredToolNames = toolNames(registeredTools);
    Set<String> requestToolNames = toolNames(effectiveRequest.tools());
    Set<String> requestCollisions = intersection(registeredToolNames, requestToolNames);
    if (!requestCollisions.isEmpty()) {
      throw new GenerationSdkException("Tool name collision: " + String.join(", ", requestCollisions));
    }

    ArrayList<ToolSpec> allTools = new ArrayList<>(registeredTools);
    allTools.addAll(effectiveRequest.tools());

    ArrayList<String> examples = new ArrayList<>(baseContract.examples());
    if (generation != null) examples.addAll(generation.examples());

    ArrayList<String> additionalRules = new ArrayList<>(baseContract.additionalRules());
    if (generation != null) additionalRules.addAll(generation.additionalRules());
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
            effectiveRequest.generationId(),
            baseContract.contractVersion(),
            generation == null ? null : generation.version(),
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

  private static void validateComponents(Map<String, ComponentPromptSpec> components, String scope) {
    for (Map.Entry<String, ComponentPromptSpec> entry : components.entrySet()) {
      if (entry.getKey() == null || entry.getKey().isBlank()) {
        throw new GenerationSdkException("Component name is required in " + scope);
      }
      ComponentPropsSchema.validate(entry.getKey(), entry.getValue());
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
