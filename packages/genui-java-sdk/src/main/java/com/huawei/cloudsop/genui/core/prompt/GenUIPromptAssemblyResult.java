/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.prompt;

/**
 * The rendered prompt together with the metadata describing how it was produced.
 *
 * @param prompt the openui-lang prompt text handed to the LLM
 * @param metadata the contract versions and tool names that shaped {@code prompt}
 * @since 2026
 */
public record GenUIPromptAssemblyResult(String prompt, GenUIPromptAssemblyMetadata metadata) {}
