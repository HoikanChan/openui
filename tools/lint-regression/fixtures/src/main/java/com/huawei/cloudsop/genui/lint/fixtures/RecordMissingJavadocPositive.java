/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.lint.fixtures;

/**
 * Record with a public method that has no Javadoc.
 *
 * @param name the name
 * @since 2026
 */
public record RecordMissingJavadocPositive(String name) {
    public String describe() {
        return name;
    }
}
