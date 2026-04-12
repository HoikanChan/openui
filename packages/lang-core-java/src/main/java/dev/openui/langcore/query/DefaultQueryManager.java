package dev.openui.langcore.query;

import dev.openui.langcore.mcp.McpToolError;
import dev.openui.langcore.parser.result.OpenUIError;
import dev.openui.langcore.util.StableJson;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Default implementation of {@link QueryManager}.
 *
 * <p>Mirrors the TypeScript {@code createQueryManager} factory in {@code queryManager.ts}.
 *
 * Ref: Design §15
 */
public final class DefaultQueryManager implements QueryManager {

    // -------------------------------------------------------------------------
    // Internal state records
    // -------------------------------------------------------------------------

    private static final class QueryEntry {
        String           toolName;
        Object           args;
        Object           defaults;
        String           cacheKey;
        String           prevCacheKey;   // nullable
        volatile boolean loading;
        boolean          everFetched;
        int              refreshInterval; // seconds; 0 = no auto-refresh
        ScheduledFuture<?> timer;         // nullable
        boolean          needsRefetch;
        OpenUIError      error;           // nullable
    }

    private static final class MutationEntry {
        String        toolName;
        MutationResult result;
        OpenUIError   error;   // nullable
    }

    private static final class CacheEntry {
        volatile Object  data;      // null = explicit null; absent key = never fetched
        volatile boolean inFlight;

        CacheEntry(Object data, boolean inFlight) {
            this.data     = data;
            this.inFlight = inFlight;
        }
    }

    // Sentinel used for "no data yet" (distinct from an explicit null result)
    private static final Object ABSENT = new Object();

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final ToolProvider toolProvider;  // nullable

    private final Map<String, QueryEntry>    queries   = new ConcurrentHashMap<>();
    private final Map<String, MutationEntry> mutations = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry>    cache     = new ConcurrentHashMap<>();
    private final CopyOnWriteArraySet<Runnable> listeners = new CopyOnWriteArraySet<>();

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "openui-qm-refresh");
                t.setDaemon(true);
                return t;
            });

    private volatile QuerySnapshot snapshot     = QuerySnapshot.empty();
    private volatile String        snapshotJson = StableJson.stringify(snapshotToMap(QuerySnapshot.empty()));
    private volatile boolean       disposed     = false;
    private final AtomicLong       generation   = new AtomicLong(0);

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public DefaultQueryManager(ToolProvider toolProvider) {
        this.toolProvider = toolProvider;
    }

    // -------------------------------------------------------------------------
    // QueryManager — public API
    // -------------------------------------------------------------------------

    @Override
    public synchronized void evaluateQueries(List<QueryNode> nodes) {
        if (disposed) return;

        Set<String> activeIds = new java.util.HashSet<>();
        for (QueryNode n : nodes) activeIds.add(n.statementId());

        // Remove stale queries
        for (String sid : new ArrayList<>(queries.keySet())) {
            if (!activeIds.contains(sid)) {
                QueryEntry q = queries.remove(sid);
                cancelTimer(q);
                cleanupCacheEntry(q.cacheKey);
                if (q.prevCacheKey != null) cleanupCacheEntry(q.prevCacheKey);
            }
        }

        // Process active queries
        for (QueryNode node : nodes) {
            if (!node.complete()) continue;

            String cacheKey = buildCacheKey(node.toolName(), node.args(), node.deps());
            QueryEntry existing = queries.get(node.statementId());

            if (existing != null) {
                if (!existing.cacheKey.equals(cacheKey)) {
                    existing.prevCacheKey = existing.cacheKey;
                }
                existing.toolName = node.toolName();
                existing.args     = node.args();
                existing.defaults = node.defaults();
                existing.cacheKey = cacheKey;
            } else {
                QueryEntry e     = new QueryEntry();
                e.toolName       = node.toolName();
                e.args           = node.args();
                e.defaults       = node.defaults();
                e.cacheKey       = cacheKey;
                e.loading        = false;
                e.everFetched    = false;
                e.refreshInterval = 0;
                e.needsRefetch   = false;
                queries.put(node.statementId(), e);
            }

            QueryEntry q = queries.get(node.statementId());

            // Fire fetch if no settled data and not already in-flight
            CacheEntry entry = cache.get(cacheKey);
            boolean hasSettled = entry != null && entry.data != ABSENT && !entry.inFlight;
            if (toolProvider != null && !hasSettled && (entry == null || !entry.inFlight)) {
                executeFetch(cacheKey, node.statementId());
            }

            // Configure auto-refresh timer
            int newInterval = node.refreshInterval() != null ? node.refreshInterval() : 0;
            if (newInterval != q.refreshInterval) {
                cancelTimer(q);
                if (newInterval > 0) {
                    String sid = node.statementId();
                    q.timer = scheduler.scheduleAtFixedRate(() -> {
                        if (disposed || toolProvider == null) return;
                        synchronized (DefaultQueryManager.this) {
                            QueryEntry qe = queries.get(sid);
                            if (qe == null) return;
                            CacheEntry ce = cache.get(qe.cacheKey);
                            if (ce == null || !ce.inFlight) {
                                executeFetch(qe.cacheKey, sid);
                            }
                        }
                    }, newInterval, newInterval, TimeUnit.SECONDS);
                }
                q.refreshInterval = newInterval;
            }
        }

        if (rebuildSnapshot()) notifyListeners();
    }

    @Override
    public synchronized Object getResult(String statementId) {
        QueryEntry q = queries.get(statementId);
        if (q == null) return null;
        CacheEntry entry = cache.get(q.cacheKey);
        if (entry != null && entry.data != ABSENT) return entry.data;
        if (q.prevCacheKey != null) {
            CacheEntry prev = cache.get(q.prevCacheKey);
            if (prev != null && prev.data != ABSENT) return prev.data;
        }
        return q.defaults;
    }

    @Override
    public boolean isLoading(String statementId) {
        QueryEntry q = queries.get(statementId);
        return q != null && q.loading;
    }

    @Override
    public boolean isAnyLoading() {
        for (QueryEntry q : queries.values()) {
            if (q.loading) return true;
        }
        return false;
    }

    @Override
    public synchronized void invalidate(List<String> statementIds) {
        if (disposed || toolProvider == null) return;

        List<String> targets = (statementIds != null && !statementIds.isEmpty())
                ? statementIds.stream().filter(queries::containsKey).toList()
                : new ArrayList<>(queries.keySet());

        for (String sid : targets) {
            QueryEntry q = queries.get(sid);
            if (q == null) continue;
            CacheEntry entry = cache.get(q.cacheKey);
            if (entry != null && entry.inFlight) {
                q.needsRefetch = true;
            } else {
                executeFetch(q.cacheKey, sid);
            }
        }
    }

    @Override
    public synchronized void registerMutations(List<MutationNode> nodes) {
        Set<String> activeIds = new java.util.HashSet<>();
        for (MutationNode n : nodes) activeIds.add(n.statementId());

        // Remove stale mutations
        mutations.keySet().removeIf(sid -> !activeIds.contains(sid));

        // Register / update
        for (MutationNode node : nodes) {
            MutationEntry existing = mutations.get(node.statementId());
            if (existing != null) {
                if (!existing.toolName.equals(node.toolName())) {
                    existing.toolName = node.toolName();
                    existing.result   = MutationResult.idle();
                    existing.error    = null;
                }
            } else {
                MutationEntry e = new MutationEntry();
                e.toolName = node.toolName();
                e.result   = MutationResult.idle();
                mutations.put(node.statementId(), e);
            }
        }

        if (rebuildSnapshot()) notifyListeners();
    }

    @Override
    public CompletableFuture<Boolean> fireMutation(
            String statementId,
            Map<String, Object> evaluatedArgs,
            List<String> refreshQueryIds) {

        synchronized (this) {
            if (disposed || toolProvider == null) return CompletableFuture.completedFuture(false);
            MutationEntry m = mutations.get(statementId);
            if (m == null) return CompletableFuture.completedFuture(false);
            if ("loading".equals(m.result.status())) return CompletableFuture.completedFuture(false);

            long gen = generation.get();
            m.result = new MutationResult("loading", null, null);
            rebuildSnapshot();
            notifyListeners();

            return toolProvider.callTool(m.toolName, evaluatedArgs)
                    .handle((data, err) -> {
                        synchronized (DefaultQueryManager.this) {
                            if (disposed || generation.get() != gen) return false;
                            MutationEntry me = mutations.get(statementId);
                            if (me == null) return false;

                            if (err != null) {
                                String msg = err.getMessage() != null ? err.getMessage() : err.toString();
                                me.result = new MutationResult("error", null, msg);
                                me.error  = buildMutationError(statementId, me.toolName, err);
                                rebuildSnapshot();
                                notifyListeners();
                                return false;
                            }

                            me.result = new MutationResult("success", data, null);
                            me.error  = null;
                            rebuildSnapshot();
                            notifyListeners();

                            if (refreshQueryIds != null && !refreshQueryIds.isEmpty()) {
                                invalidate(refreshQueryIds);
                            }
                            return true;
                        }
                    });
        }
    }

    @Override
    public synchronized MutationResult getMutationResult(String statementId) {
        MutationEntry m = mutations.get(statementId);
        return m != null ? m.result : null;
    }

    @Override
    public Runnable subscribe(Runnable listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    @Override
    public QuerySnapshot getSnapshot() {
        return snapshot;
    }

    @Override
    public synchronized void activate() {
        disposed = false;
    }

    @Override
    public synchronized void dispose() {
        disposed = true;
        generation.incrementAndGet();
        listeners.clear();
        for (QueryEntry q : queries.values()) {
            cancelTimer(q);
            q.loading       = false;
            q.needsRefetch  = false;
        }
        mutations.clear();
    }

    // -------------------------------------------------------------------------
    // Internal — fetch
    // -------------------------------------------------------------------------

    /** Must be called with {@code this} lock held. */
    private void executeFetch(String cacheKey, String statementId) {
        if (toolProvider == null) return;
        QueryEntry q = queries.get(statementId);
        if (q == null) return;

        String toolName = q.toolName;
        Object args     = q.args;

        CacheEntry entry = cache.computeIfAbsent(cacheKey, k -> new CacheEntry(ABSENT, false));
        entry.inFlight = true;
        q.loading      = true;

        rebuildSnapshot();
        notifyListeners();

        @SuppressWarnings("unchecked")
        Map<String, Object> argsMap = (args instanceof Map<?, ?> m)
                ? (Map<String, Object>) m : Map.of();

        toolProvider.callTool(toolName, argsMap)
                .whenComplete((data, err) -> {
                    synchronized (DefaultQueryManager.this) {
                        entry.inFlight = false;
                        QueryEntry current = queries.get(statementId);

                        if (err == null) {
                            if (!disposed && current != null && current.cacheKey.equals(cacheKey)) {
                                entry.data       = data != null ? data : null;
                                current.everFetched = true;
                                current.error    = null;

                                if (current.prevCacheKey != null
                                        && !current.prevCacheKey.equals(cacheKey)) {
                                    String prev = current.prevCacheKey;
                                    current.prevCacheKey = null;
                                    cleanupCacheEntry(prev);
                                }
                            }
                        } else {
                            if (current != null && current.cacheKey.equals(cacheKey)) {
                                current.error = buildQueryError(statementId, toolName, err);
                            }
                        }

                        if (current != null && current.cacheKey.equals(cacheKey)) {
                            current.loading = false;
                            if (rebuildSnapshot()) notifyListeners();
                            if (current.needsRefetch) {
                                current.needsRefetch = false;
                                executeFetch(current.cacheKey, statementId);
                            }
                        } else {
                            if (rebuildSnapshot()) notifyListeners();
                        }
                    }
                });
    }

    /** Must be called with {@code this} lock held. */
    private void cleanupCacheEntry(String cacheKey) {
        for (QueryEntry q : queries.values()) {
            if (cacheKey.equals(q.cacheKey) || cacheKey.equals(q.prevCacheKey)) return;
        }
        cache.remove(cacheKey);
    }

    // -------------------------------------------------------------------------
    // Internal — snapshot
    // -------------------------------------------------------------------------

    /** Must be called with {@code this} lock held. Returns true if snapshot changed. */
    private boolean rebuildSnapshot() {
        Map<String, Object> data = new LinkedHashMap<>();
        List<String>        loading    = new ArrayList<>();
        List<String>        refetching = new ArrayList<>();
        List<OpenUIError>   errors     = new ArrayList<>();

        for (Map.Entry<String, QueryEntry> e : queries.entrySet()) {
            String     sid = e.getKey();
            QueryEntry q   = e.getValue();

            CacheEntry entry = cache.get(q.cacheKey);
            if (entry != null && entry.data != ABSENT) {
                data.put(sid, entry.data);
            } else if (q.prevCacheKey != null) {
                CacheEntry prev = cache.get(q.prevCacheKey);
                if (prev != null && prev.data != ABSENT) {
                    data.put(sid, prev.data);
                } else {
                    data.put(sid, q.defaults);
                }
            } else {
                data.put(sid, q.defaults);
            }

            if (q.loading) {
                loading.add(sid);
                if (q.everFetched) refetching.add(sid);
            }
            if (q.error != null) errors.add(q.error);
        }

        for (Map.Entry<String, MutationEntry> e : mutations.entrySet()) {
            data.put(e.getKey(), e.getValue().result);
            if (e.getValue().error != null) errors.add(e.getValue().error);
        }

        QuerySnapshot next = new QuerySnapshot(
                java.util.Collections.unmodifiableMap(data),
                List.copyOf(loading), List.copyOf(refetching), List.copyOf(errors));
        String nextJson = StableJson.stringify(snapshotToMap(next));
        if (nextJson.equals(snapshotJson)) return false;
        snapshot     = next;
        snapshotJson = nextJson;
        return true;
    }

    // -------------------------------------------------------------------------
    // Internal — helpers
    // -------------------------------------------------------------------------

    private void notifyListeners() {
        for (Runnable l : listeners) l.run();
    }

    private static void cancelTimer(QueryEntry q) {
        if (q.timer != null) {
            q.timer.cancel(false);
            q.timer = null;
        }
    }

    private static String buildCacheKey(String toolName, Object args, Object deps) {
        String depsKey = deps != null ? "::" + StableJson.stringify(deps) : "";
        return toolName + "::" + StableJson.stringify(args) + depsKey;
    }

    private static OpenUIError buildQueryError(String statementId, String toolName, Throwable err) {
        if (err instanceof ToolNotFoundError tnf) {
            String hint = tnf.availableTools().isEmpty()
                    ? null
                    : "Available tools: " + String.join(", ", tnf.availableTools());
            return new OpenUIError("query", "tool-not-found",
                    "Query tool \"" + toolName + "\" not found",
                    statementId, "Query", toolName, hint);
        }
        if (err instanceof McpToolError mte) {
            return new OpenUIError("query", "mcp-error",
                    "Query \"" + toolName + "\" returned an error: " + mte.getMessage(),
                    statementId, "Query", toolName, null);
        }
        String msg = err.getMessage() != null ? err.getMessage() : err.toString();
        return new OpenUIError("query", "tool-error",
                "Query \"" + toolName + "\" failed: " + msg,
                statementId, "Query", toolName, null);
    }

    private static OpenUIError buildMutationError(String statementId, String toolName, Throwable err) {
        if (err instanceof ToolNotFoundError tnf) {
            String hint = tnf.availableTools().isEmpty()
                    ? null
                    : "Available tools: " + String.join(", ", tnf.availableTools());
            return new OpenUIError("mutation", "tool-not-found",
                    "Mutation tool \"" + toolName + "\" not found",
                    statementId, "Mutation", toolName, hint);
        }
        if (err instanceof McpToolError mte) {
            return new OpenUIError("mutation", "mcp-error",
                    "Mutation \"" + toolName + "\" returned an error: " + mte.getMessage(),
                    statementId, "Mutation", toolName, null);
        }
        String msg = err.getMessage() != null ? err.getMessage() : err.toString();
        return new OpenUIError("mutation", "tool-error",
                "Mutation \"" + toolName + "\" failed: " + msg,
                statementId, "Mutation", toolName, null);
    }

    /** Convert a QuerySnapshot to a plain Map for StableJson serialization. */
    private static Map<String, Object> snapshotToMap(QuerySnapshot s) {
        Map<String, Object> m = new LinkedHashMap<>(s.data());
        m.put("__openui_loading",    s.loading());
        m.put("__openui_refetching", s.refetching());
        m.put("__openui_errors",     s.errors());
        return m;
    }
}
