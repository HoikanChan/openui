/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation;

/**
 * 粗粒度的修复策略。后续章节将用完整的 {@code RepairPolicy} 值对象包装本类型，附加重试次数、超时等参数；本类型不放高级参数。
 *
 * @since 2026
 */
public enum RepairPolicyKind {
    /** 不修复：将校验失败直接暴露给调用方。 */
    NONE,
    /** 最终校验失败后尝试基于规则的修复。 */
    FINAL_REPAIR,
    /** 中止当前流并立即携带错误上下文重新请求 LLM。 */
    FAIL_FAST_REASK
}
