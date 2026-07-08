/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation;

/**
 * {@link ValidationIssue} 的严重程度。只有 {@link #ERROR} 被视为阻塞级别（即导致 {@link ValidationStatus#INVALID}）。
 *
 * @since 2026
 */
public enum ValidationSeverity {
    /** 阻塞级别：导致校验结果为 {@link ValidationStatus#INVALID}。 */
    ERROR,
    /** 非阻塞警告。 */
    WARNING,
    /** 提示信息。 */
    INFO
}
