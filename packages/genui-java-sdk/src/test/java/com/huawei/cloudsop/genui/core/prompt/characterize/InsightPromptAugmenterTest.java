/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.prompt.characterize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Behavior tests for contract matching and {@code {{slot | default}}} rendering. */
class InsightPromptAugmenterTest {

    @Test
    void renderSubstitutesScalarsCollectionsAndDefaults() {
        Map<String, Object> slots = new LinkedHashMap<>();
        slots.put("arrayPath", "readings");
        slots.put("joinKey", "deviceId");
        slots.put("tabTitles", List.of("A", "B"));

        assertEquals(
                "path=readings titles=[\"A\",\"B\"] missing=fallback",
                InsightPromptAugmenter.render(
                        "path={{arrayPath}} titles={{ tabTitles }} missing={{missing | fallback}}",
                        slots));
    }

    @Test
    void renderLeavesPlaceholderWhenSlotAndDefaultAreMissing() {
        assertEquals(
                "keep {{unknown}}", InsightPromptAugmenter.render("keep {{unknown}}", Map.of()));
    }

    @Test
    void augmentFiltersByInsightIdAndTagAndKeepsUntaggedEntries() {
        DataInsight insight = insight("l2-grouped-aggregation", Set.of("group-by"), "grouped rule");

        InsightPromptAugmenter.Augmented augmented =
                InsightPromptAugmenter.augment(
                        List.of(insight),
                        List.of(
                                "root = Stack([])",
                                "[[l2-grouped-aggregation]] path {{arrayPath}}",
                                "[[group-by]] tag match",
                                "[[l2-top-n-ranking]] unmatched"),
                        List.of(
                                "Base rule",
                                "[[group-by]] rule match {{arrayPath | data}}",
                                "[[unmatched-id]] dropped"));

        assertEquals(
                List.of("root = Stack([])", "path {{arrayPath}}", "tag match"),
                augmented.examples());
        assertEquals(
                List.of("Base rule", "rule match data", "grouped rule"),
                augmented.additionalRules());
    }

    @Test
    void augmentReturnsInputsUnchangedWhenNoInsightFires() {
        List<String> examples = List.of("[[tabs]] x");
        List<String> rules = List.of("[[tabs]] y");

        InsightPromptAugmenter.Augmented augmented =
                InsightPromptAugmenter.augment(List.of(), examples, rules);

        assertEquals(examples, augmented.examples());
        assertEquals(rules, augmented.additionalRules());
    }

    @Test
    void augmentDoesNotDuplicateInsightRulePrompts() {
        DataInsight insight = insight("l1-multi-entity-tabs", Set.of("tabs"), "same rule");

        InsightPromptAugmenter.Augmented augmented =
                InsightPromptAugmenter.augment(List.of(insight), List.of(), List.of("same rule"));

        assertEquals(List.of("same rule"), augmented.additionalRules());
        assertFalse(augmented.examples().stream().anyMatch(text -> text.contains("same rule")));
        assertTrue(augmented.additionalRules().size() == 1);
    }

    private static DataInsight insight(String id, Set<String> tags, String rulePrompt) {
        return new DataInsight(
                id, InsightLevel.L1_CONTAINER, "data", 50, tags, Map.of(), rulePrompt);
    }
}
