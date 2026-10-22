/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.contract.BuiltinSpec;
import com.huawei.cloudsop.genui.core.contract.CapabilityGapSpec;
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
    void replacesSameIdCapabilityGapInPlaceAndAppendsNewGaps() {
        CapabilityGapSpec replacedGroupBy = new CapabilityGapSpec("group-by", "data", "自定义分组",
                List.of("GroupBy", "CustomGroup"), List.of("按X分组"), "自定义兜底");
        CapabilityGapSpec appendedTree = new CapabilityGapSpec("tree-map", "component", "树图", List.of("TreeMap"),
                List.of("树状图"), "用 Table 展示");
        GenerationSdk sdk = GenerationSdk.builder().baseContract(testBaseContract())
                .baseSupplement(new GenUIBaseSupplement(Map.of(), List.of(), List.of(), List.of(),
                        List.of(appendedTree, replacedGroupBy)))
                .build();

        List<CapabilityGapSpec> gaps = sdk.baseContract().capabilityGaps();
        assertEquals(List.of("group-by", "top-n", "tree-map"), gaps.stream().map(CapabilityGapSpec::id).toList(),
                "replaced gap keeps its original base position; new gap appends");
        assertEquals("自定义分组", gaps.get(0).name());
        assertEquals(List.of("GroupBy", "CustomGroup"), gaps.get(0).aliases());
    }

    @Test
    void rejectsCapabilityGapIdCollisionInExtensionAndAppendsNonCollidingExtensionGaps() {
        GenerationSdk sdk = GenerationSdk.builder().baseContract(testBaseContract()).build();

        GenUIExtension collidingId = new GenUIExtension("ext1", "v1", Map.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(new CapabilityGapSpec("group-by", "data", "冲突", List.of("G"), List.of("p"), "f")));
        GenerationSdkException idError = assertThrows(GenerationSdkException.class, () -> sdk.register(collidingId));
        assertTrue(idError.getMessage().contains("group-by"), idError.getMessage());

        GenUIExtension validExtension = new GenUIExtension("ext2", "v1", Map.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(new CapabilityGapSpec("gantt", "component", "甘特图", List.of("GanttChart"),
                        List.of("甘特图"), "用 Table 展示")));
        sdk.register(validExtension);
        GenerationContract merged = sdk
                .mergedContract(new GenUIPromptRequest("ext2", null, List.of(), null, null, null, null));
        assertEquals(List.of("group-by", "top-n", "gantt"),
                merged.capabilityGaps().stream().map(CapabilityGapSpec::id).toList());
    }

    @Test
    void rejectsCapabilityGapAliasCollidingWithComponentOrBuiltinAcrossLayers() {
        // Supplement gap alias collides with base builtin "map"
        GenerationSdkException builtinErr = assertThrows(GenerationSdkException.class, () -> GenerationSdk.builder()
                .baseContract(testBaseContract())
                .baseSupplement(new GenUIBaseSupplement(Map.of(), List.of(), List.of(), List.of(),
                        List.of(new CapabilityGapSpec("bad-gap", "data", "X", List.of("map"), List.of("x"), "f"))))
                .build());
        assertTrue(builtinErr.getMessage().contains("map"), builtinErr.getMessage());

        // Extension adds component "GroupBy" which collides with base capabilityGap alias "GroupBy"
        GenerationSdk sdk = GenerationSdk.builder().baseContract(testBaseContract()).build();
        GenUIExtension compCollision = new GenUIExtension("extComp", "v1", Map.ofEntries(component("GroupBy")),
                List.of(), List.of(), List.of(), List.of());
        GenerationSdkException compErr = assertThrows(GenerationSdkException.class, () -> sdk.register(compCollision));
        assertTrue(compErr.getMessage().contains("GroupBy"), compErr.getMessage());
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

        assertTrue(error.getMessage().contains("Invalid base supplement"));
        assertTrue(error.getMessage().contains("MissingChart"));
    }

    @Test
    void attributesBaseContractErrorToBaseContractEvenWhenSupplementIsProvided() {
        GenerationContract brokenBase = new GenerationContract("base-v1", "Stack", testBaseContract().components(),
                List.of(new ComponentGroup("BrokenGroup", List.of("NonExistentBaseComp"), List.of())), List.of(),
                List.of(), List.of(), List.of(), List.of());

        GenerationSdkException error = assertThrows(GenerationSdkException.class,
                () -> GenerationSdk.builder().baseContract(brokenBase)
                        .baseSupplement(new GenUIBaseSupplement(Map.of(), List.of(), List.of(), List.of())).build());

        assertTrue(error.getMessage().contains("base contract"));
        assertFalse(error.getMessage().contains("Invalid base supplement"));
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
                List.of(new BuiltinSpec("map", "map(items, fn)", "maps items", false, List.of(), null)),
                List.of(new CapabilityGapSpec("group-by", "data", "分组展示", List.of("GroupBy"), List.of("按X分组"),
                        "单张 Table"),
                        new CapabilityGapSpec("top-n", "data", "截取前 N 条", List.of("Slice"), List.of("前N"), "不截取")));
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
