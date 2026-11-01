/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.type.analysis;

/**
 * 单次分析的完整性标记，不依赖中间事实是否继续传播。
 *
 * @since 2026
 */
final class AnalysisCompleteness {
    private boolean incomplete;

    /** 标记本次分析发生截断，后续不能恢复为完整。 */
    void markIncomplete() {
        incomplete = true;
    }

    /**
     * 是否未发生截断。
     *
     * @return 未触发内部限制时为 true
     */
    boolean isComplete() {
        return !incomplete;
    }
}
