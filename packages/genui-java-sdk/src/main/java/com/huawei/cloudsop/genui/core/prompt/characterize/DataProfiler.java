/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.prompt.characterize;

import com.huawei.cloudsop.genui.core.Json;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deep module that inspects a host-data payload and produces deterministic, rule-derived {@link
 * DataInsight} values plus the existing {@link Characterized} sample/shape pair.
 *
 * <p>Exposes two public APIs: {@link #characterize(Map, CharacterizationConfig)} preserves the
 * existing {@link Characterizer} gate behavior, while {@link #profile(Map, CharacterizationConfig)}
 * runs a layer-by-layer rule engine regardless of the size trigger. Layer evaluation is ordered:
 * {@link InsightLevel#L0_GATE} first, and a match short-circuits so no deeper layer runs. Otherwise
 * deeper-layer rules (L2 dataset with per-path priority competition, and L3 field statistics) run
 * in a single pass.
 *
 * @since 2026
 */
public final class DataProfiler {
    private DataProfiler() {}

    /** Stable identifier of the empty-data L0 gate insight. */
    public static final String ID_L0_EMPTY_DATA = "l0-empty-data";

    /** Stable identifier of the status-ack-only L0 gate insight. */
    public static final String ID_L0_STATUS_ACK_ONLY = "l0-status-ack-only";

    /** Stable identifier of the correlated-object-merge L1 container insight. */
    public static final String ID_L1_CORRELATED_OBJECT_MERGE = "l1-correlated-object-merge";

    /** Stable identifier of the multi-entity-tabs L1 container insight. */
    public static final String ID_L1_MULTI_ENTITY_TABS = "l1-multi-entity-tabs";

    /** Stable identifier of the paginated-envelope L1 container insight. */
    public static final String ID_L1_PAGINATED_ENVELOPE = "l1-paginated-envelope";

    /** Stable identifier of the dynamic-key-map L1 container insight. */
    public static final String ID_L1_DYNAMIC_KEY_MAP = "l1-dynamic-key-map";

    /** Stable identifier of the single-record-detail L1 container insight. */
    public static final String ID_L1_SINGLE_RECORD_DETAIL = "l1-single-record-detail";

    /** Priority of the empty-data L0 gate (runs first among L0 rules). */
    private static final int PRIORITY_L0_EMPTY_DATA = 100;

    /** Priority of the status-ack L0 gate (runs after empty-data). */
    private static final int PRIORITY_L0_STATUS_ACK_ONLY = 95;

    /** Target path used by L0 gate rules: they describe the whole payload. */
    private static final String L0_TARGET_PATH = "data";

    /** Slot key carrying the empty-data reason string. */
    private static final String SLOT_REASON = "reason";

    /** Slot key carrying the list of empty array / root paths. */
    private static final String SLOT_EMPTY_PATHS = "emptyPaths";

    /** Slot key carrying the name of the field used to derive the outcome. */
    private static final String SLOT_STATUS_FIELD = "statusField";

    /** Slot key carrying the raw value of that status field. */
    private static final String SLOT_STATUS_VALUE = "statusValue";

    /** Slot key carrying the name of the field used as the human message. */
    private static final String SLOT_MESSAGE_FIELD = "messageField";

    /** Slot key carrying the raw value of that message field. */
    private static final String SLOT_MESSAGE_VALUE = "messageValue";

    /** Slot key carrying the resolved outcome: "success" / "error" / "unknown". */
    private static final String SLOT_OUTCOME = "outcome";

    /** Reason recorded when the payload is {@code null} or an empty object. */
    private static final String REASON_NULL_OR_EMPTY_OBJECT = "null_or_empty_object";

    /** Reason recorded when the payload is an empty-list envelope. */
    private static final String REASON_EMPTY_ARRAY = "empty_array";

    /** Reason recorded when every leaf in the payload is null / empty. */
    private static final String REASON_ALL_NULL_LEAVES = "all_null_leaves";

    /** Outcome recorded for an affirmative status acknowledgement. */
    private static final String OUTCOME_SUCCESS = "success";

    /** Outcome recorded for a failing status acknowledgement. */
    private static final String OUTCOME_ERROR = "error";

    /** Outcome recorded when no recognizable status indicator is present. */
    private static final String OUTCOME_UNKNOWN = "unknown";

    /** Lowest HTTP-style status code treated as success (inclusive). */
    private static final long SUCCESS_CODE_MIN = 200L;

    /** Highest HTTP-style status code treated as success (inclusive). */
    private static final long SUCCESS_CODE_MAX = 299L;

    /** HTTP-style status code at or above which the outcome is treated as an error. */
    private static final long ERROR_CODE_MIN = 400L;

    /** Keys (normalized) that carry pagination counters and are safe to ignore for emptiness. */
    private static final Set<String> PAGINATION_KEYS =
            Set.of(
                    "total",
                    "totalcount",
                    "count",
                    "page",
                    "pageindex",
                    "pagenum",
                    "pagesize",
                    "size",
                    "limit",
                    "offset",
                    "hasmore");

    /** Keys (normalized) that form a status envelope and carry no business data. */
    private static final Set<String> STATUS_ENVELOPE_KEYS =
            Set.of(
                    "code",
                    "retcode",
                    "errcode",
                    "status",
                    "statuscode",
                    "state",
                    "msg",
                    "message",
                    "err",
                    "error",
                    "errmsg",
                    "detail",
                    "reason",
                    "success",
                    "ok",
                    "requestid",
                    "traceid",
                    "timestamp");

    /** Keys (normalized) that hold the actual business payload inside an envelope. */
    private static final Set<String> PAYLOAD_KEYS = Set.of("data", "result");

    /** Keys (normalized) whose presence marks the payload as a status acknowledgement. */
    private static final Set<String> STATUS_INDICATOR_KEYS =
            Set.of(
                    "code",
                    "retcode",
                    "errcode",
                    "status",
                    "statuscode",
                    "success",
                    "ok",
                    "error",
                    "errmsg");

    /** Keys (normalized) that hold a human-readable message. */
    private static final Set<String> MESSAGE_KEYS =
            Set.of("msg", "message", "errmsg", "detail", "reason");

    /** Short string values that, in a payload slot, still count as a status ack. */
    private static final Set<String> STATUS_STRING_VALUES = Set.of("ok", "success");

    /**
     * Runs the existing characterization gate over a raw host-data map.
     *
     * <p>Behavior is identical to {@link Characterizer#characterize(Map, CharacterizationConfig)}:
     * a {@code null} or empty map passes through, a disabled config or a payload whose compact JSON
     * form fits under the trigger size also passes through, and anything larger is delegated to
     * {@link ShapeWalker#walk(Object, CharacterizationConfig, int)} at depth 0.
     *
     * @param raw the raw data-model map to inspect; {@code null} or empty passes through unchanged
     * @param cfg the gate and sampling configuration; must not be {@code null}
     * @return the sample/shape pair, with a {@code null} shape when the gate passed through
     */
    public static Characterized characterize(Map<String, Object> raw, CharacterizationConfig cfg) {
        if (raw == null || raw.isEmpty()) {
            return new Characterized(raw, null);
        }
        if (!cfg.enabled() || Json.stringify(raw).length() <= cfg.triggerBytes()) {
            return new Characterized(raw, null);
        }
        return ShapeWalker.walk(raw, cfg, 0);
    }

    /**
     * Profiles a payload with the default configuration.
     *
     * @param raw the raw data-model map to inspect
     * @return the insights fired by the rule engine, in evaluation order
     */
    public static List<DataInsight> profile(Map<String, Object> raw) {
        return profile(raw, CharacterizationConfig.defaults());
    }

    /**
     * Profiles a payload and returns the deterministic insights its rules fire.
     *
     * <p>Evaluation runs {@link InsightLevel#L0_GATE} first; a match short-circuits and is returned
     * as the only insight. Otherwise deeper-layer analyzers evaluate the payload in a single pass.
     *
     * @param raw the raw data-model map to inspect; may be {@code null}
     * @param cfg the gate and sampling configuration; must not be {@code null}
     * @return the insights fired by the rule engine, in evaluation order
     */
    public static List<DataInsight> profile(Map<String, Object> raw, CharacterizationConfig cfg) {
        DataInsight l0 = evaluateL0Gate(raw);
        if (l0 != null) {
            return List.of(l0);
        }
        return evaluateDeeperLayers(raw, cfg);
    }

    /**
     * Evaluates the L0 gate rules in priority order and returns the first match, or {@code null}.
     *
     * @param raw the raw data-model map to inspect; may be {@code null}
     * @return the matching L0 insight, or {@code null} when no rule fires
     */
    private static DataInsight evaluateL0Gate(Map<String, Object> raw) {
        DataInsight empty = matchL0EmptyData(raw);
        if (empty != null) {
            return empty;
        }
        return matchL0StatusAckOnly(raw);
    }

    /**
     * Runs the deeper-layer rules (L1 container, L2 dataset, and L3 field) over the payload.
     *
     * <p>The payload is walked exactly once with a {@link ProfileCollector} threaded through {@link
     * ShapeWalker}; the analyzers then derive insights from the gathered statistics without any
     * second traversal. Outer arrays identified as heterogeneous/multi-entity L1 containers skip
     * flat outer L2/L3 evaluation while retaining inner sub-table L2/L3 insights. L2 insights
     * compete per target path (highest priority wins, ties broken by declaration order); L1 and L3
     * insights compose freely. The result is sorted by level (L1, then L2, then L3), preserving
     * encounter order within each level.
     *
     * @param raw the raw data-model map to inspect
     * @param cfg the gate and sampling configuration
     * @return the insights fired by deeper layers, in deterministic output order
     */
    private static List<DataInsight> evaluateDeeperLayers(
            Map<String, Object> raw, CharacterizationConfig cfg) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        ProfileCollector collector = new ProfileCollector(cfg);
        ShapeWalker.walk(raw, cfg, 0, collector);
        ContainerInsightAnalyzer.AnalysisResult l1Result =
                ContainerInsightAnalyzer.analyze(collector);
        List<DataInsight> l1 = l1Result.insights();
        Set<String> suppressed = l1Result.suppressedL2Paths();
        List<DataInsight> l2 = new ArrayList<>();
        List<DataInsight> l3 = new ArrayList<>();
        for (ArrayProfile profile : collector.arrays().values()) {
            if (suppressed.contains(profile.arrayPath())) {
                continue;
            }
            l2.addAll(DatasetInsightAnalyzer.analyze(profile, cfg));
            l3.addAll(FieldInsightAnalyzer.analyze(profile, cfg));
        }
        List<DataInsight> l2Winners = competePerTargetPath(l2);
        List<DataInsight> merged = new ArrayList<>(l1.size() + l2Winners.size() + l3.size());
        merged.addAll(l1);
        merged.addAll(l2Winners);
        merged.addAll(l3);
        merged.sort(Comparator.comparingInt(insight -> insight.level().ordinal()));
        return merged;
    }

    /**
     * Keeps only the highest-priority L2 insight per target path, breaking ties by declaration
     * (encounter) order.
     *
     * @param l2 the fired L2 insights in encounter order
     * @return the surviving L2 insights, one per target path
     */
    private static List<DataInsight> competePerTargetPath(List<DataInsight> l2) {
        Map<String, DataInsight> winners = new LinkedHashMap<>();
        for (DataInsight insight : l2) {
            DataInsight current = winners.get(insight.targetPath());
            if (current == null || insight.priority() > current.priority()) {
                winners.put(insight.targetPath(), insight);
            }
        }
        return new ArrayList<>(winners.values());
    }

    /**
     * Matches the {@code l0-empty-data} rule against the payload.
     *
     * @param raw the raw data-model map to inspect; may be {@code null}
     * @return the insight when the payload is empty, or {@code null} otherwise
     */
    private static DataInsight matchL0EmptyData(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return buildEmptyDataInsight(REASON_NULL_OR_EMPTY_OBJECT, List.of());
        }
        List<String> emptyPaths = new ArrayList<>();
        collectEmptyPaths(raw, "", emptyPaths);
        if (emptyPaths.size() == countLeaves(raw)) {
            return buildEmptyDataInsight(REASON_ALL_NULL_LEAVES, emptyPaths);
        }
        if (isEmptyListEnvelope(raw)) {
            return buildEmptyDataInsight(REASON_EMPTY_ARRAY, emptyPaths);
        }
        return null;
    }

    /**
     * Matches the {@code l0-status-ack-only} rule against the payload.
     *
     * @param raw the raw data-model map to inspect; may be {@code null}
     * @return the insight when the payload is a status-only acknowledgement, or {@code null}
     */
    private static DataInsight matchL0StatusAckOnly(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        boolean hasStatusIndicator = false;
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            String key = normalizeKey(entry.getKey());
            if (!isAckKey(key, entry.getValue())) {
                return null;
            }
            if (STATUS_INDICATOR_KEYS.contains(key)) {
                hasStatusIndicator = true;
            }
        }
        if (!hasStatusIndicator) {
            return null;
        }
        return buildStatusAckInsight(raw);
    }

    /**
     * Reports whether one top-level entry still fits inside a status-ack envelope.
     *
     * @param key the normalized top-level key
     * @param value the raw value bound to that key
     * @return {@code true} when the key/value pair is envelope-only
     */
    private static boolean isAckKey(String key, Object value) {
        if (STATUS_ENVELOPE_KEYS.contains(key) || PAGINATION_KEYS.contains(key)) {
            return true;
        }
        return PAYLOAD_KEYS.contains(key) && isAckPayloadValue(value);
    }

    /**
     * Reports whether a {@code data}/{@code result} slot is compatible with a status ack.
     *
     * @param value the raw value bound to the payload slot
     * @return {@code true} when the slot carries no business payload
     */
    private static boolean isAckPayloadValue(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        return value instanceof String text
                && STATUS_STRING_VALUES.contains(text.toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * Builds the {@code l0-status-ack-only} insight, resolving the outcome from the payload.
     *
     * @param raw the raw data-model map that already matched the rule
     * @return the populated insight
     */
    private static DataInsight buildStatusAckInsight(Map<String, Object> raw) {
        Map<String, Object> slots = new LinkedHashMap<>();
        resolveStatusSlot(raw, slots);
        resolveMessageSlot(raw, slots);
        slots.put(SLOT_OUTCOME, resolveOutcome(raw));
        Set<String> tags = new LinkedHashSet<>();
        tags.add(ID_L0_STATUS_ACK_ONLY);
        tags.add("status-ack");
        tags.add("operation-feedback");
        String prompt = buildStatusAckPrompt(slots);
        return new DataInsight(
                ID_L0_STATUS_ACK_ONLY,
                InsightLevel.L0_GATE,
                L0_TARGET_PATH,
                PRIORITY_L0_STATUS_ACK_ONLY,
                tags,
                slots,
                prompt);
    }

    /**
     * Finds the first status-indicator field and records it in {@code slots}.
     *
     * @param raw the raw data-model map that already matched the rule
     * @param slots the mutable slot map being populated
     */
    private static void resolveStatusSlot(Map<String, Object> raw, Map<String, Object> slots) {
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            String key = normalizeKey(entry.getKey());
            if (STATUS_INDICATOR_KEYS.contains(key)) {
                slots.put(SLOT_STATUS_FIELD, entry.getKey());
                slots.put(SLOT_STATUS_VALUE, entry.getValue());
                return;
            }
        }
    }

    /**
     * Finds the first human-readable message field and records it in {@code slots}.
     *
     * @param raw the raw data-model map that already matched the rule
     * @param slots the mutable slot map being populated
     */
    private static void resolveMessageSlot(Map<String, Object> raw, Map<String, Object> slots) {
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            String key = normalizeKey(entry.getKey());
            if (MESSAGE_KEYS.contains(key) && entry.getValue() != null) {
                slots.put(SLOT_MESSAGE_FIELD, entry.getKey());
                slots.put(SLOT_MESSAGE_VALUE, entry.getValue());
                return;
            }
        }
    }

    /**
     * Resolves the outcome of a status acknowledgement from its boolean / message / code fields.
     *
     * @param raw the raw data-model map that already matched the rule
     * @return one of {@code "success"}, {@code "error"}, or {@code "unknown"}
     */
    private static String resolveOutcome(Map<String, Object> raw) {
        String outcome = outcomeFromBooleans(raw);
        if (!OUTCOME_UNKNOWN.equals(outcome)) {
            return outcome;
        }
        outcome = outcomeFromErrorFields(raw);
        if (!OUTCOME_UNKNOWN.equals(outcome)) {
            return outcome;
        }
        return outcomeFromCodes(raw);
    }

    /**
     * Resolves an outcome from {@code success}/{@code ok} boolean fields.
     *
     * @param raw the raw data-model map that already matched the rule
     * @return the resolved outcome, or {@code "unknown"} when no boolean indicator is present
     */
    private static String outcomeFromBooleans(Map<String, Object> raw) {
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            String key = normalizeKey(entry.getKey());
            if (("success".equals(key) || "ok".equals(key))
                    && entry.getValue() instanceof Boolean flag) {
                return flag ? OUTCOME_SUCCESS : OUTCOME_ERROR;
            }
        }
        return OUTCOME_UNKNOWN;
    }

    /**
     * Resolves an outcome from {@code error}/{@code errmsg} fields.
     *
     * @param raw the raw data-model map that already matched the rule
     * @return {@code "error"} when a non-blank error message is present, else {@code "unknown"}
     */
    private static String outcomeFromErrorFields(Map<String, Object> raw) {
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            String key = normalizeKey(entry.getKey());
            if (("error".equals(key) || "errmsg".equals(key))
                    && entry.getValue() instanceof String text
                    && !text.isBlank()) {
                return OUTCOME_ERROR;
            }
        }
        return OUTCOME_UNKNOWN;
    }

    /**
     * Resolves an outcome from numeric {@code code}/{@code status}/{@code errcode} fields.
     *
     * @param raw the raw data-model map that already matched the rule
     * @return the resolved outcome, or {@code "unknown"} when no numeric indicator is present
     */
    private static String outcomeFromCodes(Map<String, Object> raw) {
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            String key = normalizeKey(entry.getKey());
            boolean isCodeKey =
                    "code".equals(key)
                            || "retcode".equals(key)
                            || "errcode".equals(key)
                            || "status".equals(key)
                            || "statuscode".equals(key);
            if (isCodeKey && entry.getValue() instanceof Number number) {
                return outcomeFromCode(number.longValue());
            }
        }
        return OUTCOME_UNKNOWN;
    }

    /**
     * Maps one numeric status code to an outcome string.
     *
     * @param code the numeric status code
     * @return the outcome implied by the code
     */
    private static String outcomeFromCode(long code) {
        if (code == 0 || (code >= SUCCESS_CODE_MIN && code <= SUCCESS_CODE_MAX)) {
            return OUTCOME_SUCCESS;
        }
        if (code >= ERROR_CODE_MIN || code < 0) {
            return OUTCOME_ERROR;
        }
        return OUTCOME_UNKNOWN;
    }

    /**
     * Builds the {@code l0-empty-data} insight.
     *
     * @param reason the empty-data reason string
     * @param emptyPaths the empty array / root paths already collected
     * @return the populated insight
     */
    private static DataInsight buildEmptyDataInsight(String reason, List<String> emptyPaths) {
        Map<String, Object> slots = new LinkedHashMap<>();
        slots.put(SLOT_REASON, reason);
        slots.put(SLOT_EMPTY_PATHS, List.copyOf(emptyPaths));
        Set<String> tags = new LinkedHashSet<>();
        tags.add(ID_L0_EMPTY_DATA);
        tags.add("empty-data");
        tags.add("empty-state");
        return new DataInsight(
                ID_L0_EMPTY_DATA,
                InsightLevel.L0_GATE,
                L0_TARGET_PATH,
                PRIORITY_L0_EMPTY_DATA,
                tags,
                slots,
                buildEmptyDataPrompt(reason));
    }

    /**
     * Reports whether the payload is an empty-list envelope: at least one empty list plus only
     * pagination / status metadata alongside it (including a nested {@code data}/{@code result}
     * empty-list envelope).
     *
     * @param raw the raw data-model map to inspect
     * @return {@code true} when the payload is an empty-list envelope
     */
    private static boolean isEmptyListEnvelope(Map<?, ?> raw) {
        boolean hasEmptyList = false;
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            String key = normalizeKey(String.valueOf(entry.getKey()));
            Object value = entry.getValue();
            if (value instanceof List<?> list) {
                if (!list.isEmpty()) {
                    return false;
                }
                hasEmptyList = true;
            } else if (PAYLOAD_KEYS.contains(key) && value instanceof Map<?, ?> nested) {
                if (nested.isEmpty() || !isEmptyListEnvelope(nested)) {
                    return false;
                }
                hasEmptyList = true;
            } else if (!isEnvelopeMetadata(key, value)) {
                return false;
            }
        }
        return hasEmptyList;
    }

    /**
     * Reports whether one top-level entry is envelope metadata (pagination or status).
     *
     * @param key the normalized top-level key
     * @param value the raw value bound to that key
     * @return {@code true} when the entry is envelope metadata
     */
    private static boolean isEnvelopeMetadata(String key, Object value) {
        if (PAGINATION_KEYS.contains(key)) {
            return true;
        }
        if (!STATUS_ENVELOPE_KEYS.contains(key)) {
            return false;
        }
        return value == null
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof String;
    }

    /**
     * Recursively collects the paths of every empty leaf (null, empty map, or empty list).
     *
     * @param value the current value being walked
     * @param path the dot-separated path of the current value from the root
     * @param emptyPaths the accumulator that receives the empty leaf paths
     */
    private static void collectEmptyPaths(Object value, String path, List<String> emptyPaths) {
        if (value == null) {
            emptyPaths.add(path);
            return;
        }
        if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                emptyPaths.add(path);
                return;
            }
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String childPath =
                        path.isEmpty()
                                ? String.valueOf(entry.getKey())
                                : path + "." + entry.getKey();
                collectEmptyPaths(entry.getValue(), childPath, emptyPaths);
            }
            return;
        }
        if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                emptyPaths.add(path);
                return;
            }
            for (int index = 0; index < list.size(); index++) {
                collectEmptyPaths(list.get(index), path + "[" + index + "]", emptyPaths);
            }
        }
    }

    /**
     * Counts the leaf values under a payload: every scalar, null, or empty collection.
     *
     * @param value the current value being walked
     * @return the number of leaf values under {@code value}
     */
    private static int countLeaves(Object value) {
        if (value == null) {
            return 1;
        }
        if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                return 1;
            }
            int total = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                total += countLeaves(entry.getValue());
            }
            return total;
        }
        if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                return 1;
            }
            int total = 0;
            for (Object item : list) {
                total += countLeaves(item);
            }
            return total;
        }
        return 1;
    }

    /**
     * Normalizes a top-level key for rule matching: lowercased, with underscores and hyphens
     * stripped.
     *
     * @param key the raw top-level key
     * @return the normalized key
     */
    private static String normalizeKey(String key) {
        StringBuilder out = new StringBuilder(key.length());
        for (int index = 0; index < key.length(); index++) {
            char c = key.charAt(index);
            if (c != '_' && c != '-') {
                out.append(Character.toLowerCase(c));
            }
        }
        return out.toString();
    }

    /**
     * Builds the localized prompt fragment for the empty-data insight.
     *
     * @param reason the empty-data reason string
     * @return the prompt fragment
     */
    private static String buildEmptyDataPrompt(String reason) {
        return "数据集为空（reason="
                + reason
                + "）。请渲染空状态提示（例如“暂无数据” / empty-state notice），"
                + "不要绘制空表格，不要编造任何数据。";
    }

    /**
     * Builds the localized prompt fragment for the status-ack insight.
     *
     * @param slots the populated slot map
     * @return the prompt fragment
     */
    private static String buildStatusAckPrompt(Map<String, Object> slots) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("该载荷仅是 API 状态回执（status acknowledgement），不包含业务数据。");
        Object statusField = slots.get(SLOT_STATUS_FIELD);
        Object statusValue = slots.get(SLOT_STATUS_VALUE);
        if (statusField != null) {
            prompt.append("状态字段 ")
                    .append(statusField)
                    .append(" = ")
                    .append(statusValue)
                    .append("；");
        }
        Object messageField = slots.get(SLOT_MESSAGE_FIELD);
        Object messageValue = slots.get(SLOT_MESSAGE_VALUE);
        if (messageField != null) {
            prompt.append("消息字段 ")
                    .append(messageField)
                    .append(" = ")
                    .append(messageValue)
                    .append("；");
        }
        prompt.append("仅渲染一条操作成功/失败的反馈横幅（banner/callout），")
                .append("不要把状态码（例如 code: 200）当作业务指标，也不要绘制表格。");
        return prompt.toString();
    }
}
