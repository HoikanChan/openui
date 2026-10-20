/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation;

import java.util.Objects;

/**
 * GenUI 生成流水线的顶层校验配置。
 *
 * <p>
 * 仅暴露两个策略维度:<em>何时</em> 校验({@link ValidationConfigMode})以及<em>失败时如何处理</em> ({@link RepairPolicyKind})。请使用预设工厂方法而非直接构造。
 *
 * <pre>
 * GenUiValidationConfig cfg = GenUiValidationConfig.incremental(); // 默认配置(#58)
 * </pre>
 *
 * @param validationMode
 *            校验时机
 * @param repairPolicy
 *            失败时的修复策略
 *
 * @since 2026
 */
public record GenUiValidationConfig(ValidationConfigMode validationMode, RepairPolicyKind repairPolicy) {

    /**
     * 紧凑构造方法,校验各字段非空。
     */
    public GenUiValidationConfig {
        Objects.requireNonNull(validationMode, "validationMode");
        Objects.requireNonNull(repairPolicy, "repairPolicy");
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
        return new GenUiValidationConfig(ValidationConfigMode.FINAL_ONLY, RepairPolicyKind.INCREMENTAL_REPAIR);
    }

    /**
     * 生成实时转发;轮末对当前有效程序统一校验,不做修复;校验失败直接暴露给调用方。
     *
     * @return 预设配置实例
     */
    public static GenUiValidationConfig finalOnly() {
        return new GenUiValidationConfig(ValidationConfigMode.FINAL_ONLY, RepairPolicyKind.NONE);
    }

    /**
     * 关闭所有校验。
     *
     * @return 预设配置实例
     */
    public static GenUiValidationConfig disabled() {
        return new GenUiValidationConfig(ValidationConfigMode.DISABLED, RepairPolicyKind.NONE);
    }
}
