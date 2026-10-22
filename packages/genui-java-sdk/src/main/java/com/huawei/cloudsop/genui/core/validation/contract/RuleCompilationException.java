/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.contract;

import com.huawei.cloudsop.genui.core.GenerationSdkException;

/**
 * 结构化类型规则的加载期失败——未知 op/tag、非法实参引用、未绑定变量、非法 arity 与规则布局冲突。 契约配置错误不能静默降级为"未知类型"。
 *
 * @since 2026
 */
public final class RuleCompilationException extends GenerationSdkException {

    public RuleCompilationException(String message) {
        super(message);
    }
}
