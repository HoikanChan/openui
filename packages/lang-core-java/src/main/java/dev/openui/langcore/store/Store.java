package dev.openui.langcore.store;

import java.util.Map;

/**
 * Reactive key-value store for {@code $state} variables.
 *
 * <p>Listeners are notified on every {@link #set} or {@link #initialize} call that
 * produces a state change. {@link #subscribe} returns an unsubscribe {@link Runnable}.
 *
 * Ref: Design §14
 */
public interface Store {

    /** Return the current value of {@code name}, or {@code null} if not set. */
    Object get(String name);

    /**
     * Set {@code name} to {@code value}. Skips notification when the new value
     * is shallow-equal to the existing one.
     */
    void set(String name, Object value);

    /**
     * Register {@code listener} to be called on every state change.
     *
     * @return a {@link Runnable} that, when called, removes the listener
     */
    Runnable subscribe(Runnable listener);

    /** Return an immutable snapshot of the current state map. */
    Map<String, Object> getSnapshot();

    /**
     * Initialise the store.
     *
     * <p>Persisted values are written first (explicit restore); defaults are applied
     * only for keys not already present. Notifies listeners after.
     *
     * @param defaults  fallback values for new keys
     * @param persisted previously-persisted values (take precedence)
     */
    void initialize(Map<String, Object> defaults, Map<String, Object> persisted);

    /** Release all resources and clear listeners. */
    void dispose();
}
