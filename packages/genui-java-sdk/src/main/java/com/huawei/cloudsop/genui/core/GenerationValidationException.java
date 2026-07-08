/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core;

import com.huawei.cloudsop.genui.core.validation.ValidationResult;

import java.util.Objects;

/**
 * 当 {@code GenUiGenerator.generate()} 最终 DSL 校验失败（INVALID 状态）且当前修复策略不再尝试恢复时抛出。
 *
 * <p>
 * 调用方可以对该子类型做模式匹配以获取具体校验问题：
 *
 * <pre>{@code
 * try {
 *     generator.generate(request);
 * } catch (GenerationValidationException e) {
 *     ValidationResult result = e.validationResult();
 *     // 检查 result.issues()、result.status() 等
 * }
 * }</pre>
 *
 * @since 2026
 */
public final class GenerationValidationException extends GenerationSdkException {

    private final ValidationResult validationResult;

    /**
     * 构造异常实例。
     *
     * @param message
     *            异常描述信息
     * @param validationResult
     *            完整的校验结果，不允许为 {@code null}
     */
    public GenerationValidationException(String message, ValidationResult validationResult) {
        super(message);
        this.validationResult = Objects.requireNonNull(validationResult, "validationResult");
    }

    /**
     * 获取完整的校验结果，包含问题列表与状态。
     *
     * @return 校验结果，不会为 {@code null}
     */
    public ValidationResult validationResult() {
        return validationResult;
    }
}
