package dev.openui.langcore.query;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Abstraction over a tool-calling backend (MCP, function-calling, etc.).
 *
 * <p>Implementors resolve a named tool with the given input arguments and return
 * a {@link CompletableFuture} that completes with the tool's result value, or
 * completes exceptionally (e.g. with {@link ToolNotFoundError}).
 *
 * Ref: Design §15
 */
@FunctionalInterface
public interface ToolProvider {

    /**
     * Invoke a tool by name with the given arguments.
     *
     * @param toolName  the name of the tool to call
     * @param arguments key/value argument map passed to the tool
     * @return a future that resolves to the tool's result, or completes
     *         exceptionally on error
     */
    CompletableFuture<Object> callTool(String toolName, Map<String, Object> arguments);
}
