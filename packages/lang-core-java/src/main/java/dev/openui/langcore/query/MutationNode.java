package dev.openui.langcore.query;

/**
 * Describes a mutation that can be fired via {@link QueryManager#fireMutation}.
 *
 * <p>Fields mirror the TypeScript {@code MutationNode} interface in {@code queryManager.ts}.
 *
 * @param statementId unique identifier for this mutation statement
 * @param toolName    name of the tool to call
 *
 * Ref: Design §15
 */
public record MutationNode(String statementId, String toolName) {}
