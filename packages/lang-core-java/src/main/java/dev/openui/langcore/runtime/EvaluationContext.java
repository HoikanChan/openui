package dev.openui.langcore.runtime;

import java.util.Map;

/**
 * Context passed to the evaluator on each evaluation call.
 *
 * <p>Implementations supply the current reactive state, reference resolution,
 * an extra scope (loop variables, etc.), and optional prop-schema lookup for
 * reactive prop dispatch.
 *
 * Ref: Req 11 AC2-4
 */
public interface EvaluationContext {

    /**
     * Return the current value of a {@code $state} variable by name
     * (including the {@code $} prefix).
     *
     * @param name the state variable name, e.g. {@code "$count"}
     * @return the current value, or {@code null} if not set
     */
    Object getState(String name);

    /**
     * Resolve a named reference (from a {@link dev.openui.langcore.parser.ast.RefNode}
     * or {@link dev.openui.langcore.parser.ast.RuntimeRefNode}) to its current value.
     *
     * @param name the identifier name
     * @return the resolved value, or {@code null} if unresolvable
     */
    Object resolveRef(String name);

    /**
     * Extra scope map — takes precedence over {@link #getState} for matching
     * {@code StateRef} names (e.g. loop-variable bindings in {@code @Each}).
     *
     * <p>Implementations may return an empty map when no extra scope is active.
     * The returned map must not be {@code null}.
     *
     * Ref: Req 11 AC3
     */
    Map<String, Object> extraScope();

    /**
     * Return the prop schema object for the named {@code $state} variable, if any.
     * Used by the evaluator to decide whether to emit a
     * {@link ReactiveAssign} (Req 11 AC8).
     *
     * @param name the state variable name
     * @return the schema object, or {@code null} if no schema is associated
     */
    Object getPropSchema(String name);

    /**
     * Return a new {@link EvaluationContext} identical to this one but with
     * {@link #extraScope()} replaced by {@code scope}.
     *
     * <p>Used by {@code @Each} to bind the loop variable without mutating
     * the parent context.
     *
     * @param scope the new extra scope map
     */
    EvaluationContext withExtraScope(Map<String, Object> scope);
}
