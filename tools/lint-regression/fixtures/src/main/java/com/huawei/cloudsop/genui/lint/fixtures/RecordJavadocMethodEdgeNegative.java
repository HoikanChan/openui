/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.lint.fixtures;

/**
 * Record Javadoc edge-case fixture with complete tags.
 *
 * @param value the value
 * @since 2026
 */
public record RecordJavadocMethodEdgeNegative(int value) {
    /**
     * Reads the value.
     *
     * @param a ignored input
     * @return the value
     * @throws java.io.IOException never
     */
    public int read(int a) throws java.io.IOException {
        if (value > 1) {
            throw new java.io.IOException();
        }
        return value;
    }
}
