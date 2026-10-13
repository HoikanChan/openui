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
    /** 仅在 LLM 生成完成后执行一次校验。 */
    FINAL_ONLY,
    /** 在每个流式检查点以及最终结果处都执行校验。 */
    STREAMING_GATE,
    /** 完全关闭校验。 */
    DISABLED
}
