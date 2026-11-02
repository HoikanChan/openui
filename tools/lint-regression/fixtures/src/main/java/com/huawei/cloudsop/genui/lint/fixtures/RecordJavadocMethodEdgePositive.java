/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.lint.fixtures;

/**
 * Record Javadoc edge-case fixture.
 *
 * @param value the value
 * @since 2026
 */
public record RecordJavadocMethodEdgePositive(int value) {
    /**
     * Uses empty tags.
     *
     * @param
     * @return
     * @throws
     */
    public int empty(int a) throws java.io.IOException {
        return a;
    }

    /**
     * Throws the same qualified exception twice.
     *
     * @return the value
     */
    public int duplicateThrows() throws java.io.IOException {
        if (value > 1) {
            throw new java.io.IOException();
        }
        if (value > 1) {
            throw new java.io.IOException();
        }
        return value;
    }

    /**
     * Throws inside a caught try block.
     *
     * @return the value
     */
    public int caught() {
        try {
            throw new IllegalStateException();
        } catch (RuntimeException ex) {
            return value;
        }
    }

    /**
     * Illegal inherit-doc use.
     *
     * {@inheritDoc}
     */
    public static int stat(int a) {
        return a;
    }
}
