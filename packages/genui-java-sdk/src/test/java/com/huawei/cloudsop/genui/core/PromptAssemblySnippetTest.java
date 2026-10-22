/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertFalse(result.prompt().contains("plausible data"),
                "plausible data rule must be removed even when dataModel is supplied");
        assertFalse(result.prompt().contains("## Capability Boundary"),
                "when capabilityGaps is empty, ## Capability Boundary must not be rendered");
    }

    @Test
    void promptRemovesPlausibleDataWithoutDataModelAndRendersCapabilityBoundaryAfterDataBuiltins() {
        GenerationSdk sdk = GenerationSdk.create();

        String withoutData = sdk.assemblePrompt(new GenUIPromptRequest(null, null, List.of(), null, null, null, null))
                .prompt();
        assertFalse(withoutData.contains("plausible data"),
                "plausible data rule must be removed when dataModel is absent");

        String withData = sdk.assemblePrompt(
                new GenUIPromptRequest(null, new DataModelSpec("Device data", Map.of("rows", List.of(Map.of("id", 1)))),
                        List.of(), null, null, null, null))
                .prompt();
        assertFalse(withData.contains("plausible data"));
        int dataBuiltinsIdx = withData.indexOf("## Data Built-ins");
        int capBoundaryIdx = withData.indexOf("## Capability Boundary");
        int streamingIdx = withData.indexOf("## Hoisting & Streaming (CRITICAL)");
        assertTrue(dataBuiltinsIdx >= 0, "## Data Built-ins must be present");
        assertTrue(capBoundaryIdx > dataBuiltinsIdx, "## Capability Boundary must follow ## Data Built-ins");
        assertTrue(streamingIdx > capBoundaryIdx, "## Capability Boundary must precede ## Hoisting & Streaming");

        assertTrue(withData.contains(
                "The components and @builtins listed above are the COMPLETE set. Anything not listed does not exist."));
        assertTrue(withData.contains("| Not supported | Typical wording | Show instead |"));
        assertTrue(
                withData.contains("| 分组展示 | 按X分组 / 每个X下列出 / 按X汇总 / 去重 | 用一张 Table 展示全部行；用 @Sort 按分组字段排序，并把该字段放在第一列 |"));
        assertTrue(withData.contains("| 截取前 N 条 | 前N / Top N / 最多的几个 | 用 @Sort 排序后展示全部行，不截取 |"));
        assertTrue(withData.contains("| 跨数组关联 | 关联 / 对应到 / 合并两张表 | 每个数组分别用一张 Table 展示 |"));
        assertTrue(
                withData.contains("capabilityNotice = TextCallout(\"info\", \"部分需求暂不支持\", \"暂不支持<name>，以下按原始数据展示\")"));
        assertTrue(withData.contains("Write the notice in the same language as the user's request."));

        int finalVerifIdx = withData.indexOf("## Final Verification");
        assertTrue(finalVerifIdx >= 0);
        String finalVerification = withData.substring(finalVerifIdx);
        assertTrue(finalVerification.contains("capabilityNotice"),
                "Final Verification must include the capabilityNotice check when capabilityGaps is non-empty");
    }
}
