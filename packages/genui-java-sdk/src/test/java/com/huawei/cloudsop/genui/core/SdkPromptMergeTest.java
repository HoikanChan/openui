package com.huawei.cloudsop.genui.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * SDK-level end-to-end checks that {@link GenerationSdk#assemblePrompt} merges base + registered
 * extension + request overlay and feeds the result into {@link PromptAssembler} correctly.
 *
 * <p>The assembler itself is byte-pinned to the TypeScript oracle by {@link PromptGoldenTest}; here
 * we independently reconstruct the merged {@code PromptInput} (base → extension → request order) and
 * assert the SDK produces exactly that prompt — locking the merge wiring and ordering.
 */
class SdkPromptMergeTest {

  private static final List<BuiltinSpec> BUILTINS = GenerationContractLoader.loadDefault().builtins();

  @Test
  void mergesBaseExtensionAndRequestOverlayIntoAssembler() {
    GenerationContract base = baseContract();
    GenerationSdk sdk = GenerationSdk.builder().baseContract(base).build();

    GenUIContextExtension extension =
        new GenUIContextExtension(
            "ctxA",
            "ext-v1",
            singleComponent("BizCard", "BizCard(title: string)", "Business card"),
            List.of(new ComponentGroup("Business", List.of("BizCard"), List.of())),
            List.of(tool("loadOrders")),
            List.of("root = Stack([BizCard(\"A\")])"),
            List.of("Extension rule"));
    sdk.register(extension);

    DataModelSpec dataModel = new DataModelSpec("Order data", orderedMap("orders", List.of(orderedMap("id", 1L))));
    ToolSpec requestTool = tool("searchTickets");
    GenUIPromptRequest request =
        new GenUIPromptRequest(
            "ctxA", dataModel, List.of(requestTool), List.of("Request rule"), null, null, null, null);

    String actual = sdk.assemblePrompt(request).prompt();
    String expected =
        PromptAssembler.assemble(
            mergedInput(base, extension, dataModel, List.of(requestTool), List.of("Request rule")),
            base.builtins());

    assertEquals(expected, actual);
  }

  @Test
  void mergesBaseAndRequestOverlayWhenNoExtensionRegistered() {
    GenerationContract base = baseContract();
    GenerationSdk sdk = GenerationSdk.builder().baseContract(base).build();

    ToolSpec requestTool = tool("searchTickets");
    GenUIPromptRequest request =
        new GenUIPromptRequest(
            "ctxA", null, List.of(requestTool), List.of("Request rule"), null, null, null, null);

    String actual = sdk.assemblePrompt(request).prompt();
    String expected =
        PromptAssembler.assemble(
            mergedInput(base, null, null, List.of(requestTool), List.of("Request rule")),
            base.builtins());

    assertEquals(expected, actual);
  }

  // ─── helpers ───────────────────────────────────────────────────────────────

  private static PromptAssembler.PromptInput mergedInput(
      GenerationContract base,
      GenUIContextExtension extension,
      DataModelSpec dataModel,
      List<ToolSpec> requestTools,
      List<String> requestRules) {
    LinkedHashMap<String, ComponentPromptSpec> components = new LinkedHashMap<>(base.components());
    List<ComponentGroup> groups = new ArrayList<>(base.componentGroups());
    List<ToolSpec> tools = new ArrayList<>(base.tools());
    List<String> examples = new ArrayList<>(base.examples());
    List<String> rules = new ArrayList<>(base.additionalRules());

    if (extension != null) {
      components.putAll(extension.components());
      groups.addAll(extension.componentGroups());
      tools.addAll(extension.tools());
      examples.addAll(extension.examples());
      rules.addAll(extension.additionalRules());
    }
    tools.addAll(requestTools);
    rules.addAll(requestRules);

    return new PromptAssembler.PromptInput(
        null, base.root(), components, groups, dataModel, tools, examples, rules, null, null, null, null);
  }

  private static GenerationContract baseContract() {
    LinkedHashMap<String, ComponentPromptSpec> components = new LinkedHashMap<>();
    components.put("Stack", new ComponentPromptSpec("Stack(children?: Component[])", "Layout"));
    components.put("TextContent", new ComponentPromptSpec("TextContent(text: string)", "Text"));
    return new GenerationContract(
        "base-v1",
        "Stack",
        components,
        List.of(new ComponentGroup("Layout", List.of("Stack", "TextContent"), List.of("Layout note."))),
        List.of(),
        List.of("root = Stack([])"),
        List.of("Base rule"),
        BUILTINS);
  }

  private static Map<String, ComponentPromptSpec> singleComponent(
      String name, String signature, String description) {
    LinkedHashMap<String, ComponentPromptSpec> map = new LinkedHashMap<>();
    map.put(name, new ComponentPromptSpec(signature, description));
    return map;
  }

  private static ToolSpec tool(String name) {
    LinkedHashMap<String, Object> inputProps = new LinkedHashMap<>();
    inputProps.put("query", Map.of("type", "string"));
    LinkedHashMap<String, Object> inputSchema = new LinkedHashMap<>();
    inputSchema.put("type", "object");
    inputSchema.put("properties", inputProps);
    inputSchema.put("required", List.of("query"));

    LinkedHashMap<String, Object> outputProps = new LinkedHashMap<>();
    outputProps.put("count", Map.of("type", "number"));
    LinkedHashMap<String, Object> outputSchema = new LinkedHashMap<>();
    outputSchema.put("type", "object");
    outputSchema.put("properties", outputProps);

    return new ToolSpec(name, name + " description", inputSchema, outputSchema, null);
  }

  private static Map<String, Object> orderedMap(String key, Object value) {
    LinkedHashMap<String, Object> map = new LinkedHashMap<>();
    map.put(key, value);
    return map;
  }
}
