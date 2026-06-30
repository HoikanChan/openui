/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.contract.ComponentGroup;
import com.huawei.cloudsop.genui.core.contract.ComponentPromptSpec;
import com.huawei.cloudsop.genui.core.contract.DataModelSpec;
import com.huawei.cloudsop.genui.core.contract.GenUIExtension;
import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import com.huawei.cloudsop.genui.core.prompt.GenUIPromptAssemblyResult;
import com.huawei.cloudsop.genui.core.prompt.GenUIPromptRequest;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class PromptAssemblySnippetTest {
    @Test
    void promptContainsBaseExtensionRequestRulesAndDataModelSections() {
        LinkedHashMap<String, ComponentPromptSpec> components = new LinkedHashMap<>();
        components.put("Stack", new ComponentPromptSpec("Stack(children?: Component[])", "Layout"));
        GenerationContract base = new GenerationContract("base-v1", "Stack", components,
                List.of(new ComponentGroup("Layout", List.of("Stack"), List.of())), List.of(), List.of(),
                List.of("Base rule"));
        GenerationSdk sdk = GenerationSdk.builder().baseContract(base).build();
        sdk.register(new GenUIExtension("genA", "gen-v1",
                Map.of("BizCard", new ComponentPromptSpec("BizCard(title: string)", "Business card")),
                List.of(new ComponentGroup("Business", List.of("BizCard"), List.of())), List.of(),
                List.of("root = Stack([BizCard(\"A\")])"), List.of("Generation rule")));

        GenUIPromptAssemblyResult result = sdk.assemblePrompt(new GenUIPromptRequest("genA",
                new DataModelSpec("Business data", Map.of("accounts", List.of(Map.of("name", "Acme")))),
                List.of("Request rule"), null, null, null, null));

        assertTrue(result.prompt().contains("Stack(children?: Component[])"));
        assertTrue(result.prompt().contains("BizCard(title: string)"));
        assertTrue(result.prompt().contains("Request rule"));
        assertTrue(result.prompt().contains("Generation rule"));
        assertTrue(result.prompt().contains("## Data Model"));
        assertTrue(result.prompt().contains("Business data"));
    }
}
