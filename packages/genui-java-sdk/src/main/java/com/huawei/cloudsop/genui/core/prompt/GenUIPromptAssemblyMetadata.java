/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.prompt;

import java.util.List;

/**
 * Describes which contract versions and tools produced an assembled prompt.
 *
 * @param extensionId the requested extension id, or {@code null} when the base contract was used
 *     alone
 * @param baseContractVersion the version of the effective base contract
 * @param generationVersion the matched extension's version, or {@code null} when no extension
 *     matched
 * @param registeredToolNames the tools included in the prompt, in registration order
 * @since 2026
 */
public record GenUIPromptAssemblyMetadata(
        String extensionId,
        String baseContractVersion,
        String generationVersion,
        List<String> registeredToolNames) {
    /**
     * Defensively copies the tool-name list so later changes cannot leak into the metadata.
     *
     * @param extensionId the requested extension id or {@code null}
     * @param baseContractVersion the effective base contract version
     * @param generationVersion the matched extension version or {@code null}
     * @param registeredToolNames the registered tool names; {@code null} becomes an empty list
     */
    public GenUIPromptAssemblyMetadata {
        registeredToolNames =
                registeredToolNames == null ? List.of() : List.copyOf(registeredToolNames);
    }
}
