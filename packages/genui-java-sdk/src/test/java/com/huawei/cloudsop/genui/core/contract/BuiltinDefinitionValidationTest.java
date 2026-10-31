/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.GenerationSdkException;
import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.core.validation.DefaultOpenuiLangValidator;
import com.huawei.cloudsop.genui.core.validation.ValidationRequest;
import com.huawei.cloudsop.genui.core.validation.ValidationResult;
import com.huawei.cloudsop.genui.core.validation.ValidationStatus;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/** #120:简洁契约加载期校验——删除的规则/作用域字段必须失败,特殊函数的布局前提由 builtin module 统一检查, 返回声明与 Java 特殊语义可证明冲突时明确报错。 */
class BuiltinDefinitionValidationTest {

    @Test
    void defaultEachAndRenderWorkFromConciseDeclarations() {
        assertEquals(
                ValidationStatus.VALID,
                validate("root = Stack(@Each(data.rows, \"row\", TextContent(row.id)))").status());
        assertEquals(
                ValidationStatus.VALID,
                validate(
                                "root = Table([c], data.rows)\n"
                                        + "c = Col(\"C\", \"name\", {cell: @Render(\"v\", TextContent(v))})")
                        .status());
        assertEquals(
                ValidationStatus.VALID,
                validate(
                                "root = Table([c], data.rows)\n"
                                        + "c = Col(\"C\", \"name\", {cell: @Render(\"v\", \"row\", Link(\"/users/\" + row.id, v))})")
                        .status());
    }

    @Test
    void eachRequiresItsFixedThreeParameterLayout() {
        Map<String, Object> raw =
                mutateBuiltin("Each", builtin -> builtin.put("params", List.of()));
        GenerationSdkException error =
                assertThrows(
                        GenerationSdkException.class,
                        () -> GenerationContractLoader.fromJson(Json.stringify(raw)));
        assertTrue(error.getMessage().contains("Each"), error.getMessage());
        assertTrue(error.getMessage().contains("exactly 3"), error.getMessage());
    }

    @Test
    void eachFirstParameterMustBeAnArray() {
        Map<String, Object> raw = mutateParam("Each", 0, param -> param.put("type", "string"));
        GenerationSdkException error =
                assertThrows(
                        GenerationSdkException.class,
                        () -> GenerationContractLoader.fromJson(Json.stringify(raw)));
        assertTrue(error.getMessage().contains("must be an array"), error.getMessage());
    }

    @Test
    void renderMiddleRowMustBeOptional() {
        Map<String, Object> raw = mutateParam("Render", 1, param -> param.remove("optional"));
        GenerationSdkException error =
                assertThrows(
                        GenerationSdkException.class,
                        () -> GenerationContractLoader.fromJson(Json.stringify(raw)));
        assertTrue(error.getMessage().contains("must be optional"), error.getMessage());
    }

    @Test
    void switchCasesMustBeARecordType() {
        Map<String, Object> raw =
                mutateParam("Switch", 1, param -> param.put("type", "Record<string, T>[]"));
        GenerationSdkException error =
                assertThrows(
                        GenerationSdkException.class,
                        () -> GenerationContractLoader.fromJson(Json.stringify(raw)));
        assertTrue(error.getMessage().contains("cases"), error.getMessage());
    }

    @Test
    void specialReturnDeclarationMustCoverTheJavaResultShape() {
        Map<String, Object> first = mutateBuiltin("First", b -> b.put("returnType", "T"));
        GenerationSdkException firstError =
                assertThrows(
                        GenerationSdkException.class,
                        () -> GenerationContractLoader.fromJson(Json.stringify(first)));
        assertTrue(firstError.getMessage().contains("does not cover"), firstError.getMessage());

        Map<String, Object> formatDate =
                mutateBuiltin("FormatDate", b -> b.put("returnType", "string"));
        GenerationSdkException dateError =
                assertThrows(
                        GenerationSdkException.class,
                        () -> GenerationContractLoader.fromJson(Json.stringify(formatDate)));
        assertTrue(dateError.getMessage().contains("does not cover"), dateError.getMessage());
    }

    @Test
    void removedBuiltinRuleAndScopeFieldsAreRejected() {
        for (String field :
                List.of(
                        "typeRule",
                        "typeRulesVersion",
                        "inference",
                        "resultBehavior",
                        "input",
                        "callForms")) {
            Map<String, Object> raw =
                    mutateBuiltin("Count", builtin -> builtin.put(field, List.of()));
            GenerationSdkException error =
                    assertThrows(
                            GenerationSdkException.class,
                            () -> GenerationContractLoader.fromJson(Json.stringify(raw)),
                            field);
            assertTrue(error.getMessage().contains("Count"), error.getMessage());
            assertTrue(error.getMessage().contains(field), error.getMessage());
        }
    }

    @Test
    void removedParamScopeFieldsAreRejected() {
        for (String field : List.of("binder", "nameSyntax")) {
            Map<String, Object> raw =
                    mutateParam("Each", 1, param -> param.put(field, Boolean.TRUE));
            GenerationSdkException error =
                    assertThrows(
                            GenerationSdkException.class,
                            () -> GenerationContractLoader.fromJson(Json.stringify(raw)),
                            field);
            assertTrue(error.getMessage().contains("Each"), error.getMessage());
            assertTrue(error.getMessage().contains(field), error.getMessage());
        }
    }

    @Test
    void removedParamRefTypeSyntaxIsRejectedAtLoadTime() {
        Map<String, Object> raw =
                mutateBuiltin("Switch", builtin -> builtin.put("returnType", "T | param(2)"));
        GenerationSdkException error =
                assertThrows(
                        GenerationSdkException.class,
                        () -> GenerationContractLoader.fromJson(Json.stringify(raw)));
        assertTrue(error.getMessage().contains("Switch"), error.getMessage());
        assertTrue(error.getMessage().contains("param(2)"), error.getMessage());
    }

    @Test
    void removedConditionalTypeSyntaxIsRejectedAtLoadTime() {
        Map<String, Object> raw =
                mutateBuiltin(
                        "FormatDate",
                        builtin ->
                                builtin.put(
                                        "returnType",
                                        "string[] when param(0) is array, else string"));
        GenerationSdkException error =
                assertThrows(
                        GenerationSdkException.class,
                        () -> GenerationContractLoader.fromJson(Json.stringify(raw)));
        assertTrue(error.getMessage().contains("FormatDate"), error.getMessage());
        assertTrue(error.getMessage().contains("when"), error.getMessage());
    }

    @Test
    void removedFunctionDoesNotBecomeLegalThroughJavaSpecialLogic() {
        Map<String, Object> raw =
                mutateBuiltin(
                        "Each",
                        builtin -> {
                            builtin.put("name", "EachRemoved");
                            // 改名后不再是 Each,按普通函数要求必须有 returnType。
                            builtin.put("returnType", "array");
                        });
        // 契约里不再有 Each:调用不能因 Java 侧存在 Each 逻辑而合法。
        GenerationContract contract = GenerationContractLoader.fromJson(Json.stringify(raw));
        ValidationResult result =
                DefaultOpenuiLangValidator.INSTANCE.validate(
                        ValidationRequest.builder()
                                .dsl("root = Stack(@Each(data.rows, row, TextContent(row.id)))")
                                .contract(contract)
                                .dataModel(Map.of("rows", List.of(Map.of("id", "a"))))
                                .build());
        assertEquals(ValidationStatus.INVALID, result.status());
    }

    @Test
    void eachSourceStillChecksIndependentBodyErrors() {
        ValidationResult result =
                validate("root = Stack(@Each([1], row, TextContent(@Round(\"bad\"))))");
        assertEquals(ValidationStatus.INVALID, result.status());
        assertTrue(
                result.issues().stream()
                        .anyMatch(
                                issue ->
                                        "builtin-argument-type-mismatch".equals(issue.code())
                                                && issue.message().contains("@Round")),
                () -> String.valueOf(result.issues()));
    }

    @Test
    void missingBuiltinNameIsRejected() {
        Map<String, Object> raw = rawDefaultContract();
        builtinEntry(raw, "Count").remove("name");
        GenerationSdkException error = loadFailure(raw);
        assertTrue(error.getMessage().contains("missing field 'name'"), error.getMessage());
    }

    @Test
    void missingSignatureIsRejected() {
        Map<String, Object> raw = mutateBuiltin("Count", builtin -> builtin.remove("signature"));
        GenerationSdkException error = loadFailure(raw);
        assertTrue(error.getMessage().contains("Count"), error.getMessage());
        assertTrue(error.getMessage().contains("missing field 'signature'"), error.getMessage());
    }

    @Test
    void missingDescriptionIsRejected() {
        Map<String, Object> raw = mutateBuiltin("Count", builtin -> builtin.remove("description"));
        GenerationSdkException error = loadFailure(raw);
        assertTrue(error.getMessage().contains("Count"), error.getMessage());
        assertTrue(error.getMessage().contains("missing field 'description'"), error.getMessage());
    }

    @Test
    void missingTemplateBuiltinIsRejected() {
        Map<String, Object> raw =
                mutateBuiltin("Count", builtin -> builtin.remove("templateBuiltin"));
        GenerationSdkException error = loadFailure(raw);
        assertTrue(error.getMessage().contains("Count"), error.getMessage());
        assertTrue(
                error.getMessage().contains("missing field 'templateBuiltin'"), error.getMessage());
    }

    @Test
    void nonBooleanTemplateBuiltinIsRejected() {
        Map<String, Object> raw =
                mutateBuiltin("Count", builtin -> builtin.put("templateBuiltin", "false"));
        GenerationSdkException error = loadFailure(raw);
        assertTrue(error.getMessage().contains("Count"), error.getMessage());
        assertTrue(error.getMessage().contains("templateBuiltin"), error.getMessage());
        assertTrue(error.getMessage().contains("boolean"), error.getMessage());
    }

    @Test
    void missingParamsIsRejected() {
        Map<String, Object> raw = mutateBuiltin("Count", builtin -> builtin.remove("params"));
        GenerationSdkException error = loadFailure(raw);
        assertTrue(error.getMessage().contains("Count"), error.getMessage());
        assertTrue(error.getMessage().contains("missing field 'params'"), error.getMessage());
    }

    @Test
    void nonArrayParamsIsRejected() {
        Map<String, Object> raw = mutateBuiltin("Count", builtin -> builtin.put("params", "array"));
        GenerationSdkException error = loadFailure(raw);
        assertTrue(error.getMessage().contains("Count"), error.getMessage());
        assertTrue(error.getMessage().contains("params"), error.getMessage());
        assertTrue(error.getMessage().contains("JSON array"), error.getMessage());
    }

    @Test
    void emptyBlankAndDuplicateParameterNamesAreRejected() {
        for (String name : List.of("", "  ", "\t")) {
            GenerationSdkException error =
                    loadFailure(mutateParam("Count", 0, param -> param.put("name", name)));
            assertTrue(error.getMessage().contains("Count"), error.getMessage());
            assertTrue(error.getMessage().contains("name"), error.getMessage());
        }
        Map<String, Object> raw =
                mutateBuiltin(
                        "Count",
                        builtin ->
                                builtin.put(
                                        "params",
                                        List.of(
                                                Map.of("name", "value", "type", "any"),
                                                Map.of("name", "value", "type", "any"))));
        GenerationSdkException error = loadFailure(raw);
        assertTrue(error.getMessage().contains("Count"), error.getMessage());
        assertTrue(error.getMessage().contains("name"), error.getMessage());
        assertTrue(error.getMessage().contains("value"), error.getMessage());
    }

    @Test
    void missingReturnTypeIsRejectedForOrdinaryBuiltin() {
        Map<String, Object> raw = mutateBuiltin("Count", builtin -> builtin.remove("returnType"));
        GenerationSdkException error = loadFailure(raw);
        assertTrue(error.getMessage().contains("Count"), error.getMessage());
        assertTrue(error.getMessage().contains("missing field 'returnType'"), error.getMessage());
    }

    @Test
    void eachAndRenderMayOmitReturnType() {
        GenerationContract contract = GenerationContractLoader.loadDefault();
        for (String name : List.of("Each", "Render")) {
            boolean omitted =
                    contract.builtins().stream()
                            .filter(spec -> name.equals(spec.name()))
                            .allMatch(spec -> spec.returnType() == null);
            assertTrue(omitted, name + " must allow an omitted returnType");
        }
    }

    private static GenerationSdkException loadFailure(Map<String, Object> raw) {
        return assertThrows(
                GenerationSdkException.class,
                () -> GenerationContractLoader.fromJson(Json.stringify(raw)));
    }

    private static ValidationResult validate(String dsl) {
        return DefaultOpenuiLangValidator.INSTANCE.validate(
                ValidationRequest.builder()
                        .dsl(dsl)
                        .contract(GenerationContractLoader.loadDefault())
                        .dataModel(Map.of("rows", List.of(Map.of("id", "a", "name", "Ada"))))
                        .build());
    }

    private static Map<String, Object> mutateBuiltin(
            String name, java.util.function.Consumer<Map<String, Object>> mutation) {
        Map<String, Object> raw = rawDefaultContract();
        mutation.accept(builtinEntry(raw, name));
        return raw;
    }

    private static Map<String, Object> mutateParam(
            String name, int index, java.util.function.Consumer<Map<String, Object>> mutation) {
        return mutateBuiltin(
                name,
                builtin -> mutation.accept(Json.asObject(paramList(builtin).get(index), "param")));
    }

    @SuppressWarnings("unchecked")
    private static List<Object> paramList(Map<String, Object> builtin) {
        return (List<Object>) builtin.get("params");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> builtinEntry(Map<String, Object> raw, String name) {
        for (Object entry : (List<Object>) raw.get("builtins")) {
            Map<String, Object> builtin = (Map<String, Object>) entry;
            if (name.equals(builtin.get("name"))) {
                return builtin;
            }
        }
        throw new IllegalStateException("builtin not found: " + name);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> rawDefaultContract() {
        String original;
        try (var stream =
                BuiltinDefinitionValidationTest.class.getResourceAsStream(
                        "/openui/base-contract.json")) {
            original = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        return (Map<String, Object>) Json.parse(original);
    }
}
