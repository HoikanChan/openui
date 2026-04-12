package dev.openui.langcore.mcp;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Duck-typed interface for an MCP (Model Context Protocol) client.
 *
 * <p>Callers provide an implementation backed by the real MCP SDK or a test stub —
 * no SDK dependency is required in this module.
 *
 * Ref: Design §16
 */
public interface McpClientLike {

    /** Call a tool on the MCP server. */
    CompletableFuture<McpResult> callTool(McpCallParams params);

    /** Parameters for a single tool call. */
    record McpCallParams(String name, Map<String, Object> arguments) {}

    /** A single content item in an MCP result. */
    record McpContentItem(String type, String text) {}

    /**
     * Result returned by the MCP server.
     *
     * @param content           text/image content items (may be empty)
     * @param structuredContent pre-parsed structured result (may be null)
     * @param isError           true if the server signals a tool error
     */
    record McpResult(List<McpContentItem> content, Object structuredContent, boolean isError) {}
}
