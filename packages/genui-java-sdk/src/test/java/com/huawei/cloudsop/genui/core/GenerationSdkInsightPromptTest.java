/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.contract.BuiltinSpec;
import com.huawei.cloudsop.genui.core.contract.ComponentGroup;
import com.huawei.cloudsop.genui.core.contract.ComponentPromptSpec;
import com.huawei.cloudsop.genui.core.contract.DataModelSpec;
import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import com.huawei.cloudsop.genui.core.contract.GenerationContractLoader;
import com.huawei.cloudsop.genui.core.prompt.GenUIPromptRequest;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * End-to-end checks that {@link GenerationSdk#assemblePrompt} injects matched, slot-rendered
 * few-shot examples, contract rules, and fired insight rule prompts for the four core scenarios.
 */
class GenerationSdkInsightPromptTest {

    private static final List<BuiltinSpec> BUILTINS =
            GenerationContractLoader.loadDefault().builtins();

    @Test
    void injectsGroupedAggregationExampleAndRuleWithRenderedSlots() {
        List<Object> readings = new ArrayList<>();
        String[] cells = {"cell-a", "cell-b", "cell-c"};
        for (int i = 0; i < 100; i++) {
            readings.add(
                    orderedMap(
                            "cellId",
                            cells[i % cells.length],
                            "signal",
                            (long) (10 + i % 7),
                            "quality",
                            i % 2 == 0 ? "good" : "fair"));
        }
        String prompt = assemble(orderedMap("readings", readings));

        assertTrue(prompt.contains("root = Stack([])"), "static example must pass through");
        assertTrue(
                prompt.contains("root = Stack([Table(readings, [cellId])])"),
                "matched grouped-aggregation example must render arrayPath and groupField");
        assertTrue(
                prompt.contains("按 cellId 分组展示 signal"),
                "tag-matched rule must render groupField and metricField");
        assertTrue(prompt.contains("@Filter"), "fired grouped-aggregation rule prompt must appear");
        assertFalse(prompt.contains("不应出现"), "unmatched tagged entries must be filtered out");
        assertFalse(prompt.contains("root = Tabs"), "unmatched tabs example must be filtered out");
    }

    @Test
    void injectsCorrelatedMergeExampleForSameIdCpuGpuObjects() {
        Map<String, Object> cpu =
                orderedMap(
                        "deviceId", "dev-01", "cpuUsage", 42.5, "cpuCores", 16L, "cpuTemp", 61.0);
        Map<String, Object> gpu =
                orderedMap(
                        "deviceId",
                        "dev-01",
                        "gpuUsage",
                        88.0,
                        "gpuMemGb",
                        24L,
                        "gpuPowerW",
                        250.0);
        String prompt = assemble(orderedMap("metrics", List.of(cpu, gpu)));

        assertTrue(
                prompt.contains("关联键 deviceId"),
                "correlated-merge example must render the join key");
        assertTrue(prompt.contains("合并渲染为同一实体"), "fired correlated-merge rule prompt must appear");
        assertFalse(prompt.contains("root = Tabs"), "tabs example must not match this payload");
    }

    @Test
    void injectsMultiEntityTabsExampleWithTabTitles() {
        List<Object> devices = new ArrayList<>();
        String[] names = {"设备A", "设备B", "设备C"};
        for (String name : names) {
            List<Object> alarms = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                alarms.add(
                        orderedMap(
                                "timestamp", 1_700_000_000_000L + i * 60_000L,
                                "severity", i % 2 == 0 ? "critical" : "warning",
                                "message", name + " alarm " + i));
            }
            devices.add(orderedMap("deviceName", name, "status", "online", "alarms", alarms));
        }
        String prompt = assemble(orderedMap("devices", devices));

        assertTrue(
                prompt.contains("root = Tabs([[\"设备A\",\"设备B\",\"设备C\"]])"),
                "tabs example must render the tab title list as compact JSON");
        assertTrue(
                prompt.contains("推荐外层使用 Tabs"), "fired multi-entity-tabs rule prompt must appear");
        assertFalse(prompt.contains("关联键 deviceId"), "merge example must not match");
    }

    @Test
    void injectsEmptyDataAndStatusAcknowledgementGates() {
        String emptyPrompt = assemble(new LinkedHashMap<>());
        assertTrue(emptyPrompt.contains("数据集为空"), "empty data gate prompt must appear");
        assertFalse(emptyPrompt.contains("root = Tabs"), "unmatched tabs example must not appear");

        String ackPrompt = assemble(orderedMap("code", 200L, "message", "success"));
        assertTrue(ackPrompt.contains("API 状态回执"), "status ack gate prompt must appear");
        assertFalse(ackPrompt.contains("数据集为空"), "empty gate must not fire for status ack");
    }

    // ─── helpers ───────────────────────────────────────────────────────────────

    private static String assemble(Map<String, Object> raw) {
        GenerationSdk sdk = GenerationSdk.builder().baseContract(contract()).build();
        GenUIPromptRequest request =
                new GenUIPromptRequest(
                        null, new DataModelSpec("Test data", raw), null, null, null, null, null);
        return sdk.assemblePrompt(request).prompt();
    }

    private static GenerationContract contract() {
        LinkedHashMap<String, ComponentPromptSpec> components = new LinkedHashMap<>();
        components.put("Stack", new ComponentPromptSpec("Stack(children?: Component[])", "Layout"));
        components.put("TextContent", new ComponentPromptSpec("TextContent(text: string)", "Text"));
        components.put("Table", new ComponentPromptSpec("Table(data?: object)", "Table"));
        components.put("Tabs", new ComponentPromptSpec("Tabs(tabs?: object[])", "Tabs"));
        List<String> examples =
                List.of(
                        "root = Stack([])",
                        "[[l2-grouped-aggregation]]"
                                + " root = Stack([Table({{arrayPath}}, [{{groupField}}])])",
                        "[[group-by]] 按 {{groupField}} 分组展示 {{metricField}}",
                        "[[tabs]] root = Tabs([{{tabTitles | [\"A\"]}}])",
                        "[[correlated-merge]] 关联键 {{joinKey}}",
                        "[[l2-top-n-ranking]] 不应出现");
        List<String> rules =
                List.of(
                        "Base rule",
                        "[[l2-grouped-aggregation, group-by]] 用 @Filter 分组 {{enumValues}}",
                        "[[tabs]] 外层使用 Tabs 承载",
                        "[[unmatched-id]] 不应出现");
        return new GenerationContract(
                "base-v1",
                "Stack",
                components,
                List.of(new ComponentGroup("Layout", List.copyOf(components.keySet()), List.of())),
                List.of(),
                examples,
                rules,
                BUILTINS,
                List.of());
    }

    private static Map<String, Object> orderedMap(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }
}
