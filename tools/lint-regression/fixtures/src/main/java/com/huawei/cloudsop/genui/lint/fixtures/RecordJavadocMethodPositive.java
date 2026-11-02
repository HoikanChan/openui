/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.lint.fixtures;

/**
 * Record method Javadoc fixture with missing tags.
 *
 * @param name the name
 * @since 2026
 */
public record RecordJavadocMethodPositive(String name) {
    /**
     * Describes the record.
     */
    public String describe() {
        return name;
    }

    /**
     * Creates an instance.
     */
    public RecordJavadocMethodPositive(String name, int ignored) {
        this(name);
    }
}
