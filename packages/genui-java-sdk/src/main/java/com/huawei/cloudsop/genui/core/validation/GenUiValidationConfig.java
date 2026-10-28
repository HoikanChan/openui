/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation;

import java.util.Objects;

/**
 * GenUI 流式生成流水线的顶层校验配置。同步生成不校验、不修复,不读取此配置。
 *
 * <p>
 * 暴露两个策略维度:<em>校验策略</em>({@link ValidationConfigMode})与增量修复的尝试次数上限(
 * {@code maxIncrementalAttempts})。请使用预设工厂方法而非直接构造。
 *
 * <p>
 * 增量修复默认只受会话截止时间限制({@code maxIncrementalAttempts = 0});#58 要求不因重复错误、空补丁或无进展 提前退出。需要硬性上限时用
 * {@link #incremental(int)} 设置增量修复次数。上限只在 {@link ValidationConfigMode#INCREMENTAL} 下生效。
 *
 * <pre>
 * GenUiValidationConfig cfg = GenUiValidationConfig.incremental(); // 默认配置(#58)
 * GenUiValidationConfig capped = GenUiValidationConfig.incremental(3); // 最多 3 轮增量修复
 * </pre>
 *
 * @param validationMode
 *            校验策略
 * @param maxIncrementalAttempts
 *            增量修复轮次上限;{@code 0} 表示只受会话截止时间限制
 *
 * @since 2026
 */
public record GenUiValidationConfig(ValidationConfigMode validationMode, int maxIncrementalAttempts) {

    /** 显式尝试次数上限;重复错误与空补丁本身不会终止修复,只有到达该上限或截止时间才结束。 */
    public static GenUiValidationConfig incremental(int maxAttempts) {
        return new GenUiValidationConfig(ValidationConfigMode.INCREMENTAL, maxAttempts);
    }

    /**
     * 紧凑构造方法,校验各字段非空且上限非负。
     */
    public GenUiValidationConfig {
        Objects.requireNonNull(validationMode, "validationMode");
        if (maxIncrementalAttempts < 0) {
            throw new IllegalArgumentException("maxIncrementalAttempts must be nonnegative");
        }
    }

    // -----------------------------------------------------------------------
    // Presets
    // -----------------------------------------------------------------------

    /**
     * 生成实时转发;轮末对当前有效程序统一校验,失败时由模型输出覆盖/新增语句做增量修复,直到通过或会话截止(#58)。 这是正常生成的默认配置。
     *
     * @return 预设配置实例
     */
    public static GenUiValidationConfig incremental() {
        return new GenUiValidationConfig(ValidationConfigMode.INCREMENTAL, 0);
    }

    /**
     * 生成实时转发;轮末对当前有效程序统一校验,不做修复;校验失败直接暴露给调用方。
     *
     * @return 预设配置实例
     */
    public static GenUiValidationConfig finalOnly() {
        return new GenUiValidationConfig(ValidationConfigMode.FINAL_ONLY, 0);
    }

    /**
     * 关闭所有校验。
     *
     * @return 预设配置实例
     */
    public static GenUiValidationConfig disabled() {
        return new GenUiValidationConfig(ValidationConfigMode.DISABLED, 0);
    }
}
