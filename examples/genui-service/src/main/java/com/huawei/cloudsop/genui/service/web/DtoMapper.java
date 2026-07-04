/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.service.web;

import com.huawei.cloudsop.genui.core.contract.DataModelSpec;
import com.huawei.cloudsop.genui.core.contract.GenUIExtension;
import com.huawei.cloudsop.genui.core.prompt.GenUIPromptAssemblyMetadata;
import com.huawei.cloudsop.genui.core.prompt.GenUIPromptAssemblyResult;
import com.huawei.cloudsop.genui.core.prompt.GenUIPromptRequest;
import com.huawei.cloudsop.genui.service.api.model.AssembleRequest;
import com.huawei.cloudsop.genui.service.api.model.AssembleResult;
import com.huawei.cloudsop.genui.service.api.model.AssemblyMetadata;
import com.huawei.cloudsop.genui.service.api.model.ComponentGroup;
import com.huawei.cloudsop.genui.service.api.model.ComponentPromptSpec;
import com.huawei.cloudsop.genui.service.api.model.DataModel;
import com.huawei.cloudsop.genui.service.api.model.ExtensionRegistration;
import com.huawei.cloudsop.genui.service.api.model.GenerationSummary;
import com.huawei.cloudsop.genui.service.api.model.ToolAnnotations;
import com.huawei.cloudsop.genui.service.api.model.ToolSpec;
import com.huawei.cloudsop.genui.service.application.GenerationSummaryData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 生成 DTO 与 SDK 领域记录之间的薄映射;不做业务校验(交给 SDK)。
 *
 * <p>
 * 生成模型与 SDK 记录同名(ToolSpec 等),SDK 侧一律全限定名引用。
 */
final class DtoMapper {
    private DtoMapper() {
    }

    static GenUIExtension toGeneration(String extensionId, ExtensionRegistration dto) {
        LinkedHashMap<String, com.huawei.cloudsop.genui.core.contract.ComponentPromptSpec> components = new LinkedHashMap<>();
        if (dto.getComponents() != null) {
            dto.getComponents().forEach((name, spec) -> components.put(name, toComponent(spec)));
        }
        return new GenUIExtension(extensionId, dto.getVersion(), components, toGroups(dto.getComponentGroups()),
                toTools(dto.getTools()), dto.getExamples(), dto.getAdditionalRules());
    }

    /**
     * 组件契约 DTO→SDK 映射:优先 propsSchema 走规范构造器 (description, propsSchema), propsSchema 缺省时回退旧 signature 形——与 SDK
     * GenerationContractLoader.componentMap 一致。
     */
    private static com.huawei.cloudsop.genui.core.contract.ComponentPromptSpec toComponent(ComponentPromptSpec spec) {
        if (spec.getPropsSchema() != null && !spec.getPropsSchema().isEmpty()) {
            return new com.huawei.cloudsop.genui.core.contract.ComponentPromptSpec(spec.getDescription(),
                    spec.getPropsSchema());
        }
        return new com.huawei.cloudsop.genui.core.contract.ComponentPromptSpec(spec.getSignature(),
                spec.getDescription());
    }

    static GenUIPromptRequest toPromptRequest(AssembleRequest dto) {
        return new GenUIPromptRequest(dto.getExtensionId(), toDataModel(dto.getDataModel()), dto.getExtraRules(),
                dto.isEditMode(), dto.isInlineMode(), dto.isToolCalls(), dto.isBindings());
    }

    static List<com.huawei.cloudsop.genui.core.contract.ToolSpec> toTools(List<ToolSpec> dtos) {
        List<com.huawei.cloudsop.genui.core.contract.ToolSpec> tools = new ArrayList<>();
        if (dtos != null) {
            for (ToolSpec dto : dtos) {
                tools.add(new com.huawei.cloudsop.genui.core.contract.ToolSpec(dto.getName(), dto.getDescription(),
                        dto.getInputSchema(), dto.getOutputSchema(), toAnnotations(dto.getAnnotations())));
            }
        }
        return tools;
    }

    static GenerationSummary toDto(GenerationSummaryData data) {
        return new GenerationSummary().extensionId(data.extensionId()).version(data.version())
                .componentCount(data.componentCount()).toolCount(data.toolCount());
    }

    static AssembleResult toDto(GenUIPromptAssemblyResult result) {
        GenUIPromptAssemblyMetadata metadata = result.metadata();
        return new AssembleResult().prompt(result.prompt()).metadata(new AssemblyMetadata()
                .extensionId(metadata.extensionId()).baseContractVersion(metadata.baseContractVersion())
                .generationVersion(metadata.generationVersion()).registeredToolNames(metadata.registeredToolNames()));
    }

    private static List<com.huawei.cloudsop.genui.core.contract.ComponentGroup> toGroups(List<ComponentGroup> dtos) {
        List<com.huawei.cloudsop.genui.core.contract.ComponentGroup> groups = new ArrayList<>();
        if (dtos != null) {
            for (ComponentGroup dto : dtos) {
                groups.add(new com.huawei.cloudsop.genui.core.contract.ComponentGroup(dto.getName(),
                        dto.getComponents(), dto.getNotes()));
            }
        }
        return groups;
    }

    private static com.huawei.cloudsop.genui.core.contract.ToolAnnotations toAnnotations(ToolAnnotations dto) {
        return dto == null
                ? null
                : new com.huawei.cloudsop.genui.core.contract.ToolAnnotations(dto.isReadOnlyHint(),
                        dto.isDestructiveHint());
    }

    static DataModelSpec toDataModel(DataModel dto) {
        return dto == null ? null : new DataModelSpec(dto.getDescription(), dto.getRaw());
    }
}
