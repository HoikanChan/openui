/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.prompt.characterize;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    private final CharacterizationConfig cfg;

    /** Profiles keyed by array path, in first-encounter order. */
    private final LinkedHashMap<String, ArrayProfile> arrays = new LinkedHashMap<>();

    ProfileCollector(CharacterizationConfig cfg) {
        this.cfg = cfg;
    }

    CharacterizationConfig config() {
        return cfg;
    }

    LinkedHashMap<String, ArrayProfile> arrays() {
        return arrays;
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
     * Observes one row of an object array, feeding every column accumulator.
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
            observeColumn(profile, accumulator, key, value, rowIndex, withinSample, rowLabel);
        }
        capturePairTimestamps(profile, row, rowIndex);
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
    private static Long asEpochMs(Object value) {
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
     * @param rowIndex the zero-based row index
     * @param withinSample whether the row is inside the sample prefix
     * @param rowLabel the row's label, or {@code null}
     */
    private void observeColumn(
            ArrayProfile profile,
            ColumnAccumulator accumulator,
            String key,
            Object value,
            long rowIndex,
            boolean withinSample,
            String rowLabel) {
        if (value == null) {
            return;
        }
        accumulator.observeSampleMembership(withinSample);
        if (value instanceof Number number) {
            observeNumericColumn(profile, accumulator, key, number, rowLabel);
        } else if (value instanceof String text) {
            observeStringColumn(accumulator, key, text);
        } else if (value instanceof List<?> list) {
            observeListColumn(accumulator, list);
        }
    }

    /**
     * Observes one numeric column value, capturing numeric stats and epoch-timestamp signals.
     *
     * @param profile the per-array profile
     * @param accumulator the column's accumulator
     * @param key the column name
     * @param number the observed numeric value
     * @param rowLabel the row's label, or {@code null}
     */
    private void observeNumericColumn(
            ArrayProfile profile,
            ColumnAccumulator accumulator,
            String key,
            Number number,
            String rowLabel) {
        accumulator.observeNumeric(number.doubleValue(), rowLabel);
        if (TimestampParser.isTimeName(TimestampParser.normalizeName(key))) {
            TimestampParser.ParsedTimestamp parsed = TimestampParser.parseNumber(number);
            if (parsed != null) {
                accumulator.observeTimestamp(parsed.epochMs(), false, String.valueOf(number));
            }
        }
    }

    /**
     * Observes one string column value, capturing timestamp signals when the column is time-like.
     *
     * @param accumulator the column's accumulator
     * @param key the column name
     * @param text the observed string value
     */
    private void observeStringColumn(ColumnAccumulator accumulator, String key, String text) {
        if (!TimestampParser.isTimeName(TimestampParser.normalizeName(key))) {
            return;
        }
        TimestampParser.ParsedTimestamp parsed = TimestampParser.parseText(text);
        if (parsed != null) {
            accumulator.observeTimestamp(parsed.epochMs(), parsed.dateOnly(), text);
        }
    }

    /**
     * Observes one list column value, counting inline numeric-array (sparkline) rows.
     *
     * @param accumulator the column's accumulator
     * @param list the observed list value
     */
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
