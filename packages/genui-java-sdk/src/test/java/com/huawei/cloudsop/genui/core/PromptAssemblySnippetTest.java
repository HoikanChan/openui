package com.huawei.cloudsop.genui.core;

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
        new GenUIContextExtension(
            "ctxA",
            "ext-v1",
            Map.of("BizCard", new ComponentPromptSpec("BizCard(title: string)", "Business card")),
            List.of(new ComponentGroup("Business", List.of("BizCard"), List.of())),
            List.of(),
            List.of("root = Stack([BizCard(\"A\")])"),
            List.of("Extension rule")));

    GenUIPromptAssemblyResult result =
        sdk.assemblePrompt(
            new GenUIPromptRequest(
                "ctxA",
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
    assertTrue(result.prompt().contains("Extension rule"));
    assertTrue(result.prompt().contains("## Data Model"));
    assertTrue(result.prompt().contains("Business data"));
  }
}
