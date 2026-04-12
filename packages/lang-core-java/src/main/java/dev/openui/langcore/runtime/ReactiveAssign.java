package dev.openui.langcore.runtime;

import dev.openui.langcore.parser.ast.Node;

/**
 * Signal returned by the evaluator when a prop should be wired as a reactive
 * setter rather than a static value.
 *
 * <p>Framework adapters inspect prop values with
 * {@code value instanceof ReactiveAssign} (i.e. {@code isReactiveAssign}) and,
 * when true, create a reactive binding (e.g. an {@code onChange} handler) that
 * calls the store setter for {@link #target} with the result of evaluating
 * {@link #expr}.
 *
 * <p>Corresponds to the TypeScript shape:
 * {@code { __reactive: "assign", target: string, expr: Node }}
 *
 * Ref: Req 11 AC4-5
 */
public record ReactiveAssign(String target, Node expr) {}
