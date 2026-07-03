/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.prompt;

import com.huawei.cloudsop.genui.core.contract.DataModelSpec;

import java.util.List;

public record GenUIPromptRequest(String extensionId, DataModelSpec dataModel, List<String> extraRules, Boolean editMode,
        Boolean inlineMode, Boolean toolCalls, Boolean bindings) {
    public GenUIPromptRequest {
        extraRules = extraRules == null ? List.of() : List.copyOf(extraRules);
    }
}
