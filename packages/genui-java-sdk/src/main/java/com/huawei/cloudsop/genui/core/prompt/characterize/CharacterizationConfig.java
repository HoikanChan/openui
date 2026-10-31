/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.prompt.characterize;

/**
 * Immutable tuning knobs for the {@link ShapeWalker} / {@link Characterizer} pipeline.
 *
 * <p>All fields are defaulted; use {@link #defaults()} for the out-of-the-box configuration or
 * {@link #builder()} to override a subset of fields.
 *
 * @param enabled whether the characterization gate runs at all; {@code false} forces pass-through
 * @param triggerBytes data whose compact JSON form is at most this many characters bypasses
 *     characterization
 * @param sampleRows maximum number of leading array elements kept in the sample
 * @param maxStringLen maximum characters retained per string before an ellipsis is appended
 * @param enumMaxDistinct maximum distinct values that still qualify a string column as an enum
 * @param enumMaxRatio maximum distinct-to-total ratio that still qualifies a string column as an
 *     enum
 * @param deepScanLimit maximum number of nested object/array values deep-scanned per table column
 * @since 2026
 */
public record CharacterizationConfig(
        boolean enabled,
        int triggerBytes,
        int sampleRows,
        int maxStringLen,
        int enumMaxDistinct,
        double enumMaxRatio,
        int deepScanLimit) {

    private static final boolean DEFAULT_ENABLED = true;
    private static final int DEFAULT_TRIGGER_BYTES = 2048;
    private static final int DEFAULT_SAMPLE_ROWS = 3;
    private static final int DEFAULT_MAX_STRING_LEN = 80;
    private static final int DEFAULT_ENUM_MAX_DISTINCT = 50;
    private static final double DEFAULT_ENUM_MAX_RATIO = 0.5;
    private static final int DEFAULT_DEEP_SCAN_LIMIT = 10000;

    /**
     * Returns the out-of-the-box configuration using every field's default value.
     *
     * @return a configuration built from the defaults
     */
    public static CharacterizationConfig defaults() {
        return builder().build();
    }

    /**
     * Returns a builder initialized with all default values.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Mutable builder for {@link CharacterizationConfig}; every field starts at its default value.
     *
     * @since 2026
     */
    public static final class Builder {
        private boolean enabled = DEFAULT_ENABLED;
        private int triggerBytes = DEFAULT_TRIGGER_BYTES;
        private int sampleRows = DEFAULT_SAMPLE_ROWS;
        private int maxStringLen = DEFAULT_MAX_STRING_LEN;
        private int enumMaxDistinct = DEFAULT_ENUM_MAX_DISTINCT;
        private double enumMaxRatio = DEFAULT_ENUM_MAX_RATIO;
        private int deepScanLimit = DEFAULT_DEEP_SCAN_LIMIT;

        private Builder() {}

        /**
         * Sets whether characterization runs; {@code false} forces the pass-through path.
         *
         * @param enabled {@code true} to characterize data models above the trigger size
         * @return this builder
         */
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        /**
         * Sets the compact-JSON size above which characterization kicks in.
         *
         * @param triggerBytes size threshold in characters
         * @return this builder
         */
        public Builder triggerBytes(int triggerBytes) {
            this.triggerBytes = triggerBytes;
            return this;
        }

        /**
         * Sets how many leading array elements are kept in each sample.
         *
         * @param sampleRows maximum sample size per array
         * @return this builder
         */
        public Builder sampleRows(int sampleRows) {
            this.sampleRows = sampleRows;
            return this;
        }

        /**
         * Sets the maximum characters retained per string before an ellipsis is appended.
         *
         * @param maxStringLen per-string character limit
         * @return this builder
         */
        public Builder maxStringLen(int maxStringLen) {
            this.maxStringLen = maxStringLen;
            return this;
        }

        /**
         * Sets the maximum distinct count for inferring an enum domain.
         *
         * @param enumMaxDistinct distinct-value ceiling
         * @return this builder
         */
        public Builder enumMaxDistinct(int enumMaxDistinct) {
            this.enumMaxDistinct = enumMaxDistinct;
            return this;
        }

        /**
         * Sets the maximum distinct-to-total ratio for inferring an enum domain.
         *
         * @param enumMaxRatio ratio ceiling in the range {@code [0, 1]}
         * @return this builder
         */
        public Builder enumMaxRatio(double enumMaxRatio) {
            this.enumMaxRatio = enumMaxRatio;
            return this;
        }

        /**
         * Sets how many nested object/array values are deep-scanned per table column.
         *
         * @param deepScanLimit per-column deep-scan budget
         * @return this builder
         */
        public Builder deepScanLimit(int deepScanLimit) {
            this.deepScanLimit = deepScanLimit;
            return this;
        }

        /**
         * Builds an immutable configuration from the current builder values.
         *
         * @return the configuration snapshot
         */
        public CharacterizationConfig build() {
            return new CharacterizationConfig(
                    enabled,
                    triggerBytes,
                    sampleRows,
                    maxStringLen,
                    enumMaxDistinct,
                    enumMaxRatio,
                    deepScanLimit);
        }
    }
}
