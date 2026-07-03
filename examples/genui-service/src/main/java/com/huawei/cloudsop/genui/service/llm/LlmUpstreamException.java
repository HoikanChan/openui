/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.service.llm;

/**
 * LLM 调用在产生首个 token 前的失败;由异常处理器映射为 502。
 *
 * @since 2026
 */
public class LlmUpstreamException extends RuntimeException {
    public LlmUpstreamException(String message) {
        super(message);
    }

    public LlmUpstreamException(String message, Throwable cause) {
        super(message, cause);
    }
}
