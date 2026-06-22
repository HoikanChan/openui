package com.huawei.cloudsop.genui.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GenerationSdkTest {
  @Test
  void assemblesPromptWithoutRegisteredExtension() {
    GenerationSdk sdk = GenerationSdk.builder().baseContract(testBaseContract()).build();

    GenUIPromptAssemblyResult result =
        sdk.assemblePrompt(new GenUIPromptRequest("ctxA", null, List.of(), List.of(), null, null, null, null));

    assertTrue(result.prompt().contains("Stack(children?: Component[])"));
    assertFalse(result.prompt().contains("BizCard("));
    assertEquals("base-v1", result.metadata().baseContractVersion());
    assertEquals(null, result.metadata().generationVersion());
  }

  @Test
  void registersReplacesAndIsolatesContexts() {
    GenerationSdk sdk = GenerationSdk.builder().baseContract(testBaseContract()).build();

    sdk.register(generation("genA", "v1", component("OldCard"), List.of(tool("loadOrders"))));
    sdk.register(generation("genB", "v1", component("OtherCard"), List.of()));
    sdk.register(generation("genA", "v2", component("BizCard"), List.of(tool("loadOrders"))));

    GenUIPromptAssemblyResult result =
        sdk.assemblePrompt(new GenUIPromptRequest("genA", null, List.of(), List.of(), null, null, null, null));

    assertTrue(result.prompt().contains("BizCard(title: string)"));
    assertFalse(result.prompt().contains("OldCard("));
    assertFalse(result.prompt().contains("OtherCard("));
    assertEquals("genA", result.metadata().generationId());
    assertEquals("v2", result.metadata().generationVersion());
    assertIterableEquals(List.of("loadOrders"), result.metadata().registeredToolNames());
  }

  @Test
  void derivesPromptSignatureFromPropsSchema() {
    GenerationSdk sdk = GenerationSdk.builder().baseContract(testBaseContract()).build();
    sdk.register(generation("genA", "v1", component("AlarmBadge"), List.of()));

    GenUIPromptAssemblyResult result =
        sdk.assemblePrompt(new GenUIPromptRequest("genA", null, List.of(), List.of(), null, null, null, null));

    assertTrue(result.prompt().contains("AlarmBadge(severity: \"critical\" | \"major\" | \"minor\", count: number, label?: string)"));
    assertTrue(result.prompt().contains("AlarmBadge description"));
  }

  @Test
  void rejectsRequiredPropsAfterOptionalProps() {
    LinkedHashMap<String, Object> properties = new LinkedHashMap<>();
    properties.put("optionalLabel", Map.of("type", "string"));
    properties.put("requiredCount", Map.of("type", "number"));
    ComponentPromptSpec component =
        new ComponentPromptSpec(
            "Broken component",
            schema(properties, List.of("requiredCount")));

    GenerationSdkException error =
        assertThrows(
            GenerationSdkException.class,
            () ->
                GenerationSdk.builder()
                    .baseContract(
                        new GenerationContract(
                            "base-v1",
                            "BrokenCard",
                            Map.of("BrokenCard", component),
                            List.of(),
                            List.of(),
                            List.of(),
                            List.of()))
                    .build());

    assertTrue(error.getMessage().contains("required props must precede optional props"));
  }

  @Test
  void rejectsExtensionComponentCollisionWithBase() {
    GenerationSdk sdk = GenerationSdk.builder().baseContract(testBaseContract()).build();

    GenerationSdkException error =
        assertThrows(
            GenerationSdkException.class,
            () -> sdk.register(generation("ctxA", "v1", component("Stack"), List.of())));

    assertTrue(error.getMessage().contains("Stack"));
  }

  @Test
  void rejectsMissingComponentGroupReferences() {
    GenerationSdk sdk = GenerationSdk.builder().baseContract(testBaseContract()).build();
    GenUIGeneration generation =
        new GenUIGeneration(
            "genA",
            "v1",
            Map.of(),
            List.of(new ComponentGroup("Broken", List.of("MissingCard"), List.of())),
            List.of(),
            List.of(),
            List.of());

    GenerationSdkException error =
        assertThrows(GenerationSdkException.class, () -> sdk.register(generation));

    assertTrue(error.getMessage().contains("MissingCard"));
  }

  @Test
  void requestToolsAndExtraRulesAreRequestScoped() {
    GenerationSdk sdk = GenerationSdk.builder().baseContract(testBaseContract()).build();
    sdk.register(generation("genA", "v1", component("BizCard"), List.of()));

    GenUIPromptAssemblyResult withOverlay =
        sdk.assemblePrompt(
            new GenUIPromptRequest(
                "genA",
                null,
                List.of(tool("searchTickets")),
                List.of("Prefer tables over charts for this request"),
                null,
                null,
                null,
                null));
    GenUIPromptAssemblyResult withoutOverlay =
        sdk.assemblePrompt(new GenUIPromptRequest("genA", null, List.of(), List.of(), null, null, null, null));

    assertTrue(withOverlay.prompt().contains("searchTickets"));
    assertTrue(withOverlay.prompt().contains("Prefer tables over charts for this request"));
    assertFalse(withoutOverlay.prompt().contains("searchTickets"));
    assertFalse(withoutOverlay.prompt().contains("Prefer tables over charts for this request"));
    assertIterableEquals(List.of("searchTickets"), withOverlay.metadata().requestToolNames());
  }

  @Test
  void rejectsRequestToolCollisionWithRegisteredTool() {
    GenerationSdk sdk = GenerationSdk.builder().baseContract(testBaseContract()).build();
    sdk.register(generation("genA", "v1", component("BizCard"), List.of(tool("loadOrders"))));

    GenerationSdkException error =
        assertThrows(
            GenerationSdkException.class,
            () ->
                sdk.assemblePrompt(
                    new GenUIPromptRequest(
                        "genA", null, List.of(tool("loadOrders")), List.of(), null, null, null, null)));

    assertTrue(error.getMessage().contains("loadOrders"));
  }

  @Test
  void includesDataModelOnlyForCurrentRequest() {
    GenerationSdk sdk = GenerationSdk.builder().baseContract(testBaseContract()).build();
    DataModelSpec dataModel = new DataModelSpec("Ticket data", Map.of("tickets", List.of(Map.of("id", 7))));

    GenUIPromptAssemblyResult withData =
        sdk.assemblePrompt(new GenUIPromptRequest("genA", dataModel, List.of(), List.of(), null, null, null, null));
    GenUIPromptAssemblyResult withoutData =
        sdk.assemblePrompt(new GenUIPromptRequest("genA", null, List.of(), List.of(), null, null, null, null));

    assertTrue(withData.prompt().contains("## Data Model"));
    assertTrue(withData.prompt().contains("Ticket data"));
    assertFalse(withoutData.prompt().contains("## Data Model"));
  }

  @Test
  void dataModelRawIsDefensivelyCopiedAndImmutable() {
    GenerationSdk sdk = GenerationSdk.builder().baseContract(testBaseContract()).build();
    LinkedHashMap<String, Object> raw = new LinkedHashMap<>();
    raw.put("status", "initial");
    DataModelSpec dataModel = new DataModelSpec("Mutable source", raw);

    raw.put("status", "mutated");

    GenUIPromptAssemblyResult result =
        sdk.assemblePrompt(new GenUIPromptRequest("genA", dataModel, List.of(), List.of(), null, null, null, null));

    assertTrue(result.prompt().contains("\"status\": \"initial\""));
    assertFalse(result.prompt().contains("mutated"));
    assertThrows(UnsupportedOperationException.class, () -> dataModel.raw().put("status", "later"));
  }

  @Test
  void requestSchemaDoesNotExposeExtraPrompt() {
    for (var component : GenUIPromptRequest.class.getRecordComponents()) {
      assertFalse(component.getName().equals("extraPrompt"));
    }
  }

  private static GenerationContract testBaseContract() {
    LinkedHashMap<String, ComponentPromptSpec> components = new LinkedHashMap<>();
    components.put(
        "Stack",
        new ComponentPromptSpec(
            "Layout",
            schema(
                new LinkedHashMap<>(
                    Map.of("children", Map.of("type", "array", "items", Map.of("component", true)))),
                List.of())));
    components.put(
        "TextContent",
        new ComponentPromptSpec(
            "Text",
            schema(new LinkedHashMap<>(Map.of("text", Map.of("type", "string"))), List.of("text"))));
    return new GenerationContract(
        "base-v1",
        "Stack",
        components,
        List.of(new ComponentGroup("Layout", List.of("Stack"), List.of("Use Stack for layout."))),
        List.of(),
        List.of("root = Stack([])"),
        List.of("Do not invent components."));
  }

  private static GenUIGeneration generation(
      String generationId, String version, Map.Entry<String, ComponentPromptSpec> component, List<ToolSpec> tools) {
    return new GenUIGeneration(
        generationId,
        version,
        Map.ofEntries(component),
        List.of(new ComponentGroup("Business", List.of(component.getKey()), List.of())),
        tools,
        List.of(component.getKey() + " example"),
        List.of(component.getKey() + " rule"));
  }

  private static Map.Entry<String, ComponentPromptSpec> component(String name) {
    LinkedHashMap<String, Object> properties = new LinkedHashMap<>();
    if (name.equals("AlarmBadge")) {
      properties.put("severity", Map.of("enum", List.of("critical", "major", "minor")));
      properties.put("count", Map.of("type", "number"));
      properties.put("label", Map.of("type", "string"));
      return Map.entry(
          name, new ComponentPromptSpec(name + " description", schema(properties, List.of("severity", "count"))));
    }
    properties.put("title", Map.of("type", "string"));
    return Map.entry(name, new ComponentPromptSpec(name + " description", schema(properties, List.of("title"))));
  }

  private static Map<String, Object> schema(Map<String, Object> properties, List<String> required) {
    LinkedHashMap<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "object");
    schema.put("properties", properties);
    schema.put("required", required);
    return schema;
  }

  private static ToolSpec tool(String name) {
    return new ToolSpec(name, name + " description", Map.of("type", "object"), Map.of("type", "object"), null);
  }
}
