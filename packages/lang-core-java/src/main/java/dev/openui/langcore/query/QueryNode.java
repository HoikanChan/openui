package dev.openui.langcore.query;

/**
 * Describes an active query that the {@link QueryManager} should keep fresh.
 *
 * <p>Fields mirror the TypeScript {@code QueryNode} interface in {@code queryManager.ts}.
 *
 * @param statementId     unique identifier for this query statement
 * @param toolName        name of the tool to call
 * @param args            evaluated argument map passed to the tool
 * @param defaults        default values applied when the result is absent
 * @param deps            evaluated dependency value — included in the cache key
 *                        so a change forces a re-fetch
 * @param refreshInterval auto-refresh interval in seconds ({@code null} = no auto-refresh)
 * @param complete        whether the query has ever received a successful result
 *
 * Ref: Design §15
 */
public record QueryNode(
        String  statementId,
        String  toolName,
        Object  args,
        Object  defaults,
        Object  deps,
        Integer refreshInterval,
        boolean complete
) {}
