/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core;

/**
 * Unchecked exception thrown when SDK configuration is invalid or a JSON value has an unexpected
 * shape.
 *
 * @since 2026
 */
public class GenerationSdkException extends RuntimeException {
    /**
     * Creates an exception with a detail message.
     *
     * @param message the detail message describing the failure
     */
    public GenerationSdkException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a detail message and the underlying cause.
     *
     * @param message the detail message describing the failure
     * @param cause the lower-level error being wrapped
     */
    public GenerationSdkException(String message, Throwable cause) {
        super(message, cause);
    }
}
