/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.lint.fixtures;

/**
 * Record method Javadoc fixture with all tags.
 *
 * @param name the name
 * @since 2026
 */
public record RecordJavadocMethodNegative(String name) {
    /**
     * Describes the record.
     *
     * @return the name
     */
    public String describe() {
        return name;
    }

    /**
     * Creates an instance.
     *
     * @param name the name
     * @param ignored ignored value
     */
    public RecordJavadocMethodNegative(String name, int ignored) {
        this(name);
    }
}
