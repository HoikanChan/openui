/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.contract;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record GenUIExtension(String extensionId, String version, Map<String, ComponentPromptSpec> components,
        List<ComponentGroup> componentGroups, List<ToolSpec> tools, List<String> examples, List<String> additionalRules,
        List<CapabilityGapSpec> capabilityGaps) {
    public GenUIExtension {
        components = components == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(components));
        componentGroups = componentGroups == null ? List.of() : List.copyOf(componentGroups);
        tools = tools == null ? List.of() : List.copyOf(tools);
        examples = examples == null ? List.of() : List.copyOf(examples);
        additionalRules = additionalRules == null ? List.of() : List.copyOf(additionalRules);
        capabilityGaps = capabilityGaps == null ? List.of() : List.copyOf(capabilityGaps);
    }

    /** Backwards-compatible constructor without capabilityGaps. */
    public GenUIExtension(String extensionId, String version, Map<String, ComponentPromptSpec> components,
            List<ComponentGroup> componentGroups, List<ToolSpec> tools, List<String> examples,
            List<String> additionalRules) {
        this(extensionId, version, components, componentGroups, tools, examples, additionalRules, List.of());
    }
}
