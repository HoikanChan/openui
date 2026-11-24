/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.prompt.characterize;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Derives {@link InsightLevel#L1_CONTAINER} container and cross-object topology insights from the
 * single-pass observations captured by {@link ProfileCollector}.
 *
 * <p>Covers five container topologies:
 *
 * <ul>
 *   <li>{@code l1-correlated-object-merge}: heterogeneous objects sharing an identifier key/value
 *       or aligned nested timestamps (within an array or across sibling objects);
 *   <li>{@code l1-multi-entity-tabs}: array of complex nested or heterogeneous entity objects with
 *       mutually exclusive discriminator values, suited for {@code Tabs};
 *   <li>{@code l1-paginated-envelope}: object pairing pagination metadata with a business array;
 *   <li>{@code l1-dynamic-key-map}: object whose values are homogeneous objects keyed by dynamic
 *       instance identifiers;
 *   <li>{@code l1-single-record-detail}: standalone single-record detail object suited for {@code
 *       Descriptions}.
 * </ul>
 *
 * @since 2026
 */
final class ContainerInsightAnalyzer {
    /** Priority of correlated heterogeneous object merge. */
    private static final int PRIORITY_CORRELATED_MERGE = 95;

    /** Priority of multi-entity tabs container. */
    private static final int PRIORITY_MULTI_ENTITY_TABS = 90;

    /** Priority of paginated envelope container. */
    private static final int PRIORITY_PAGINATED_ENVELOPE = 80;

    /** Priority of dynamic-key homogeneous object map. */
    private static final int PRIORITY_DYNAMIC_KEY_MAP = 75;

    /** Priority of single-record detail object. */
    private static final int PRIORITY_SINGLE_RECORD_DETAIL = 40;

    /** Maximum average key-set Jaccard overlap for objects to be considered heterogeneous. */
    private static final double HETEROGENEOUS_JACCARD_MAX = 0.75;

    /** Minimum pairwise key-set Jaccard overlap for a map's values to be homogeneous. */
    private static final double HOMOGENEOUS_JACCARD_MIN = 0.75;

    /** Minimum shared-timestamp overlap ratio for time-correlated heterogeneous objects. */
    private static final double TIME_OVERLAP_MIN = 0.7;

    /**
     * Minimum business scalar fields required before a standalone object counts as a detail view.
     */
    private static final int SINGLE_RECORD_MIN_FIELDS = 3;

    /** Maximum number of entities eligible for Tabs layout before preferring a table. */
    private static final int MAX_TAB_ENTITIES = 12;

    /** Maximum sample keys emitted in dynamic-key-map slots. */
    private static final int MAX_SAMPLE_KEYS = 6;

    /** Root target path label used when an object sits at the payload root. */
    private static final String ROOT_PATH = "data";

    /** Normalized keys treated as pagination metadata in envelope detection. */
    private static final Set<String> PAGINATION_KEYS =
            Set.of(
                    "total",
                    "totalcount",
                    "totalelements",
                    "count",
                    "page",
                    "pageindex",
                    "pagenum",
                    "current",
                    "pagesize",
                    "size",
                    "limit",
                    "offset",
                    "hasmore",
                    "totalpages");

    /** Normalized keys treated as status envelope metadata rather than business fields. */
    private static final Set<String> ENVELOPE_META_KEYS =
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

    /** Normalized payload wrapper keys inside a status envelope. */
    private static final Set<String> PAYLOAD_WRAPPER_KEYS = Set.of("data", "result");

    /** Preferred identifier column keywords (ordered) for tab titles and join-key ranking. */
    private static final List<String> PREFERRED_DISCRIMINATOR_NAMES =
            List.of(
                    "devicename",
                    "name",
                    "title",
                    "label",
                    "hostname",
                    "host",
                    "node",
                    "service",
                    "entity",
                    "deviceid",
                    "entityid",
                    "id",
                    "key");

    private ContainerInsightAnalyzer() {}

    /**
     * Evaluates all L1 container rules against the single-pass observations in {@code collector}.
     *
     * @param collector the single-pass profiling collector
     * @return the fired L1 insights plus the set of outer array paths whose L2/L3 should be
     *     suppressed
     */
    static AnalysisResult analyze(ProfileCollector collector) {
        List<DataInsight> insights = new ArrayList<>();
        Set<String> suppressedL2Paths = new LinkedHashSet<>();
        analyzeArrayContainers(collector, insights, suppressedL2Paths);
        analyzeObjectContainers(collector, insights);
        return new AnalysisResult(insights, suppressedL2Paths);
    }

    private static void analyzeArrayContainers(
            ProfileCollector collector, List<DataInsight> insights, Set<String> suppressedL2Paths) {
        for (ArrayProfile profile : collector.arrays().values()) {
            if (!profile.allObjectRows() || profile.rowCount() < 2) {
                continue;
            }
            // Skip aggregated inner sub-arrays (path contains "[].") for outer container rules;
            // inner sub-arrays are governed by L2/L3 rules and compose with the outer L1 container.
            if (profile.arrayPath().contains("[].")) {
                continue;
            }
            DataInsight merged = matchArrayCorrelatedMerge(profile);
            if (merged != null) {
                insights.add(merged);
                suppressedL2Paths.add(profile.arrayPath());
                continue;
            }
            DataInsight tabs = matchMultiEntityTabs(profile);
            if (tabs != null) {
                insights.add(tabs);
                suppressedL2Paths.add(profile.arrayPath());
            }
        }
    }

    private static void analyzeObjectContainers(
            ProfileCollector collector, List<DataInsight> insights) {
        boolean hasAnyObjectArray = hasNonEmptyObjectArray(collector);
        for (Map.Entry<String, Map<?, ?>> entry : collector.objects().entrySet()) {
            String path = entry.getKey();
            Map<?, ?> map = entry.getValue();
            if (map == null || map.isEmpty()) {
                continue;
            }
            DataInsight paginated = matchPaginatedEnvelope(path, map);
            if (paginated != null) {
                insights.add(paginated);
            }
            DataInsight siblingMerge = matchSiblingCorrelatedMerge(path, map);
            if (siblingMerge != null) {
                insights.add(siblingMerge);
                continue;
            }
            DataInsight dynamicMap = matchDynamicKeyMap(path, map);
            if (dynamicMap != null) {
                insights.add(dynamicMap);
            }
        }
        if (!hasAnyObjectArray && insights.isEmpty()) {
            DataInsight detail = matchSingleRecordDetail(collector);
            if (detail != null) {
                insights.add(detail);
            }
        }
    }

    // ---- Rule 1: l1-correlated-object-merge (array and sibling forms) ----

    private static DataInsight matchArrayCorrelatedMerge(ArrayProfile profile) {
        int count = profile.rowKeySets().size();
        if (count < 2 || profile.rowCount() > ArrayProfile.MAX_CONTAINER_SAMPLE_ROWS) {
            return null;
        }
        if (!areHeterogeneous(profile.rowKeySets())) {
            return null;
        }
        JoinMatch joinMatch = findSharedKeyJoin(profile.rowScalars(), profile.rowKeySets());
        if (joinMatch != null) {
            return buildCorrelatedMergeInsight(
                    displayPath(profile.arrayPath()),
                    joinMatch.joinKey(),
                    joinMatch.sharedValues(),
                    true,
                    extractUniqueFieldGroups(profile.rowKeySets(), joinMatch.joinKey()),
                    null);
        }
        if (hasAlignedNestedTimestamps(profile.rowNestedTimestamps())) {
            return buildCorrelatedMergeInsight(
                    displayPath(profile.arrayPath()),
                    "timestamp",
                    List.of(),
                    true,
                    extractUniqueFieldGroups(profile.rowKeySets(), null),
                    null);
        }
        return null;
    }

    private static DataInsight matchSiblingCorrelatedMerge(String path, Map<?, ?> map) {
        List<String> sourceNames = new ArrayList<>();
        List<Set<String>> keySets = new ArrayList<>();
        List<Map<String, Object>> scalars = new ArrayList<>();
        List<Set<Long>> nestedTimes = new ArrayList<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (!(entry.getValue() instanceof Map<?, ?> child) || child.isEmpty()) {
                continue;
            }
            if (PAYLOAD_WRAPPER_KEYS.contains(TimestampParser.normalizeName(key))) {
                continue;
            }
            sourceNames.add(key);
            keySets.add(stringKeySet(child));
            scalars.add(extractScalars(child));
            nestedTimes.add(extractNestedTimestamps(child));
        }
        if (sourceNames.size() < 2 || !areHeterogeneous(keySets)) {
            return null;
        }
        JoinMatch joinMatch = findSharedKeyJoin(scalars, keySets);
        if (joinMatch != null) {
            return buildCorrelatedMergeInsight(
                    displayPath(path),
                    joinMatch.joinKey(),
                    joinMatch.sharedValues(),
                    true,
                    extractUniqueFieldGroups(keySets, joinMatch.joinKey()),
                    sourceNames);
        }
        if (hasAlignedNestedTimestamps(nestedTimes)) {
            return buildCorrelatedMergeInsight(
                    displayPath(path),
                    "timestamp",
                    List.of(),
                    true,
                    extractUniqueFieldGroups(keySets, null),
                    sourceNames);
        }
        return null;
    }

    private static DataInsight buildCorrelatedMergeInsight(
            String targetPath,
            String joinKey,
            List<Object> sharedValues,
            boolean aligned,
            List<List<String>> uniqueFieldGroups,
            List<String> sourceKeys) {
        Map<String, Object> slots = new LinkedHashMap<>();
        slots.put("arrayPath", targetPath);
        slots.put("containerPath", targetPath);
        slots.put("joinKey", joinKey);
        slots.put("sharedValues", sharedValues);
        slots.put("aligned", aligned);
        slots.put("uniqueFieldGroups", uniqueFieldGroups);
        if (sourceKeys != null) {
            slots.put("sourceKeys", sourceKeys);
        }
        String prompt =
                "路径 "
                        + targetPath
                        + " 包含结构异构的对象，但通过关联键 "
                        + joinKey
                        + (sharedValues.isEmpty() ? "" : "（公共值 " + sharedValues + "）")
                        + " 指向同一实体；各自独有字段组为 "
                        + uniqueFieldGroups
                        + "。请将它们合并渲染为同一实体的综合视图，不要拆分画成多行无关表格。";
        return build(
                DataProfiler.ID_L1_CORRELATED_OBJECT_MERGE,
                PRIORITY_CORRELATED_MERGE,
                targetPath,
                Set.of(
                        DataProfiler.ID_L1_CORRELATED_OBJECT_MERGE,
                        "correlated-merge",
                        "heterogeneous-objects"),
                slots,
                prompt);
    }

    // ---- Rule 2: l1-multi-entity-tabs ----

    private static DataInsight matchMultiEntityTabs(ArrayProfile profile) {
        if (profile.rowCount() < 2 || profile.rowCount() > MAX_TAB_ENTITIES) {
            return null;
        }
        boolean hasComplexNesting =
                !profile.nestedArrayFields().isEmpty() || !profile.nestedObjectFields().isEmpty();
        boolean heterogeneous = areHeterogeneous(profile.rowKeySets());
        if (!hasComplexNesting && !heterogeneous) {
            return null;
        }
        DiscriminatorMatch discriminator = findDiscriminator(profile);
        if (discriminator == null) {
            return null;
        }
        String targetPath = displayPath(profile.arrayPath());
        List<String> nestedArrays = new ArrayList<>(profile.nestedArrayFields());
        Map<String, Object> slots = new LinkedHashMap<>();
        slots.put("arrayPath", targetPath);
        slots.put("containerPath", targetPath);
        slots.put("tabKey", discriminator.field());
        slots.put("tabTitles", discriminator.titles());
        slots.put("nestedArrayFields", nestedArrays);
        slots.put("entityCount", profile.rowCount());
        String prompt =
                "数组 "
                        + targetPath
                        + " 包含 "
                        + profile.rowCount()
                        + " 个独立复杂实体，按互斥标识键 "
                        + discriminator.field()
                        + " 区分，Tab 标题为 "
                        + discriminator.titles()
                        + (nestedArrays.isEmpty() ? "" : "，内部含子表字段 " + nestedArrays)
                        + "。推荐外层使用 Tabs 分页承载各实体详情，不要把多实体嵌套结构硬塞进单张平铺表格。";
        return build(
                DataProfiler.ID_L1_MULTI_ENTITY_TABS,
                PRIORITY_MULTI_ENTITY_TABS,
                targetPath,
                Set.of(DataProfiler.ID_L1_MULTI_ENTITY_TABS, "multi-entity-tabs", "tabs"),
                slots,
                prompt);
    }

    private static DiscriminatorMatch findDiscriminator(ArrayProfile profile) {
        List<Map<String, Object>> rows = profile.rowScalars();
        if (rows.size() < 2) {
            return null;
        }
        List<String> candidates = orderDiscriminatorCandidates(profile.columns().keySet());
        for (String field : candidates) {
            List<String> titles = extractDistinctRowTitles(rows, field);
            if (titles != null) {
                return new DiscriminatorMatch(field, titles);
            }
        }
        return null;
    }

    private static List<String> orderDiscriminatorCandidates(Set<String> columns) {
        List<String> ordered = new ArrayList<>();
        for (String preferred : PREFERRED_DISCRIMINATOR_NAMES) {
            for (String column : columns) {
                if (TimestampParser.normalizeName(column).equals(preferred)
                        && !ordered.contains(column)) {
                    ordered.add(column);
                }
            }
        }
        for (String column : columns) {
            if (!ordered.contains(column)) {
                ordered.add(column);
            }
        }
        return ordered;
    }

    private static List<String> extractDistinctRowTitles(
            List<Map<String, Object>> rows, String field) {
        Set<String> seen = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            Object raw = row.get(field);
            if (!(raw instanceof String text) || text.isBlank()) {
                return null;
            }
            if (!seen.add(text)) {
                return null;
            }
        }
        return seen.size() == rows.size() ? new ArrayList<>(seen) : null;
    }

    // ---- Rule 3: l1-paginated-envelope ----

    private static DataInsight matchPaginatedEnvelope(String path, Map<?, ?> map) {
        String listField = null;
        Map<String, Object> pagination = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            String normalized = TimestampParser.normalizeName(key);
            Object value = entry.getValue();
            if (value instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map) {
                if (listField == null) {
                    listField = key;
                }
            } else if (PAGINATION_KEYS.contains(normalized)
                    && (value instanceof Number || value instanceof Boolean)) {
                pagination.put(key, value);
            }
        }
        if (listField == null || pagination.isEmpty()) {
            return null;
        }
        String containerPath = displayPath(path);
        String arrayPath = path.isEmpty() ? listField : path + "." + listField;
        Map<String, Object> slots = new LinkedHashMap<>();
        slots.put("containerPath", containerPath);
        slots.put("arrayPath", arrayPath);
        slots.put("listField", listField);
        slots.put("paginationFields", pagination);
        String prompt =
                "路径 "
                        + containerPath
                        + " 是分页信封容器（分页元数据 "
                        + pagination
                        + "，数据列表 "
                        + arrayPath
                        + "）。推荐使用 Descriptions/摘要条展示分页信息，并为主列表 "
                        + arrayPath
                        + " 渲染表格或主视图。";
        return build(
                DataProfiler.ID_L1_PAGINATED_ENVELOPE,
                PRIORITY_PAGINATED_ENVELOPE,
                containerPath,
                Set.of(DataProfiler.ID_L1_PAGINATED_ENVELOPE, "paginated-envelope", "pagination"),
                slots,
                prompt);
    }

    // ---- Rule 4: l1-dynamic-key-map ----

    private static DataInsight matchDynamicKeyMap(String path, Map<?, ?> map) {
        List<String> keys = new ArrayList<>();
        List<Set<String>> childKeySets = new ArrayList<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            String normalized = TimestampParser.normalizeName(key);
            Object value = entry.getValue();
            if (ENVELOPE_META_KEYS.contains(normalized) || PAGINATION_KEYS.contains(normalized)) {
                continue;
            }
            if (!(value instanceof Map<?, ?> child) || child.isEmpty()) {
                return null;
            }
            keys.add(key);
            childKeySets.add(stringKeySet(child));
        }
        if (keys.size() < 2 || !areHomogeneous(childKeySets)) {
            return null;
        }
        Set<String> sharedFields = intersectionAll(childKeySets);
        if (sharedFields.isEmpty()) {
            return null;
        }
        String containerPath = displayPath(path);
        List<String> sampleKeys = keys.subList(0, Math.min(MAX_SAMPLE_KEYS, keys.size()));
        Map<String, Object> slots = new LinkedHashMap<>();
        slots.put("containerPath", containerPath);
        slots.put("entryCount", keys.size());
        slots.put("sampleKeys", new ArrayList<>(sampleKeys));
        slots.put("sharedFields", new ArrayList<>(sharedFields));
        String prompt =
                "路径 "
                        + containerPath
                        + " 是包含 "
                        + keys.size()
                        + " 个动态键的同构对象字典（示例键 "
                        + sampleKeys
                        + "，公共字段 "
                        + sharedFields
                        + "）。推荐使用 @ObjectEntries("
                        + containerPath
                        + ") 展开为键值行后渲染表格或列表。";
        return build(
                DataProfiler.ID_L1_DYNAMIC_KEY_MAP,
                PRIORITY_DYNAMIC_KEY_MAP,
                containerPath,
                Set.of(DataProfiler.ID_L1_DYNAMIC_KEY_MAP, "dynamic-key-map", "object-entries"),
                slots,
                prompt);
    }

    // ---- Rule 5: l1-single-record-detail ----

    private static DataInsight matchSingleRecordDetail(ProfileCollector collector) {
        Map<?, ?> root = collector.objects().get("");
        if (root == null || root.isEmpty()) {
            return null;
        }
        TargetRecord target = unwrapSingleRecordTarget(root);
        if (target == null) {
            return null;
        }
        List<String> businessFields = new ArrayList<>();
        for (Map.Entry<?, ?> entry : target.map().entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            if (value instanceof String || value instanceof Number || value instanceof Boolean) {
                if (!ENVELOPE_META_KEYS.contains(TimestampParser.normalizeName(key))) {
                    businessFields.add(key);
                }
            }
        }
        if (businessFields.size() < SINGLE_RECORD_MIN_FIELDS) {
            return null;
        }
        Map<String, Object> slots = new LinkedHashMap<>();
        slots.put("containerPath", target.path());
        slots.put("fieldCount", businessFields.size());
        slots.put("fields", businessFields);
        String prompt =
                "路径 "
                        + target.path()
                        + " 是单条记录详情对象（包含 "
                        + businessFields.size()
                        + " 个业务字段："
                        + businessFields
                        + "）。推荐使用 Descriptions / 详情卡片按键值对展示，不要绘制只有一行的表格。";
        return build(
                DataProfiler.ID_L1_SINGLE_RECORD_DETAIL,
                PRIORITY_SINGLE_RECORD_DETAIL,
                target.path(),
                Set.of(DataProfiler.ID_L1_SINGLE_RECORD_DETAIL, "single-record", "descriptions"),
                slots,
                prompt);
    }

    private static TargetRecord unwrapSingleRecordTarget(Map<?, ?> root) {
        Map<?, ?> wrapped = null;
        String wrappedKey = null;
        for (Map.Entry<?, ?> entry : root.entrySet()) {
            String key = String.valueOf(entry.getKey());
            String normalized = TimestampParser.normalizeName(key);
            Object value = entry.getValue();
            if (PAYLOAD_WRAPPER_KEYS.contains(normalized) && value instanceof Map<?, ?> child) {
                wrapped = child;
                wrappedKey = key;
            } else if (value instanceof List<?> list && !list.isEmpty()) {
                return null;
            }
        }
        if (wrapped != null) {
            return new TargetRecord(wrappedKey, wrapped);
        }
        return new TargetRecord(ROOT_PATH, root);
    }

    // ---- Shared topology helpers ----

    private static boolean hasNonEmptyObjectArray(ProfileCollector collector) {
        for (ArrayProfile profile : collector.arrays().values()) {
            if (profile.allObjectRows() && profile.rowCount() >= 1) {
                return true;
            }
        }
        return false;
    }

    private static boolean areHeterogeneous(List<Set<String>> keySets) {
        if (keySets.size() < 2) {
            return false;
        }
        double total = 0.0;
        int pairs = 0;
        boolean hasUniqueSide = false;
        for (int i = 0; i < keySets.size(); i++) {
            for (int j = i + 1; j < keySets.size(); j++) {
                Set<String> left = keySets.get(i);
                Set<String> right = keySets.get(j);
                if (!left.equals(right) && hasMutuallyExclusiveKeys(left, right)) {
                    hasUniqueSide = true;
                }
                total += jaccard(left, right);
                pairs++;
            }
        }
        return hasUniqueSide && pairs > 0 && (total / pairs) < HETEROGENEOUS_JACCARD_MAX;
    }

    private static boolean areHomogeneous(List<Set<String>> keySets) {
        if (keySets.size() < 2) {
            return false;
        }
        for (int i = 0; i < keySets.size(); i++) {
            for (int j = i + 1; j < keySets.size(); j++) {
                if (jaccard(keySets.get(i), keySets.get(j)) < HOMOGENEOUS_JACCARD_MIN) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean hasMutuallyExclusiveKeys(Set<String> left, Set<String> right) {
        boolean leftOnly = false;
        for (String key : left) {
            if (!right.contains(key)) {
                leftOnly = true;
                break;
            }
        }
        boolean rightOnly = false;
        for (String key : right) {
            if (!left.contains(key)) {
                rightOnly = true;
                break;
            }
        }
        return leftOnly && rightOnly;
    }

    private static double jaccard(Set<String> left, Set<String> right) {
        Set<String> union = new LinkedHashSet<>(left);
        union.addAll(right);
        if (union.isEmpty()) {
            return 1.0;
        }
        int common = 0;
        for (String key : left) {
            if (right.contains(key)) {
                common++;
            }
        }
        return common / (double) union.size();
    }

    private static JoinMatch findSharedKeyJoin(
            List<Map<String, Object>> scalars, List<Set<String>> keySets) {
        Set<String> commonKeys = intersectionAll(keySets);
        List<String> ranked = orderJoinCandidates(commonKeys);
        for (String key : ranked) {
            Map<Object, List<Set<String>>> valueToKeySets = new LinkedHashMap<>();
            for (int i = 0; i < scalars.size(); i++) {
                Object val = scalars.get(i).get(key);
                if (val == null) {
                    continue;
                }
                valueToKeySets
                        .computeIfAbsent(val, ignored -> new ArrayList<>())
                        .add(keySets.get(i));
            }
            List<Object> shared = new ArrayList<>();
            for (Map.Entry<Object, List<Set<String>>> entry : valueToKeySets.entrySet()) {
                if (entry.getValue().size() >= 2 && areHeterogeneous(entry.getValue())) {
                    shared.add(entry.getKey());
                }
            }
            if (!shared.isEmpty()) {
                return new JoinMatch(key, shared);
            }
        }
        return null;
    }

    private static List<String> orderJoinCandidates(Set<String> commonKeys) {
        List<String> ranked = new ArrayList<>();
        for (String key : commonKeys) {
            String normalized = TimestampParser.normalizeName(key);
            if ("id".equals(normalized)
                    || normalized.endsWith("id")
                    || normalized.endsWith("key")
                    || normalized.endsWith("code")) {
                ranked.add(key);
            }
        }
        for (String key : commonKeys) {
            if (!ranked.contains(key)) {
                ranked.add(key);
            }
        }
        return ranked;
    }

    private static boolean hasAlignedNestedTimestamps(List<Set<Long>> nestedTimestamps) {
        List<Set<Long>> nonEmpty = new ArrayList<>();
        for (Set<Long> set : nestedTimestamps) {
            if (set != null && set.size() >= 2) {
                nonEmpty.add(set);
            }
        }
        if (nonEmpty.size() < 2) {
            return false;
        }
        Set<Long> union = new LinkedHashSet<>();
        for (Set<Long> set : nonEmpty) {
            union.addAll(set);
        }
        double totalRatio = 0.0;
        for (Set<Long> set : nonEmpty) {
            totalRatio += set.size() / (double) union.size();
        }
        return (totalRatio / nonEmpty.size()) >= TIME_OVERLAP_MIN;
    }

    private static List<List<String>> extractUniqueFieldGroups(
            List<Set<String>> keySets, String joinKey) {
        Set<String> common = intersectionAll(keySets);
        List<List<String>> groups = new ArrayList<>();
        for (Set<String> keySet : keySets) {
            List<String> unique = new ArrayList<>();
            for (String key : keySet) {
                if (!key.equals(joinKey) && !common.contains(key)) {
                    unique.add(key);
                }
            }
            if (!unique.isEmpty() && !groups.contains(unique)) {
                groups.add(unique);
            }
        }
        return groups;
    }

    private static Set<String> intersectionAll(List<Set<String>> sets) {
        if (sets.isEmpty()) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>(sets.get(0));
        for (int i = 1; i < sets.size(); i++) {
            result.retainAll(sets.get(i));
        }
        return result;
    }

    private static Set<String> stringKeySet(Map<?, ?> map) {
        Set<String> keys = new LinkedHashSet<>();
        for (Object key : map.keySet()) {
            keys.add(String.valueOf(key));
        }
        return keys;
    }

    private static Map<String, Object> extractScalars(Map<?, ?> map) {
        Map<String, Object> scalars = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object val = entry.getValue();
            if (val instanceof String || val instanceof Number || val instanceof Boolean) {
                scalars.put(String.valueOf(entry.getKey()), val);
            }
        }
        return scalars;
    }

    private static Set<Long> extractNestedTimestamps(Map<?, ?> map) {
        Set<Long> out = new LinkedHashSet<>();
        for (Object val : map.values()) {
            if (val instanceof List<?> list && !list.isEmpty()) {
                ProfileCollector.collectTimestampsFromList(list, out);
            }
        }
        return out;
    }

    private static String displayPath(String path) {
        return (path == null || path.isEmpty()) ? ROOT_PATH : path;
    }

    private static DataInsight build(
            String id,
            int priority,
            String targetPath,
            Set<String> tags,
            Map<String, Object> slots,
            String prompt) {
        return new DataInsight(
                id, InsightLevel.L1_CONTAINER, targetPath, priority, tags, slots, prompt);
    }

    /**
     * Output of L1 container analysis: the fired L1 insights plus array paths whose outer L2/L3
     * should be suppressed because the array is a heterogeneous/multi-entity container.
     *
     * @param insights the fired L1 container insights
     * @param suppressedL2Paths outer array paths that should skip L2/L3 evaluation
     * @since 2026
     */
    record AnalysisResult(List<DataInsight> insights, Set<String> suppressedL2Paths) {}

    private record JoinMatch(String joinKey, List<Object> sharedValues) {}

    private record DiscriminatorMatch(String field, List<String> titles) {}

    private record TargetRecord(String path, Map<?, ?> map) {}
}
