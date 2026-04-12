package dev.openui.langcore.reactive;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Identity-based WeakSet equivalent for marking schemas as reactive.
 *
 * <p>A schema marked reactive causes the evaluator to emit a {@code ReactiveAssign}
 * instead of a plain value when a {@code $state} binding is assigned through it.
 *
 * <p>Uses a {@link WeakHashMap} so that marking a schema does not prevent it from
 * being garbage-collected once no other references exist.
 *
 * Ref: Design §12
 */
public final class ReactiveSchemas {

    private static final Map<Object, Boolean> REGISTRY =
            Collections.synchronizedMap(new WeakHashMap<>());

    private ReactiveSchemas() {}

    /**
     * Mark {@code schema} as reactive.
     *
     * @param schema any non-null object representing a prop schema
     */
    public static void markReactive(Object schema) {
        REGISTRY.put(schema, Boolean.TRUE);
    }

    /**
     * Return {@code true} if {@code schema} was previously marked reactive.
     *
     * @param schema the schema object to check (may be {@code null})
     */
    public static boolean isReactiveSchema(Object schema) {
        return schema != null && REGISTRY.containsKey(schema);
    }
}
