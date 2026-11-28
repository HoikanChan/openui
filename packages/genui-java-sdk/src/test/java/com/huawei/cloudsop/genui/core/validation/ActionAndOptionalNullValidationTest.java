/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import com.huawei.cloudsop.genui.core.contract.GenerationContractLoader;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 回归验证 #145：校验器对 {@code Action} / {@code @ActionStep} 动作调用以及可选参数字面量 {@code null} 占位的放行与拦截边界。
 *
 * @since 2026
 */
class ActionAndOptionalNullValidationTest {

    private static final GenerationContract CONTRACT = GenerationContractLoader.loadDefault();

    private static ValidationResult validate(String dsl, Map<String, Object> dataModel) {
        return DefaultOpenuiLangValidator.INSTANCE.validate(
                ValidationRequest.builder()
                        .dsl(dsl)
                        .contract(CONTRACT)
                        .rootName(CONTRACT.root())
                        .dataModel(dataModel)
                        .build());
    }

    private static List<ValidationIssue> errorsOf(ValidationResult result) {
        return result.issues().stream()
                .filter(issue -> issue.severity() == ValidationSeverity.ERROR)
                .toList();
    }

    @Test
    void allowsActionContainerAndPrefixedActionSteps() {
        String dsl =
                """
                $filter = "all"
                q = Query("list_items", {filter: $filter}, {rows: []})
                act = Action([@Set($filter, "high"), @Run(q), @Reset($filter), @OpenUrl("https://example.com"), @ToAssistant("Summarize")])
                btn = Button("Apply", act, "primary")
                root = Stack([btn])
                """;
        ValidationResult result = validate(dsl, Map.of());
        assertTrue(result.isValid(), () -> "Expected valid DSL, got errors: " + errorsOf(result));
    }

    @Test
    void stillRejectsUnprefixedActionStepsAndHallucinatedActionSteps() {
        String bareStepDsl =
                """
                $filter = "all"
                btn = Button("Apply", Action([Set($filter, "high")]), "primary")
                root = Stack([btn])
                """;
        ValidationResult bareResult = validate(bareStepDsl, Map.of());
        assertFalse(bareResult.isValid());
        ValidationIssue bareIssue =
                errorsOf(bareResult).stream()
                        .filter(
                                i ->
                                        "unknown-component".equals(i.code())
                                                && "Set".equals(i.component()))
                        .findFirst()
                        .orElseThrow();
        assertTrue(
                bareIssue.hint() != null && bareIssue.hint().contains("@Set"),
                () -> "Expected @Set hint, got: " + bareIssue.hint());

        String hallucinatedStepDsl =
                """
                btn = Button("Export", Action([@Download("report.csv")]), "primary")
                root = Stack([btn])
                """;
        ValidationResult hallucinatedResult = validate(hallucinatedStepDsl, Map.of());
        assertFalse(hallucinatedResult.isValid());
        assertTrue(
                errorsOf(hallucinatedResult).stream()
                        .anyMatch(
                                i ->
                                        "unknown-component".equals(i.code())
                                                && "Download".equals(i.component())));
    }

    @Test
    void stillRejectsUnresolvedReferenceInsideActionArguments() {
        String dsl =
                """
                btn = Button("Open", Action([@OpenUrl(missingUrl)]), "primary")
                root = Stack([btn])
                """;
        ValidationResult result = validate(dsl, Map.of());
        assertFalse(result.isValid());
        assertTrue(
                errorsOf(result).stream().anyMatch(i -> "unresolved-ref".equals(i.code())),
                () -> "Expected unresolved-ref inside Action args, got: " + errorsOf(result));
    }

    @Test
    void allowsNullLiteralAsOptionalPositionalPlaceholder() {
        String dsl =
                """
                $selected = "a"
                hdr = CardHeader("Overview", null)
                sel = Select([SelectItem("A", "a"), SelectItem("B", "b")], null, $selected)
                btn = Button("Share", null, "secondary", null, "small", null)
                card = Card([hdr, sel, btn])
                root = Stack([card])
                """;
        ValidationResult result = validate(dsl, Map.of());
        assertTrue(result.isValid(), () -> "Expected valid DSL, got errors: " + errorsOf(result));
    }

    @Test
    void stillRejectsNullOnRequiredPropsAndNonLiteralNullExpressionsOnOptionalProps() {
        ValidationResult requiredNull =
                validate("btn = Button(null)\nroot = Stack([btn])", Map.of());
        assertFalse(requiredNull.isValid());
        assertTrue(
                errorsOf(requiredNull).stream().anyMatch(i -> "null-required".equals(i.code())),
                () -> "Expected null-required on Button.label, got: " + errorsOf(requiredNull));

        Map<String, Object> dm = new LinkedHashMap<>();
        dm.put("nullSubtitle", null);
        ValidationResult dynamicNullOnOptional =
                validate(
                        "hdr = CardHeader(\"Overview\", data.nullSubtitle)\nroot = Stack([hdr])",
                        dm);
        assertFalse(dynamicNullOnOptional.isValid());
        assertEquals(
                List.of("type-prop-mismatch"),
                errorsOf(dynamicNullOnOptional).stream().map(ValidationIssue::code).toList());

        ValidationResult missingPathOnOptional =
                validate(
                        "hdr = CardHeader(\"Overview\", data.typoSubtitle)\nroot = Stack([hdr])",
                        Map.of("subtitle", "real"));
        assertFalse(missingPathOnOptional.isValid());
        assertTrue(
                errorsOf(missingPathOnOptional).stream()
                        .anyMatch(i -> "type-data-path-missing".equals(i.code())),
                () -> "Expected type-data-path-missing, got: " + errorsOf(missingPathOnOptional));
    }
}
