/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.contract.ComponentPromptSpec;
import com.huawei.cloudsop.genui.core.contract.GenerationContract;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class ContractCatalogTest {

    private static GenerationContract contract(String root, Map<String, ComponentPromptSpec> comps) {
        return new GenerationContract("v1", root, comps, List.of(), List.of(), List.of(), List.of());
    }

    @Test
    void extractsParamOrderRequiredAndDefault() {
        ComponentPromptSpec header = new ComponentPromptSpec("Header",
                Map.of("type", "object", "properties", new java.util.LinkedHashMap<>(
                        Map.of("title", Map.of("type", "string"), "level", Map.of("type", "number", "default", 1.0))),
                        "required", List.of("title")));
        ContractCatalog catalog = ContractCatalog.from(contract("Header", Map.of("Header", header)));

        assertTrue(catalog.isKnown("Header"));
        assertEquals("Header", catalog.root());
        ComponentDef def = catalog.get("Header");
        assertFalse(def.signatureOnly());

        // title required, no default; level optional with default.
        ParamDef title = find(def.params(), "title");
        assertTrue(title.required());
        assertFalse(title.hasDefault());

        ParamDef level = find(def.params(), "level");
        assertFalse(level.required());
        assertTrue(level.hasDefault());
        assertEquals(1.0, level.defaultValue());
    }

    @Test
    void signatureOnlyComponentHasNoParams() {
        ComponentPromptSpec sig = new ComponentPromptSpec("Custom(a, b)", "a custom component");
        ContractCatalog catalog = ContractCatalog.from(contract("Custom", Map.of("Custom", sig)));

        ComponentDef def = catalog.get("Custom");
        assertTrue(def.signatureOnly());
        assertTrue(def.params().isEmpty());
    }

    @Test
    void unknownComponentReturnsNull() {
        ContractCatalog catalog = ContractCatalog.from(contract(null, Map.of()));
        assertFalse(catalog.isKnown("Nope"));
        assertNull(catalog.get("Nope"));
    }

    private static ParamDef find(List<ParamDef> params, String name) {
        return params.stream().filter(p -> p.name().equals(name)).findFirst().orElseThrow();
    }
}
