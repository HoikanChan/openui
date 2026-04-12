package dev.openui.langcore.query;

import java.util.List;

/**
 * Thrown when a requested tool name is not registered with the {@link ToolProvider}.
 *
 * Ref: Design §15
 */
public final class ToolNotFoundError extends RuntimeException {

    private final String       toolName;
    private final List<String> availableTools;

    public ToolNotFoundError(String toolName, List<String> availableTools) {
        super("Tool not found: \"" + toolName + "\". Available tools: " + availableTools);
        this.toolName       = toolName;
        this.availableTools = availableTools != null ? List.copyOf(availableTools) : List.of();
    }

    public String toolName() { return toolName; }

    public List<String> availableTools() { return availableTools; }
}
