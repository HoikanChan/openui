/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation;

import java.util.Objects;

/**
 * GenUI 生成流水线的顶层校验配置。
 *
 * <p>
 * 仅暴露两个策略维度：<em>何时</em> 校验（{@link ValidationConfigMode}）以及<em>失败时如何处理</em>
 * （{@link RepairPolicyKind}）。请使用预设工厂方法而非直接构造。
 *
 * <pre>
 * GenUiValidationConfig cfg = GenUiValidationConfig.finalOnly(); // 默认配置
 * GenUiValidationConfig cfg = GenUiValidationConfig.streamingGateWithReask();
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
     * 紧凑构造方法，校验各字段非空。
     */
    public GenUiValidationConfig {
        Objects.requireNonNull(validationMode, "validationMode");
        Objects.requireNonNull(repairPolicy, "repairPolicy");
    }

    // -----------------------------------------------------------------------
    // Presets
    // -----------------------------------------------------------------------

    /**
     * 生成完成后再校验，不做修复；这是默认配置。
     *
     * @return 预设配置实例
     */
    public static GenUiValidationConfig finalOnly() {
        return new GenUiValidationConfig(ValidationConfigMode.FINAL_ONLY, RepairPolicyKind.NONE);
    }

    /**
     * 在流式检查点及最终结果处校验，不做修复。
     *
     * @return 预设配置实例
     */
    public static GenUiValidationConfig streamingGate() {
        return new GenUiValidationConfig(ValidationConfigMode.STREAMING_GATE, RepairPolicyKind.NONE);
    }

    /**
     * 在流式检查点处校验；失败时中止并重新请求（re-ask）。
     *
     * @return 预设配置实例
     */
    public static GenUiValidationConfig streamingGateWithReask() {
        return new GenUiValidationConfig(ValidationConfigMode.STREAMING_GATE, RepairPolicyKind.FAIL_FAST_REASK);
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
