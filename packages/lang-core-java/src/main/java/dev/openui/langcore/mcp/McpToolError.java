package dev.openui.langcore.mcp;

/**
 * Thrown by {@link McpAdapter#extractToolResult} when the MCP server returns
 * a result with {@code isError: true}.
 *
 * Ref: Design §16
 */
public final class McpToolError extends RuntimeException {

    public McpToolError(String text) {
        super(text);
    }
}
