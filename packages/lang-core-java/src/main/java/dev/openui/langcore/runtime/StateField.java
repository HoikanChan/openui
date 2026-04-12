package dev.openui.langcore.runtime;

import dev.openui.langcore.store.Store;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A resolved prop binding, carrying the concrete value and a setter for the framework adapter.
 *
 * <p>When {@code isReactive} is {@code true}, {@code setValue} wires to the reactive store:
 * it evaluates the {@link ReactiveAssign#expr()} with {@code $value} injected into scope, then
 * calls {@link Store#set} with the result. The framework adapter should use this as an
 * {@code onChange}-style handler.
 *
 * <p>When {@code isReactive} is {@code false}, {@code setValue} directly calls the provided
 * field setter — a static prop update with no reactive pipeline.
 *
 * Ref: Design §17 (StateField section)
 */
public record StateField<T>(String name, T value, Consumer<T> setValue, boolean isReactive) {

    /**
     * Resolve a prop binding to a {@link StateField}.
     *
     * <p>If {@code bindingValue} is a {@link ReactiveAssign} and both {@code store} and
     * {@code evalCtx} are non-null, the reactive branch is taken: the store's current value
     * for {@link ReactiveAssign#target()} becomes {@code value}, and {@code setValue} evaluates
     * the expression with {@code $value} injected into the extra scope before calling
     * {@link Store#set}.
     *
     * <p>Otherwise the static branch is taken: {@code value} is coalesced from
     * {@code fieldGetter.apply(name)} (falls back to {@code bindingValue}), and
     * {@code setValue} delegates to {@code fieldSetter}.
     *
     * @param name          prop name
     * @param bindingValue  the evaluated prop value (may be a {@link ReactiveAssign})
     * @param store         the reactive store (nullable)
     * @param evalCtx       evaluation context (nullable)
     * @param fieldGetter   reads the current prop value from the rendering context
     * @param fieldSetter   writes a new prop value to the rendering context
     */
    @SuppressWarnings("unchecked")
    public static <T> StateField<T> resolveStateField(
            String name,
            Object bindingValue,
            Store store,
            EvaluationContext evalCtx,
            Function<String, Object> fieldGetter,
            BiConsumer<String, Object> fieldSetter
    ) {
        if (bindingValue instanceof ReactiveAssign ra && store != null && evalCtx != null) {
            // Reactive branch — Req 11 AC8
            T currentVal = (T) store.get(ra.target());
            Consumer<T> setter = value -> {
                Map<String, Object> scope = Map.of("$value", value);
                Object next = new Evaluator().evaluate(ra.expr(), evalCtx.withExtraScope(scope));
                store.set(ra.target(), next);
            };
            return new StateField<>(name, currentVal, setter, true);
        }

        // Static branch
        T currentVal = (T) coalesce(fieldGetter.apply(name), bindingValue);
        return new StateField<>(name, currentVal, value -> fieldSetter.accept(name, value), false);
    }

    /** Return {@code a} if non-null, otherwise {@code b}. */
    private static Object coalesce(Object a, Object b) {
        return a != null ? a : b;
    }
}
