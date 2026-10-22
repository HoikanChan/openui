/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.GenerationSdkException;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class GenUIBaseSupplementLoaderTest {
    @Test
    void parsesLegalSupplementWithBothComponentForms() {
        GenUIBaseSupplement supplement = GenUIBaseSupplementLoader.fromJson("""
                {
                  "supplementVersion": "supp-v1",
                  "components": {
                    "TopoChart": {
                      "description": "Topology chart.",
                      "propsSchema": {
                        "type": "object",
                        "properties": { "nodes": { "type": "array", "items": { "type": "any" } } },
                        "required": ["nodes"]
                      }
                    },
                    "LegacyBadge": { "signature": "LegacyBadge(label: string)", "description": "Legacy badge." }
                  },
                  "componentGroups": [
                    { "name": "Charts", "components": ["TopoChart"], "notes": ["Topology first."] }
                  ],
                  "examples": ["root = TopoChart(data.nodes)"],
                  "additionalRules": ["Prefer TopoChart for topology."],
                  "capabilityGaps": [
                    {
                      "id": "tree-map",
                      "kind": "component",
                      "name": "树图",
                      "aliases": ["TreeMap"],
                      "phrases": ["树状图"],
                      "fallback": "用 Table 展示"
                    }
                  ]
                }
                """);

        assertEquals(List.of("TopoChart", "LegacyBadge"), List.copyOf(supplement.components().keySet()));
        assertEquals("Topology chart.", supplement.components().get("TopoChart").description());
        assertTrue(supplement.components().get("TopoChart").propsSchema().containsKey("properties"));
        assertEquals("LegacyBadge(label: string)", supplement.components().get("LegacyBadge").signature());
        assertEquals(1, supplement.componentGroups().size());
        assertEquals("Charts", supplement.componentGroups().get(0).name());
        assertEquals(List.of("TopoChart"), supplement.componentGroups().get(0).components());
        assertEquals(List.of("Topology first."), supplement.componentGroups().get(0).notes());
        assertEquals(List.of("root = TopoChart(data.nodes)"), supplement.examples());
        assertEquals(List.of("Prefer TopoChart for topology."), supplement.additionalRules());
        assertEquals(1, supplement.capabilityGaps().size());
        assertEquals("tree-map", supplement.capabilityGaps().get(0).id());
        assertEquals(List.of("TreeMap"), supplement.capabilityGaps().get(0).aliases());
    }

    @Test
    void defaultsMissingSectionsToEmptyCollections() {
        GenUIBaseSupplement supplement = GenUIBaseSupplementLoader.fromJson("{}");

        assertEquals(Map.of(), supplement.components());
        assertEquals(List.of(), supplement.componentGroups());
        assertEquals(List.of(), supplement.examples());
        assertEquals(List.of(), supplement.additionalRules());
        assertEquals(List.of(), supplement.capabilityGaps());
    }

    @Test
    void rejectsBaseOnlyTopLevelKeysAndListsAllOfThem() {
        GenerationSdkException error = assertThrows(GenerationSdkException.class,
                () -> GenUIBaseSupplementLoader.fromJson("""
                        { "tools": [], "root": "Stack", "builtins": [], "contractVersion": "v1" }
                        """));

        assertTrue(error.getMessage().contains("tools"));
        assertTrue(error.getMessage().contains("root"));
        assertTrue(error.getMessage().contains("builtins"));
        assertTrue(error.getMessage().contains("contractVersion"));
    }

    @Test
    void rejectsMisspelledTopLevelKey() {
        GenerationSdkException error = assertThrows(GenerationSdkException.class,
                () -> GenUIBaseSupplementLoader.fromJson("{ \"additionalRule\": [\"typo\"] }"));

        assertTrue(error.getMessage().contains("additionalRule"));
    }

    @Test
    void fromResourceLoadsClasspathJson() {
        GenUIBaseSupplement supplement = GenUIBaseSupplementLoader.fromResource("genui/test-supplement.json");

        assertEquals(List.of("TopoChart"), List.copyOf(supplement.components().keySet()));
        assertEquals("Charts", supplement.componentGroups().get(0).name());
    }

    @Test
    void fromResourceRejectsMissingResource() {
        GenerationSdkException error = assertThrows(GenerationSdkException.class,
                () -> GenUIBaseSupplementLoader.fromResource("genui/does-not-exist.json"));

        assertTrue(error.getMessage().contains("genui/does-not-exist.json"));
    }

    @Test
    void rejectsNullElementInCapabilityGapAliasesAndPhrasesWithSdkException() {
        GenerationSdkException aliasError = assertThrows(GenerationSdkException.class,
                () -> GenUIBaseSupplementLoader.fromJson("""
                        {
                          "capabilityGaps": [
                            {
                              "id": "gap-1",
                              "kind": "data",
                              "name": "测试缺口",
                              "aliases": [null],
                              "phrases": ["短语"],
                              "fallback": "降级说明"
                            }
                          ]
                        }
                        """));
        assertTrue(aliasError.getMessage().contains("blank alias"));

        GenerationSdkException phraseError = assertThrows(GenerationSdkException.class,
                () -> GenUIBaseSupplementLoader.fromJson("""
                        {
                          "capabilityGaps": [
                            {
                              "id": "gap-2",
                              "kind": "data",
                              "name": "测试缺口",
                              "aliases": ["GapAlias"],
                              "phrases": [null],
                              "fallback": "降级说明"
                            }
                          ]
                        }
                        """));
        assertTrue(phraseError.getMessage().contains("blank phrase"));
    }

    @Test
    void rejectsSupplementCapabilityGapCollidingWithSupplementComponent() {
        GenerationSdkException error = assertThrows(GenerationSdkException.class,
                () -> GenUIBaseSupplementLoader.fromJson("""
                        {
                          "components": {
                            "TopoChart": { "signature": "TopoChart(nodes: any[])", "description": "Topology chart." }
                          },
                          "capabilityGaps": [
                            {
                              "id": "topo-gap",
                              "kind": "component",
                              "name": "拓扑图",
                              "aliases": ["TopoChart"],
                              "phrases": ["拓扑"],
                              "fallback": "用 Table 展示"
                            }
                          ]
                        }
                        """));
        assertTrue(error.getMessage().contains("TopoChart"));
    }
}
