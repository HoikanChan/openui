/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.huawei.cloudsop.genui.core.llm;

/**
 * DSL 会话的最终结束原因。即使独立 Markdown 降级成功,DSL 会话的原因仍保留在结果中。
 *
 * @since 2026
 */
public enum GenerationEndReason {
    /** 轮末校验通过。 */
    CHECK_PASSED,
    /** 轮末校验失败,且修复未能得到有效程序。 */
    VALIDATION_FAILED,
    /** 未执行校验,例如校验关闭或未走到轮末。 */
    UNCHECKED,
    /** 20 秒预算耗尽。 */
    TIMEOUT,
    /** 模型请求或流式传输失败。 */
    MODEL_REQUEST_FAILED
}
