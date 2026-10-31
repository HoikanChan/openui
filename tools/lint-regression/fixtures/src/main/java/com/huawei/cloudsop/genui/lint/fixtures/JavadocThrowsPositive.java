/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.lint.fixtures;

import java.io.IOException;

/**
 * Missing throws tag fixture.
 *
 * @since 2026
 */
public class JavadocThrowsPositive {
    /**
     * Reads a value.
     *
     * @return the value
     */
    public int read() throws IOException {
        return 0;
    }
}
