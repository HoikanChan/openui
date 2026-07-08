/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation;

/**
 * DSL 校验器顶层 API。
 *
 * <p>
 * 接收一个 {@link ValidationRequest}，返回一个 {@link ValidationResult}，其 {@link ValidationStatus} 由实现方计算：
 * <ul>
 * <li>{@link ValidationStatus#INVALID} —— 存在任意 {@code ERROR} 级别的问题。</li>
 * <li>{@link ValidationStatus#PARTIAL} —— 仅在 {@link ValidationMode#STREAMING} 下，存在非阻塞（非 ERROR）问题且无阻塞
 * ERROR 时出现。</li>
 * <li>{@link ValidationStatus#VALID} —— 无问题，或仅有提示性/非阻塞问题。</li>
 * </ul>
 *
 * <p>
 * 除非另有说明，实现必须是无状态且线程安全的。
 *
 * @since 2026
 */
public interface OpenuiLangValidator {

    /**
     * 校验 {@code request} 中的 DSL 并返回结果。
     *
     * @param request
     *            校验输入，不允许为 {@code null}
     * @return 校验结果，不会为 {@code null}
     */
    ValidationResult validate(ValidationRequest request);
}
