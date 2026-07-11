/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.contract.BuiltinSpec;
import com.huawei.cloudsop.genui.core.contract.ComponentGroup;
import com.huawei.cloudsop.genui.core.contract.ComponentPromptSpec;
import com.huawei.cloudsop.genui.core.contract.GenUIBaseSupplement;
import com.huawei.cloudsop.genui.core.contract.GenUIExtension;
import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import com.huawei.cloudsop.genui.core.contract.ToolSpec;
import com.huawei.cloudsop.genui.core.prompt.GenUIPromptRequest;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class GenerationSdkBaseSupplementTest {
    @Test
    void appendsNewComponentsAfterBaseInSupplementOrder() {
        GenerationSdk sdk = GenerationSdk.builder().baseContract(testBaseContract())
                .baseSupplement(new GenUIBaseSupplement(orderedComponents(component("TopoChart"), component("BizCard")),
                        List.of(), List.of(), List.of()))
                .build();

        assertEquals(List.of("Stack", "Tag", "TopoChart", "BizCard"),
                List.copyOf(sdk.baseContract().components().keySet()));
    }

    @Test
    void replacesSameNameComponentWholesaleKeepingPosition() {
        ComponentPromptSpec replacement = new ComponentPromptSpec("Supplement tag docs",
                schema(properties("text", Map.of("type", "string")), List.of("text")));
        GenerationSdk sdk = GenerationSdk.builder().baseContract(testBaseContract())
                .baseSupplement(new GenUIBaseSupplement(Map.of("Tag", replacement), List.of(), List.of(), List.of()))
                .build();

        assertEquals(List.of("Stack", "Tag"), List.copyOf(sdk.baseContract().components().keySet()));
        assertEquals("Supplement tag docs", sdk.baseContract().components().get("Tag").description());
        assertEquals(replacement.propsSchema(), sdk.baseContract().components().get("Tag").propsSchema());
    }

    @Test
    void mergesSameNameGroupAsOrderedDeduplicatedUnionWithAppendedNotes() {
        GenUIBaseSupplement supplement = new GenUIBaseSupplement(orderedComponents(component("TopoChart")),
                List.of(new ComponentGroup("Charts", List.of("TopoChart", "Tag"), List.of("supplement note")),
                        new ComponentGroup("Business", List.of("TopoChart"), List.of())),
                List.of(), List.of());
        GenerationSdk sdk = GenerationSdk.builder().baseContract(testBaseContract()).baseSupplement(supplement).build();

        List<ComponentGroup> groups = sdk.baseContract().componentGroups();
        assertEquals(List.of("Layout", "Charts", "Business"), groups.stream().map(ComponentGroup::name).toList());
        ComponentGroup charts = groups.get(1);
        assertEquals(List.of("Tag", "TopoChart"), charts.components());
        assertEquals(List.of("base note", "supplement note"), charts.notes());
    }

    @Test
    void appendsExamplesAndAdditionalRulesAfterBase() {
        GenerationSdk sdk = GenerationSdk.builder().baseContract(testBaseContract()).baseSupplement(
                new GenUIBaseSupplement(Map.of(), List.of(), List.of("supplement example"), List.of("supplement rule")))
                .build();

        assertEquals(List.of("root = Stack([])", "supplement example"), sdk.baseContract().examples());
        assertEquals(List.of("base rule", "supplement rule"), sdk.baseContract().additionalRules());
    }

    @Test
    void keepsImmutableSectionsFromBase() {
        GenerationContract base = testBaseContract();
        GenerationSdk sdk = GenerationSdk.builder().baseContract(base)
                .baseSupplement(new GenUIBaseSupplement(orderedComponents(component("TopoChart")), List.of(),
                        List.of("x"), List.of("y")))
                .build();

        assertEquals(base.contractVersion(), sdk.baseContract().contractVersion());
        assertEquals(base.root(), sdk.baseContract().root());
        assertEquals(base.tools(), sdk.baseContract().tools());
        assertEquals(base.builtins(), sdk.baseContract().builtins());
    }

    @Test
    void rejectsSupplementComponentWithInvalidPropsSchema() {
        LinkedHashMap<String, Object> propertiesOptionalFirst = new LinkedHashMap<>();
        propertiesOptionalFirst.put("optionalLabel", Map.of("type", "string"));
        propertiesOptionalFirst.put("requiredCount", Map.of("type", "number"));
        ComponentPromptSpec broken = new ComponentPromptSpec("Broken",
                schema(propertiesOptionalFirst, List.of("requiredCount")));

        GenerationSdkException error = assertThrows(GenerationSdkException.class,
                () -> GenerationSdk.builder().baseContract(testBaseContract())
                        .baseSupplement(
                                new GenUIBaseSupplement(Map.of("BrokenCard", broken), List.of(), List.of(), List.of()))
                        .build());

        assertTrue(error.getMessage().contains("base supplement"));
        assertTrue(error.getMessage().contains("BrokenCard"));
    }

    @Test
    void rejectsSupplementGroupReferencingMissingComponent() {
        GenerationSdkException error = assertThrows(GenerationSdkException.class,
                () -> GenerationSdk.builder().baseContract(testBaseContract())
                        .baseSupplement(new GenUIBaseSupplement(Map.of(),
                                List.of(new ComponentGroup("Charts", List.of("MissingChart"), List.of())), List.of(),
                                List.of()))
                        .build());

        assertTrue(error.getMessage().contains("MissingChart"));
    }

    @Test
    void rejectsExtensionCollidingWithSupplementComponent() {
        GenerationSdk sdk = GenerationSdk.builder().baseContract(testBaseContract()).baseSupplement(
                new GenUIBaseSupplement(orderedComponents(component("TopoChart")), List.of(), List.of(), List.of()))
                .build();
        GenUIExtension extension = new GenUIExtension("genA", "v1", Map.ofEntries(component("TopoChart")), List.of(),
                List.of(), List.of(), List.of());

        GenerationSdkException error = assertThrows(GenerationSdkException.class, () -> sdk.register(extension));

        assertTrue(error.getMessage().contains("TopoChart"));
    }

    @Test
    void assemblesPromptWithSupplementAppliedGlobally() {
        ComponentPromptSpec replacedTag = new ComponentPromptSpec("Supplement tag docs",
                schema(properties("text", Map.of("type", "string")), List.of("text")));
        LinkedHashMap<String, ComponentPromptSpec> components = new LinkedHashMap<>();
        components.put("TopoChart", component("TopoChart").getValue());
        components.put("Tag", replacedTag);
        GenUIBaseSupplement supplement = new GenUIBaseSupplement(components,
                List.of(new ComponentGroup("Charts", List.of("TopoChart"), List.of())),
                List.of("supplement example line"), List.of("supplement rule line"));
        GenerationSdk sdk = GenerationSdk.builder().baseContract(testBaseContract()).baseSupplement(supplement).build();

        String prompt = sdk.assemblePrompt(new GenUIPromptRequest(null, null, List.of(), null, null, null, null))
                .prompt();

        assertTrue(prompt.contains("TopoChart(title: string)"));
        assertTrue(prompt.contains("Supplement tag docs"));
        assertFalse(prompt.contains("Base tag docs"));
        assertTrue(prompt.contains("supplement example line"));
        assertTrue(prompt.contains("supplement rule line"));
    }

    private static GenerationContract testBaseContract() {
        LinkedHashMap<String, ComponentPromptSpec> components = new LinkedHashMap<>();
        components.put("Stack", new ComponentPromptSpec("Layout", schema(
                properties("children", Map.of("type", "array", "items", Map.of("component", true))), List.of())));
        components.put("Tag", new ComponentPromptSpec("Base tag docs",
                schema(properties("text", Map.of("type", "string")), List.of("text"))));
        return new GenerationContract("base-v1", "Stack", components,
                List.of(new ComponentGroup("Layout", List.of("Stack"), List.of()),
                        new ComponentGroup("Charts", List.of("Tag"), List.of("base note"))),
                List.of(new ToolSpec("loadOrders", "loads orders", Map.of(), Map.of(), null)),
                List.of("root = Stack([])"), List.of("base rule"),
                List.of(new BuiltinSpec("map(items, fn)", "maps items", false)));
    }

    @SafeVarargs
    private static LinkedHashMap<String, ComponentPromptSpec> orderedComponents(
            Map.Entry<String, ComponentPromptSpec>... entries) {
        LinkedHashMap<String, ComponentPromptSpec> result = new LinkedHashMap<>();
        for (Map.Entry<String, ComponentPromptSpec> entry : entries)
            result.put(entry.getKey(), entry.getValue());
        return result;
    }

    private static Map.Entry<String, ComponentPromptSpec> component(String name) {
        return Map.entry(name, new ComponentPromptSpec(name + " description",
                schema(properties("title", Map.of("type", "string")), List.of("title"))));
    }

    private static LinkedHashMap<String, Object> properties(String name, Map<String, Object> schema) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put(name, schema);
        return result;
    }

    private static Map<String, Object> schema(Map<String, Object> properties, List<String> required) {
        LinkedHashMap<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        return schema;
    }
}
