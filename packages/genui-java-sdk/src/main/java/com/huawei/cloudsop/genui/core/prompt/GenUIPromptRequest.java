/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.prompt;

import com.huawei.cloudsop.genui.core.contract.DataModelSpec;

import java.util.List;

/**
 * Inputs selecting which contract view and mode options to use when assembling a prompt.
 *
 * <p>The mode flags are nullable tri-state values: {@code null} means "infer the default", while
 * {@code TRUE} or {@code FALSE} force the mode.
 *
 * @param extensionId the generation extension to merge in, or {@code null} for the base contract
 *     alone
 * @param dataModel host data appended as a data-model section, or {@code null} for none
 * @param extraRules additional rules appended after the contract's own rules; {@code null} is
 *     treated as empty
 * @param editMode when {@code true}, append the edit-mode instructions
 * @param inlineMode when {@code true}, append the inline-mode instructions
 * @param toolCalls enables tool-call prompting; {@code null} defaults to "tools are present"
 * @param bindings enables bindings prompting; {@code null} defaults to {@code toolCalls}
 * @since 2026
 */
public record GenUIPromptRequest(
        String extensionId,
        DataModelSpec dataModel,
        List<String> extraRules,
        Boolean editMode,
        Boolean inlineMode,
        Boolean toolCalls,
        Boolean bindings) {
    /**
     * Defensively copies {@code extraRules} so the request is immutable.
     *
     * @param extensionId the extension id or {@code null}
     * @param dataModel the data model or {@code null}
     * @param extraRules extra rules; {@code null} becomes an empty list
     * @param editMode the edit-mode flag or {@code null}
     * @param inlineMode the inline-mode flag or {@code null}
     * @param toolCalls the tool-call flag or {@code null}
     * @param bindings the bindings flag or {@code null}
     */
    public GenUIPromptRequest {
        extraRules = extraRules == null ? List.of() : List.copyOf(extraRules);
    }
}
