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
import com.huawei.cloudsop.genui.core.contract.DataModelSpec;
import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import com.huawei.cloudsop.genui.core.contract.GenerationContractLoader;
import com.huawei.cloudsop.genui.core.prompt.PromptAssembler;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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

    @Test
    void loadsDefaultCapabilityGapsFromBaseContract() {
        GenerationContract contract = GenerationContractLoader.loadDefault();

        assertEquals(List.of("group-by", "top-n", "join"),
                contract.capabilityGaps().stream().map(CapabilityGapSpec::id).toList());
        CapabilityGapSpec groupBy = contract.capabilityGaps().get(0);
        assertEquals("data", groupBy.kind());
        assertEquals("分组展示", groupBy.name());
        assertTrue(groupBy.aliases().containsAll(List.of("GroupBy", "Distinct", "ObjectFromEntries")));
        assertEquals(List.of("按X分组", "每个X下列出", "按X汇总", "去重"), groupBy.phrases());
        assertEquals("用一张 Table 展示全部行；用 @Sort 按分组字段排序，并把该字段放在第一列", groupBy.fallback());
    }

    @Test
    void failsFastWhenCapabilityGapAliasCollidesWithBuiltinOrComponent() {
        GenerationSdkException builtinCollision = assertThrows(GenerationSdkException.class,
                () -> GenerationContractLoader.fromJson(
                        """
                                {
                                  "builtins": [
                                    {"name": "Join", "signature": "Join(a)", "description": "d", "params": [{"name": "a", "type": "any"}]}
                                  ],
                                  "capabilityGaps": [
                                    {"id": "join", "kind": "data", "name": "关联", "aliases": ["Join"], "phrases": ["关联"], "fallback": "单表"}
                                  ]
                                }
                                """));
        assertTrue(builtinCollision.getMessage().contains("Join"), builtinCollision.getMessage());

        GenerationSdkException componentCollision = assertThrows(GenerationSdkException.class,
                () -> GenerationContractLoader.fromJson(
                        """
                                {
                                  "components": {
                                    "Table": {"signature": "Table(cols, rows)", "description": "t"}
                                  },
                                  "capabilityGaps": [
                                    {"id": "tbl", "kind": "component", "name": "表格", "aliases": ["Table"], "phrases": ["表格"], "fallback": "单表"}
                                  ]
                                }
                                """));
        assertTrue(componentCollision.getMessage().contains("Table"), componentCollision.getMessage());
    }

    @Test
    void failsFastOnDuplicateCapabilityGapIdOrInvalidKind() {
        assertThrows(GenerationSdkException.class, () -> GenerationContractLoader.fromJson("""
                {
                  "capabilityGaps": [
                    {"id": "g1", "kind": "data", "name": "A", "aliases": ["Foo"], "phrases": ["a"], "fallback": "f"},
                    {"id": "g1", "kind": "data", "name": "B", "aliases": ["Bar"], "phrases": ["b"], "fallback": "f"}
                  ]
                }
                """));

        assertThrows(GenerationSdkException.class, () -> GenerationContractLoader.fromJson("""
                {
                  "capabilityGaps": [
                    {"id": "g1", "kind": "unknown", "name": "A", "aliases": ["Foo"], "phrases": ["a"], "fallback": "f"}
                  ]
                }
                """));
    }

    @Test
    void exportedFormattingContractRoundTripsIntoPromptSignatures() throws Exception {
        String exported;
        try (InputStream stream = getClass().getResourceAsStream("/openui/base-contract.json")) {
            assertTrue(stream != null, "exported base-contract.json resource missing");
            exported = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        // 端到端:导出的契约文本重新加载后,加载结果与原契约一致(不是读取另一份默认词表)。
        GenerationContract reloaded = GenerationContractLoader.fromJson(exported);
        GenerationContract defaults = GenerationContractLoader.loadDefault();
        for (String name : FORMATTING_BUILTINS) {
            BuiltinSpec fromExport = find(reloaded, name);
            BuiltinSpec fromDefault = find(defaults, name);
            assertEquals(fromDefault.signature(), fromExport.signature(), name + " signature");
            assertEquals(fromDefault.returnType(), fromExport.returnType(), name + " returnType");
            assertTrue(fromExport.typeRule() != null, name + " must carry a compiled structured rule");
            assertEquals(fromDefault.typeRule(), fromExport.typeRule(), name + " typeRule");
        }

        // 提示词中的 builtin 文本直接来自同一份加载结果:signature 与描述逐字一致。
        String prompt = PromptAssembler.assemble(
                new PromptAssembler.PromptInput(null, reloaded.root(), reloaded.components(),
                        reloaded.componentGroups(), new DataModelSpec("Demo data", Map.of("value", 1)),
                        reloaded.tools(), reloaded.examples(), reloaded.additionalRules(), null, null, null, null),
                reloaded.builtins(), reloaded.capabilityGaps());
        for (String name : FORMATTING_BUILTINS) {
            BuiltinSpec spec = find(reloaded, name);
            assertTrue(prompt.contains("@" + spec.signature() + " — " + spec.description()),
                    name + " prompt signature drifted from the loaded contract");
        }
    }

    private static final List<String> FORMATTING_BUILTINS = List.of("FormatDate", "FormatBytes", "FormatNumber",
            "FormatPercent", "FormatDuration");

    private static BuiltinSpec find(GenerationContract contract, String name) {
        return contract.builtins().stream().filter(spec -> name.equals(spec.name())).findFirst()
                .orElseThrow(() -> new AssertionError("builtin missing: " + name));
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
