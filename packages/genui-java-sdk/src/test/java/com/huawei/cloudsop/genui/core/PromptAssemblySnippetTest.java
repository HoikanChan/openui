package com.huawei.cloudsop.genui.core;

import com.huawei.cloudsop.genui.core.contract.BuiltinSpec;
import com.huawei.cloudsop.genui.core.contract.ComponentGroup;
import com.huawei.cloudsop.genui.core.contract.ComponentPromptSpec;
import com.huawei.cloudsop.genui.core.contract.DataModelSpec;
import com.huawei.cloudsop.genui.core.contract.GenUIGeneration;
import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import com.huawei.cloudsop.genui.core.contract.GenerationContractLoader;
import com.huawei.cloudsop.genui.core.contract.ToolAnnotations;
import com.huawei.cloudsop.genui.core.contract.ToolSpec;
import com.huawei.cloudsop.genui.core.prompt.GenUIPromptAssemblyResult;
import com.huawei.cloudsop.genui.core.prompt.GenUIPromptRequest;
import com.huawei.cloudsop.genui.core.prompt.PromptAssembler;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PromptAssemblySnippetTest {
  @Test
  void promptContainsBaseExtensionRequestToolsRulesAndDataModelSections() {
    LinkedHashMap<String, ComponentPromptSpec> components = new LinkedHashMap<>();
    components.put("Stack", new ComponentPromptSpec("Stack(children?: Component[])", "Layout"));
    GenerationContract base =
        new GenerationContract(
            "base-v1",
            "Stack",
            components,
            List.of(new ComponentGroup("Layout", List.of("Stack"), List.of())),
            List.of(),
            List.of(),
            List.of("Base rule"));
    GenerationSdk sdk = GenerationSdk.builder().baseContract(base).build();
    sdk.register(
        new GenUIGeneration(
            "genA",
            "gen-v1",
            Map.of("BizCard", new ComponentPromptSpec("BizCard(title: string)", "Business card")),
            List.of(new ComponentGroup("Business", List.of("BizCard"), List.of())),
            List.of(),
            List.of("root = Stack([BizCard(\"A\")])"),
            List.of("Generation rule")));

    GenUIPromptAssemblyResult result =
        sdk.assemblePrompt(
            new GenUIPromptRequest(
                "genA",
                new DataModelSpec("Business data", Map.of("accounts", List.of(Map.of("name", "Acme")))),
                List.of(
                    new ToolSpec(
                        "searchTickets",
                        "Search tickets",
                        Map.of("type", "object"),
                        Map.of("type", "object"),
                        null)),
                List.of("Request rule"),
                null,
                null,
                null,
                null));

    assertTrue(result.prompt().contains("Stack(children?: Component[])"));
    assertTrue(result.prompt().contains("BizCard(title: string)"));
    assertTrue(result.prompt().contains("searchTickets"));
    assertTrue(result.prompt().contains("Request rule"));
    assertTrue(result.prompt().contains("Generation rule"));
    assertTrue(result.prompt().contains("## Data Model"));
    assertTrue(result.prompt().contains("Business data"));
  }
}
