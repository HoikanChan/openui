/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.contract.GenerationContractLoader;
import com.huawei.cloudsop.genui.core.validation.DefaultOpenuiLangValidator;
import com.huawei.cloudsop.genui.core.validation.ValidationRequest;
import com.huawei.cloudsop.genui.core.validation.ValidationResult;
import com.huawei.cloudsop.genui.core.validation.ValidationStatus;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/** Numeric builtin signatures are checked through the public validator. */
class NumericBuiltinValidationTest {
    @Test
    void minAndMaxReturnNumbersForDownstreamCalls() {
        valid("@Round(@Min([1, 2]), 1)");
        valid("@Round(@Max([1, 2]), 1)");
        issue("@Count(@Min([1, 2]))", "builtin-argument-type-mismatch", "/args/0");
        issue("@Count(@Max([1, 2]))", "builtin-argument-type-mismatch", "/args/0");
    }

    @Test
    void floorAndCeilReturnNumbersForDownstreamCalls() {
        valid("@Round(@Floor(1.5), 1)");
        valid("@Round(@Ceil(1.5), 1)");
        issue("@Count(@Floor(1.5))", "builtin-argument-type-mismatch", "/args/0");
        issue("@Count(@Ceil(1.5))", "builtin-argument-type-mismatch", "/args/0");
    }

    @Test
    void minRejectsWrongArgumentCounts() {
        issue("@Min()", "builtin-argument-count-mismatch", "/args");
        issue("@Min([1], [2])", "builtin-argument-count-mismatch", "/args");
    }

    @Test
    void maxRejectsWrongArgumentCounts() {
        issue("@Max()", "builtin-argument-count-mismatch", "/args");
        issue("@Max([1], [2])", "builtin-argument-count-mismatch", "/args");
    }

    @Test
    void floorRejectsWrongArgumentCounts() {
        issue("@Floor()", "builtin-argument-count-mismatch", "/args");
        issue("@Floor(1, 2)", "builtin-argument-count-mismatch", "/args");
    }

    @Test
    void ceilRejectsWrongArgumentCounts() {
        issue("@Ceil()", "builtin-argument-count-mismatch", "/args");
        issue("@Ceil(1, 2)", "builtin-argument-count-mismatch", "/args");
    }

    @Test
    void aggregatesRejectScalarArguments() {
        issue("@Min(1)", "builtin-argument-type-mismatch", "/args/0");
        issue("@Max(1)", "builtin-argument-type-mismatch", "/args/0");
    }

    @Test
    void roundingFunctionsRejectStringArguments() {
        issue("@Floor(\"x\")", "builtin-argument-type-mismatch", "/args/0");
        issue("@Ceil(\"x\")", "builtin-argument-type-mismatch", "/args/0");
    }

    @Test
    void roundAcceptsOneOrTwoArguments() {
        valid("@Round(1.5)");
        valid("@Round(1.5, 1)");
        issue("@Round()", "builtin-argument-count-mismatch", "/args");
        issue("@Round(1, 2, 3)", "builtin-argument-count-mismatch", "/args");
    }

    @Test
    void numberAcceptsStringAndReturnsNumber() {
        valid("@Round(@Number(\"12\"))");
        issue("@Count(@Number(\"12\"))", "builtin-argument-type-mismatch", "/args/0");
        issue("@Number()", "builtin-argument-count-mismatch", "/args");
        issue("@Number(1, 2)", "builtin-argument-count-mismatch", "/args");
    }

    @Test
    void emptyAggregatesDoNotProduceArgumentTypeErrors() {
        for (String name : List.of("Min", "Max")) {
            ValidationResult result = validate("@String(@" + name + "([]))");
            assertEquals(ValidationStatus.VALID, result.status(), () -> name + result.issues());
            assertTrue(
                    result.issues().stream()
                            .noneMatch(
                                    item -> "builtin-argument-type-mismatch".equals(item.code())),
                    () -> name + result.issues());
        }
    }

    private static void valid(String expression) {
        ValidationResult result = validate("@String(" + expression + ")");
        assertEquals(ValidationStatus.VALID, result.status(), () -> result.issues().toString());
        assertEquals("0", result.metadata().extra().get("typeChecks.mismatched"));
    }

    private static void issue(String expression, String code, String path) {
        ValidationResult result = validate(expression);
        assertTrue(
                result.issues().stream()
                        .anyMatch(item -> code.equals(item.code()) && path.equals(item.path())),
                () ->
                        expression
                                + ": expected "
                                + code
                                + " at "
                                + path
                                + ", got "
                                + result.issues());
    }

    private static ValidationResult validate(String expression) {
        return DefaultOpenuiLangValidator.INSTANCE.validate(
                ValidationRequest.builder()
                        .dsl("root = TextContent(" + expression + ")")
                        .contract(GenerationContractLoader.loadDefault())
                        .dataModel(Map.of())
                        .build());
    }
}
