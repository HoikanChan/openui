/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.contract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Host-provided global supplement to the base contract, merged once at {@code GenerationSdk} build time.
 *
 * <p>
 * Merge semantics (applied by {@link #applyTo(GenerationContract)}):
 * <ul>
 * <li>{@code components} — a component whose name already exists in the base contract REPLACES the base spec wholesale
 * (description and propsSchema together) while keeping its original position; new names are appended in supplement
 * order. Components are never merged field-by-field and never removed.</li>
 * <li>{@code componentGroups} — a group with a name already in the base contract is merged as the order-preserving
 * de-duplicated union of members (base members first) with notes appended; new groups are appended.</li>
 * <li>{@code examples} / {@code additionalRules} — appended after the base entries.</li>
 * <li>{@code contractVersion} / {@code root} / {@code tools} / {@code builtins} — never touched.</li>
 * </ul>
 *
 * <p>
 * {@code GenUIBaseSupplementLoader} is the only supported construction path (a JSON document with the same shape as
 * base-contract.json restricted to the four sections above). The canonical record constructor is public only because
 * the Java language requires it; direct instantiation is reserved for tests.
 */
public record GenUIBaseSupplement(Map<String, ComponentPromptSpec> components, List<ComponentGroup> componentGroups,
        List<String> examples, List<String> additionalRules) {
    public GenUIBaseSupplement {
        components = components == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(components));
        componentGroups = componentGroups == null ? List.of() : List.copyOf(componentGroups);
        examples = examples == null ? List.of() : List.copyOf(examples);
        additionalRules = additionalRules == null ? List.of() : List.copyOf(additionalRules);
    }

    /**
     * Merges this supplement into {@code base}, producing the Effective Base Contract. See the class Javadoc for the
     * per-section rules; {@code contractVersion}, {@code root}, {@code tools} and {@code builtins} are carried over
     * from {@code base} untouched. Internal — called once by the {@code GenerationSdk} constructor; hosts never call
     * this directly.
     */
    public GenerationContract applyTo(GenerationContract base) {
        LinkedHashMap<String, ComponentPromptSpec> mergedComponents = new LinkedHashMap<>(base.components());
        mergedComponents.putAll(components);

        ArrayList<ComponentGroup> mergedGroups = new ArrayList<>();
        LinkedHashMap<String, ComponentGroup> supplementGroupsByName = new LinkedHashMap<>();
        for (ComponentGroup group : componentGroups) {
            supplementGroupsByName.merge(group.name(), group, GenUIBaseSupplement::unionGroups);
        }
        for (ComponentGroup baseGroup : base.componentGroups()) {
            ComponentGroup supplementGroup = supplementGroupsByName.remove(baseGroup.name());
            mergedGroups.add(supplementGroup == null ? baseGroup : unionGroups(baseGroup, supplementGroup));
        }
        mergedGroups.addAll(supplementGroupsByName.values());

        ArrayList<String> mergedExamples = new ArrayList<>(base.examples());
        mergedExamples.addAll(examples);
        ArrayList<String> mergedRules = new ArrayList<>(base.additionalRules());
        mergedRules.addAll(additionalRules);

        return new GenerationContract(base.contractVersion(), base.root(), mergedComponents, mergedGroups, base.tools(),
                mergedExamples, mergedRules, base.builtins());
    }

    /** Order-preserving de-duplicated union of members (left first), notes appended. */
    private static ComponentGroup unionGroups(ComponentGroup left, ComponentGroup right) {
        LinkedHashSet<String> members = new LinkedHashSet<>(left.components());
        members.addAll(right.components());
        ArrayList<String> notes = new ArrayList<>(left.notes());
        notes.addAll(right.notes());
        return new ComponentGroup(left.name(), new ArrayList<>(members), notes);
    }
}
