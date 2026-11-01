/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation;

/**
 * Streaming validation and repair configuration. Synchronous generation is never validated.
 *
 * @param validationEnabled whether streaming validation and repair are enabled
 * @param maxIncrementalAttempts repair attempt limit; zero uses the deadline
 * @since 2026
 */
public record GenUiValidationConfig(boolean validationEnabled, int maxIncrementalAttempts) {
    /** A zero attempt limit uses the generation deadline instead of a fixed limit. */
    public GenUiValidationConfig {
        if (maxIncrementalAttempts < 0) {
            throw new IllegalArgumentException("maxIncrementalAttempts must be nonnegative");
        }
    }

    /** Enable validation and repair with the default time budget. */
    public static GenUiValidationConfig enabled() {
        return new GenUiValidationConfig(true, 0);
    }

    /** Enable validation and repair with a fixed attempt limit. */
    public static GenUiValidationConfig enabled(int maxAttempts) {
        return new GenUiValidationConfig(true, maxAttempts);
    }

    /** Disable both validation and repair. */
    public static GenUiValidationConfig disabled() {
        return new GenUiValidationConfig(false, 0);
    }
}
