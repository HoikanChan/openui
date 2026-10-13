/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation;

/**
 * 告知校验器单次调用应采用的规则集。区别于控制校验触发时机的 {@link ValidationConfigMode}。
 *
 * @since 2026
 */
public enum ValidationMode {
    /** 校验完整的最终 DSL 字符串。 */
    FINAL,
    /** 校验进行中的流式 DSL 片段（规则较宽松）。 */
    STREAMING
}
