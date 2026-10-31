/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.GenerationSdkException;
import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import com.huawei.cloudsop.genui.core.contract.GenerationContractLoader;
import com.huawei.cloudsop.genui.core.validation.DefaultOpenuiLangValidator;
import com.huawei.cloudsop.genui.core.validation.ValidationIssue;
import com.huawei.cloudsop.genui.core.validation.ValidationRequest;
import com.huawei.cloudsop.genui.core.validation.ValidationResult;
import com.huawei.cloudsop.genui.core.validation.ValidationStatus;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * #119:普通 builtin 只靠 params + returnType 完成参数检查与返回类型推导。
 *
 * <p>契约是测试专用的扩展场景(Median/Head/... 不存在于运行时词表),经公开 validator 验证;不新增真实 builtin, 也不新增 repair-case。
 */
class SignatureBuiltinValidationTest {

    private static final String CONTRACT =
            """
            {
              "contractVersion": "signature-test",
              "root": "Box",
              "components": {
                "Box": {
                  "description": "number box",
                  "propsSchema": {
                    "type": "object",
                    "properties": {"value": {"type": "number"}},
                    "required": ["value"]
                  }
                },
                "TextContent": {
                  "description": "text",
                  "propsSchema": {
                    "type": "object",
                    "properties": {"text": {"type": "string"}},
                    "required": ["text"]
                  }
                }
              },
              "builtins": [
                %s
              ]
            }
            """;

    private static final String BUILTINS =
            """
            {"name": "Median", "signature": "Median(number[]) -> number", "description": "median",
             "templateBuiltin": false, "params": [{"name": "array", "type": "number[]"}],
             "returnType": "number"},
            {"name": "Head", "signature": "Head(T[]) -> T", "description": "first element",
             "templateBuiltin": false, "params": [{"name": "array", "type": "T[]"}],
             "returnType": "T"},
            {"name": "Zip", "signature": "Zip(A, B)", "description": "pair",
             "templateBuiltin": false,
             "params": [{"name": "a", "type": "A"}, {"name": "b", "type": "B"}],
             "returnType": "{first: A, second: B}"},
            {"name": "Deep", "signature": "Deep(T[][]) -> T", "description": "nested",
             "templateBuiltin": false, "params": [{"name": "matrix", "type": "T[][]"}],
             "returnType": "T"},
            {"name": "Values", "signature": "Values(Record<string, T>) -> T[]",
             "description": "record values", "templateBuiltin": false,
             "params": [{"name": "record", "type": "Record<string, T>"}],
             "returnType": "T[]"},
            {"name": "Extract", "signature": "Extract({name, value}[]) -> V[]",
             "description": "object rows", "templateBuiltin": false,
             "params": [{"name": "rows", "type": "{name: string, value: V}[]"}],
             "returnType": "V[]"},
            {"name": "Shape", "signature": "Shape(string | {count: T}) -> T[]",
             "description": "union parameter", "templateBuiltin": false,
             "params": [{"name": "v", "type": "string | {count: T}"}],
             "returnType": "T[]"},
            {"name": "Maybe", "signature": "Maybe(T, D?) -> T | D", "description": "optional generic",
             "templateBuiltin": false,
             "params": [{"name": "x", "type": "T"},
                        {"name": "fallback", "type": "D", "optional": true}],
             "returnType": "T | D"},
            {"name": "Collect", "signature": "Collect(T, ...T) -> T[]", "description": "variadic",
             "templateBuiltin": false,
             "params": [{"name": "first", "type": "T"},
                        {"name": "rest", "type": "T", "variadic": true}],
             "returnType": "T[]"},
            {"name": "Scale", "signature": "Scale(number, number?) -> number",
             "description": "optional parameter", "templateBuiltin": false,
             "params": [{"name": "x", "type": "number"},
                        {"name": "factor", "type": "number", "optional": true}],
             "returnType": "number"},
            {"name": "RecordSum", "signature": "RecordSum(Record<string, number>) -> number",
             "description": "sum record values", "templateBuiltin": false,
             "params": [{"name": "values", "type": "Record<string, number>"}],
             "returnType": "number"},
            {"name": "Raw", "signature": "Raw(any) -> any", "description": "pass through",
             "templateBuiltin": false, "params": [{"name": "x", "type": "any"}],
             "returnType": "any"}
            """;

    @Test
    void simpleSignatureChecksArityOptionalAndArgumentTypes() {
        valid("root = Box(@Median([1, 2, 3]))");
        valid("root = Box(@Scale(1))");
        valid("root = Box(@Scale(1, 2))");

        assertIssue("root = Box(@Median())", "builtin-argument-count-mismatch", "/args");
        assertIssue("root = Box(@Median(1, 2))", "builtin-argument-count-mismatch", "/args");
        assertIssue("root = Box(@Scale())", "builtin-argument-count-mismatch", "/args");
        assertIssue("root = Box(@Scale(1, 2, 3))", "builtin-argument-count-mismatch", "/args");
        assertIssue("root = Box(@Median([\"a\"]))", "builtin-argument-type-mismatch", "/args/0");
        assertIssue("root = Box(@Scale(\"a\"))", "builtin-argument-type-mismatch", "/args/0");
        assertIssue("root = Box(@Scale(1, \"a\"))", "builtin-argument-type-mismatch", "/args/1");
    }

    @Test
    void returnTypePropagatesWithoutAnyJavaSpecificBranch() {
        // 普通新函数(Median/Head)只靠契约声明即可让下游组件看到具体返回类型。
        assertIssueProducedBy("root = TextContent(@Median([1, 2, 3]))", "/text", "number");
        valid("root = Box(@Head([1, 2]))");
        assertIssueProducedBy("root = Box(@Head([\"a\"]))", "/value", "\"a\"");
    }

    @Test
    void namedTypeVariablesBindIndependently() {
        valid("root = Box(@Zip(1, \"x\").first)");
        assertIssueProducedBy("root = Box(@Zip(\"s\", 1).first)", "/value", "\"s\"");
    }

    @Test
    void genericBindingRecursesIntoNestedArraysRecordsAndObjectLiterals() {
        valid("root = Box(@Deep([[1, 2], [3]]))");
        assertIssueProducedBy("root = Box(@Deep([[\"a\"]]))", "/value", "\"a\"");

        valid("root = Box(@Median(@Values({a: 1, b: 2})))");
        assertIssueProducedBy("root = Box(@Head(@Values({a: \"x\"})))", "/value", "\"x\"");

        valid("root = Box(@Median(@Extract([{name: \"a\", value: 1}])))");
        assertIssueProducedBy(
                "root = Box(@Head(@Extract([{name: \"a\", value: \"x\"}])))", "/value", "\"x\"");
    }

    @Test
    void recordValueTypeConstrainsEveryDeclaredEntry() {
        // Record<string, number>:值类型参与入口检查,不只是要求对象。
        valid("root = Box(@RecordSum({a: 1, b: 2}))");
        // 空对象没有值可违反。
        valid("root = Box(@RecordSum({}))");

        assertIssue(
                "root = Box(@RecordSum({a: \"x\"}))", "builtin-argument-type-mismatch", "/args/0");

        // 值类型未知时只应未证明,不得伪报参数错误。
        ValidationResult unknown = validate("root = Box(@RecordSum(data.absent))");
        assertTrue(
                unknown.issues().stream()
                        .noneMatch(issue -> "builtin-argument-type-mismatch".equals(issue.code())),
                () -> unknown.issues().toString());
        assertEquals("0", unknown.metadata().extra().get("typeChecks.mismatched"));
        assertTrue(Long.parseLong(unknown.metadata().extra().get("typeChecks.unproven")) > 0);
    }

    @Test
    void unionParameterBindsOnlyApplicableBranch() {
        // 字符串输入满足 string 分支:不得因为另一个对象分支而要求 count 字段。
        ValidationResult plain = validate("root = Box(@Median(@Shape(\"plain\")))");
        assertTrue(
                plain.issues().stream()
                        .noneMatch(issue -> "builtin-argument-type-mismatch".equals(issue.code())),
                () -> plain.issues().toString());
        assertEquals("0", plain.metadata().extra().get("typeChecks.mismatched"));
        assertTrue(Long.parseLong(plain.metadata().extra().get("typeChecks.unproven")) > 0);

        valid("root = Box(@Median(@Shape({count: 1})))");
    }

    @Test
    void omittedOptionalGenericParameterBindsNull() {
        ValidationResult withoutFallback = validate("root = Box(@Maybe(1))");
        assertIssueProducedBy(withoutFallback, "/value", "null");

        ValidationResult withFallback = validate("root = Box(@Maybe(1, \"x\"))");
        assertIssueProducedBy(withFallback, "/value", "\"x\"");
    }

    @Test
    void everyVariadicArgumentParticipatesInBinding() {
        // first 绑定 number,rest 每个实参都参与:T 合并为 number | "a"。
        assertIssueProducedBy("root = Box(@Head(@Collect(1, \"a\")))", "/value", "\"a\"");
        valid("root = Box(@Head(@Collect(1, 2, 3)))");
    }

    @Test
    void unknownInputIsNotMaskedByKnownArguments() {
        // first 未知、rest 已知 string:T 必须保持未知,不能收窄成 string[]。
        ValidationResult result = validate("root = Box(@Median(@Collect(data.absent, \"a\")))");
        assertTrue(
                result.issues().stream()
                        .noneMatch(issue -> "builtin-argument-type-mismatch".equals(issue.code())),
                () -> result.issues().toString());
        assertEquals("0", result.metadata().extra().get("typeChecks.mismatched"));
        assertTrue(Long.parseLong(result.metadata().extra().get("typeChecks.unproven")) > 0);
    }

    @Test
    void anyReturnStaysUnprovenInsteadOfBecomingAConcreteType() {
        ValidationResult result = validate("root = TextContent(@Raw(1))");
        assertEquals(ValidationStatus.VALID, result.status(), () -> result.issues().toString());
        assertEquals("0", result.metadata().extra().get("typeChecks.mismatched"));
        assertTrue(Long.parseLong(result.metadata().extra().get("typeChecks.unproven")) > 0);
    }

    @Test
    void loaderRejectsReturnVariableWithoutParameterSource() {
        String json =
                CONTRACT.formatted(
                        """
                        {"name": "Broken", "signature": "Broken(number) -> T", "description": "broken",
                         "templateBuiltin": false, "params": [{"name": "x", "type": "number"}],
                         "returnType": "T"}
                        """);
        GenerationSdkException error =
                assertThrows(
                        GenerationSdkException.class,
                        () -> GenerationContractLoader.fromJson(json));
        assertTrue(error.getMessage().contains("Broken"), error.getMessage());
        assertTrue(error.getMessage().contains("'T'"), error.getMessage());
    }

    @Test
    void loaderAcceptsNamedVariablesWithParameterSources() {
        // D 只在可选参数中出现,也是合法来源;独立的 A/B 各自绑定。
        GenerationContract contract =
                GenerationContractLoader.fromJson(CONTRACT.formatted(BUILTINS));
        assertEquals(12, contract.builtins().size());
    }

    // ───────────────────────────────────────────────────────────────────────
    // helpers
    // ───────────────────────────────────────────────────────────────────────

    private static void valid(String dsl) {
        ValidationResult result = validate(dsl);
        assertEquals(ValidationStatus.VALID, result.status(), () -> result.issues().toString());
        assertEquals("0", result.metadata().extra().get("typeChecks.mismatched"));
    }

    private static void assertIssue(String dsl, String code, String path) {
        assertTrue(
                matches(
                        validate(dsl),
                        issue -> code.equals(issue.code()) && path.equals(issue.path())),
                () -> validate(dsl).issues().toString());
    }

    private static void assertIssueProducedBy(String dsl, String path, String expectedText) {
        assertIssueProducedBy(validate(dsl), path, expectedText);
    }

    private static void assertIssueProducedBy(
            ValidationResult result, String path, String expectedText) {
        ValidationIssue issue =
                result.issues().stream()
                        .filter(
                                item ->
                                        "type-prop-mismatch".equals(item.code())
                                                && path.equals(item.path()))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError(result.issues().toString()));
        assertTrue(issue.message().contains(expectedText), issue.message());
    }

    private static boolean matches(ValidationResult result, Predicate<ValidationIssue> predicate) {
        return result.issues().stream().anyMatch(predicate);
    }

    private static ValidationResult validate(String dsl) {
        GenerationContract contract =
                GenerationContractLoader.fromJson(CONTRACT.formatted(BUILTINS));
        return DefaultOpenuiLangValidator.INSTANCE.validate(
                ValidationRequest.builder()
                        .dsl(dsl)
                        .contract(contract)
                        .dataModel(Map.of("rows", List.of()))
                        .build());
    }
}
