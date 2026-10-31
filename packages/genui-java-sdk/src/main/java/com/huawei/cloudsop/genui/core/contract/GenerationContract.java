/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.contract;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 有效基础契约：根数据类型、组件、分组、工具、示例、规则、builtin 与能力缺口的只读快照。
 *
 * <p>{@code components} 使用保序不可变 Map，组件顺序参与提示词字节对齐。
 *
 * @param contractVersion 契约版本
 * @param root 根数据类型名
 * @param components 组件规格，保持契约中的声明顺序
 * @param componentGroups 组件分组
 * @param tools 工具声明
 * @param examples 提示词示例
 * @param additionalRules 额外生成规则
 * @param builtins builtin 声明
 * @param capabilityGaps 能力缺口声明
 * @since 2026
 */
public record GenerationContract(
        String contractVersion,
        String root,
        Map<String, ComponentPromptSpec> components,
        List<ComponentGroup> componentGroups,
        List<ToolSpec> tools,
        List<String> examples,
        List<String> additionalRules,
        List<BuiltinSpec> builtins,
        List<CapabilityGapSpec> capabilityGaps) {
    /** 规范化构造：{@code null} 值归一为空集合，其余复制为不可变集合。 */
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
        capabilityGaps = capabilityGaps == null ? List.of() : List.copyOf(capabilityGaps);
    }

    /**
     * 测试/遗留兼容构造：不带能力缺口（默认为空）。
     *
     * <p>生产代码必须使用 9 参规范构造，避免 {@code capabilityGaps} 被静默丢弃。
     *
     * @param contractVersion 契约版本
     * @param root 根数据类型名
     * @param components 组件规格
     * @param componentGroups 组件分组
     * @param tools 工具声明
     * @param examples 提示词示例
     * @param additionalRules 额外生成规则
     * @param builtins builtin 声明
     */
    public GenerationContract(
            String contractVersion,
            String root,
            Map<String, ComponentPromptSpec> components,
            List<ComponentGroup> componentGroups,
            List<ToolSpec> tools,
            List<String> examples,
            List<String> additionalRules,
            List<BuiltinSpec> builtins) {
        this(
                contractVersion,
                root,
                components,
                componentGroups,
                tools,
                examples,
                additionalRules,
                builtins,
                List.of());
    }

    /**
     * 测试/遗留兼容构造：不带 {@code builtins} 与能力缺口（均为空）。
     *
     * <p>生产代码必须使用 9 参规范构造。
     *
     * @param contractVersion 契约版本
     * @param root 根数据类型名
     * @param components 组件规格
     * @param componentGroups 组件分组
     * @param tools 工具声明
     * @param examples 提示词示例
     * @param additionalRules 额外生成规则
     */
    public GenerationContract(
            String contractVersion,
            String root,
            Map<String, ComponentPromptSpec> components,
            List<ComponentGroup> componentGroups,
            List<ToolSpec> tools,
            List<String> examples,
            List<String> additionalRules) {
        this(
                contractVersion,
                root,
                components,
                componentGroups,
                tools,
                examples,
                additionalRules,
                List.of(),
                List.of());
    }
}
