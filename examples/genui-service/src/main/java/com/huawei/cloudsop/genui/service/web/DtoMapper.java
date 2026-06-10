package com.huawei.cloudsop.genui.service.web;

import com.huawei.cloudsop.genui.core.DataModelSpec;
import com.huawei.cloudsop.genui.core.GenUIContextExtension;
import com.huawei.cloudsop.genui.core.GenUIPromptAssemblyMetadata;
import com.huawei.cloudsop.genui.core.GenUIPromptAssemblyResult;
import com.huawei.cloudsop.genui.core.GenUIPromptRequest;
import com.huawei.cloudsop.genui.service.api.model.AssembleRequest;
import com.huawei.cloudsop.genui.service.api.model.AssembleResult;
import com.huawei.cloudsop.genui.service.api.model.AssemblyMetadata;
import com.huawei.cloudsop.genui.service.api.model.ComponentGroup;
import com.huawei.cloudsop.genui.service.api.model.ContextSummary;
import com.huawei.cloudsop.genui.service.api.model.DataModel;
import com.huawei.cloudsop.genui.service.api.model.ExtensionRegistration;
import com.huawei.cloudsop.genui.service.api.model.ToolAnnotations;
import com.huawei.cloudsop.genui.service.api.model.ToolSpec;
import com.huawei.cloudsop.genui.service.application.ContextSummaryData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 生成 DTO 与 SDK 领域记录之间的薄映射;不做业务校验(交给 SDK)。
 *
 * <p>生成模型与 SDK 记录同名(ToolSpec 等),SDK 侧一律全限定名引用。
 */
final class DtoMapper {
  private DtoMapper() {}

  static GenUIContextExtension toExtension(String contextId, ExtensionRegistration dto) {
    LinkedHashMap<String, com.huawei.cloudsop.genui.core.ComponentPromptSpec> components =
        new LinkedHashMap<>();
    if (dto.getComponents() != null) {
      dto.getComponents()
          .forEach(
              (name, spec) ->
                  components.put(
                      name,
                      new com.huawei.cloudsop.genui.core.ComponentPromptSpec(
                          spec.getSignature(), spec.getDescription())));
    }
    return new GenUIContextExtension(
        contextId,
        dto.getVersion(),
        components,
        toGroups(dto.getComponentGroups()),
        toTools(dto.getTools()),
        dto.getExamples(),
        dto.getAdditionalRules());
  }

  static GenUIPromptRequest toPromptRequest(AssembleRequest dto) {
    return new GenUIPromptRequest(
        dto.getContextId(),
        toDataModel(dto.getDataModel()),
        toTools(dto.getTools()),
        dto.getExtraRules(),
        dto.isEditMode(),
        dto.isInlineMode(),
        dto.isToolCalls(),
        dto.isBindings());
  }

  static List<com.huawei.cloudsop.genui.core.ToolSpec> toTools(List<ToolSpec> dtos) {
    List<com.huawei.cloudsop.genui.core.ToolSpec> tools = new ArrayList<>();
    if (dtos != null) {
      for (ToolSpec dto : dtos) {
        tools.add(
            new com.huawei.cloudsop.genui.core.ToolSpec(
                dto.getName(),
                dto.getDescription(),
                dto.getInputSchema(),
                dto.getOutputSchema(),
                toAnnotations(dto.getAnnotations())));
      }
    }
    return tools;
  }

  static ContextSummary toDto(ContextSummaryData data) {
    return new ContextSummary()
        .contextId(data.contextId())
        .version(data.version())
        .componentCount(data.componentCount())
        .toolCount(data.toolCount());
  }

  static AssembleResult toDto(GenUIPromptAssemblyResult result) {
    GenUIPromptAssemblyMetadata metadata = result.metadata();
    return new AssembleResult()
        .prompt(result.prompt())
        .metadata(
            new AssemblyMetadata()
                .contextId(metadata.contextId())
                .baseContractVersion(metadata.baseContractVersion())
                .extensionVersion(metadata.extensionVersion())
                .registeredToolNames(metadata.registeredToolNames())
                .requestToolNames(metadata.requestToolNames()));
  }

  private static List<com.huawei.cloudsop.genui.core.ComponentGroup> toGroups(
      List<ComponentGroup> dtos) {
    List<com.huawei.cloudsop.genui.core.ComponentGroup> groups = new ArrayList<>();
    if (dtos != null) {
      for (ComponentGroup dto : dtos) {
        groups.add(
            new com.huawei.cloudsop.genui.core.ComponentGroup(
                dto.getName(), dto.getComponents(), dto.getNotes()));
      }
    }
    return groups;
  }

  private static com.huawei.cloudsop.genui.core.ToolAnnotations toAnnotations(ToolAnnotations dto) {
    return dto == null
        ? null
        : new com.huawei.cloudsop.genui.core.ToolAnnotations(
            dto.isReadOnlyHint(), dto.isDestructiveHint());
  }

  static DataModelSpec toDataModel(DataModel dto) {
    return dto == null ? null : new DataModelSpec(dto.getDescription(), dto.getRaw());
  }
}
