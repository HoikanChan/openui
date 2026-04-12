package dev.openui.langcore.prompt;

import java.util.Map;

/**
 * Descriptor for an MCP / tool-call tool shown in the prompt.
 *
 * <p>All fields except {@code name} are nullable — only {@code name} is required.
 * {@code inputSchema} and {@code outputSchema} are raw JSON Schema maps.
 * {@code annotations} is an opaque map of provider-specific metadata.
 *
 * Ref: Design §11
 */
public record ToolSpec(
        String name,
        String description,
        Map<String, Object> inputSchema,
        Map<String, Object> outputSchema,
        Map<String, Object> annotations
) {}
