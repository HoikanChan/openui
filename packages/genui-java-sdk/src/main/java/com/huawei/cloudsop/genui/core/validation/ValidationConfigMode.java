/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation;

/**
 * 生成流水线的校验策略:轮末是否校验,以及校验失败后是否做增量修复。
 *
 * <p>两种校验策略(FINAL_ONLY / INCREMENTAL)都只在模型单轮输出结束后对当前有效程序执行一次统一检查;生成过程实时转发,不逐语句校验。
 *
 * @since 2026
 */
public enum ValidationConfigMode {
    /** 轮末统一校验;失败后由模型输出覆盖或新增语句做增量修复,实时转发并合并进当前程序,直到通过或会话截止(#58)。正常生成的默认策略。 */
    INCREMENTAL,
    /** 轮末统一校验,不做修复;校验失败直接暴露给调用方。 */
    FINAL_ONLY,
    /** 完全关闭校验。 */
    DISABLED
}
