/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.contract.BuiltinSpec;
import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import com.huawei.cloudsop.genui.core.contract.GenerationContractLoader;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

class BaseContractFixtureTest {
    @Test
    void loadsGeneratedDslEngineBaseContractResource() {
        GenerationContract contract = GenerationContractLoader.loadDefault();

        assertEquals("@cloudsop/openui-react-ui-dsl@1.1.6", contract.contractVersion());
        assertEquals("Stack", contract.root());
        assertTrue(contract.components().containsKey("Stack"));
        assertFalse(contract.components().get("Stack").propsSchema().isEmpty());
        assertTrue(contract.componentGroups().stream()
                .anyMatch(group -> group.name().equals("Layout") && group.components().contains("Stack")));
        assertFalse(contract.additionalRules().isEmpty());
        assertFalse(contract.examples().isEmpty());
    }

    @Test
    void loadsAllTwentyNineBuiltinsWithStructuredSignatures() {
        GenerationContract contract = GenerationContractLoader.loadDefault();

        assertEquals(29, contract.builtins().size());
        Map<String, BuiltinSpec> byName = new LinkedHashMap<>();
        for (BuiltinSpec spec : contract.builtins()) {
            byName.put(spec.name(), spec);
        }
        // 29 条 name 全量在列（含补录的 FormatNumber/FormatPercent）。
        assertEquals(java.util.Set.of("Count", "First", "Last", "Sum", "Avg", "Min", "Max", "Sort", "Filter", "Flatten",
                "ObjectEntries", "ObjectKeys", "ObjectValues", "Round", "Abs", "Floor", "Ceil", "Number", "Switch",
                "FormatDate", "FormatBytes", "FormatNumber", "FormatPercent", "FormatDuration", "MergeObject", "Join",
                "String", "Each", "Render"), byName.keySet());
        // Each/Render 不带 returnType（Java 兜底）；其余 27 条必须带。
        for (BuiltinSpec spec : contract.builtins()) {
            if ("Each".equals(spec.name()) || "Render".equals(spec.name())) {
                assertEquals(Optional.empty(), Optional.ofNullable(spec.returnType()), spec.name());
            } else {
                assertTrue(spec.returnType() != null, spec.name() + " must declare returnType");
            }
        }
    }

    @Test
    void derivesArityMatchingTheLegacyHardcodedTable() {
        GenerationContract contract = GenerationContractLoader.loadDefault();
        Map<String, BuiltinSpec> byName = new LinkedHashMap<>();
        for (BuiltinSpec spec : contract.builtins()) {
            byName.put(spec.name(), spec);
        }
        // 与旧 BuiltinTypeRegistry 硬编码 arity 逐条一致（含旧表之外的 7 条新校验）。
        assertArity(byName, "Count", 1, 1);
        assertArity(byName, "First", 1, 1);
        assertArity(byName, "Last", 1, 1);
        assertArity(byName, "Sum", 1, 1);
        assertArity(byName, "Avg", 1, 1);
        assertArity(byName, "Min", 1, 1);
        assertArity(byName, "Max", 1, 1);
        assertArity(byName, "Sort", 2, 3);
        assertArity(byName, "Filter", 4, 4);
        assertArity(byName, "ObjectEntries", 1, 1);
        assertArity(byName, "ObjectKeys", 1, 1);
        assertArity(byName, "Round", 1, 2);
        assertArity(byName, "Abs", 1, 1);
        assertArity(byName, "Floor", 1, 1);
        assertArity(byName, "Ceil", 1, 1);
        assertArity(byName, "Switch", 2, 3);
        assertArity(byName, "FormatDate", 1, 3);
        assertArity(byName, "FormatBytes", 1, 1);
        assertArity(byName, "FormatNumber", 1, 3);
        assertArity(byName, "FormatPercent", 1, 3);
        assertArity(byName, "FormatDuration", 1, 3);
        assertArity(byName, "Each", 3, 3);
        assertArity(byName, "Render", 2, 3);
        // 新增校验的 7 条（旧词表外）。
        assertArity(byName, "Flatten", 1, 1);
        assertArity(byName, "ObjectValues", 1, 1);
        assertArity(byName, "Number", 1, 1);
        assertArity(byName, "MergeObject", 2, null);
        assertArity(byName, "Join", 1, 2);
        assertArity(byName, "String", 1, 1);
    }

    private static void assertArity(Map<String, BuiltinSpec> byName, String name, int min, Integer max) {
        BuiltinSpec spec = byName.get(name);
        assertTrue(spec != null, name + " missing from contract");
        assertEquals(min, spec.minArity(), name + " min arity");
        assertEquals(Optional.ofNullable(max), spec.maxArity(), name + " max arity");
    }

    @Test
    void derivesBinderIndexesFromContractParams() {
        GenerationContract contract = GenerationContractLoader.loadDefault();
        Map<String, BuiltinSpec> byName = new LinkedHashMap<>();
        for (BuiltinSpec spec : contract.builtins()) {
            byName.put(spec.name(), spec);
        }
        assertEquals(List.of(1), byName.get("Each").binderIndexes());
        assertEquals(List.of(0, 1), byName.get("Render").binderIndexes());
    }

    @Test
    void failsFastOnDuplicateBuiltinName() {
        GenerationSdkException error = assertThrows(GenerationSdkException.class,
                () -> GenerationContractLoader.fromJson("{\"builtins\":[" + builtinJson("A", "\"type\": \"any\"", null)
                        + "," + builtinJson("A", "\"type\": \"any\"", null) + "]}"));
        assertTrue(error.getMessage().contains("duplicate builtin name"), error.getMessage());
    }

    @Test
    void failsFastOnRequiredParamAfterOptional() {
        GenerationSdkException error = assertThrows(GenerationSdkException.class,
                () -> GenerationContractLoader.fromJson("{\"builtins\":["
                        + builtinJson("A",
                                "\"type\": \"any\", \"optional\": true}, {\"name\": \"b\", \"type\": \"number\"", null)
                        + "]}"));
        assertTrue(error.getMessage().contains("must not follow an optional parameter"), error.getMessage());
    }

    @Test
    void failsFastOnVariadicNotInLastPosition() {
        GenerationSdkException error = assertThrows(GenerationSdkException.class,
                () -> GenerationContractLoader.fromJson("{\"builtins\":["
                        + builtinJson("A",
                                "\"type\": \"any\", \"variadic\": true}, {\"name\": \"b\", \"type\": \"number\"", null)
                        + "]}"));
        assertTrue(error.getMessage().contains("must be the last parameter"), error.getMessage());
    }

    @Test
    void failsFastOnBadTypeSyntax() {
        GenerationSdkException error = assertThrows(GenerationSdkException.class, () -> GenerationContractLoader
                .fromJson("{\"builtins\":[" + builtinJson("A", "\"type\": \"integer\"", null) + "]}"));
        assertTrue(error.getMessage().contains("A.a"), error.getMessage());
        assertTrue(error.getMessage().contains("integer"), error.getMessage());
    }

    @Test
    void failsFastOnUnboundTypeVar() {
        GenerationSdkException error = assertThrows(GenerationSdkException.class, () -> GenerationContractLoader
                .fromJson("{\"builtins\":[" + builtinJson("A", "\"type\": \"any\"", "T") + "]}"));
        assertTrue(error.getMessage().contains("no parameter type binds it"), error.getMessage());
    }

    @Test
    void failsFastOnOutOfRangeParamRef() {
        GenerationSdkException error = assertThrows(GenerationSdkException.class, () -> GenerationContractLoader
                .fromJson("{\"builtins\":[" + builtinJson("A", "\"type\": \"any\"", "param(2)") + "]}"));
        assertTrue(error.getMessage().contains("out of range"), error.getMessage());
    }

    private static String builtinJson(String name, String firstParamTail, String returnType) {
        StringBuilder json = new StringBuilder();
        json.append("{\"name\": \"").append(name).append("\", \"signature\": \"").append(name)
                .append("(a)\", \"description\": \"d\", \"params\": [{\"name\": \"a\", ").append(firstParamTail)
                .append("}]");
        if (returnType != null) {
            json.append(", \"returnType\": \"").append(returnType).append("\"");
        }
        return json.append("}").toString();
    }
}
