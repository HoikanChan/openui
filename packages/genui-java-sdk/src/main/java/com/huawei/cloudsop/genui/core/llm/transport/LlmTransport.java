/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.llm.transport;

import java.io.InputStream;

public interface LlmTransport {
    String post(String body) throws LlmTransportException;

    InputStream postStream(String body) throws LlmTransportException;
}
