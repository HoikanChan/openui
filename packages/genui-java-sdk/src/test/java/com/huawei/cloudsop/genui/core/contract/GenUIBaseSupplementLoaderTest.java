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
                  "additionalRules": ["Prefer TopoChart for topology."]
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
    }

    @Test
    void defaultsMissingSectionsToEmptyCollections() {
        GenUIBaseSupplement supplement = GenUIBaseSupplementLoader.fromJson("{}");

        assertEquals(Map.of(), supplement.components());
        assertEquals(List.of(), supplement.componentGroups());
        assertEquals(List.of(), supplement.examples());
        assertEquals(List.of(), supplement.additionalRules());
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
}
