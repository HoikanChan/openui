package dev.openui.langcore.query;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DefaultQueryManager}.
 *
 * Ref: Design §15, Task 19.6
 */
@Timeout(10)
class QueryManagerTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static QueryNode node(String id, String tool, Object args) {
        return new QueryNode(id, tool, args, null, null, null, true);
    }

    private static QueryNode nodeWithDeps(String id, String tool, Object args, Object deps) {
        return new QueryNode(id, tool, args, null, deps, null, true);
    }

    private static QueryNode incomplete(String id, String tool) {
        return new QueryNode(id, tool, Map.of(), null, null, null, false);
    }

    private static MutationNode mutNode(String id, String tool) {
        return new MutationNode(id, tool);
    }

    /** Waits until the snapshot's loading list is empty (all fetches settled). */
    private static void awaitIdle(DefaultQueryManager qm) throws InterruptedException {
        for (int i = 0; i < 200; i++) {
            if (!qm.isAnyLoading()) return;
            Thread.sleep(10);
        }
        fail("QueryManager did not become idle in time");
    }

    // -------------------------------------------------------------------------
    // Fetch fires on first evaluateQueries
    // -------------------------------------------------------------------------

    @Test
    void fetch_firesOnFirstEvaluateQueries() throws Exception {
        AtomicInteger callCount = new AtomicInteger();
        ToolProvider tp = (name, args) -> {
            callCount.incrementAndGet();
            return CompletableFuture.completedFuture("result-" + name);
        };

        DefaultQueryManager qm = new DefaultQueryManager(tp);
        qm.evaluateQueries(List.of(node("q1", "getThing", Map.of())));

        awaitIdle(qm);
        assertEquals(1, callCount.get());
        assertEquals("result-getThing", qm.getResult("q1"));
    }

    // -------------------------------------------------------------------------
    // Cache hit skips second fetch
    // -------------------------------------------------------------------------

    @Test
    void cacheHit_skipsSecondFetch() throws Exception {
        AtomicInteger callCount = new AtomicInteger();
        ToolProvider tp = (name, args) -> {
            callCount.incrementAndGet();
            return CompletableFuture.completedFuture("data");
        };

        DefaultQueryManager qm = new DefaultQueryManager(tp);
        qm.evaluateQueries(List.of(node("q1", "getThing", Map.of())));
        awaitIdle(qm);

        // Second call with same args — cache hit, no new fetch
        qm.evaluateQueries(List.of(node("q1", "getThing", Map.of())));
        awaitIdle(qm);

        assertEquals(1, callCount.get(), "cache hit must skip re-fetch");
    }

    // -------------------------------------------------------------------------
    // Args change triggers new fetch
    // -------------------------------------------------------------------------

    @Test
    void argsChange_triggersNewFetch() throws Exception {
        AtomicInteger callCount = new AtomicInteger();
        ToolProvider tp = (name, args) -> {
            callCount.incrementAndGet();
            return CompletableFuture.completedFuture("v" + callCount.get());
        };

        DefaultQueryManager qm = new DefaultQueryManager(tp);
        qm.evaluateQueries(List.of(node("q1", "getThing", Map.of("x", 1))));
        awaitIdle(qm);

        qm.evaluateQueries(List.of(node("q1", "getThing", Map.of("x", 2))));
        awaitIdle(qm);

        assertEquals(2, callCount.get());
    }

    // -------------------------------------------------------------------------
    // invalidate re-fetches
    // -------------------------------------------------------------------------

    @Test
    void invalidate_refeatchesQuery() throws Exception {
        AtomicInteger callCount = new AtomicInteger();
        ToolProvider tp = (name, args) -> {
            int n = callCount.incrementAndGet();
            return CompletableFuture.completedFuture("v" + n);
        };

        DefaultQueryManager qm = new DefaultQueryManager(tp);
        qm.evaluateQueries(List.of(node("q1", "getThing", Map.of())));
        awaitIdle(qm);
        assertEquals("v1", qm.getResult("q1"));

        qm.invalidate(List.of("q1"));
        awaitIdle(qm);
        assertEquals("v2", qm.getResult("q1"));
        assertEquals(2, callCount.get());
    }

    @Test
    void invalidate_null_refeatchesAll() throws Exception {
        AtomicInteger callCount = new AtomicInteger();
        ToolProvider tp = (name, args) -> {
            callCount.incrementAndGet();
            return CompletableFuture.completedFuture("data");
        };

        DefaultQueryManager qm = new DefaultQueryManager(tp);
        qm.evaluateQueries(List.of(
                node("q1", "getA", Map.of()),
                node("q2", "getB", Map.of())));
        awaitIdle(qm);

        qm.invalidate(null);
        awaitIdle(qm);
        assertEquals(4, callCount.get()); // 2 initial + 2 after invalidate
    }

    // -------------------------------------------------------------------------
    // Incomplete nodes are skipped
    // -------------------------------------------------------------------------

    @Test
    void incompleteNode_isSkipped() throws Exception {
        AtomicInteger callCount = new AtomicInteger();
        ToolProvider tp = (name, args) -> {
            callCount.incrementAndGet();
            return CompletableFuture.completedFuture("data");
        };

        DefaultQueryManager qm = new DefaultQueryManager(tp);
        qm.evaluateQueries(List.of(incomplete("q1", "getThing")));
        Thread.sleep(50); // no fetch should occur

        assertEquals(0, callCount.get());
        assertNull(qm.getResult("q1"));
    }

    // -------------------------------------------------------------------------
    // getSnapshot reflects loading state
    // -------------------------------------------------------------------------

    @Test
    void snapshot_reflectsLoadingState() throws Exception {
        CompletableFuture<Object> pending = new CompletableFuture<>();
        ToolProvider tp = (name, args) -> pending;

        DefaultQueryManager qm = new DefaultQueryManager(tp);
        qm.evaluateQueries(List.of(node("q1", "slow", Map.of())));

        // Should be loading immediately
        assertTrue(qm.isLoading("q1"), "should be loading while fetch is in-flight");
        assertTrue(qm.getSnapshot().loading().contains("q1"));

        // Complete the fetch
        pending.complete("done");
        awaitIdle(qm);

        assertFalse(qm.isLoading("q1"));
        assertFalse(qm.getSnapshot().loading().contains("q1"));
        assertEquals("done", qm.getResult("q1"));
    }

    // -------------------------------------------------------------------------
    // subscribe listener fires on changes
    // -------------------------------------------------------------------------

    @Test
    void subscribe_firesOnSnapshotChange() throws Exception {
        AtomicInteger fires = new AtomicInteger();
        ToolProvider tp = (name, args) -> CompletableFuture.completedFuture("data");

        DefaultQueryManager qm = new DefaultQueryManager(tp);
        Runnable unsub = qm.subscribe(fires::incrementAndGet);

        qm.evaluateQueries(List.of(node("q1", "getThing", Map.of())));
        awaitIdle(qm);

        assertTrue(fires.get() >= 1, "listener must have fired at least once");

        // Unsubscribe — no more fires
        unsub.run();
        int before = fires.get();
        qm.invalidate(List.of("q1"));
        awaitIdle(qm);
        assertEquals(before, fires.get(), "unsubscribed listener must not fire");
    }

    // -------------------------------------------------------------------------
    // Concurrent mutation loading guard
    // -------------------------------------------------------------------------

    @Test
    void mutation_concurrentLoadingGuard() throws Exception {
        CompletableFuture<Object> pending = new CompletableFuture<>();
        ToolProvider tp = (name, args) -> pending;

        DefaultQueryManager qm = new DefaultQueryManager(tp);
        qm.registerMutations(List.of(mutNode("m1", "doThing")));

        CompletableFuture<Boolean> first  = qm.fireMutation("m1", Map.of(), List.of());
        CompletableFuture<Boolean> second = qm.fireMutation("m1", Map.of(), List.of());

        // Second fire while first is loading must return false immediately
        assertFalse(second.get(), "concurrent mutation must be rejected");

        pending.complete("ok");
        assertTrue(first.get(), "first mutation must succeed");
        assertEquals("success", qm.getMutationResult("m1").status());
    }

    // -------------------------------------------------------------------------
    // fireMutation triggers refresh
    // -------------------------------------------------------------------------

    @Test
    void fireMutation_refreshesSpecifiedQueries() throws Exception {
        AtomicInteger queryCalls = new AtomicInteger();
        ToolProvider tp = (name, args) -> {
            if (name.equals("mutTool")) return CompletableFuture.completedFuture("mutated");
            queryCalls.incrementAndGet();
            return CompletableFuture.completedFuture("qdata");
        };

        DefaultQueryManager qm = new DefaultQueryManager(tp);
        qm.evaluateQueries(List.of(node("q1", "getQ", Map.of())));
        qm.registerMutations(List.of(mutNode("m1", "mutTool")));
        awaitIdle(qm);

        int beforeMut = queryCalls.get();
        qm.fireMutation("m1", Map.of(), List.of("q1")).get();
        awaitIdle(qm);

        assertTrue(queryCalls.get() > beforeMut, "mutation must have triggered query refresh");
    }

    // -------------------------------------------------------------------------
    // fireMutation on unknown statementId returns false
    // -------------------------------------------------------------------------

    @Test
    void fireMutation_unknownId_returnsFalse() throws Exception {
        DefaultQueryManager qm = new DefaultQueryManager((name, args) ->
                CompletableFuture.completedFuture("x"));
        assertFalse(qm.fireMutation("nonexistent", Map.of(), List.of()).get());
    }

    // -------------------------------------------------------------------------
    // dispose / activate lifecycle
    // -------------------------------------------------------------------------

    @Test
    void dispose_preventsNewFetches() throws Exception {
        AtomicInteger callCount = new AtomicInteger();
        ToolProvider tp = (name, args) -> {
            callCount.incrementAndGet();
            return CompletableFuture.completedFuture("data");
        };

        DefaultQueryManager qm = new DefaultQueryManager(tp);
        qm.evaluateQueries(List.of(node("q1", "getThing", Map.of())));
        awaitIdle(qm);

        qm.dispose();
        int after = callCount.get();

        qm.invalidate(List.of("q1")); // should be ignored while disposed
        Thread.sleep(50);
        assertEquals(after, callCount.get(), "disposed manager must not fetch");
    }

    @Test
    void activate_resumesAfterDispose() throws Exception {
        AtomicInteger callCount = new AtomicInteger();
        ToolProvider tp = (name, args) -> {
            callCount.incrementAndGet();
            return CompletableFuture.completedFuture("data");
        };

        DefaultQueryManager qm = new DefaultQueryManager(tp);
        qm.dispose();
        qm.activate();

        qm.evaluateQueries(List.of(node("q1", "getThing", Map.of())));
        awaitIdle(qm);

        assertEquals(1, callCount.get());
    }

    // -------------------------------------------------------------------------
    // Stale fetch discarded after dispose
    // -------------------------------------------------------------------------

    @Test
    void staleFetch_discardedAfterDispose() throws Exception {
        CompletableFuture<Object> pending = new CompletableFuture<>();
        AtomicReference<Object> stored = new AtomicReference<>();

        ToolProvider tp = (name, args) -> pending;

        DefaultQueryManager qm = new DefaultQueryManager(tp);
        qm.subscribe(() -> stored.set(qm.getResult("q1")));

        qm.evaluateQueries(List.of(node("q1", "slow", Map.of())));
        qm.dispose();

        // Complete the in-flight fetch after dispose — result must be discarded
        pending.complete("stale-value");
        Thread.sleep(50);

        // The result should remain null/default (not "stale-value")
        assertNotEquals("stale-value", qm.getResult("q1"));
    }
}
