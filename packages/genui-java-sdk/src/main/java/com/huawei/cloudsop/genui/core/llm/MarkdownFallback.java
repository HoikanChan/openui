/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.huawei.cloudsop.genui.core.llm;

import java.util.function.Consumer;

/**
 * 宿主降级入口。DSL 失败后、在修复预算之外调用一次,用于整页替换。
 *
 * @since 2026
 */
@FunctionalInterface
public interface MarkdownFallback {
    /**
     * 生成整页 Markdown 替换内容。
     *
     * @param original 原始生成请求
     * @param reason DSL 会话的失败原因
     * @param sink 渲染帧接收器,用于发送 Markdown DSL 帧
     * @return 降级生成结果
     */
    GenUiGenerationResult generate(
            UiGenerationRequest original,
            GenerationEndReason reason,
            Consumer<RenderStreamEnvelope> sink);
}
