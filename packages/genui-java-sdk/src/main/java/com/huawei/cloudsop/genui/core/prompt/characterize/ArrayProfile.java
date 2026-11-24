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
 * Per-array profiling state accumulated in a single pass alongside {@link ShapeWalker}'s Pass B.
 *
 * <p>Holds the column accumulators and row count of one scanned object array (or the pair-scan
 * state of one tuple array). Cross-column observations such as group-timestamp alignment are
 * derived from the column accumulators after the scan completes; nothing here outlives a single
 * array's scan.
 *
 * @since 2026
 */
final class ArrayProfile {
    /** The dot-joined path of this array from the payload root (for example {@code data.rows}). */
    private final String arrayPath;

    /** Ordered column accumulators keyed by column name. */
    private final LinkedHashMap<String, ColumnAccumulator> columns = new LinkedHashMap<>();

    /** Number of rows scanned in this array. */
    private long rowCount;

    /** Whether every row scanned so far was a {@link Map}. */
    private boolean allObjectRows = true;

    /** Whether every row scanned so far was a 2-element list with a numeric second element. */
    private boolean allTupleRows = true;

    /** Distinct X values seen in tuple rows (first element), in first-seen order. */
    private final Set<Object> tupleXValues = new LinkedHashSet<>();

    /** Whether every tuple X value seen so far is numeric. */
    private boolean tupleXNumeric = true;

    /**
     * Captured (time-column, group-column) timestamp series, keyed by {@link #pairKey(String,
     * String)}. Each entry maps a group value to its observed (epochMs, rowIndex) pairs, in row
     * order. Captured during the single scan so the alignment check needs no second walk.
     */
    private final Map<String, Map<String, List<long[]>>> pairTimestamps = new LinkedHashMap<>();

    /**
     * Returns the canonical map key for a (time-column, group-column) pair.
     *
     * @param timeField the time column name
     * @param groupField the group column name
     * @return the collision-free pair key
     */
    static String pairKey(String timeField, String groupField) {
        return timeField + '\0' + groupField;
    }

    ArrayProfile(String arrayPath) {
        this.arrayPath = arrayPath;
    }

    String arrayPath() {
        return arrayPath;
    }

    LinkedHashMap<String, ColumnAccumulator> columns() {
        return columns;
    }

    long rowCount() {
        return rowCount;
    }

    void incrementRowCount() {
        rowCount++;
    }

    boolean allObjectRows() {
        return allObjectRows;
    }

    void markNonObjectRow() {
        allObjectRows = false;
    }

    boolean allTupleRows() {
        return allTupleRows;
    }

    void markNonTupleRow() {
        allTupleRows = false;
    }

    Set<Object> tupleXValues() {
        return tupleXValues;
    }

    boolean tupleXNumeric() {
        return tupleXNumeric;
    }

    void markTupleXNonNumeric() {
        tupleXNumeric = false;
    }

    Map<String, Map<String, List<long[]>>> pairTimestamps() {
        return pairTimestamps;
    }

    /**
     * Records one (groupValue, epochMs, rowIndex) observation for a candidate (time, group) column
     * pair, used by the partitioned-time-series alignment check.
     *
     * @param timeField the time column name
     * @param groupField the group column name
     * @param groupValue the group key as a string
     * @param epochMs the observed timestamp in epoch milliseconds
     * @param rowIndex the zero-based row index, retained to verify per-group monotonicity
     */
    void observePairTimestamp(
            String timeField, String groupField, String groupValue, long epochMs, long rowIndex) {
        String pairKey = pairKey(timeField, groupField);
        pairTimestamps
                .computeIfAbsent(pairKey, key -> new LinkedHashMap<>())
                .computeIfAbsent(groupValue, key -> new ArrayList<>())
                .add(new long[] {epochMs, rowIndex});
    }
}
