/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.repair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.contract.GenerationContractLoader;
import com.huawei.cloudsop.genui.core.validation.ValidationIssue;
import com.huawei.cloudsop.genui.core.validation.ValidationSeverity;
import com.huawei.cloudsop.genui.core.validation.semantic.RepairHints;

import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 按诊断选取专项指导的独立维护点测试。
 *
 * @since 2026
 */
class RepairGuidanceTest {

    private static ValidationIssue error(String code, String source) {
        return new ValidationIssue(
                code,
                ValidationSeverity.ERROR,
                source,
                "message",
                "stmt",
                null,
                null,
                -1,
                -1,
                null,
                false);
    }

    private static ValidationIssue errorWithHint(
            String code, String source, String component, String hint) {
        return new ValidationIssue(
                code,
                ValidationSeverity.ERROR,
                source,
                "message",
                "stmt",
                component,
                null,
                -1,
                -1,
                hint,
                false);
    }

    private static List<String> renderGuidance(List<ValidationIssue> issues) {
        var round =
                new com.huawei.cloudsop.genui.core.validation.stream.RoundEndResult(
                        "",
                        "",
                        com.huawei.cloudsop.genui.core.validation.ValidationResult.invalid(
                                issues == null ? List.of() : issues, null),
                        0L,
                        null,
                        null);
        String system = RepairPromptBuilder.build(null, null, null, round).get(0).content();
        String heading = "## Diagnostic-selected guidance\n";
        int start = system.indexOf(heading);
        return start < 0
                ? List.of()
                : system.substring(start + heading.length())
                        .lines()
                        .filter(line -> line.startsWith("- "))
                        .map(line -> line.substring(2))
                        .toList();
    }

    private static String joined(List<String> lines) {
        return String.join("\n", lines);
    }

    @Test
    void syntaxIssueSelectsSyntaxGuidanceOnly() {
        String guidance =
                joined(renderGuidance(List.of(error("syntax-unclosed-bracket", "syntax"))));
        assertTrue(guidance.contains("Rewrite the whole diagnosed statement"));
        assertTrue(guidance.contains("Arguments are positional"));
        assertFalse(guidance.contains("data.<path>"));
        assertFalse(guidance.contains("current full contract"));
    }

    @Test
    void unresolvedRefSelectsCallClassificationGuidance() {
        String guidance = joined(renderGuidance(List.of(error("unresolved-ref", "reference"))));
        assertTrue(guidance.contains("`@name(...)` is a builtin call"));
        assertTrue(guidance.contains("bare identifier is a statement reference"));
        assertTrue(guidance.contains("add the leading `@`"));
    }

    @Test
    void dataIssueSelectsNoSampleAsFullDataGuidance() {
        String guidance = joined(renderGuidance(List.of(error("type-data-path-missing", "type"))));
        assertTrue(guidance.contains("`data.<path>` must exist"));
        assertTrue(guidance.contains("sample rows as if they were the full dataset"));
        assertTrue(guidance.contains("state the limitation in the page"));
        assertTrue(guidance.contains("caller-supplied data model as the primary content"));
        assertTrue(guidance.contains("without exposing internal field or path names"));
        assertTrue(guidance.contains("never \"数据源不匹配\""));
    }

    @Test
    void tableColumnMissingSelectsDataFirstAndNoExtraColWorkaroundGuidance() {
        String guidance =
                joined(renderGuidance(List.of(error("type-table-column-missing", "type"))));
        assertTrue(guidance.contains("caller-supplied data model as the primary content"));
        assertTrue(guidance.contains("never use `ExtraCol` to work around a missing row field"));
        assertTrue(guidance.contains("without exposing internal field/path names"));
        assertTrue(guidance.contains("never \"数据源\" or \"数据源不匹配\""));
    }

    @Test
    void unknownComponentSelectsContractSignatureGuidance() {
        String guidance = joined(renderGuidance(List.of(error("unknown-component", "contract"))));
        assertTrue(guidance.contains("only components listed in the original generation contract"));
        assertTrue(guidance.contains("positional argument order"));
    }

    @Test
    void
            unknownComponentTier2CapabilityGapSelectsDegradationGuidanceInsteadOfOrdinaryComponentGuidance() {
        String hint =
                RepairHints.unknownComponentHint(
                        "GroupBy", GenerationContractLoader.loadDefault().capabilityGaps());
        String guidance =
                joined(
                        renderGuidance(
                                List.of(
                                        errorWithHint(
                                                "unknown-component",
                                                "contract",
                                                "GroupBy",
                                                hint))));
        assertTrue(
                guidance.contains("Capability boundary (overrides the original request)"),
                "tier-2 unknown-component must select capability boundary guidance: " + guidance);
        assertTrue(
                guidance.contains("capabilityNotice"),
                "guidance must mention capabilityNotice: " + guidance);
        assertFalse(
                guidance.contains("Copy the exact component name and positional argument order"),
                "tier-2 must not emit ordinary unknown-component signature guidance");
    }

    @Test
    void unknownComponentTier1MissingAtSelectsLeadingAtGuidance() {
        String hint =
                RepairHints.unknownComponentHint(
                        "Set", GenerationContractLoader.loadDefault().capabilityGaps());
        String guidance =
                joined(
                        renderGuidance(
                                List.of(
                                        errorWithHint(
                                                "unknown-component", "contract", "Set", hint))));
        assertTrue(
                guidance.contains("leading `@`"),
                "tier-1 unknown-component must select leading @ guidance");
        assertFalse(
                guidance.contains("Copy the exact component name and positional argument order"));
    }

    @Test
    void rootIssueSelectsSingleReachableRootGuidance() {
        String guidance = joined(renderGuidance(List.of(error("root-not-renderable", "root"))));
        assertTrue(guidance.contains("exactly one reachable `root`"));
    }

    @Test
    void duplicateCategoriesAreDeduplicated() {
        List<String> guidance =
                renderGuidance(List.of(error("syntax-a", "syntax"), error("syntax-b", "syntax")));
        assertEquals(3, guidance.size(), "syntax category rendered once");
    }

    @Test
    void warningsAndNullsProduceNoGuidance() {
        ValidationIssue warning =
                new ValidationIssue(
                        "soft-warn",
                        ValidationSeverity.WARNING,
                        "x",
                        "message",
                        null,
                        null,
                        null,
                        -1,
                        -1,
                        null,
                        true);
        assertTrue(renderGuidance(List.of(warning)).isEmpty());
        assertTrue(renderGuidance(null).isEmpty());
    }
}
