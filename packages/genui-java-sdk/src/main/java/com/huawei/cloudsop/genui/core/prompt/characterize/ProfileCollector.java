/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.prompt.characterize;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Profiling observer threaded through {@link ShapeWalker}'s single pass when deeper-layer insights
 * are requested.
 *
 * <p>The collector owns no walking logic of its own: {@link ShapeWalker} calls it at the exact
 * points where it already inspects rows and values, so all statistics are gathered in the same
 * single pass that builds the shape tree. When {@code null} is passed as the collector the walker
 * behaves exactly as before, which keeps the public {@code walk(value, cfg, depth)} output
 * byte-identical.
 *
 * <p>Column-role decisions (which column is the time column, which is the group column, which is
 * the row label) are made after the scan by the analyzers, from the column accumulators' own
 * counters. The collector records raw observations only; it never commits to a role mid-scan.
 *
 * @since 2026
 */
final class ProfileCollector {
    /** Minimum size of an inline numeric array treated as a sparkline series. */
    private static final int MIN_SPARKLINE_LENGTH = 2;

    /** Maximum nesting depth for collecting inner sub-arrays inside object-array rows. */
    private static final int MAX_NESTED_ARRAY_DEPTH = 3;

    private final CharacterizationConfig cfg;

    /** Profiles keyed by array path, in first-encounter order. */
    private final LinkedHashMap<String, ArrayProfile> arrays = new LinkedHashMap<>();

    /** Non-row container objects keyed by path ({@code ""} for root), in first-encounter order. */
    private final LinkedHashMap<String, Map<?, ?>> objects = new LinkedHashMap<>();

    ProfileCollector(CharacterizationConfig cfg) {
        this.cfg = cfg;
    }

    CharacterizationConfig config() {
        return cfg;
    }

    LinkedHashMap<String, ArrayProfile> arrays() {
        return arrays;
    }

    LinkedHashMap<String, Map<?, ?>> objects() {
        return objects;
    }

    /**
     * Records a container object visited by {@link ShapeWalker#walk}.
     *
     * @param path the dot-joined path from the payload root ({@code ""} at root)
     * @param map the visited map
     */
    void observeObject(String path, Map<?, ?> map) {
        objects.put(path, map);
    }

    /**
     * Begins profiling one array and returns its mutable per-array state.
     *
     * @param arrayPath the dot-joined path of the array from the payload root
     * @return the per-array profile for this array
     */
    ArrayProfile beginArray(String arrayPath) {
        ArrayProfile profile = new ArrayProfile(arrayPath);
        arrays.put(arrayPath, profile);
        return profile;
    }

    /**
     * Observes one row of an object array, feeding every column accumulator and capturing L1
     * row-shape and nested sub-array observations.
     *
     * @param profile the per-array profile for the array being scanned
     * @param row the row map being observed
     * @param rowIndex the zero-based index of the row inside the array
     */
    void observeObjectRow(ArrayProfile profile, Map<?, ?> row, long rowIndex) {
        profile.incrementRowCount();
        boolean withinSample = rowIndex < cfg.sampleRows();
        String labelField = chooseLabelField(profile);
        String rowLabel = labelField == null ? null : asLabel(row.get(labelField));
        for (Map.Entry<String, ColumnAccumulator> entry : profile.columns().entrySet()) {
            String key = entry.getKey();
            ColumnAccumulator accumulator = entry.getValue();
            Object value = row.get(key);
            observeColumn(profile, accumulator, key, value, withinSample, rowLabel);
        }
        capturePairTimestamps(profile, row, rowIndex);
        captureRowContainerSignals(profile, row, 0);
    }

    /**
     * Captures row key sets, scalar snapshots, nested object/array markers, and feeds nested object
     * or tuple sub-arrays into their own aggregated {@link ArrayProfile}s.
     *
     * @param profile the enclosing array profile
     * @param row the current row map
     * @param nestedDepth current recursion depth for nested sub-array collection
     */
    private void captureRowContainerSignals(ArrayProfile profile, Map<?, ?> row, int nestedDepth) {
        boolean captureShape = profile.rowKeySets().size() < ArrayProfile.MAX_CONTAINER_SAMPLE_ROWS;
        Set<String> keys = captureShape ? new LinkedHashSet<>() : null;
        Map<String, Object> scalars = captureShape ? new LinkedHashMap<>() : null;
        Set<Long> nestedTimestamps = captureShape ? new LinkedHashSet<>() : null;
        for (Map.Entry<?, ?> entry : row.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            if (captureShape) {
                keys.add(key);
            }
            recordEntrySignal(profile, key, value, scalars, nestedTimestamps, nestedDepth);
        }
        if (captureShape) {
            profile.recordRowShape(keys, scalars, nestedTimestamps);
        }
    }

    private void recordEntrySignal(
            ArrayProfile profile,
            String key,
            Object value,
            Map<String, Object> scalars,
            Set<Long> nestedTimestamps,
            int nestedDepth) {
        if (value == null) {
            return;
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            if (scalars != null) {
                scalars.put(key, value);
            }
            return;
        }
        if (value instanceof Map<?, ?> nestedMap && !nestedMap.isEmpty()) {
            profile.markNestedObjectField(key);
            if (nestedDepth < MAX_NESTED_ARRAY_DEPTH) {
                collectNestedArraysInMap(
                        profile.arrayPath() + "[]." + key,
                        nestedMap,
                        nestedTimestamps,
                        nestedDepth + 1);
            }
            return;
        }
        if (value instanceof List<?> nestedList && !nestedList.isEmpty()) {
            handleNestedList(profile, key, nestedList, nestedTimestamps, nestedDepth);
        }
    }

    private void handleNestedList(
            ArrayProfile profile,
            String key,
            List<?> nestedList,
            Set<Long> nestedTimestamps,
            int nestedDepth) {
        Object first = nestedList.get(0);
        boolean isObjectOrTupleList = first instanceof Map<?, ?> || first instanceof List<?>;
        if (!isObjectOrTupleList) {
            return;
        }
        profile.markNestedArrayField(key);
        if (nestedTimestamps != null) {
            collectTimestampsFromList(nestedList, nestedTimestamps);
        }
        if (nestedDepth < MAX_NESTED_ARRAY_DEPTH) {
            String nestedPath = profile.arrayPath() + "[]." + key;
            observeNestedList(nestedPath, nestedList, nestedDepth + 1);
        }
    }

    private void collectNestedArraysInMap(
            String mapPath, Map<?, ?> map, Set<Long> nestedTimestamps, int nestedDepth) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object value = entry.getValue();
            String childPath = mapPath + "." + entry.getKey();
            if (value instanceof List<?> nestedList && !nestedList.isEmpty()) {
                Object first = nestedList.get(0);
                if (first instanceof Map<?, ?> || first instanceof List<?>) {
                    if (nestedTimestamps != null) {
                        collectTimestampsFromList(nestedList, nestedTimestamps);
                    }
                    observeNestedList(childPath, nestedList, nestedDepth + 1);
                }
            } else if (value instanceof Map<?, ?> childMap
                    && !childMap.isEmpty()
                    && nestedDepth < MAX_NESTED_ARRAY_DEPTH) {
                collectNestedArraysInMap(childPath, childMap, nestedTimestamps, nestedDepth + 1);
            }
        }
    }

    /**
     * Collects timestamps from rows inside a nested list (object rows with time-named fields or
     * 2-element tuple rows whose first element is a timestamp) for cross-object correlation.
     *
     * @param list the nested list to inspect
     * @param out receiver for parsed epoch-millis values
     */
    static void collectTimestampsFromList(List<?> list, Set<Long> out) {
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    String key = String.valueOf(entry.getKey());
                    if (TimestampParser.isTimeName(TimestampParser.normalizeName(key))) {
                        Long epochMs = asEpochMs(entry.getValue());
                        if (epochMs != null) {
                            out.add(epochMs);
                        }
                    }
                }
            } else if (item instanceof List<?> pair && pair.size() == MIN_SPARKLINE_LENGTH) {
                Long epochMs = asEpochMs(pair.get(0));
                if (epochMs != null) {
                    out.add(epochMs);
                }
            }
        }
    }

    /**
     * Aggregates rows of a nested object or tuple list into a shared {@link ArrayProfile} keyed by
     * {@code nestedPath}, keeping column accumulators consistent with {@link ShapeWalker}.
     *
     * @param nestedPath the aggregated path (for example {@code devices[].alarms})
     * @param list the nested list whose rows are appended
     * @param nestedDepth current nested recursion depth
     */
    private void observeNestedList(String nestedPath, List<?> list, int nestedDepth) {
        ArrayProfile nestedProfile = arrays.computeIfAbsent(nestedPath, ArrayProfile::new);
        Object first = list.get(0);
        if (first instanceof Map<?, ?>) {
            observeNestedObjectRows(nestedProfile, list, nestedDepth);
        } else {
            for (Object row : list) {
                observeTupleRow(nestedProfile, row);
            }
        }
    }

    private void observeNestedObjectRows(ArrayProfile profile, List<?> list, int nestedDepth) {
        for (Object rawRow : list) {
            if (!(rawRow instanceof Map<?, ?> row)) {
                profile.markNonObjectRow();
                return;
            }
            long rowsSeenBefore = profile.rowCount();
            for (Map.Entry<?, ?> entry : row.entrySet()) {
                String key = String.valueOf(entry.getKey());
                profile.columns()
                        .computeIfAbsent(
                                key,
                                k -> {
                                    ColumnAccumulator acc =
                                            new ColumnAccumulator(cfg.enumMaxDistinct());
                                    for (long i = 0L; i < rowsSeenBefore; i++) {
                                        acc.observeMissing();
                                    }
                                    return acc;
                                });
            }
            for (Map.Entry<String, ColumnAccumulator> colEntry : profile.columns().entrySet()) {
                String key = colEntry.getKey();
                ColumnAccumulator acc = colEntry.getValue();
                if (!row.containsKey(key)) {
                    acc.observeMissing();
                    continue;
                }
                observeBasicScalarShape(acc, row.get(key));
            }
            long rowIndex = profile.rowCount();
            profile.incrementRowCount();
            boolean withinSample = rowIndex < cfg.sampleRows();
            String labelField = chooseLabelField(profile);
            String rowLabel = labelField == null ? null : asLabel(row.get(labelField));
            for (Map.Entry<String, ColumnAccumulator> entry : profile.columns().entrySet()) {
                observeColumn(
                        profile,
                        entry.getValue(),
                        entry.getKey(),
                        row.get(entry.getKey()),
                        withinSample,
                        rowLabel);
            }
            capturePairTimestamps(profile, row, rowIndex);
            if (nestedDepth < MAX_NESTED_ARRAY_DEPTH) {
                captureRowContainerSignals(profile, row, nestedDepth);
            }
        }
    }

    private static void observeBasicScalarShape(ColumnAccumulator acc, Object value) {
        if (value == null) {
            acc.observeNull();
        } else if (value instanceof String text) {
            acc.observePresent(text);
            acc.observeType(ScalarType.STRING);
        } else if (value instanceof Number) {
            acc.observeScalarType(ScalarType.NUMBER);
        } else if (value instanceof Boolean) {
            acc.observeScalarType(ScalarType.BOOLEAN);
        } else {
            acc.observeScalarType(ScalarType.UNKNOWN);
        }
    }

    /**
     * Chooses the first non-id, non-time string column as the row-label source for the whole array.
     * The choice is deterministic (column insertion order) so every row uses the same label field.
     *
     * @param profile the per-array profile
     * @return the label column name, or {@code null} when the array has no string label column
     */
    private static String chooseLabelField(ArrayProfile profile) {
        for (Map.Entry<String, ColumnAccumulator> entry : profile.columns().entrySet()) {
            String field = entry.getKey();
            String normalized = TimestampParser.normalizeName(field);
            if ("id".equals(normalized) || normalized.endsWith("id")) {
                continue;
            }
            if (TimestampParser.isTimeName(normalized)) {
                continue;
            }
            if (entry.getValue().scalarTypesSeen().contains(ScalarType.STRING)) {
                return field;
            }
        }
        return null;
    }

    /**
     * Converts a raw column value to its row-label string form, or {@code null}.
     *
     * @param value the raw value
     * @return the label string, or {@code null} when absent or not a scalar
     */
    private static String asLabel(Object value) {
        if (value instanceof String text) {
            return text;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        return null;
    }

    /**
     * Captures (groupValue, epochMs, rowIndex) for every candidate (time-named, group-candidate)
     * column pair in one row, so the alignment check needs no second walk.
     *
     * @param profile the per-array profile
     * @param row the row map being observed
     * @param rowIndex the zero-based row index
     */
    private void capturePairTimestamps(ArrayProfile profile, Map<?, ?> row, long rowIndex) {
        for (Map.Entry<String, ColumnAccumulator> timeEntry : profile.columns().entrySet()) {
            String timeField = timeEntry.getKey();
            if (!TimestampParser.isTimeName(TimestampParser.normalizeName(timeField))) {
                continue;
            }
            Long epochMs = asEpochMs(row.get(timeField));
            if (epochMs == null) {
                continue;
            }
            for (Map.Entry<String, ColumnAccumulator> groupEntry : profile.columns().entrySet()) {
                String groupField = groupEntry.getKey();
                if (groupField.equals(timeField)) {
                    continue;
                }
                Object groupRaw = row.get(groupField);
                if (groupRaw instanceof String groupValue) {
                    profile.observePairTimestamp(
                            timeField, groupField, groupValue, epochMs, rowIndex);
                }
            }
        }
    }

    /**
     * Converts a raw column value to epoch milliseconds when it parses as a timestamp.
     *
     * @param value the raw value
     * @return the epoch millis, or {@code null} when the value is not a timestamp
     */
    static Long asEpochMs(Object value) {
        if (value instanceof Number number) {
            TimestampParser.ParsedTimestamp parsed = TimestampParser.parseNumber(number);
            return parsed == null ? null : parsed.epochMs();
        }
        if (value instanceof String text) {
            TimestampParser.ParsedTimestamp parsed = TimestampParser.parseText(text);
            return parsed == null ? null : parsed.epochMs();
        }
        return null;
    }

    /**
     * Observes one non-object row of an array (tuple list or scalar), updating tuple-scan state.
     *
     * @param profile the per-array profile for the array being scanned
     * @param row the row value being observed
     */
    void observeTupleRow(ArrayProfile profile, Object row) {
        profile.incrementRowCount();
        profile.markNonObjectRow();
        if (!(row instanceof List<?> pair) || pair.size() != MIN_SPARKLINE_LENGTH) {
            profile.markNonTupleRow();
            return;
        }
        Object xValue = pair.get(0);
        Object yValue = pair.get(1);
        if (!(yValue instanceof Number)) {
            profile.markNonTupleRow();
            return;
        }
        profile.tupleXValues().add(xValue);
        if (!(xValue instanceof Number)) {
            profile.markTupleXNonNumeric();
        }
    }

    /**
     * Observes one column value inside an object-array row.
     *
     * @param profile the per-array profile
     * @param accumulator the column's accumulator
     * @param key the column name
     * @param value the observed value (may be {@code null})
     * @param withinSample whether the row is inside the sample prefix
     * @param rowLabel the row's label, or {@code null}
     */
    private void observeColumn(
            ArrayProfile profile,
            ColumnAccumulator accumulator,
            String key,
            Object value,
            boolean withinSample,
            String rowLabel) {
        if (value == null) {
            return;
        }
        accumulator.observeSampleMembership(withinSample);
        if (value instanceof Number number) {
            observeNumericColumn(accumulator, key, number, rowLabel);
        } else if (value instanceof String text) {
            observeStringColumn(accumulator, key, text);
        } else if (value instanceof List<?> list) {
            observeListColumn(accumulator, list);
        }
    }

    private void observeNumericColumn(
            ColumnAccumulator accumulator, String key, Number number, String rowLabel) {
        accumulator.observeNumeric(number.doubleValue(), rowLabel);
        if (TimestampParser.isTimeName(TimestampParser.normalizeName(key))) {
            TimestampParser.ParsedTimestamp parsed = TimestampParser.parseNumber(number);
            if (parsed != null) {
                accumulator.observeTimestamp(parsed.epochMs(), false, String.valueOf(number));
            }
        }
    }

    private void observeStringColumn(ColumnAccumulator accumulator, String key, String text) {
        if (!TimestampParser.isTimeName(TimestampParser.normalizeName(key))) {
            return;
        }
        TimestampParser.ParsedTimestamp parsed = TimestampParser.parseText(text);
        if (parsed != null) {
            accumulator.observeTimestamp(parsed.epochMs(), parsed.dateOnly(), text);
        }
    }

    private void observeListColumn(ColumnAccumulator accumulator, List<?> list) {
        if (list.size() < MIN_SPARKLINE_LENGTH) {
            return;
        }
        for (Object element : list) {
            if (!(element instanceof Number)) {
                return;
            }
        }
        accumulator.observeNumericArray(list.size());
    }
}
