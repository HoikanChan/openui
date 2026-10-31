/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.contract;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 宿主扩展包：在基础契约之上追加或覆盖组件、工具、分组、示例与能力缺口。
 *
 * @param extensionId 扩展唯一标识
 * @param version 扩展版本
 * @param components 扩展提供的组件
 * @param componentGroups 扩展提供的组件分组
 * @param tools 扩展提供的工具
 * @param examples 追加到契约之后的示例
 * @param additionalRules 追加到契约之后的规则
 * @param capabilityGaps 按 {@code id} 原地替换或追加的能力缺口
 * @since 2026
 */
public record GenUIExtension(
        String extensionId,
        String version,
        Map<String, ComponentPromptSpec> components,
        List<ComponentGroup> componentGroups,
        List<ToolSpec> tools,
        List<String> examples,
        List<String> additionalRules,
        List<CapabilityGapSpec> capabilityGaps) {
    /** 规范化构造：{@code null} 值归一为空集合，其余复制为不可变集合。 */
    public GenUIExtension {
        components =
                components == null
                        ? Map.of()
                        : Collections.unmodifiableMap(new LinkedHashMap<>(components));
        componentGroups = componentGroups == null ? List.of() : List.copyOf(componentGroups);
        tools = tools == null ? List.of() : List.copyOf(tools);
        examples = examples == null ? List.of() : List.copyOf(examples);
        additionalRules = additionalRules == null ? List.of() : List.copyOf(additionalRules);
        capabilityGaps = capabilityGaps == null ? List.of() : List.copyOf(capabilityGaps);
    }

    /**
     * 不带能力缺口的兼容构造。
     *
     * @param extensionId 扩展唯一标识
     * @param version 扩展版本
     * @param components 扩展提供的组件
     * @param componentGroups 扩展提供的组件分组
     * @param tools 扩展提供的工具
     * @param examples 追加示例
     * @param additionalRules 追加规则
     */
    public GenUIExtension(
            String extensionId,
            String version,
            Map<String, ComponentPromptSpec> components,
            List<ComponentGroup> componentGroups,
            List<ToolSpec> tools,
            List<String> examples,
            List<String> additionalRules) {
        this(
                extensionId,
                version,
                components,
                componentGroups,
                tools,
                examples,
                additionalRules,
                List.of());
    }
}
