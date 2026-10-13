/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.repair;

import com.huawei.cloudsop.genui.core.validation.RepairPolicyKind;

import java.time.Duration;
import java.util.Objects;

/**
 * 完整的修复策略值对象（设计决策 #8 / #9）。将 {@code GenUiValidationConfig} 上粗粒度的
 * {@link RepairPolicyKind} 与高级调优参数 —— 重试次数上限与单次请求超时 —— 包装在一起；这些高级参数刻意不放在
 * 顶层公共配置中。
 *
 * <p>
 * 生成器通过 {@link #from(RepairPolicyKind)} 将 {@code config.repairPolicy()}（一个 KIND）映射为
 * {@code RepairPolicy}，并应用合理的默认值（{@code maxAttempts = 1}）。
 *
 * @param kind
 *            粗粒度策略（NONE / FINAL_REPAIR / FAIL_FAST_REASK）
 * @param maxAttempts
 *            最大修复尝试次数（同步场景为整份 DSL 修复次数，流式场景为重新请求轮数），必须 {@code >= 1}，
 *            默认 {@code 1}
 * @param timeout
 *            单次完整修复请求的时间预算；{@code Duration.ZERO} 表示“无显式超时”（依赖传输层自身的超时）
 * @param statementRepairTimeout
 *            单轮流式重新请求的时间预算；{@code Duration.ZERO} 表示“无显式超时”
 *
 * @since 2026
 */
public record RepairPolicy(RepairPolicyKind kind, int maxAttempts, Duration timeout, Duration statementRepairTimeout) {

    /** 默认尝试次数预算（对应决策 #8）：单次修复尝试。 */
    public static final int DEFAULT_MAX_ATTEMPTS = 1;

    /**
     * 紧凑构造方法，校验参数并将超时字段归一化为非空值。
     */
    public RepairPolicy {
        Objects.requireNonNull(kind, "kind");
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1, got: " + maxAttempts);
        }
        timeout = timeout == null ? Duration.ZERO : timeout;
        statementRepairTimeout = statementRepairTimeout == null ? Duration.ZERO : statementRepairTimeout;
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must not be negative: " + timeout);
        }
        if (statementRepairTimeout.isNegative()) {
            throw new IllegalArgumentException(
                    "statementRepairTimeout must not be negative: " + statementRepairTimeout);
        }
    }

    /**
     * 将顶层配置上的粗粒度 {@link RepairPolicyKind} 映射为带默认高级参数（{@code maxAttempts = 1}、
     * 无显式超时）的 {@code RepairPolicy}。
     *
     * @param kind
     *            粗粒度策略
     * @return 应用默认高级参数后的修复策略
     */
    public static RepairPolicy from(RepairPolicyKind kind) {
        return new RepairPolicy(kind, DEFAULT_MAX_ATTEMPTS, Duration.ZERO, Duration.ZERO);
    }

    /**
     * 便捷工厂方法：指定策略与自定义尝试次数（超时使用默认值）。
     *
     * @param kind
     *            粗粒度策略
     * @param maxAttempts
     *            最大修复尝试次数
     * @return 修复策略实例
     */
    public static RepairPolicy of(RepairPolicyKind kind, int maxAttempts) {
        return new RepairPolicy(kind, maxAttempts, Duration.ZERO, Duration.ZERO);
    }

    /**
     * 判断是否设置了显式（非零）的完整修复超时。
     *
     * @return 设置了显式超时时返回 {@code true}
     */
    public boolean hasTimeout() {
        return !timeout.isZero();
    }

    /**
     * 判断是否设置了显式（非零）的流式重新请求超时。
     *
     * @return 设置了显式超时时返回 {@code true}
     */
    public boolean hasStatementRepairTimeout() {
        return !statementRepairTimeout.isZero();
    }
}
