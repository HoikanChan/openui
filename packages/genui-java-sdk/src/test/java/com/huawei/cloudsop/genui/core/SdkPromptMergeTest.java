/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.huawei.cloudsop.genui.core.contract.BuiltinSpec;
import com.huawei.cloudsop.genui.core.contract.ComponentGroup;
import com.huawei.cloudsop.genui.core.contract.ComponentPromptSpec;
import com.huawei.cloudsop.genui.core.contract.DataModelSpec;
import com.huawei.cloudsop.genui.core.contract.GenUIExtension;
import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import com.huawei.cloudsop.genui.core.contract.GenerationContractLoader;
import com.huawei.cloudsop.genui.core.contract.ToolSpec;
import com.huawei.cloudsop.genui.core.prompt.GenUIPromptRequest;
import com.huawei.cloudsop.genui.core.prompt.PromptAssembler;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SDK-level end-to-end checks that {@link GenerationSdk#assemblePrompt} merges base + registered generation + request
 * additions and feeds the result into {@link PromptAssembler} correctly.
 *
 * <p>
 * The assembler itself is byte-pinned to the TypeScript oracle by {@link PromptGoldenTest}; here we independently
 * reconstruct the merged {@code PromptInput} (base → generation → request order) and assert the SDK produces exactly
 * that prompt — locking the merge wiring and ordering.
 */
class SdkPromptMergeTest {

    private static final List<BuiltinSpec> BUILTINS = GenerationContractLoader.loadDefault().builtins();

    @Test
    void mergesBaseExtensionAndRequestAdditionsIntoAssembler() {
        GenerationContract base = baseContract();
        GenerationSdk sdk = GenerationSdk.builder().baseContract(base).build();

        GenUIExtension generation = new GenUIExtension("genA", "gen-v1",
                singleComponent("BizCard", "BizCard(title: string)", "Business card"),
                List.of(new ComponentGroup("Business", List.of("BizCard"), List.of())), List.of(tool("loadOrders")),
                List.of("root = Stack([BizCard(\"A\")])"), List.of("Generation rule"));
        sdk.register(generation);

        DataModelSpec dataModel = new DataModelSpec("Order data", orderedMap("orders", List.of(orderedMap("id", 1L))));
        GenUIPromptRequest request = new GenUIPromptRequest("genA", dataModel, List.of("Request rule"), null, null,
                null, null);

        String actual = sdk.assemblePrompt(request).prompt();
        String expected = PromptAssembler.assemble(mergedInput(base, generation, dataModel, List.of("Request rule")),
                base.builtins());

        assertEquals(expected, actual);
    }

    @Test
    void mergesBaseAndRequestAdditionsWhenNoExtensionRegistered() {
        GenerationContract base = baseContract();
        GenerationSdk sdk = GenerationSdk.builder().baseContract(base).build();

        GenUIPromptRequest request = new GenUIPromptRequest("genA", null, List.of("Request rule"), null, null, null,
                null);

        String actual = sdk.assemblePrompt(request).prompt();
        String expected = PromptAssembler.assemble(mergedInput(base, null, null, List.of("Request rule")),
                base.builtins());

        assertEquals(expected, actual);
    }

    // ─── helpers ───────────────────────────────────────────────────────────────

    private static PromptAssembler.PromptInput mergedInput(GenerationContract base, GenUIExtension generation,
            DataModelSpec dataModel, List<String> requestRules) {
        LinkedHashMap<String, ComponentPromptSpec> components = new LinkedHashMap<>(base.components());
        List<ComponentGroup> groups = new ArrayList<>(base.componentGroups());
        List<ToolSpec> tools = new ArrayList<>(base.tools());
        List<String> examples = new ArrayList<>(base.examples());
        List<String> rules = new ArrayList<>(base.additionalRules());

        if (generation != null) {
            components.putAll(generation.components());
            groups.addAll(generation.componentGroups());
            tools.addAll(generation.tools());
            examples.addAll(generation.examples());
            rules.addAll(generation.additionalRules());
        }
        rules.addAll(requestRules);

        return new PromptAssembler.PromptInput(null, base.root(), components, groups, dataModel, tools, examples, rules,
                null, null, null, null);
    }

    private static GenerationContract baseContract() {
        LinkedHashMap<String, ComponentPromptSpec> components = new LinkedHashMap<>();
        components.put("Stack", new ComponentPromptSpec("Stack(children?: Component[])", "Layout"));
        components.put("TextContent", new ComponentPromptSpec("TextContent(text: string)", "Text"));
        return new GenerationContract("base-v1", "Stack", components,
                List.of(new ComponentGroup("Layout", List.of("Stack", "TextContent"), List.of("Layout note."))),
                List.of(), List.of("root = Stack([])"), List.of("Base rule"), BUILTINS);
    }

    private static Map<String, ComponentPromptSpec> singleComponent(String name, String signature, String description) {
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
