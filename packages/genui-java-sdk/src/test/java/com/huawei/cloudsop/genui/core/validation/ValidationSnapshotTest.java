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
    void typesOnlyDefinitionsReachableFromTheSelectedEntry() {
        String dsl = "page = CardHeader(data.row.name)\nother = CardHeader(data.row.missing)";
        Map<String, Object> data = Map.of("row", Map.of("name", "Ada"));
        ValidationResult root = validate(dsl, data, "page");
        ValidationResult other = validate(dsl, data, "other");

        assertEquals(ValidationStatus.VALID, root.status(), () -> root.issues().toString());
        assertEquals("page", root.metadata().rootName());
        assertEquals(ValidationStatus.INVALID, other.status());
        assertEquals("other", other.metadata().rootName());
        assertTrue(
                other.issues().stream()
                        .anyMatch(issue -> issue.code().equals("type-data-path-missing")));
        assertFalse(root.issues().stream().anyMatch(issue -> issue.code().startsWith("type-")));
    }

    @Test
    void fixesNestedSchemaBeforeSemanticAndTypeChecks() {
        Map<String, Object> title =
                new LinkedHashMap<>(Map.of("type", "string")) {
                    @Override
                    public Object get(Object key) {
                        Object value = super.get(key);
                        put("type", "number");
                        return value;
                    }
                };
        GenerationContract contract = contract(title);
        ValidationResult result =
                DefaultOpenuiLangValidator.INSTANCE.validate(
                        ValidationRequest.builder()
                                .dsl("root = Header(data.name)")
                                .contract(contract)
                                .dataModel(Map.of("name", "Ada"))
                                .build());

        assertEquals(ValidationStatus.VALID, result.status(), () -> result.issues().toString());
        assertEquals("string", title.entrySet().iterator().next().getValue());
    }

    @Test
    void rebuildsInputsAfterProgramContractDataAndExternalReferencesChange() {
        Map<String, Object> title = new LinkedHashMap<>(Map.of("type", "string"));
        GenerationContract contract = contract(title);
        Map<String, Object> row = new LinkedHashMap<>(Map.of("name", "Ada"));
        ValidationRequest request =
                ValidationRequest.builder()
                        .dsl("root = Header(data.row.name)")
                        .contract(contract)
                        .dataModel(Map.of("row", row))
                        .build();
        assertEquals(
                ValidationStatus.VALID,
                DefaultOpenuiLangValidator.INSTANCE.validate(request).status());
        row.put("name", 42);
        assertEquals(
                ValidationStatus.INVALID,
                DefaultOpenuiLangValidator.INSTANCE.validate(request).status());
        title.put("type", "number");
        assertEquals(
                ValidationStatus.VALID,
                DefaultOpenuiLangValidator.INSTANCE.validate(request).status());

        ValidationRequest changed =
                ValidationRequest.builder()
                        .dsl("root = Header(data.row.missing)")
                        .contract(contract)
                        .dataModel(request.dataModel())
                        .build();
        assertTrue(
                DefaultOpenuiLangValidator.INSTANCE.validate(changed).issues().stream()
                        .anyMatch(issue -> issue.code().equals("type-data-path-missing")));
        ValidationRequest external =
                ValidationRequest.builder()
                        .dsl("root = Header(host)")
                        .contract(contract)
                        .externalRefs(Set.of("host"))
                        .build();
        assertEquals(
                ValidationStatus.VALID,
                DefaultOpenuiLangValidator.INSTANCE.validate(external).status());
        assertEquals(
                ValidationStatus.INVALID,
                DefaultOpenuiLangValidator.INSTANCE
                        .validate(
                                ValidationRequest.builder()
                                        .dsl(external.dsl())
                                        .contract(contract)
                                        .build())
                        .status());
    }

    @Test
    void isolatesParallelRequestsOnTheSharedPublicValidator() throws Exception {
        List<Callable<ValidationResult>> calls = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            boolean valid = i % 2 == 0;
            calls.add(
                    () ->
                            validate(
                                    "root = CardHeader(data.row.name)",
                                    Map.of("row", Map.of("name", valid ? "Ada" : 42)),
                                    null));
        }
        try (var executor = Executors.newFixedThreadPool(4)) {
            var results = executor.invokeAll(calls);
            for (int i = 0; i < results.size(); i++) {
                assertEquals(
                        i % 2 == 0 ? ValidationStatus.VALID : ValidationStatus.INVALID,
                        results.get(i).get().status());
            }
        }
    }

    @Test
    void usesTheSameCurrentBuiltinContractForParsingAndTypeChecking() {
        GenerationContract base = contract(Map.of("type", "string"));
        ValidationResult invalid = validateIdentity(identityContract(base, "number"));
        assertTrue(
                invalid.issues().stream()
                        .anyMatch(issue -> issue.code().equals("builtin-argument-type-mismatch")));
        assertFalse(
                invalid.issues().stream()
                        .anyMatch(issue -> issue.code().equals("unknown-component")));
        assertEquals(
                ValidationStatus.VALID,
                validateIdentity(identityContract(base, "string")).status());
    }

    private static GenerationContract identityContract(GenerationContract base, String paramType) {
        return new GenerationContract(
                "custom",
                base.root(),
                base.components(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(
                        new BuiltinSpec(
                                "Identity",
                                "",
                                "",
                                false,
                                List.of(
                                        new BuiltinSpec.ParamSpec(
                                                "value",
                                                paramType,
                                                new TypeExpr.Base(paramType),
                                                false,
                                                false)),
                                new TypeExpr.Base("string"))),
                List.of());
    }

    private static ValidationResult validateIdentity(GenerationContract contract) {
        return DefaultOpenuiLangValidator.INSTANCE.validate(
                ValidationRequest.builder()
                        .dsl("root = Header(@Identity(data.name))")
                        .contract(contract)
                        .dataModel(Map.of("name", "Ada"))
                        .build());
    }

    @Test
    void keepsLastDefinitionForFinalValidation() {
        Map<String, Object> data = Map.of("row", Map.of("name", "Ada"));
        assertEquals(
                ValidationStatus.VALID,
                validate(
                                "root = CardHeader(data.row.missing)\n"
                                        + "root = CardHeader(data.row.name)",
                                data,
                                null)
                        .status());
        assertEquals(
                ValidationStatus.INVALID,
                validate("root = CardHeader(data.row.missing)", data, null).status());
    }

    @Test
    void snapshotRetainsConciseBuiltinDefinitions() {
        // 快照复制 BuiltinSpec 时必须保留简洁声明,否则快照内的语义/类型分析与请求契约不一致。
        ValidationSnapshot snapshot =
                ValidationSnapshot.build(
                        ValidationRequest.builder()
                                .dsl("root = Stack(@Each(data.rows, row, TextContent(row.id)))")
                                .contract(GenerationContractLoader.loadDefault())
                                .dataModel(Map.of("rows", List.of(Map.of("id", "a"))))
                                .build());

        BuiltinSpec each = snapshot.builtins().spec("Each");
        BuiltinSpec defaultEach =
                GenerationContractLoader.loadDefault().builtins().stream()
                        .filter(spec -> "Each".equals(spec.name()))
                        .findFirst()
                        .orElseThrow();
        assertEquals(defaultEach.params(), each.params());
        assertEquals(defaultEach.returnType(), each.returnType());

        BuiltinSpec render = snapshot.builtins().spec("Render");
        BuiltinSpec defaultRender =
                GenerationContractLoader.loadDefault().builtins().stream()
                        .filter(spec -> "Render".equals(spec.name()))
                        .findFirst()
                        .orElseThrow();
        assertEquals(defaultRender.params(), render.params());
    }

    private GenerationContract contract(Map<String, Object> title) {
        return new GenerationContract(
                "test",
                "Header",
                Map.of(
                        "Header",
                        new ComponentPromptSpec(
                                "header",
                                Map.of(
                                        "type",
                                        "object",
                                        "properties",
                                        Map.of("title", title),
                                        "required",
                                        List.of("title")))),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    private ValidationResult validate(String dsl, Map<String, Object> data, String rootName) {
        return DefaultOpenuiLangValidator.INSTANCE.validate(
                ValidationRequest.builder()
                        .dsl(dsl)
                        .contract(GenerationContractLoader.loadDefault())
                        .dataModel(data)
                        .rootName(rootName)
                        .build());
    }
}
