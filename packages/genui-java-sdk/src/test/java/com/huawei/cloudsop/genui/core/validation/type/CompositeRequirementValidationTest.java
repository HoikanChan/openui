/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

import java.util.List;
import java.util.Map;

/**
 * Public-validator regression for #82 on the integrated #80 engine: composite component-schema requirements
 * (anyOf/oneOf/required/additionalProperties) are evaluated against the SAME observed value (never stitching
 * per-candidate counterexamples into one error), unsupported schema keywords stay UNPROVEN and visible in the
 * {@code typeChecks.*} coverage rather than being read as a fabricated full MATCH.
 */
class CompositeRequirementValidationTest {

    @Test
    void anyOfAcceptsNumberThenStringOnSameElements() {
        // TextContent.text is anyOf [string, number]. A literal number and a literal string each pass on their own
        // value.
        assertValid("root = Stack([TextContent(1), TextContent(\"x\")])");
    }

    @Test
    void anyOfMismatchesOnOneValueViolatingAllCandidates() {
        // A boolean fails both string and number -> the SAME single value violates every candidate -> real
        // counterexample.
        ValidationResult result = validate("root = TextContent(true)");
        ValidationIssue issue = issue(result, "type-prop-mismatch");
        assertEquals("/text", issue.path());
    }

    @Test
    void unionOfMixedLiteralsDoesNotStitchCounterexamples() {
        // data.items = [1, "x"] fed to a number-or-string text prop: candidate number has counterexample "x", candidate
        // string has counterexample 1, but NO single element fails both — this must NOT be reported as a violation.
        ValidationResult result = validate("root = @Each(data.items, \"i\", TextContent(i))",
                Map.of("items", List.of(1, "x")));
        assertFalse(hasCode(result, "type-prop-mismatch"),
                () -> "must not stitch per-candidate counterexamples across different elements: " + result.issues());
    }

    @Test
    void unionContainingBooleanFlagsAnyOf() {
        // data.items = [1, "x", true]: the SAME observed true violates both string and number -> one real
        // counterexample.
        ValidationResult result = validate("root = @Each(data.items, \"i\", TextContent(i))",
                Map.of("items", List.of(1, "x", true)));
        assertTrue(hasCode(result, "type-prop-mismatch") || hasCode(result, "builtin-argument-type-mismatch"),
                () -> "the single true value must be the AnyOf counterexample: " + result.issues());
    }

    @Test
    void requiredFieldMissingIsReported() {
        // Table requires columns+rows. Omitting rows must surface a required-field violation, not a silent pass.
        ValidationResult result = validate("root = Table([nameCol])\nnameCol = Col(\"Name\", \"name\")",
                Map.of("users", List.of(Map.of("name", "Ada"))));
        assertTrue(hasCode(result, "missing-required"),
                () -> "missing required prop must be reported: " + result.issues());
    }

    @Test
    void mismatchCoverageIsReportedInMetadata() {
        ValidationResult result = validate("root = TextContent(true)");
        assertTrue(Long.parseLong(result.metadata().extra().getOrDefault("typeChecks.mismatched", "0")) >= 1,
                () -> "mismatch count must be in coverage metadata: " + result.metadata().extra());
    }

    @Test
    void discoveredRequirementsAreCounted() {
        // A valid page discovers at least one matched requirement; discovered = matched + mismatched + unproven.
        ValidationResult result = validate("root = TextContent(\"x\")");
        long discovered = Long.parseLong(result.metadata().extra().getOrDefault("typeChecks.discovered", "0"));
        assertTrue(discovered >= 1,
                () -> "discovered requirement count must be reported: " + result.metadata().extra());
    }

    @Test
    void componentSlotAnyOfAcceptsEachBranchOnItsOwnValue() {
        // FormControl.input is anyOf [Input, TextArea, ..., DatePicker]; an Input value and a DatePicker value each
        // match their own single component branch on their own observed value.
        assertValid("root = Stack([FormControl(\"a\", \"A\", Input(\"x\")), FormControl(\"b\", \"B\", DatePicker())])");
    }

    private static void assertValid(String dsl) {
        ValidationResult result = validate(dsl, Map.of("present", true));
        assertEquals(ValidationStatus.VALID, result.status(), () -> String.valueOf(result.issues()));
    }

    private static boolean hasCode(ValidationResult result, String code) {
        return result.issues().stream().anyMatch(i -> code.equals(i.code()));
    }

    private static ValidationResult validate(String dsl) {
        return validate(dsl, Map.of("present", true));
    }

    private static ValidationResult validate(String dsl, Map<String, Object> dataModel) {
        GenerationContract contract = GenerationContractLoader.loadDefault();
        return DefaultOpenuiLangValidator.INSTANCE.validate(ValidationRequest.builder().dsl(dsl).contract(contract)
                .dataModel(dataModel).mode(ValidationMode.FINAL).build());
    }

    private static ValidationIssue issue(ValidationResult result, String code) {
        ValidationIssue issue = result.issues().stream().filter(c -> code.equals(c.code())).findFirst().orElse(null);
        assertNotNull(issue, () -> "missing " + code + " in " + result.issues());
        return issue;
    }
}
