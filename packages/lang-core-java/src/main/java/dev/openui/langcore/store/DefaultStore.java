package dev.openui.langcore.store;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Default {@link Store} implementation backed by a {@link LinkedHashMap} and a
 * {@link CopyOnWriteArraySet} of listeners.
 *
 * <p>Thread-safety: {@link #set} and {@link #initialize} are {@code synchronized}
 * to guard the mutable state map. The snapshot field is {@code volatile} so readers
 * always see the latest published snapshot without holding the lock.
 *
 * Ref: Design §14
 */
public final class DefaultStore implements Store {

    private final Map<String, Object> state = new LinkedHashMap<>();
    private final CopyOnWriteArraySet<Runnable> listeners = new CopyOnWriteArraySet<>();
    private volatile Map<String, Object> snapshot = Map.of();
    private volatile boolean disposed = false;

    // -------------------------------------------------------------------------
    // Store interface
    // -------------------------------------------------------------------------

    @Override
    public Object get(String name) {
        return state.get(name);
    }

    @Override
    public synchronized void set(String name, Object value) {
        if (disposed) return;
        Object existing = state.get(name);
        if (shallowEquals(existing, value)) return;
        state.put(name, value);
        rebuildSnapshot();
        notifyListeners();
    }

    @Override
    public Runnable subscribe(Runnable listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    @Override
    public Map<String, Object> getSnapshot() {
        return snapshot;
    }

    @Override
    public synchronized void initialize(Map<String, Object> defaults, Map<String, Object> persisted) {
        if (disposed) return;
        // Persisted values first (explicit restore)
        persisted.forEach(state::put);
        // Defaults only for NEW keys
        defaults.forEach(state::putIfAbsent);
        rebuildSnapshot();
        notifyListeners();
    }

    @Override
    public void dispose() {
        disposed = true;
        listeners.clear();
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void rebuildSnapshot() {
        snapshot = Collections.unmodifiableMap(new LinkedHashMap<>(state));
    }

    private void notifyListeners() {
        for (Runnable listener : listeners) {
            listener.run();
        }
    }

    /**
     * Shallow equality check mirroring TypeScript {@code Object.is} + shallow-object
     * comparison from {@code store.ts}.
     *
     * <ul>
     *   <li>Identity ({@code ==}) → equal</li>
     *   <li>Both {@link Map}: same key set and per-key identity on values → equal</li>
     *   <li>Otherwise: not equal</li>
     * </ul>
     */
    static boolean shallowEquals(Object a, Object b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a instanceof Map<?, ?> ma && b instanceof Map<?, ?> mb) {
            if (!ma.keySet().equals(mb.keySet())) return false;
            for (Object key : ma.keySet()) {
                if (ma.get(key) != mb.get(key)) return false;
            }
            return true;
        }
        return false;
    }
}
