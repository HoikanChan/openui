/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation;

/**
 * 流式生成在轮末校验失败后的修复策略。同步生成不读取此配置。
 *
 * @since 2026
 */
public enum RepairPolicyKind {
    /** 不修复:将校验失败直接暴露给调用方。 */
    NONE,
    /**
     * 轮末校验失败后由模型只输出覆盖或新增语句,实时转发并合并进当前程序,轮末再次校验;在会话截止时间内持续修复(#58)。 正常生成的默认策略。
     */
    INCREMENTAL_REPAIR
}
