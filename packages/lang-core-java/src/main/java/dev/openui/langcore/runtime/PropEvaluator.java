package dev.openui.langcore.runtime;

import dev.openui.langcore.parser.ast.ElementNode;

/**
 * Recursively evaluates all props in an {@link ElementNode} tree.
 *
 * <p>This is the public entry point corresponding to the TypeScript
 * {@code evaluateElementProps(root, context)} function. It delegates to
 * {@link Evaluator#evaluate} which already recurses through nested
 * {@link ElementNode}s via {@link Evaluator#evalElement}.
 *
 * <p>Nodes with {@code hasDynamicProps == false} are returned unchanged —
 * the evaluator skips them as a mandatory optimization signal (Req 11 AC7).
 *
 * Ref: Req 11 AC6-7
 */
public final class PropEvaluator {

    private final Evaluator evaluator;

    public PropEvaluator() {
        this.evaluator = new Evaluator();
    }

    /** Package-visible constructor for injecting a custom evaluator in tests. */
    PropEvaluator(Evaluator evaluator) {
        this.evaluator = evaluator;
    }

    /**
     * Evaluate all props in {@code root} and its nested element children,
     * returning a new {@link ElementNode} tree with concrete prop values.
     *
     * <p>Static nodes ({@code hasDynamicProps == false}) are returned as-is.
     *
     * @param root    the root element of the UI tree
     * @param context the current evaluation context
     * @return a new element tree with evaluated prop values
     */
    public ElementNode evaluateElementProps(ElementNode root, EvaluationContext context) {
        if (!root.hasDynamicProps()) {
            return root; // AC7: skip static nodes
        }
        Object result = evaluator.evaluate(root, context);
        // evaluate(ElementNode) → evalElement → returns new ElementNode
        return result instanceof ElementNode elem ? elem : root;
    }
}
