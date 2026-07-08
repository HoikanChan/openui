/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation;

import java.util.List;

/**
 * 单次 DSL 校验调用的结果。
 *
 * <p>
 * 从校验逻辑构造结果时，请使用静态工厂方法（{@link #valid}、{@link #invalid}、{@link #partial}），而非规范构造方法。
 *
 * @param status
 *            校验状态
 * @param normalizedDsl
 *            归一化（去除多余空白/自动闭合）后的 DSL 文本，不可用时为 {@code null}
 * @param issues
 *            发现的全部问题；不会为 {@code null}
 * @param metadata
 *            校验元数据
 *
 * @since 2026
 */
public record ValidationResult(ValidationStatus status, String normalizedDsl, List<ValidationIssue> issues,
        ValidationMetadata metadata) {

    /**
     * 紧凑构造方法，将 {@code issues} 归一化为不可变列表。
     */
    public ValidationResult {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    /**
     * 判断校验结果是否为 {@link ValidationStatus#VALID}。
     *
     * @return 状态为 VALID 时返回 {@code true}
     */
    public boolean isValid() {
        return status == ValidationStatus.VALID;
    }

    /**
     * 判断是否存在阻塞级别的问题。
     *
     * <p>
     * 只要存在任意 {@link ValidationSeverity#ERROR} 级别的问题即返回 {@code true}；仅含警告/提示信息的部分结果不算“阻塞”。
     *
     * @return 存在阻塞问题时返回 {@code true}
     */
    public boolean hasBlockingIssues() {
        return issues.stream().anyMatch(i -> i.severity() == ValidationSeverity.ERROR);
    }

    // -----------------------------------------------------------------------
    // Static factories
    // -----------------------------------------------------------------------

    /**
     * 构建一个 {@link ValidationStatus#VALID} 结果。
     *
     * @param normalizedDsl
     *            归一化后的 DSL 文本
     * @param issues
     *            发现的问题列表
     * @param metadata
     *            校验元数据
     * @return 有效结果
     */
    public static ValidationResult valid(String normalizedDsl, List<ValidationIssue> issues,
            ValidationMetadata metadata) {
        return new ValidationResult(ValidationStatus.VALID, normalizedDsl, issues, metadata);
    }

    /**
     * 构建一个 {@link ValidationStatus#INVALID} 结果（不含归一化 DSL）。
     *
     * @param issues
     *            发现的问题列表
     * @param metadata
     *            校验元数据
     * @return 无效结果
     */
    public static ValidationResult invalid(List<ValidationIssue> issues, ValidationMetadata metadata) {
        return new ValidationResult(ValidationStatus.INVALID, null, issues, metadata);
    }

    /**
     * 构建一个 {@link ValidationStatus#PARTIAL} 结果（流式模式）。
     *
     * @param normalizedDsl
     *            归一化后的 DSL 文本
     * @param issues
     *            发现的问题列表
     * @param metadata
     *            校验元数据
     * @return 部分有效结果
     */
    public static ValidationResult partial(String normalizedDsl, List<ValidationIssue> issues,
            ValidationMetadata metadata) {
        return new ValidationResult(ValidationStatus.PARTIAL, normalizedDsl, issues, metadata);
    }
}
