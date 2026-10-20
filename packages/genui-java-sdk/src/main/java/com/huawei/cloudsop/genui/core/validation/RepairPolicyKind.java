/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation;

/**
 * 粗粒度的修复策略。后续章节将用完整的 {@code RepairPolicy} 值对象包装本类型,附加重试次数、超时等参数;本类型不放高级参数。
 *
 * @since 2026
 */
public enum RepairPolicyKind {
    /** 不修复:将校验失败直接暴露给调用方。 */
    NONE,
    /** 轮末校验失败后尝试整份 DSL 修复(已有失败处理)。 */
    FINAL_REPAIR,
    /**
     * 轮末校验失败后由模型只输出覆盖或新增语句,实时转发并合并进当前程序,轮末再次校验;在会话截止时间内持续修复(#58)。 正常生成的默认策略。
     */
    INCREMENTAL_REPAIR
}
