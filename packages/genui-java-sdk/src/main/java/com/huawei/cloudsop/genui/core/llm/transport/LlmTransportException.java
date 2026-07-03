/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.llm.transport;

public class LlmTransportException extends Exception {
    public LlmTransportException(String message) {
        super(message);
    }

    public LlmTransportException(String message, Throwable cause) {
        super(message, cause);
    }
}
