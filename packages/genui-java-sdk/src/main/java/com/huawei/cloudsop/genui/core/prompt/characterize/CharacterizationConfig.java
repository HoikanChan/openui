/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.prompt.characterize;

/**
 * Immutable tuning knobs for the {@link ShapeWalker} / {@link Characterizer} pipeline.
 *
 * <p>
 * All fields are defaulted; use {@link #defaults()} for the out-of-the-box configuration or {@link #builder()} to
 * override a subset of fields.
 *
 * @since 2026
 */
public record CharacterizationConfig(boolean enabled, int triggerBytes, int sampleRows, int maxStringLen,
        int enumMaxDistinct, double enumMaxRatio, int deepScanLimit) {

    private static final boolean DEFAULT_ENABLED = true;
    private static final int DEFAULT_TRIGGER_BYTES = 2048;
    private static final int DEFAULT_SAMPLE_ROWS = 3;
    private static final int DEFAULT_MAX_STRING_LEN = 80;
    private static final int DEFAULT_ENUM_MAX_DISTINCT = 50;
    private static final double DEFAULT_ENUM_MAX_RATIO = 0.5;
    private static final int DEFAULT_DEEP_SCAN_LIMIT = 10000;

    public static CharacterizationConfig defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean enabled = DEFAULT_ENABLED;
        private int triggerBytes = DEFAULT_TRIGGER_BYTES;
        private int sampleRows = DEFAULT_SAMPLE_ROWS;
        private int maxStringLen = DEFAULT_MAX_STRING_LEN;
        private int enumMaxDistinct = DEFAULT_ENUM_MAX_DISTINCT;
        private double enumMaxRatio = DEFAULT_ENUM_MAX_RATIO;
        private int deepScanLimit = DEFAULT_DEEP_SCAN_LIMIT;

        private Builder() {
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder triggerBytes(int triggerBytes) {
            this.triggerBytes = triggerBytes;
            return this;
        }

        public Builder sampleRows(int sampleRows) {
            this.sampleRows = sampleRows;
            return this;
        }

        public Builder maxStringLen(int maxStringLen) {
            this.maxStringLen = maxStringLen;
            return this;
        }

        public Builder enumMaxDistinct(int enumMaxDistinct) {
            this.enumMaxDistinct = enumMaxDistinct;
            return this;
        }

        public Builder enumMaxRatio(double enumMaxRatio) {
            this.enumMaxRatio = enumMaxRatio;
            return this;
        }

        public Builder deepScanLimit(int deepScanLimit) {
            this.deepScanLimit = deepScanLimit;
            return this;
        }

        public CharacterizationConfig build() {
            return new CharacterizationConfig(enabled, triggerBytes, sampleRows, maxStringLen, enumMaxDistinct,
                    enumMaxRatio, deepScanLimit);
        }
    }
}
