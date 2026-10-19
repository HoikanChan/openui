/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.huawei.cloudsop.genui.core.llm;

/** The DSL session outcome, retained even when independent Markdown generation succeeds. */
public enum GenerationEndReason {
    CHECK_PASSED, VALIDATION_FAILED, UNCHECKED, TIMEOUT, MODEL_REQUEST_FAILED
}
