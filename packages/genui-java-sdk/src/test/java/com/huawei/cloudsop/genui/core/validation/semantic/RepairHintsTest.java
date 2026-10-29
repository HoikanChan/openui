/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.contract.CapabilityGapSpec;
import com.huawei.cloudsop.genui.core.contract.GenerationContractLoader;

import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Hint routing for unresolved references (design Decision 11.2) and unknown components (#75). Hint
 * text and the root-cause predicate MUST stay in the same class: {@code ValidationIssueReducer}
 * exempts root-cause-hinted issues from cascade suppression by this predicate, so a wording change
 * that breaks the predicate silently re-enables suppression of the very hints that explain the
 * failure.
 *
 * @since 2026
 */
class RepairHintsTest {

    // ── tier 1: builtin name missing '@' (exact, case-sensitive match) ─────────

    @Test
    void builtinNameMissingAtGetsDidYouMean() {
        assertEquals(
                "did you mean \"@FormatNumber\"? openui-lang builtins must be called with a leading '@'",
                RepairHints.unresolvedRefHint("FormatNumber"));
    }

    @Test
    void builtinMatchIsCaseSensitive() {
        // "formatnumber" is not an exact builtin name → falls through to the default hint.
        assertEquals(
                "define a statement named \"formatnumber\" earlier in the document",
                RepairHints.unresolvedRefHint("formatnumber"));
    }

    @Test
    void builtinMatchWinsOverJsGlobal() {
        // "Set" is both an openui-lang action builtin and a JS global; the builtin hint is
        // actionable ("@Set") so it takes precedence.
        assertEquals(
                "did you mean \"@Set\"? openui-lang builtins must be called with a leading '@'",
                RepairHints.unresolvedRefHint("Set"));
    }

    // ── tier 2: JS globals / keywords not in the openui-lang subset ────────────

    @Test
    void jsGlobalGetsRootCauseHint() {
        assertEquals(
                "\"Math\" is a JavaScript global — JS globals and methods are not available in"
                        + " openui-lang; use builtins like @Abs, @Round, @FormatNumber, @FormatDate instead",
                RepairHints.unresolvedRefHint("Math"));
    }

    @Test
    void jsKeywordNewGetsRootCauseHint() {
        // The parser surfaces keywords like `new` (from `new Date()`) as plain refs.
        assertTrue(RepairHints.unresolvedRefHint("new").contains("JavaScript global"));
    }

    // ── tier 3: default — no misleading external-ref advice ────────────────────

    @Test
    void defaultHintNeverSuggestsExternalRef() {
        String hint = RepairHints.unresolvedRefHint("kpiValue");
        assertEquals("define a statement named \"kpiValue\" earlier in the document", hint);
        assertFalse(hint.contains("external ref"));
    }

    // ── root-cause predicate (cascade-suppression exemption) ───────────────────

    @Test
    void rootCausePredicateMatchesTier1And2Only() {
        assertTrue(RepairHints.isRootCauseHint(RepairHints.unresolvedRefHint("FormatNumber")));
        assertTrue(RepairHints.isRootCauseHint(RepairHints.unresolvedRefHint("Math")));
        assertFalse(RepairHints.isRootCauseHint(RepairHints.unresolvedRefHint("kpiValue")));
        assertFalse(RepairHints.isRootCauseHint(null));
        assertFalse(RepairHints.isRootCauseHint("may resolve as more of the stream arrives"));
    }

    // ── unknown-component 3-tier routing (#75) ─────────────────────────────────

    @Test
    void unknownComponentTier1BuiltinOrActionMissingAtGetsDidYouMean() {
        List<CapabilityGapSpec> gaps = GenerationContractLoader.loadDefault().capabilityGaps();
        String builtinHint = RepairHints.unknownComponentHint("FormatNumber", gaps);
        assertEquals(
                "did you mean \"@FormatNumber\"? openui-lang builtins must be called with a leading '@'",
                builtinHint);
        assertTrue(RepairHints.isRootCauseHint(builtinHint));
        assertTrue(RepairHints.isMissingAtHint(builtinHint));

        String actionHint = RepairHints.unknownComponentHint("Set", gaps);
        assertEquals(
                "did you mean \"@Set\"? openui-lang builtins must be called with a leading '@'",
                actionHint);
        assertTrue(RepairHints.isRootCauseHint(actionHint));
    }

    @Test
    void unknownComponentTier2CapabilityGapAliasGetsCapabilityBoundaryRootCauseHint() {
        List<CapabilityGapSpec> gaps = GenerationContractLoader.loadDefault().capabilityGaps();
        String groupByHint = RepairHints.unknownComponentHint("GroupBy", gaps);
        assertTrue(
                groupByHint.contains("分组展示"),
                "hint must name the matched capability gap: " + groupByHint);
        assertTrue(
                groupByHint.contains("用一张 Table 展示全部行"),
                "hint must include gap fallback: " + groupByHint);
        assertTrue(
                groupByHint.contains(
                        "capabilityNotice = TextCallout(\"info\", \"部分需求暂不支持\", \"暂不支持分组展示，以下按原始数据展示\")"),
                "hint must include capabilityNotice template: " + groupByHint);
        assertTrue(
                RepairHints.isRootCauseHint(groupByHint),
                "tier-2 capability gap hint must be a root-cause hint");
        assertTrue(RepairHints.isCapabilityGapHint(groupByHint));

        String atObjectFromEntriesHint =
                RepairHints.unknownComponentHint("@ObjectFromEntries", gaps);
        assertTrue(
                atObjectFromEntriesHint.contains("分组展示"),
                "leading '@' must be ignored and ObjectFromEntries must route to group-by");
        assertTrue(RepairHints.isRootCauseHint(atObjectFromEntriesHint));
    }

    @Test
    void unknownComponentTier3OrdinaryUnknownNameGetsContractRewriteHint() {
        List<CapabilityGapSpec> gaps = GenerationContractLoader.loadDefault().capabilityGaps();
        String hint = RepairHints.unknownComponentHint("ChartCard", gaps);
        assertTrue(hint.contains("ChartCard"), "hint must name the unknown component");
        assertTrue(
                hint.contains("contract"), "hint must direct the model to use contract components");
        assertFalse(
                hint.contains("capabilityNotice"),
                "ordinary unknown component must not trigger degradation");
        assertFalse(RepairHints.isRootCauseHint(hint), "tier-3 hint must not be a root-cause hint");
        assertFalse(RepairHints.isCapabilityGapHint(hint));
    }
}
