/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.prompt;

public record GenUIPromptAssemblyResult(String prompt, GenUIPromptAssemblyMetadata metadata, String dataContext) {
    public GenUIPromptAssemblyResult(String prompt, GenUIPromptAssemblyMetadata metadata) {
        this(prompt, metadata, null);
    }
}
