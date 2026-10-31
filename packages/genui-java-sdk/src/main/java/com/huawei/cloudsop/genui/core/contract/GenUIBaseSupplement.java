/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.contract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Host-provided global supplement to the base contract, merged once at {@code GenerationSdk} build
 * time.
 *
 * <p>Merge semantics (applied by {@link #applyTo(GenerationContract)}):
 *
 * <ul>
 *   <li>{@code components} — a component whose name already exists in the base contract REPLACES
 *       the base spec wholesale (description and propsSchema together) while keeping its original
 *       position; new names are appended in supplement order. Components are never merged
 *       field-by-field and never removed.
 *   <li>{@code componentGroups} — a group with a name already in the base contract is merged as the
 *       order-preserving de-duplicated union of members (base members first) with notes appended;
 *       new groups are appended.
 *   <li>{@code examples} / {@code additionalRules} — appended after the base entries.
 *   <li>{@code capabilityGaps} — a gap whose {@code id} already exists in the base contract
 *       replaces the base gap in place; new {@code id}s are appended in supplement order.
 *   <li>{@code contractVersion} / {@code root} / {@code tools} / {@code builtins} — never touched.
 * </ul>
 *
 * <p>{@code GenUIBaseSupplementLoader} is the only supported construction path (a JSON document
 * with the same shape as base-contract.json restricted to the sections above). The canonical record
 * constructor is public only because the Java language requires it; direct instantiation is
 * reserved for tests.
 *
 * @param components 补充组件，按名整体替换基础契约中的同名条目
 * @param componentGroups 补充分组，与同名基础分组取成员并集
 * @param examples 追加到基础契约之后的示例
 * @param additionalRules 追加到基础契约之后的额外规则
 * @param capabilityGaps 按 {@code id} 原地替换或追加的能力缺口
 * @since 2026
 */
public record GenUIBaseSupplement(
        Map<String, ComponentPromptSpec> components,
        List<ComponentGroup> componentGroups,
        List<String> examples,
        List<String> additionalRules,
        List<CapabilityGapSpec> capabilityGaps) {
    /** 规范化构造：{@code null} 值归一为空集合，其余复制为不可变集合。 */
    public GenUIBaseSupplement {
        components =
                components == null
                        ? Map.of()
                        : Collections.unmodifiableMap(new LinkedHashMap<>(components));
        componentGroups = componentGroups == null ? List.of() : List.copyOf(componentGroups);
        examples = examples == null ? List.of() : List.copyOf(examples);
        additionalRules = additionalRules == null ? List.of() : List.copyOf(additionalRules);
        capabilityGaps = capabilityGaps == null ? List.of() : List.copyOf(capabilityGaps);
    }

    /**
     * 不带能力缺口的兼容构造。
     *
     * @param components 补充组件
     * @param componentGroups 补充分组
     * @param examples 追加示例
     * @param additionalRules 追加规则
     */
    public GenUIBaseSupplement(
            Map<String, ComponentPromptSpec> components,
            List<ComponentGroup> componentGroups,
            List<String> examples,
            List<String> additionalRules) {
        this(components, componentGroups, examples, additionalRules, List.of());
    }

    /**
     * 将本补充合并到 {@code base}，生成有效基础契约。
     *
     * <p>各分节的合并规则见类文档；{@code contractVersion}、{@code root}、{@code tools} 与 {@code builtins} 原样沿用
     * {@code base}。内部方法，仅由 {@code GenerationSdk} 构造时调用一次， 宿主不应直接调用。
     *
     * @param base 基础契约
     * @return 合并后的有效基础契约
     */
    public GenerationContract applyTo(GenerationContract base) {
        LinkedHashMap<String, ComponentPromptSpec> mergedComponents =
                new LinkedHashMap<>(base.components());
        mergedComponents.putAll(components);

        ArrayList<ComponentGroup> mergedGroups = new ArrayList<>();
        LinkedHashMap<String, ComponentGroup> supplementGroupsByName = new LinkedHashMap<>();
        for (ComponentGroup group : componentGroups) {
            supplementGroupsByName.merge(group.name(), group, GenUIBaseSupplement::unionGroups);
        }
        for (ComponentGroup baseGroup : base.componentGroups()) {
            ComponentGroup supplementGroup = supplementGroupsByName.remove(baseGroup.name());
            mergedGroups.add(
                    supplementGroup == null ? baseGroup : unionGroups(baseGroup, supplementGroup));
        }
        mergedGroups.addAll(supplementGroupsByName.values());

        ArrayList<String> mergedExamples = new ArrayList<>(base.examples());
        mergedExamples.addAll(examples);
        ArrayList<String> mergedRules = new ArrayList<>(base.additionalRules());
        mergedRules.addAll(additionalRules);

        LinkedHashMap<String, CapabilityGapSpec> mergedGapsById = new LinkedHashMap<>();
        for (CapabilityGapSpec baseGap : base.capabilityGaps()) {
            mergedGapsById.put(baseGap.id(), baseGap);
        }
        for (CapabilityGapSpec suppGap : capabilityGaps) {
            mergedGapsById.put(suppGap.id(), suppGap);
        }

        return new GenerationContract(
                base.contractVersion(),
                base.root(),
                mergedComponents,
                mergedGroups,
                base.tools(),
                mergedExamples,
                mergedRules,
                base.builtins(),
                new ArrayList<>(mergedGapsById.values()));
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
