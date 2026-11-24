/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.prompt.characterize;

import com.huawei.cloudsop.genui.core.Json;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Selects contract {@code examples} / {@code additionalRules} entries by {@link DataInsight} id or
 * tag, renders {@code {{slot | default}}} placeholders from the insight slots, and appends the
 * insights' own rule prompts.
 *
 * <p>Every contract entry keeps its plain-string form. An entry may start with a selector prefix
 * {@code [[id-or-tag, other-tag]]}; it is kept only when at least one selector equals a fired
 * insight's {@link DataInsight#id()} or one of its {@link DataInsight#tags()}. Entries without a
 * prefix are always kept, which preserves every existing untagged contract byte for byte as long as
 * it has no placeholders.
 *
 * <p>Placeholders use {@code {{slotName}}} or {@code {{slotName | default}}}. A missing or null
 * slot falls back to the default; without a default the placeholder is left untouched so template
 * mistakes stay visible. Collections and maps are rendered as compact JSON (for example {@code
 * ["A","B"]}), strings and scalars as their plain value.
 *
 * @since 2026
 */
public final class InsightPromptAugmenter {
    /** Prefix opening a selector list; entries without it are always kept. */
    public static final String SELECTOR_OPEN = "[[";

    /** Prefix closing a selector list. */
    public static final String SELECTOR_CLOSE = "]]";

    /** Opening delimiter of a placeholder. */
    private static final String PLACEHOLDER_OPEN = "{{";

    /** Closing delimiter of a placeholder. */
    private static final String PLACEHOLDER_CLOSE = "}}";

    /** Separator between a slot name and its fallback value. */
    private static final char PLACEHOLDER_DEFAULT_SEPARATOR = '|';

    private InsightPromptAugmenter() {}

    /**
     * Augments contract prompt entries with insight-driven filtering, rendering and rule prompts.
     *
     * @param insights the deterministic insights fired by {@link DataProfiler}
     * @param examples contract few-shot examples, in declaration order
     * @param additionalRules contract rules, in declaration order
     * @return the kept/rendered entries plus the insights' rule prompts appended to the rules
     */
    public static Augmented augment(
            List<DataInsight> insights, List<String> examples, List<String> additionalRules) {
        List<DataInsight> safeInsights = insights == null ? List.of() : insights;
        if (safeInsights.isEmpty()) {
            return new Augmented(
                    examples == null ? List.of() : examples,
                    additionalRules == null ? List.of() : additionalRules);
        }
        Set<String> matchKeys = matchKeys(safeInsights);
        Map<String, Object> slots = mergedSlots(safeInsights);
        List<String> keptExamples = filterAndRender(examples, matchKeys, slots);
        List<String> keptRules = filterAndRender(additionalRules, matchKeys, slots);
        for (DataInsight insight : safeInsights) {
            String rulePrompt = insight.rulePrompt();
            if (rulePrompt != null && !rulePrompt.isBlank() && !keptRules.contains(rulePrompt)) {
                keptRules.add(rulePrompt);
            }
        }
        return new Augmented(keptExamples, keptRules);
    }

    /**
     * Renders {@code {{slotName | default}}} placeholders in one template.
     *
     * @param template the template text; {@code null} becomes an empty string
     * @param slots the slot values available for substitution; {@code null} becomes empty
     * @return the rendered text with unresolved placeholders left as written
     */
    public static String render(String template, Map<String, Object> slots) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        Map<String, Object> safeSlots = slots == null ? Map.of() : slots;
        StringBuilder out = new StringBuilder(template.length());
        int cursor = 0;
        while (true) {
            int open = template.indexOf(PLACEHOLDER_OPEN, cursor);
            if (open < 0) {
                break;
            }
            int close = template.indexOf(PLACEHOLDER_CLOSE, open + PLACEHOLDER_OPEN.length());
            if (close < 0) {
                break;
            }
            int separator = template.indexOf(PLACEHOLDER_DEFAULT_SEPARATOR, open);
            boolean hasDefault = separator >= 0 && separator < close;
            String slotName =
                    template.substring(
                                    open + PLACEHOLDER_OPEN.length(),
                                    hasDefault ? separator : close)
                            .strip();
            if (!isValidSlotName(slotName)) {
                out.append(template, cursor, close + PLACEHOLDER_CLOSE.length());
                cursor = close + PLACEHOLDER_CLOSE.length();
                continue;
            }
            out.append(template, cursor, open);
            Object value = safeSlots.get(slotName);
            if (value != null) {
                out.append(formatValue(value));
            } else if (hasDefault) {
                out.append(template.substring(separator + 1, close).strip());
            } else {
                out.append(template, open, close + PLACEHOLDER_CLOSE.length());
            }
            cursor = close + PLACEHOLDER_CLOSE.length();
        }
        out.append(template, cursor, template.length());
        return out.toString();
    }

    /**
     * Reports whether a placeholder name uses the supported slot-name characters.
     *
     * @param name the stripped placeholder name
     * @return {@code true} for {@code [A-Za-z0-9_.-]+}
     */
    private static boolean isValidSlotName(String name) {
        if (name.isEmpty()) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            if (!isSlotNameChar(name.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isSlotNameChar(char c) {
        if (c >= 'A' && c <= 'Z') {
            return true;
        }
        if (c >= 'a' && c <= 'z') {
            return true;
        }
        if (c >= '0' && c <= '9') {
            return true;
        }
        return c == '_' || c == '.' || c == '-';
    }

    /**
     * Keeps entries that are untagged or whose selector matches a fired insight id/tag, then
     * renders their placeholders.
     *
     * @param entries the contract entries in declaration order
     * @param matchKeys fired insight ids and tags
     * @param slots the merged insight slots
     * @return the kept and rendered entries
     */
    private static List<String> filterAndRender(
            List<String> entries, Set<String> matchKeys, Map<String, Object> slots) {
        List<String> kept = new ArrayList<>();
        if (entries == null) {
            return kept;
        }
        for (String entry : entries) {
            if (entry == null) {
                continue;
            }
            SelectorPrefix prefix = splitSelector(entry);
            if (prefix.selectors().isEmpty() || intersects(prefix.selectors(), matchKeys)) {
                kept.add(render(prefix.body(), slots));
            }
        }
        return kept;
    }

    /**
     * Splits an optional leading {@code [[a, b]]} selector from the entry body.
     *
     * @param entry the raw contract entry
     * @return the selectors (empty when untagged) and the body to render
     */
    private static SelectorPrefix splitSelector(String entry) {
        String trimmed = entry.stripLeading();
        if (!trimmed.startsWith(SELECTOR_OPEN)) {
            return new SelectorPrefix(Set.of(), entry);
        }
        int close = trimmed.indexOf(SELECTOR_CLOSE);
        if (close < 0) {
            return new SelectorPrefix(Set.of(), entry);
        }
        String selectorText = trimmed.substring(SELECTOR_OPEN.length(), close);
        Set<String> selectors = new LinkedHashSet<>();
        for (String part : selectorText.split(",")) {
            String selector = asciiLowerCase(part.trim());
            if (!selector.isEmpty()) {
                selectors.add(selector);
            }
        }
        String body = trimmed.substring(close + SELECTOR_CLOSE.length()).stripLeading();
        return new SelectorPrefix(selectors, body);
    }

    /**
     * Lowercases A-Z only, leaving every other character untouched so matching stays independent of
     * the default locale and of Unicode case-mapping expansions.
     *
     * @param text the text to normalize
     * @return the ASCII-lowercased text
     */
    private static String asciiLowerCase(String text) {
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] >= 'A' && chars[i] <= 'Z') {
                chars[i] = (char) (chars[i] + ('a' - 'A'));
            }
        }
        return new String(chars);
    }

    private static boolean intersects(Set<String> selectors, Set<String> matchKeys) {
        for (String selector : selectors) {
            if (matchKeys.contains(selector)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Collects every fired insight id and tag as lowercase match keys.
     *
     * @param insights the fired insights
     * @return the matchable keys
     */
    private static Set<String> matchKeys(List<DataInsight> insights) {
        Set<String> keys = new LinkedHashSet<>();
        for (DataInsight insight : insights) {
            if (insight.id() != null && !insight.id().isBlank()) {
                keys.add(asciiLowerCase(insight.id()));
            }
            for (String tag : insight.tags()) {
                if (tag != null && !tag.isBlank()) {
                    keys.add(asciiLowerCase(tag));
                }
            }
        }
        return keys;
    }

    /**
     * Merges the slots of every fired insight in encounter order; the first insight wins when two
     * share a slot name, keeping the outcome deterministic.
     *
     * @param insights the fired insights
     * @return the merged slot map
     */
    private static Map<String, Object> mergedSlots(List<DataInsight> insights) {
        Map<String, Object> merged = new LinkedHashMap<>();
        for (DataInsight insight : insights) {
            for (Map.Entry<String, Object> entry : insight.slots().entrySet()) {
                merged.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
        return merged;
    }

    /**
     * Formats one slot value for insertion into prompt text.
     *
     * @param value the slot value
     * @return plain text for scalars, compact JSON for collections and maps
     */
    private static String formatValue(Object value) {
        if (value instanceof Collection<?> || value instanceof Map<?, ?>) {
            return Json.stringify(value);
        }
        return String.valueOf(value);
    }

    /**
     * Augmented prompt inputs: the filtered/rendered contract entries and rules plus the fired
     * insights' rule prompts.
     *
     * @param examples the examples to render
     * @param additionalRules the rules to render
     * @since 2026
     */
    public record Augmented(List<String> examples, List<String> additionalRules) {}

    private record SelectorPrefix(Set<String> selectors, String body) {}
}
