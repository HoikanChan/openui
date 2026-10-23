/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import com.huawei.cloudsop.genui.core.contract.GenerationContractLoader;
import com.huawei.cloudsop.genui.core.validation.DefaultOpenuiLangValidator;
import com.huawei.cloudsop.genui.core.validation.ValidationIssue;
import com.huawei.cloudsop.genui.core.validation.ValidationMode;
import com.huawei.cloudsop.genui.core.validation.ValidationRequest;
import com.huawei.cloudsop.genui.core.validation.ValidationResult;
import com.huawei.cloudsop.genui.core.validation.ValidationStatus;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

class ObjectEnumerationValidationTest {
    @Test
    void renamedEnumerationRulesWorkThroughPublicValidator() throws IOException {
        String json;
        try (var input = getClass().getResourceAsStream("/openui/base-contract.json")) {
            json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        GenerationContract contract = GenerationContractLoader
                .fromJson(json.replace("\"name\": \"ObjectKeys\"", "\"name\": \"KeysAlias\"")
                        .replace("\"name\": \"ObjectValues\"", "\"name\": \"ValuesAlias\"")
                        .replace("\"name\": \"ObjectEntries\"", "\"name\": \"EntriesAlias\""));
        for (String expression : new String[]{"@KeysAlias({port: 1})", "@ValuesAlias({port: 1})",
                "@Each(@EntriesAlias({port: 1}), e, e.value)"}) {
            ValidationResult result = validate(contract, "root = Stack(@Each(" + expression + ", v, TextContent(v)))");
            assertEquals(ValidationStatus.VALID, result.status(), () -> result.issues().toString());
            assertEquals("0", result.metadata().extra().get("typeChecks.unproven"));
        }
    }

    @Test
    void effectiveContractOverridesDefaultEnumerationRule() throws IOException {
        String json;
        try (var input = getClass().getResourceAsStream("/openui/base-contract.json")) {
            json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        var document = com.huawei.cloudsop.genui.core.Json.asObject(com.huawei.cloudsop.genui.core.Json.parse(json),
                "contract");
        for (Object raw : com.huawei.cloudsop.genui.core.Json.asList(document.get("builtins"), "builtins")) {
            var builtin = com.huawei.cloudsop.genui.core.Json.asObject(raw, "builtin");
            if ("ObjectValues".equals(builtin.get("name"))) {
                builtin.put("typeRule", Map.of("typeRulesVersion", 1, "arity", Map.of("min", 1, "max", 1), "parameters",
                        java.util.List.of(Map.of("name", "obj")), "result", Map.of("type", "string")));
            }
        }
        var contract = GenerationContractLoader.fromJson(com.huawei.cloudsop.genui.core.Json.stringify(document));
        ValidationResult result = validate(contract, "root = TextContent(@Abs(@ObjectValues({port: 1})))");
        assertEquals("/args/0", issue(result, "builtin-argument-type-mismatch").path());
    }

    @Test
    void enumerationFeedsEachAndComponentRequirements() {
        for (String expression : new String[]{"@ObjectKeys({port: 1, name: \"a\"})",
                "@ObjectValues({port: 1, name: \"a\"})", "@Each(@ObjectEntries({port: 1, name: \"a\"}), e, e.value)"}) {
            ValidationResult result = validate(GenerationContractLoader.loadDefault(),
                    "root = Stack(@Each(" + expression + ", item, TextContent(item)))");
            assertEquals(ValidationStatus.VALID, result.status(), () -> result.issues().toString());
        }
        ValidationResult missing = validate(GenerationContractLoader.loadDefault(),
                "root = Stack(@Each(@ObjectEntries({port: 1}), e, TextContent(e.port)))");
        var issue = issue(missing, "type-field-missing");
        assertEquals("root", issue.statementId());
        assertTrue(issue.message().contains("port"));
        ValidationResult wrongValue = validate(GenerationContractLoader.loadDefault(),
                "root = Stack(@Each(@ObjectValues({nested: {port: 1}}), v, TextContent(v)))");
        var mismatch = issue(wrongValue, "type-prop-mismatch");
        assertEquals("TextContent", mismatch.component());
        assertEquals("/text", mismatch.path());
    }

    @Test
    void closedEmptyEnumerationStillChecksIndependentBodyErrors() {
        for (String builtin : new String[]{"ObjectKeys", "ObjectValues", "ObjectEntries"}) {
            ValidationResult result = validate(GenerationContractLoader.loadDefault(),
                    "root = Stack(@Each(@" + builtin + "({}), e, TextContent(@Abs(\"bad\"))))");
            assertEquals("/args/0", issue(result, "builtin-argument-type-mismatch").path());
            assertFalse(result.issues().stream().anyMatch(i -> "type-field-missing".equals(i.code())));
            ValidationResult dependent = validate(GenerationContractLoader.loadDefault(),
                    "root = Stack(@Each(@" + builtin + "({}), e, TextContent(e.missing)))");
            assertFalse(dependent.issues().stream().anyMatch(i -> "type-field-missing".equals(i.code())));
            assertTrue(Long.parseLong(dependent.metadata().extra().get("typeChecks.unproven")) > 0);
        }
    }

    @Test
    void entriesCorrelationSatisfiesOnlyMatchingComponentAlternatives() {
        var base = GenerationContractLoader.loadDefault();
        var components = new java.util.LinkedHashMap<>(base.components());
        Map<String, Object> port = entrySchema("port", "number");
        Map<String, Object> name = entrySchema("name", "string");
        components.put("EntryView",
                new com.huawei.cloudsop.genui.core.contract.ComponentPromptSpec("entry",
                        Map.of("type", "object", "properties",
                                Map.of("entry", Map.of("anyOf", java.util.List.of(port, name))), "required",
                                java.util.List.of("entry"))));
        var contract = new GenerationContract(base.contractVersion(), base.root(), components, base.componentGroups(),
                base.tools(), base.examples(), base.additionalRules(), base.builtins(), base.capabilityGaps());
        ValidationResult good = validate(contract,
                "root = Stack(@Each(@ObjectEntries({port: 1, name: \"a\"}), e, EntryView(e)))");
        assertEquals(ValidationStatus.VALID, good.status(), () -> good.issues().toString());
        assertEquals("0", good.metadata().extra().get("typeChecks.unproven"));
        ValidationResult bad = validate(contract,
                "root = Stack(@Each(@ObjectEntries({port: \"wrong\"}), e, EntryView(e)))");
        assertEquals("EntryView", issue(bad, "type-prop-mismatch").component());
    }

    @Test
    void explicitNullValuesStillReachComponentConstraints() {
        ValidationResult result = validate(GenerationContractLoader.loadDefault(),
                "root = Stack(@Each(@ObjectValues({nil: null}), v, TextContent(v)))");
        assertEquals("/text", issue(result, "type-prop-mismatch").path());
    }

    @Test
    void conclusionsAreStableAcrossIndependentValidationRuns() {
        String dsl = "root = Stack(@Each(@ObjectEntries({port: 1}), e, TextContent(e.missing)))";
        var contract = GenerationContractLoader.loadDefault();
        ValidationResult first = validate(contract, dsl);
        ValidationResult second = validate(contract, dsl);
        assertEquals(first.issues(), second.issues());
        assertEquals(first.metadata().extra(), second.metadata().extra());
    }

    @Test
    void unknownObjectInputRemainsUnprovenWithoutArgumentError() {
        for (String builtin : new String[]{"ObjectKeys", "ObjectValues", "ObjectEntries"}) {
            ValidationResult result = DefaultOpenuiLangValidator.INSTANCE.validate(ValidationRequest.builder()
                    .dsl("root = Stack(@Each(@" + builtin + "(data.object), e, TextContent(@String(e))))")
                    .contract(GenerationContractLoader.loadDefault()).mode(ValidationMode.FINAL).build());
            assertFalse(result.issues().stream().anyMatch(i -> "builtin-argument-type-mismatch".equals(i.code())),
                    () -> result.issues().toString());
            assertTrue(Long.parseLong(result.metadata().extra().get("typeChecks.unproven")) > 0);
        }
    }

    @Test
    void rejectsNonObjectInputsAtTheirArgumentPosition() {
        for (String builtin : new String[]{"ObjectKeys", "ObjectValues", "ObjectEntries"}) {
            for (String input : new String[]{"null", "[]", "1"}) {
                ValidationResult result = validate(GenerationContractLoader.loadDefault(),
                        "root = TextContent(@Count(@" + builtin + "(" + input + ")))");
                assertEquals("/args/0", issue(result, "builtin-argument-type-mismatch").path());
            }
        }
    }

    private static Map<String, Object> entrySchema(String key, String valueType) {
        return Map.of("type", "object", "properties",
                Map.of("key", Map.of("type", "string", "enum", java.util.List.of(key)), "value",
                        Map.of("type", valueType)),
                "required", java.util.List.of("key", "value"), "additionalProperties", false);
    }

    private static ValidationIssue issue(ValidationResult result, String code) {
        return result.issues().stream().filter(i -> code.equals(i.code())).findFirst()
                .orElseThrow(() -> new AssertionError("missing " + code + ": " + result.issues()));
    }

    private static ValidationResult validate(GenerationContract contract, String dsl) {
        return DefaultOpenuiLangValidator.INSTANCE.validate(ValidationRequest.builder().dsl(dsl).contract(contract)
                .dataModel(Map.of()).mode(ValidationMode.FINAL).build());
    }
}
