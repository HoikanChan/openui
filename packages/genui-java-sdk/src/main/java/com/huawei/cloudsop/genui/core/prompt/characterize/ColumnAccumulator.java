/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.prompt.characterize;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Per-column accumulation state used while feature-scanning an object-array (table) during {@link
 * ShapeWalker}'s Pass B.
 *
 * <p>Tracks, across all observed rows: the set of {@link ScalarType}s seen, an order-preserving
 * capacity-bounded distinct-string frequency map (stops growing once it exceeds {@code
 * enumMaxDistinct + 1}, at which point the column degrades to free-text {@code string}), the
 * occurrence count (rows where the key was present), and the null count (rows where the key was
 * present but the value was {@code null}). Missing-row tracking is exposed separately so the caller
 * can derive {@code optional}.
 *
 * <p>Beyond the shape-inference counters, the accumulator also records lightweight scalar
 * statistics used by the profiler's deeper layers: exact string frequencies for non-overflowed
 * columns, numeric min/max/sum with optional extreme-row labels, timestamp min/max and ordering
 * signals, sample-prefix non-null counts for sparse-sample protection, and inline numeric-array
 * (sparkline) shape counts. No percentiles or continuous bucketing are kept.
 *
 * @since 2026
 */
final class ColumnAccumulator {
    /** Sentinel for "no extreme observed yet" in min/max numeric trackers. */
    private static final double UNSET_MIN = Double.POSITIVE_INFINITY;

    /** Sentinel for "no extreme observed yet" in min/max numeric trackers. */
    private static final double UNSET_MAX = Double.NEGATIVE_INFINITY;

    private final int distinctCap;
    private final LinkedHashMap<String, Long> distinctStringCounts = new LinkedHashMap<>();
    private final EnumSet<ScalarType> scalarTypesSeen = EnumSet.noneOf(ScalarType.class);
    private boolean distinctOverflowed;
    private long occurrences;
    private long nullCount;
    private long missingCount;
    private long sampleNonNullCount;
    private ColumnAccumulator childAccumulator;
    private ShapeNode childDeepShape;
    private long deepScanned;

    // Numeric statistics (null-extremes represented by the sentinels above).
    private long numericCount;
    private double minNumeric = UNSET_MIN;
    private double maxNumeric = UNSET_MAX;
    private double sumNumeric;
    private String minRowLabel;
    private String maxRowLabel;

    // Timestamp statistics (epoch-millis extremes).
    private long timeCount;
    private long minEpochMs = Long.MAX_VALUE;
    private long maxEpochMs = Long.MIN_VALUE;
    private String minTimeRaw;
    private String maxTimeRaw;
    private boolean ascending = true;
    private boolean descending = true;
    private boolean allDateOnly = true;
    private long lastEpochMs = Long.MIN_VALUE;

    // Inline numeric-array (sparkline) statistics.
    private long numericArrayCount;
    private int maxNumericArrayLen;

    ColumnAccumulator(int distinctCap) {
        this.distinctCap = distinctCap;
    }

    /**
     * Records a row where the key was present with a non-null string value.
     *
     * @param value the observed string value
     */
    void observePresent(String value) {
        occurrences++;
        if (!distinctOverflowed) {
            if (distinctStringCounts.size() >= distinctCap + 1
                    && !distinctStringCounts.containsKey(value)) {
                distinctOverflowed = true;
            } else {
                distinctStringCounts.merge(value, 1L, Long::sum);
            }
        }
    }

    /**
     * Records a row where the key was present with a non-null, non-string scalar value.
     *
     * @param type the observed scalar type
     */
    void observeScalarType(ScalarType type) {
        occurrences++;
        scalarTypesSeen.add(type);
    }

    /** Records a row where the key was present but the value was {@code null}. */
    void observeNull() {
        occurrences++;
        nullCount++;
    }

    /** Records a row where the key was absent entirely. */
    void observeMissing() {
        missingCount++;
    }

    /**
     * Records the type seen for a non-string, non-null scalar without consuming the type set.
     *
     * @param type the observed scalar type
     */
    void observeType(ScalarType type) {
        scalarTypesSeen.add(type);
    }

    /**
     * Records a numeric observation for profiling statistics, optionally carrying the row's label
     * so extreme entities are captured in the same pass.
     *
     * @param value the observed numeric value
     * @param rowLabel an optional label for the row (may be {@code null})
     */
    void observeNumeric(double value, String rowLabel) {
        numericCount++;
        sumNumeric += value;
        if (value < minNumeric) {
            minNumeric = value;
            minRowLabel = rowLabel;
        }
        if (value > maxNumeric) {
            maxNumeric = value;
            maxRowLabel = rowLabel;
        }
    }

    /**
     * Records a timestamp observation for profiling statistics, tracking extremes and ordering.
     *
     * @param epochMs the observed instant in epoch milliseconds
     * @param dateOnly whether the source value was a date-only string
     * @param raw the raw source text, retained for min/max reporting
     */
    void observeTimestamp(long epochMs, boolean dateOnly, String raw) {
        timeCount++;
        if (!dateOnly) {
            allDateOnly = false;
        }
        if (epochMs < minEpochMs) {
            minEpochMs = epochMs;
            minTimeRaw = raw;
        }
        if (epochMs > maxEpochMs) {
            maxEpochMs = epochMs;
            maxTimeRaw = raw;
        }
        if (lastEpochMs != Long.MIN_VALUE) {
            if (epochMs < lastEpochMs) {
                ascending = false;
            }
            if (epochMs > lastEpochMs) {
                descending = false;
            }
        }
        lastEpochMs = epochMs;
    }

    /**
     * Records an inline numeric-array (sparkline) observation.
     *
     * @param length the number of points in the observed numeric array
     */
    void observeNumericArray(int length) {
        numericArrayCount++;
        if (length > maxNumericArrayLen) {
            maxNumericArrayLen = length;
        }
    }

    /**
     * Records whether this row's non-null value falls inside the sample prefix.
     *
     * @param withinSample {@code true} when this observation came from the first {@code sampleRows}
     *     rows
     */
    void observeSampleMembership(boolean withinSample) {
        if (withinSample) {
            sampleNonNullCount++;
        }
    }

    long occurrences() {
        return occurrences;
    }

    long nullCount() {
        return nullCount;
    }

    long missingCount() {
        return missingCount;
    }

    long sampleNonNullCount() {
        return sampleNonNullCount;
    }

    boolean distinctOverflowed() {
        return distinctOverflowed;
    }

    /**
     * Returns the distinct string values seen, preserving first-observed order.
     *
     * @return the distinct string key set
     */
    Set<String> distinctStrings() {
        return distinctStringCounts.keySet();
    }

    /**
     * Returns the exact order-preserving frequency map of distinct string values.
     *
     * <p>Only meaningful while the column has not overflowed its distinct cap; once overflowed the
     * map is frozen and no longer reflects true frequencies.
     *
     * @return the distinct string-to-count map
     */
    Map<String, Long> distinctStringCounts() {
        return distinctStringCounts;
    }

    EnumSet<ScalarType> scalarTypesSeen() {
        return scalarTypesSeen;
    }

    long numericCount() {
        return numericCount;
    }

    double minNumeric() {
        return minNumeric;
    }

    double maxNumeric() {
        return maxNumeric;
    }

    double sumNumeric() {
        return sumNumeric;
    }

    String minRowLabel() {
        return minRowLabel;
    }

    String maxRowLabel() {
        return maxRowLabel;
    }

    long timeCount() {
        return timeCount;
    }

    long minEpochMs() {
        return minEpochMs;
    }

    long maxEpochMs() {
        return maxEpochMs;
    }

    String minTimeRaw() {
        return minTimeRaw;
    }

    String maxTimeRaw() {
        return maxTimeRaw;
    }

    boolean ascending() {
        return ascending;
    }

    boolean descending() {
        return descending;
    }

    boolean allDateOnly() {
        return allDateOnly;
    }

    long numericArrayCount() {
        return numericArrayCount;
    }

    int maxNumericArrayLen() {
        return maxNumericArrayLen;
    }

    /**
     * Lazily creates and returns the nested accumulator for an object/array column.
     *
     * @return the nested accumulator, created on first use
     */
    ColumnAccumulator childAccumulator() {
        if (childAccumulator == null) {
            childAccumulator = new ColumnAccumulator(distinctCap);
        }
        return childAccumulator;
    }

    ShapeNode childDeepShape() {
        return childDeepShape;
    }

    void setChildDeepShape(ShapeNode shape) {
        this.childDeepShape = shape;
    }

    long deepScanned() {
        return deepScanned;
    }

    void incrementDeepScanned() {
        deepScanned++;
    }
}
