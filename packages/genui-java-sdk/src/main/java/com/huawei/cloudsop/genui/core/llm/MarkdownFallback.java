/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.huawei.cloudsop.genui.core.llm;

import java.util.function.Consumer;

/** Host handoff. Invoked once after DSL failure, outside its repair budget, to replace the entire page. */
@FunctionalInterface
public interface MarkdownFallback {
    GenUiGenerationResult generate(UiGenerationRequest original, GenerationEndReason reason,
            Consumer<RenderStreamEnvelope> sink);
}
