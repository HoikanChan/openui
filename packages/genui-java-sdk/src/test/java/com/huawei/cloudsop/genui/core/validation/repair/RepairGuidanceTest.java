/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.repair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.validation.ValidationIssue;
import com.huawei.cloudsop.genui.core.validation.ValidationSeverity;

import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 按诊断选取专项指导的独立维护点测试。
 *
 * @since 2026
 */
class RepairGuidanceTest {

    private static ValidationIssue error(String code, String source) {
        return new ValidationIssue(code, ValidationSeverity.ERROR, source, "message", "stmt", null, null, -1, -1, null,
                false);
    }

    private static String joined(List<String> lines) {
        return String.join("\n", lines);
    }

    @Test
    void syntaxIssueSelectsSyntaxGuidanceOnly() {
        String guidance = joined(RepairGuidance.forIssues(List.of(error("syntax-unclosed-bracket", "syntax"))));
        assertTrue(guidance.contains("Rewrite the whole diagnosed statement"));
        assertTrue(guidance.contains("Arguments are positional"));
        assertFalse(guidance.contains("data.<path>"));
        assertFalse(guidance.contains("current full contract"));
    }

    @Test
    void unresolvedRefSelectsCallClassificationGuidance() {
        String guidance = joined(RepairGuidance.forIssues(List.of(error("unresolved-ref", "reference"))));
        assertTrue(guidance.contains("`@name(...)` is a builtin call"));
        assertTrue(guidance.contains("bare identifier is a statement reference"));
        assertTrue(guidance.contains("add the leading `@`"));
    }

    @Test
    void dataIssueSelectsNoSampleAsFullDataGuidance() {
        String guidance = joined(RepairGuidance.forIssues(List.of(error("type-data-path-missing", "type"))));
        assertTrue(guidance.contains("`data.<path>` must exist"));
        assertTrue(guidance.contains("sample rows as if they were the full dataset"));
        assertTrue(guidance.contains("state the limitation in the page"));
    }

    @Test
    void unknownComponentSelectsContractSignatureGuidance() {
        String guidance = joined(RepairGuidance.forIssues(List.of(error("unknown-component", "contract"))));
        assertTrue(guidance.contains("only components listed in the current full contract"));
        assertTrue(guidance.contains("positional argument order"));
    }

    @Test
    void rootIssueSelectsSingleReachableRootGuidance() {
        String guidance = joined(RepairGuidance.forIssues(List.of(error("root-not-renderable", "root"))));
        assertTrue(guidance.contains("exactly one reachable `root`"));
    }

    @Test
    void duplicateCategoriesAreDeduplicated() {
        List<String> guidance = RepairGuidance
                .forIssues(List.of(error("syntax-a", "syntax"), error("syntax-b", "syntax")));
        assertEquals(3, guidance.size(), "syntax category rendered once");
    }

    @Test
    void warningsAndNullsProduceNoGuidance() {
        ValidationIssue warning = new ValidationIssue("soft-warn", ValidationSeverity.WARNING, "x", "message", null,
                null, null, -1, -1, null, true);
        assertTrue(RepairGuidance.forIssues(List.of(warning)).isEmpty());
        assertTrue(RepairGuidance.forIssues(null).isEmpty());
    }
}
