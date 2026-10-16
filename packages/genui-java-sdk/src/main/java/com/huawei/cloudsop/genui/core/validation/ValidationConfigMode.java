/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation;

/**
 * 控制生成流水线中何时触发校验器。区别于按调用选择规则集的 {@link ValidationMode}。
 *
 * @since 2026
 */
public enum ValidationConfigMode {
    /**
     * 轮末统一校验(#56):生成过程实时转发、不校验、不逐语句修复;模型本轮输出结束后,对当前有效程序(同名 last-wins)执行一次统一检查。这是开启校验时的唯一时机策略(名称沿用历史,不表示还有其他校验时机)。
     */
    FINAL_ONLY,
    /** 完全关闭校验。 */
    DISABLED
}
