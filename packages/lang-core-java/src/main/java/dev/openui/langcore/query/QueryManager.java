package dev.openui.langcore.query;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Manages the lifecycle of queries and mutations derived from a parsed OpenUI Lang program.
 *
 * <p>Mirrors the TypeScript {@code QueryManager} interface in {@code queryManager.ts}.
 *
 * Ref: Design §15
 */
public interface QueryManager {

    /**
     * Synchronise the set of active queries with the given list.
     * New queries are fetched; stale queries (no longer in the list) are removed.
     * Already-cached queries skip the network unless invalidated.
     */
    void evaluateQueries(List<QueryNode> nodes);

    /**
     * Return the current result for the given statement ID, or {@code null} if
     * no result is available yet.
     */
    Object getResult(String statementId);

    /** {@code true} if the query is currently performing its first (uncached) fetch. */
    boolean isLoading(String statementId);

    /** {@code true} if any query is currently in a loading or refetching state. */
    boolean isAnyLoading();

    /**
     * Invalidate the given statement IDs so they are re-fetched on the next
     * {@link #evaluateQueries} call. Pass {@code null} to invalidate all queries.
     */
    void invalidate(List<String> statementIds);

    /**
     * Register the set of active mutations.
     * Previously registered mutations not in {@code nodes} are removed.
     */
    void registerMutations(List<MutationNode> nodes);

    /**
     * Fire a mutation by statement ID.
     *
     * @param statementId      the mutation to fire
     * @param evaluatedArgs    pre-evaluated arguments to pass to the tool
     * @param refreshQueryIds  query statement IDs to invalidate and re-fetch after success
     * @return a future that resolves to {@code true} on success, {@code false} if a
     *         concurrent mutation is already in-flight for this statement
     */
    CompletableFuture<Boolean> fireMutation(
            String statementId,
            Map<String, Object> evaluatedArgs,
            List<String> refreshQueryIds);

    /**
     * Return the current {@link MutationResult} for the given statement ID,
     * or {@code null} if the mutation has not been registered.
     */
    MutationResult getMutationResult(String statementId);

    /**
     * Subscribe to snapshot change notifications.
     *
     * @param listener called whenever the snapshot changes
     * @return a {@link Runnable} that unsubscribes the listener when invoked
     */
    Runnable subscribe(Runnable listener);

    /** Return a point-in-time snapshot of all query results and status. */
    QuerySnapshot getSnapshot();

    /**
     * Activate the manager: start auto-refresh timers for queries that have a
     * {@link QueryNode#refreshInterval()}.  Safe to call multiple times.
     */
    void activate();

    /**
     * Dispose the manager: cancel all timers, clear mutations, and mark the
     * instance as disposed so in-flight fetches are discarded.
     * The result cache is preserved so a re-activated manager can serve stale data.
     */
    void dispose();
}
