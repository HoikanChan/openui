/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core;

import com.huawei.cloudsop.genui.core.contract.CapabilityGapSpec;
import com.huawei.cloudsop.genui.core.contract.ComponentGroup;
import com.huawei.cloudsop.genui.core.contract.ComponentPromptSpec;
import com.huawei.cloudsop.genui.core.contract.ComponentPropsSchema;
import com.huawei.cloudsop.genui.core.contract.DataModelSpec;
import com.huawei.cloudsop.genui.core.contract.GenUIBaseSupplement;
import com.huawei.cloudsop.genui.core.contract.GenUIExtension;
import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import com.huawei.cloudsop.genui.core.contract.GenerationContractLoader;
import com.huawei.cloudsop.genui.core.contract.ToolSpec;
import com.huawei.cloudsop.genui.core.prompt.GenUIPromptAssemblyMetadata;
import com.huawei.cloudsop.genui.core.prompt.GenUIPromptAssemblyResult;
import com.huawei.cloudsop.genui.core.prompt.GenUIPromptRequest;
import com.huawei.cloudsop.genui.core.prompt.PromptAssembler;
import com.huawei.cloudsop.genui.core.prompt.characterize.CharacterizationConfig;
import com.huawei.cloudsop.genui.core.prompt.characterize.Characterized;
import com.huawei.cloudsop.genui.core.prompt.characterize.Characterizer;
import com.huawei.cloudsop.genui.core.prompt.characterize.TsTypeRenderer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Facade for assembling openui-lang generation prompts from a base contract plus registered
 * extensions.
 *
 * <p>The base contract is validated once at construction time. {@link #register(GenUIExtension)}
 * adds extensions, while {@link #assemblePrompt(GenUIPromptRequest)} and {@link
 * #mergedContract(GenUIPromptRequest)} compute the merged view for a single request without
 * mutating the base contract. The SDK never calls an LLM; it only produces prompt text and metadata
 * for a caller-supplied transport.
 *
 * @since 2026
 */
public final class GenerationSdk {
    private final GenerationContract baseContract;
    private final CharacterizationConfig characterization;
    private final Map<String, GenUIExtension> generations = new LinkedHashMap<>();

    private GenerationSdk(
            GenerationContract baseContract,
            GenUIBaseSupplement baseSupplement,
            CharacterizationConfig characterization) {
        if (baseContract == null) {
            throw new GenerationSdkException("baseContract is required");
        }
        if (baseContract.contractVersion() == null || baseContract.contractVersion().isBlank()) {
            throw new GenerationSdkException("baseContract.contractVersion is required");
        }
        validateComponents(baseContract.components(), "base contract");
        validateComponentGroups(
                baseContract.componentGroups(),
                baseContract.components().keySet(),
                "base contract");
        GenerationContractLoader.validateCapabilityGaps(
                baseContract.capabilityGaps(),
                baseContract.components().keySet(),
                baseContract.builtins(),
                "base contract");
        if (baseSupplement != null) {
            // Validate the supplement's own components and merged result under "base supplement"
            // so schema/group/capabilityGap errors introduced by the supplement attribute to the
            // supplement.
            try {
                validateComponents(baseSupplement.components(), "base supplement");
                LinkedHashSet<String> effectiveComponents =
                        new LinkedHashSet<>(baseContract.components().keySet());
                effectiveComponents.addAll(baseSupplement.components().keySet());
                GenerationContractLoader.validateCapabilityGaps(
                        baseSupplement.capabilityGaps(),
                        effectiveComponents,
                        baseContract.builtins(),
                        "base supplement");
                baseContract = baseSupplement.applyTo(baseContract);
                validateComponentGroups(
                        baseContract.componentGroups(),
                        baseContract.components().keySet(),
                        "base supplement");
                GenerationContractLoader.validateCapabilityGaps(
                        baseContract.capabilityGaps(),
                        baseContract.components().keySet(),
                        baseContract.builtins(),
                        "base supplement");
            } catch (GenerationSdkException error) {
                throw new GenerationSdkException(
                        "Invalid base supplement: " + error.getMessage(), error);
            }
        }
        this.baseContract = baseContract;
        this.characterization =
                characterization == null ? CharacterizationConfig.defaults() : characterization;
    }

    /**
     * Creates an SDK from the bundled default contract with default characterization settings.
     *
     * <p>Equivalent to {@code builder().build()}.
     *
     * @return a ready-to-use SDK instance
     */
    public static GenerationSdk create() {
        return builder().build();
    }

    /**
     * Returns a fresh builder for configuring a custom base contract or base supplement.
     *
     * @return a new builder with no base contract selected
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the base contract in effect, including any base supplement applied at build time.
     *
     * @return the effective base contract, never {@code null}
     */
    public GenerationContract baseContract() {
        return baseContract;
    }

    /**
     * Registers a generation extension whose components, tools and capability gaps are merged into
     * the base contract when a request names its {@code extensionId}.
     *
     * <p>Duplicate component names, tool names or capability gap ids, a blank extension id, an
     * invalid component schema, or a component group referencing an unknown component all raise
     * {@link GenerationSdkException} before anything is stored. Registering the same id twice
     * replaces the earlier extension.
     *
     * @param generation the extension to register; must have a non-blank id
     * @throws GenerationSdkException if the extension is {@code null}, its id is blank, or its
     *     components, tools or gaps are invalid or collide with existing definitions
     */
    public void register(GenUIExtension generation) {
        if (generation == null) {
            throw new GenerationSdkException("generation is required");
        }
        if (generation.extensionId() == null || generation.extensionId().isBlank()) {
            throw new GenerationSdkException("generation.extensionId is required");
        }
        validateComponents(generation.components(), "generation " + generation.extensionId());

        Set<String> baseComponentNames = baseContract.components().keySet();
        Set<String> generationComponentNames = generation.components().keySet();
        Set<String> componentCollisions =
                intersection(baseComponentNames, generationComponentNames);
        if (!componentCollisions.isEmpty()) {
            throw new GenerationSdkException(
                    "Component name collision: " + String.join(", ", componentCollisions));
        }

        LinkedHashSet<String> finalComponentNames = new LinkedHashSet<>(baseComponentNames);
        finalComponentNames.addAll(generationComponentNames);
        validateComponentGroups(
                generation.componentGroups(),
                finalComponentNames,
                "generation " + generation.extensionId());

        Set<String> baseToolNames = toolNames(baseContract.tools());
        Set<String> generationToolNames = toolNames(generation.tools());
        Set<String> toolCollisions = intersection(baseToolNames, generationToolNames);
        if (!toolCollisions.isEmpty()) {
            throw new GenerationSdkException(
                    "Tool name collision: " + String.join(", ", toolCollisions));
        }

        Set<String> baseGapIds = gapIds(baseContract.capabilityGaps());
        Set<String> generationGapIds = gapIds(generation.capabilityGaps());
        Set<String> gapIdCollisions = intersection(baseGapIds, generationGapIds);
        if (!gapIdCollisions.isEmpty()) {
            throw new GenerationSdkException(
                    "Capability gap id collision: " + String.join(", ", gapIdCollisions));
        }
        ArrayList<CapabilityGapSpec> mergedGaps = new ArrayList<>(baseContract.capabilityGaps());
        mergedGaps.addAll(generation.capabilityGaps());
        GenerationContractLoader.validateCapabilityGaps(
                mergedGaps,
                finalComponentNames,
                baseContract.builtins(),
                "generation " + generation.extensionId());

        generations.put(generation.extensionId(), generation);
    }

    /**
     * Returns the merged {@link GenerationContract} that would be used for a given prompt request.
     *
     * <p>This is the same merge that {@link #assemblePrompt(GenUIPromptRequest)} performs (base +
     * registered extension for the request's {@code extensionId}), exposed so callers such as the
     * validator can obtain the SAME contract the LLM was prompted with — ensuring component names
     * and props match exactly what the model was told.
     *
     * @param request the request whose {@code extensionId} selects the extension to merge; {@code
     *     null} is treated as an empty request, yielding the base contract alone
     * @return the merged contract, preserving the base version, root and builtins
     */
    public GenerationContract mergedContract(GenUIPromptRequest request) {
        GenUIPromptRequest effectiveRequest =
                request == null
                        ? new GenUIPromptRequest(null, null, List.of(), null, null, null, null)
                        : request;
        MergedParts parts = merge(effectiveRequest);
        return new GenerationContract(
                baseContract.contractVersion(),
                baseContract.root(),
                parts.components(),
                parts.componentGroups(),
                parts.tools(),
                parts.examples(),
                parts.additionalRules(),
                baseContract.builtins(),
                parts.capabilityGaps());
    }

    /**
     * Assembles the openui-lang prompt and its metadata for one request.
     *
     * <p>Merges the base contract with the extension named by {@link
     * GenUIPromptRequest#extensionId()} (an unknown id contributes nothing), characterizes the
     * request's data model, and renders the prompt via {@link PromptAssembler}.
     *
     * @param request the prompt request; {@code null} is treated as an empty request
     * @return the rendered prompt plus metadata naming the contract versions and registered tools
     */
    public GenUIPromptAssemblyResult assemblePrompt(GenUIPromptRequest request) {
        GenUIPromptRequest effectiveRequest =
                request == null
                        ? new GenUIPromptRequest(null, null, List.of(), null, null, null, null)
                        : request;
        MergedParts parts = merge(effectiveRequest);
        Set<String> registeredToolNames = toolNames(parts.tools());

        DataModelSpec characterizedData = characterize(effectiveRequest.dataModel());
        String prompt =
                PromptAssembler.assemble(
                        new PromptAssembler.PromptInput(
                                null, // preamble — the SDK always uses
                                // the default openui-lang
                                // preamble
                                baseContract.root(),
                                parts.components(),
                                parts.componentGroups(),
                                characterizedData,
                                parts.tools(),
                                parts.examples(),
                                parts.additionalRules(),
                                effectiveRequest.editMode(),
                                effectiveRequest.inlineMode(),
                                effectiveRequest.toolCalls(),
                                effectiveRequest.bindings()),
                        baseContract.builtins(),
                        parts.capabilityGaps());

        GenUIExtension generation =
                effectiveRequest.extensionId() == null
                        ? null
                        : generations.get(effectiveRequest.extensionId());
        GenUIPromptAssemblyMetadata metadata =
                new GenUIPromptAssemblyMetadata(
                        effectiveRequest.extensionId(),
                        baseContract.contractVersion(),
                        generation == null ? null : generation.version(),
                        new ArrayList<>(registeredToolNames));
        return new GenUIPromptAssemblyResult(prompt, metadata);
    }

    /**
     * Shared merge helper: base + registered extension
     * components/groups/tools/examples/rules/capabilityGaps. Used by both {@link #assemblePrompt}
     * and {@link #mergedContract} so the two never drift.
     */
    private MergedParts merge(GenUIPromptRequest effectiveRequest) {
        GenUIExtension generation =
                effectiveRequest.extensionId() == null
                        ? null
                        : generations.get(effectiveRequest.extensionId());

        LinkedHashMap<String, ComponentPromptSpec> components = new LinkedHashMap<>();
        components.putAll(baseContract.components());
        if (generation != null) {
            components.putAll(generation.components());
        }

        ArrayList<ComponentGroup> componentGroups = new ArrayList<>(baseContract.componentGroups());
        if (generation != null) {
            componentGroups.addAll(generation.componentGroups());
        }
        validateComponentGroups(componentGroups, components.keySet(), "assembled context");

        ArrayList<ToolSpec> registeredTools = new ArrayList<>(baseContract.tools());
        if (generation != null) {
            registeredTools.addAll(generation.tools());
        }

        ArrayList<String> examples = new ArrayList<>(baseContract.examples());
        if (generation != null) {
            examples.addAll(generation.examples());
        }

        ArrayList<String> additionalRules = new ArrayList<>(baseContract.additionalRules());
        if (generation != null) {
            additionalRules.addAll(generation.additionalRules());
        }
        additionalRules.addAll(effectiveRequest.extraRules());

        ArrayList<CapabilityGapSpec> capabilityGaps =
                new ArrayList<>(baseContract.capabilityGaps());
        if (generation != null) {
            capabilityGaps.addAll(generation.capabilityGaps());
        }

        return new MergedParts(
                components,
                componentGroups,
                registeredTools,
                examples,
                additionalRules,
                capabilityGaps);
    }

    /**
     * Value object carrying the merged contract parts from {@link #merge}.
     *
     * @since 2026
     */
    private record MergedParts(
            LinkedHashMap<String, ComponentPromptSpec> components,
            ArrayList<ComponentGroup> componentGroups,
            ArrayList<ToolSpec> tools,
            ArrayList<String> examples,
            ArrayList<String> additionalRules,
            ArrayList<CapabilityGapSpec> capabilityGaps) {}

    /**
     * Characterizes the prompt copy of the data model for the convergence point in {@link
     * #assemblePrompt}. When the gate passes through (small/disabled/empty raw data), this returns
     * the SAME {@code in} instance unchanged — no sidecar is attached, so {@code dataModelSection}
     * stays byte-identical and existing golden prompts are unaffected.
     */
    private DataModelSpec characterize(DataModelSpec in) {
        if (in == null) {
            return null;
        }
        Characterized c = Characterizer.characterize(in.raw(), characterization);
        if (c.shape() == null) {
            return in;
        } // gate passed through — unchanged, no sidecar (parity preserved)
        @SuppressWarnings("unchecked")
        Map<String, Object> sample = (Map<String, Object>) c.sample();
        String sidecar = TsTypeRenderer.render(c.shape(), characterization.sampleRows());
        return new DataModelSpec(in.description(), sample, sidecar);
    }

    private static void validateComponentGroups(
            List<ComponentGroup> groups, Set<String> knownComponentNames, String scope) {
        LinkedHashSet<String> missing = new LinkedHashSet<>();
        for (ComponentGroup group : groups) {
            for (String componentName : group.components()) {
                if (!knownComponentNames.contains(componentName)) {
                    missing.add(componentName);
                }
            }
        }
        if (!missing.isEmpty()) {
            throw new GenerationSdkException(
                    "Component group references missing component(s) in "
                            + scope
                            + ": "
                            + String.join(", ", missing));
        }
    }

    private static void validateComponents(
            Map<String, ComponentPromptSpec> components, String scope) {
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
            if (!names.add(tool.name())) {
                duplicates.add(tool.name());
            }
        }
        if (!duplicates.isEmpty()) {
            throw new GenerationSdkException(
                    "Tool name collision: " + String.join(", ", duplicates));
        }
        return names;
    }

    private static Set<String> gapIds(List<CapabilityGapSpec> gaps) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        if (gaps == null) {
            return ids;
        }
        for (CapabilityGapSpec gap : gaps) {
            if (gap != null && gap.id() != null) {
                ids.add(gap.id());
            }
        }
        return ids;
    }

    private static Set<String> intersection(Set<String> left, Set<String> right) {
        LinkedHashSet<String> result = new LinkedHashSet<>(left);
        result.retainAll(right);
        return result;
    }

    /**
     * Fluent builder for {@link GenerationSdk}.
     *
     * <p>Obtain one via {@link GenerationSdk#builder()}. When unset, the base contract defaults to
     * {@link GenerationContractLoader#loadDefault()} and characterization to {@link
     * CharacterizationConfig#defaults()}.
     *
     * @since 2026
     */
    public static final class Builder {
        private GenerationContract baseContract;
        private GenUIBaseSupplement baseSupplement;
        private CharacterizationConfig characterization;

        private Builder() {}

        /**
         * Sets the base contract to build around; when unset, the bundled default is loaded.
         *
         * @param baseContract the base contract, or {@code null} to use the bundled default
         * @return this builder
         */
        public Builder baseContract(GenerationContract baseContract) {
            this.baseContract = baseContract;
            return this;
        }

        /**
         * Global base supplement merged into the base contract at build time (at most one per SDK
         * instance). Load it with {@code GenUIBaseSupplementLoader}; see {@link
         * GenUIBaseSupplement} for the merge rules.
         *
         * @param baseSupplement the global supplement to merge, or {@code null} for none
         * @return this builder
         */
        public Builder baseSupplement(GenUIBaseSupplement baseSupplement) {
            this.baseSupplement = baseSupplement;
            return this;
        }

        /**
         * Overrides characterization tuning; when unset, {@link CharacterizationConfig#defaults()}
         * is used.
         *
         * @param characterization the configuration to use, or {@code null} for defaults
         * @return this builder
         */
        public Builder characterization(CharacterizationConfig characterization) {
            this.characterization = characterization;
            return this;
        }

        /**
         * Builds an SDK, loading the default contract when no base contract was set.
         *
         * @return a new SDK instance
         * @throws GenerationSdkException if the base contract or base supplement is invalid
         */
        public GenerationSdk build() {
            return new GenerationSdk(
                    baseContract == null ? GenerationContractLoader.loadDefault() : baseContract,
                    baseSupplement,
                    characterization == null
                            ? CharacterizationConfig.defaults()
                            : characterization);
        }
    }
}
