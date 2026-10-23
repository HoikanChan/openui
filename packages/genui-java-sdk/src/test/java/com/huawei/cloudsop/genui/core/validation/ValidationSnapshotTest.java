package com.huawei.cloudsop.genui.core.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.contract.BuiltinSpec;
import com.huawei.cloudsop.genui.core.contract.ComponentPromptSpec;
import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import com.huawei.cloudsop.genui.core.contract.GenerationContractLoader;
import com.huawei.cloudsop.genui.core.contract.typeexpr.TypeExpr;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

class ValidationSnapshotTest {
    @Test
    void readsNestedDataOnceBeforeAnalyzingTheProgram() {
        Map<String, Object> row = new LinkedHashMap<>(Map.of("name", "Ada")) {
            @Override
            public Object get(Object key) {
                Object value = super.get(key);
                put("name", 42);
                return value;
            }
        };
        ValidationResult result = validate("root = Stack([CardHeader(data.row.name), CardHeader(data.row.name)])",
                Map.of("row", row), null, ValidationMode.FINAL);

        assertEquals(ValidationStatus.VALID, result.status(), () -> result.issues().toString());
        assertEquals("Ada", row.entrySet().iterator().next().getValue());
    }

    @Test
    void fixesNestedListsAndPreservesNullValues() {
        Map<String, Object> row = new LinkedHashMap<>(Map.of("name", "Ada"));
        row.put("optional", null);
        List<Object> rows = new ArrayList<>(List.of(row)) {
            @Override
            public Object get(int index) {
                Object value = super.get(index);
                row.put("name", 42);
                return value;
            }
        };
        ValidationResult result = validate("root = CardHeader(data.rows[0].name)", Map.of("rows", rows), null,
                ValidationMode.FINAL);
        assertEquals(ValidationStatus.VALID, result.status(), () -> result.issues().toString());
        assertEquals("Ada", row.get("name"));
        assertTrue(row.containsKey("optional"));
    }

    @Test
    void typesOnlyDefinitionsReachableFromTheSelectedEntry() {
        String dsl = "page = CardHeader(data.row.name)\nother = CardHeader(data.row.missing)";
        Map<String, Object> data = Map.of("row", Map.of("name", "Ada"));
        ValidationResult root = validate(dsl, data, "page", ValidationMode.FINAL);
        ValidationResult other = validate(dsl, data, "other", ValidationMode.FINAL);

        assertEquals(ValidationStatus.VALID, root.status(), () -> root.issues().toString());
        assertEquals("page", root.metadata().rootName());
        assertEquals(ValidationStatus.INVALID, other.status());
        assertEquals("other", other.metadata().rootName());
        assertTrue(other.issues().stream().anyMatch(issue -> issue.code().equals("type-data-path-missing")));
        assertFalse(root.issues().stream().anyMatch(issue -> issue.code().startsWith("type-")));
    }

    @Test
    void fixesNestedSchemaBeforeSemanticAndTypeChecks() {
        Map<String, Object> title = new LinkedHashMap<>(Map.of("type", "string")) {
            @Override
            public Object get(Object key) {
                Object value = super.get(key);
                put("type", "number");
                return value;
            }
        };
        GenerationContract contract = contract(title);
        ValidationResult result = DefaultOpenuiLangValidator.INSTANCE
                .validate(ValidationRequest.builder().dsl("root = Header(data.name)").contract(contract)
                        .dataModel(Map.of("name", "Ada")).mode(ValidationMode.FINAL).build());

        assertEquals(ValidationStatus.VALID, result.status(), () -> result.issues().toString());
        assertEquals("string", title.entrySet().iterator().next().getValue());
    }

    @Test
    void rebuildsInputsAfterProgramContractDataAndExternalReferencesChange() {
        Map<String, Object> title = new LinkedHashMap<>(Map.of("type", "string"));
        GenerationContract contract = contract(title);
        Map<String, Object> row = new LinkedHashMap<>(Map.of("name", "Ada"));
        ValidationRequest request = ValidationRequest.builder().dsl("root = Header(data.row.name)").contract(contract)
                .dataModel(Map.of("row", row)).mode(ValidationMode.FINAL).build();
        assertEquals(ValidationStatus.VALID, DefaultOpenuiLangValidator.INSTANCE.validate(request).status());
        row.put("name", 42);
        assertEquals(ValidationStatus.INVALID, DefaultOpenuiLangValidator.INSTANCE.validate(request).status());
        title.put("type", "number");
        assertEquals(ValidationStatus.VALID, DefaultOpenuiLangValidator.INSTANCE.validate(request).status());

        ValidationRequest changed = ValidationRequest.builder().dsl("root = Header(data.row.missing)")
                .contract(contract).dataModel(request.dataModel()).mode(ValidationMode.FINAL).build();
        assertTrue(DefaultOpenuiLangValidator.INSTANCE.validate(changed).issues().stream()
                .anyMatch(issue -> issue.code().equals("type-data-path-missing")));
        ValidationRequest external = ValidationRequest.builder().dsl("root = Header(host)").contract(contract)
                .externalRefs(Set.of("host")).mode(ValidationMode.FINAL).build();
        assertEquals(ValidationStatus.VALID, DefaultOpenuiLangValidator.INSTANCE.validate(external).status());
        assertEquals(ValidationStatus.INVALID, DefaultOpenuiLangValidator.INSTANCE.validate(
                ValidationRequest.builder().dsl(external.dsl()).contract(contract).mode(ValidationMode.FINAL).build())
                .status());
    }

    @Test
    void isolatesParallelRequestsOnTheSharedPublicValidator() throws Exception {
        List<Callable<ValidationResult>> calls = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            boolean valid = i % 2 == 0;
            calls.add(() -> validate("root = CardHeader(data.row.name)",
                    Map.of("row", Map.of("name", valid ? "Ada" : 42)), null, ValidationMode.FINAL));
        }
        try (var executor = Executors.newFixedThreadPool(4)) {
            var results = executor.invokeAll(calls);
            for (int i = 0; i < results.size(); i++) {
                assertEquals(i % 2 == 0 ? ValidationStatus.VALID : ValidationStatus.INVALID,
                        results.get(i).get().status());
            }
        }
    }

    @Test
    void usesTheSameCurrentBuiltinContractForParsingAndTypeChecking() {
        List<BuiltinSpec.ParamSpec> params = new ArrayList<>(List
                .of(new BuiltinSpec.ParamSpec("value", "number", new TypeExpr.Base("number"), false, false, false)));
        GenerationContract base = contract(Map.of("type", "string"));
        GenerationContract contract = new GenerationContract("custom", base.root(), base.components(), List.of(),
                List.of(), List.of(), List.of(),
                List.of(new BuiltinSpec("Identity", "", "", false, params, new TypeExpr.Base("string"))), List.of());
        ValidationRequest request = ValidationRequest.builder().dsl("root = Header(@Identity(data.name))")
                .contract(contract).dataModel(Map.of("name", "Ada")).mode(ValidationMode.FINAL).build();
        ValidationResult invalid = DefaultOpenuiLangValidator.INSTANCE.validate(request);
        assertTrue(invalid.issues().stream().anyMatch(issue -> issue.code().equals("builtin-argument-type-mismatch")));
        assertFalse(invalid.issues().stream().anyMatch(issue -> issue.code().equals("unknown-component")));
        params.set(0, new BuiltinSpec.ParamSpec("value", "string", new TypeExpr.Base("string"), false, false, false));
        assertEquals(ValidationStatus.VALID, DefaultOpenuiLangValidator.INSTANCE.validate(request).status());
    }

    @Test
    void keepsLastDefinitionAndStreamingTypePolicy() {
        Map<String, Object> data = Map.of("row", Map.of("name", "Ada"));
        assertEquals(ValidationStatus.VALID,
                validate("root = CardHeader(data.row.missing)\n" + "root = CardHeader(data.row.name)", data, null,
                        ValidationMode.FINAL).status());
        ValidationResult streaming = validate("root = CardHeader(data.row.missing)", data, null,
                ValidationMode.STREAMING);
        assertEquals(ValidationStatus.VALID, streaming.status());
        assertFalse(streaming.issues().stream().anyMatch(issue -> issue.code().startsWith("type-")));
        assertEquals(ValidationStatus.INVALID,
                validate("root = CardHeader(data.row.missing)", data, null, ValidationMode.FINAL).status());
    }

    @Test
    void snapshotRetainsCompiledBinderCallFormsAndInputSource() {
        // #83:快照复制 BuiltinSpec 时必须连同编译后的 callForms/inputIndex 一起复制,
        // 否则快照内的语义/类型分析与请求契约不一致。
        ValidationSnapshot snapshot = ValidationSnapshot
                .build(ValidationRequest.builder().dsl("root = Stack(@Each(data.rows, row, TextContent(row.id)))")
                        .contract(GenerationContractLoader.loadDefault())
                        .dataModel(Map.of("rows", List.of(Map.of("id", "a")))).mode(ValidationMode.FINAL).build());

        BuiltinSpec each = snapshot.builtins().spec("Each");
        assertEquals(0, each.inputIndex());
        assertEquals(2, each.bodyIndex(3));
        assertEquals(List.of(1), each.activeBinderIndexes(3));
        assertEquals(List.of(new BuiltinSpec.CallForm(3, 2, List.of(1))), each.callForms());

        BuiltinSpec render = snapshot.builtins().spec("Render");
        assertEquals(-1, render.inputIndex());
        assertEquals(List.of(new BuiltinSpec.CallForm(2, 1, List.of(0)), new BuiltinSpec.CallForm(3, 2, List.of(0, 1))),
                render.callForms());
    }

    private GenerationContract contract(Map<String, Object> title) {
        return new GenerationContract("test", "Header",
                Map.of("Header", new ComponentPromptSpec("header",
                        Map.of("type", "object", "properties", Map.of("title", title), "required", List.of("title")))),
                List.of(), List.of(), List.of(), List.of());
    }

    private ValidationResult validate(String dsl, Map<String, Object> data, String rootName, ValidationMode mode) {
        return DefaultOpenuiLangValidator.INSTANCE
                .validate(ValidationRequest.builder().dsl(dsl).contract(GenerationContractLoader.loadDefault())
                        .dataModel(data).rootName(rootName).mode(mode).build());
    }
}
