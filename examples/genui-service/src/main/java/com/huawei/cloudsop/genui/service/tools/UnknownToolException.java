/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.service.tools;

public class UnknownToolException extends RuntimeException {
    public UnknownToolException(String toolName) {
        super("No executor registered for tool: " + toolName);
    }
}
