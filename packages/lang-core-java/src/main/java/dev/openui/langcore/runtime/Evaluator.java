package dev.openui.langcore.runtime;

import dev.openui.langcore.parser.ast.ArrayNode;
import dev.openui.langcore.parser.ast.AssignNode;
import dev.openui.langcore.parser.ast.BinaryNode;
import dev.openui.langcore.parser.ast.BuiltinCallNode;
import dev.openui.langcore.parser.ast.CallNode;
import dev.openui.langcore.parser.ast.ElementNode;
import dev.openui.langcore.parser.ast.LiteralNode;
import dev.openui.langcore.parser.ast.MemberNode;
import dev.openui.langcore.parser.ast.Node;
import dev.openui.langcore.parser.ast.ObjectNode;
import dev.openui.langcore.parser.ast.RefNode;
import dev.openui.langcore.parser.ast.RuntimeRefNode;
import dev.openui.langcore.parser.ast.StateRefNode;
import dev.openui.langcore.parser.ast.TernaryNode;
import dev.openui.langcore.parser.ast.UnaryNode;
import dev.openui.langcore.reactive.ReactiveSchemas;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AST node evaluator — recursively reduces a {@link Node} to a concrete Java value.
 *
 * <p>Ref: Req 11 AC1-9, Req 3 AC3-10
 */
public final class Evaluator {

    // -------------------------------------------------------------------------
    // Public entry point
    // -------------------------------------------------------------------------

    /**
     * Evaluate {@code node} in the given {@code ctx}, returning a concrete value.
     *
     * Ref: Req 11 AC1
     */
    public Object evaluate(Node node, EvaluationContext ctx) {
        return switch (node) {
            // --- 12.2: literals, state, refs, binary, unary ---
            case LiteralNode(var v)               -> v;
            case StateRefNode(var n)              -> resolveState(n, ctx);
            case RefNode(var n)                   -> ctx.resolveRef(n);
            case RuntimeRefNode(var n, var __)    -> ctx.resolveRef(n);
            case BinaryNode(var op, var l, var r) -> evalBinary(op, l, r, ctx);
            case UnaryNode(var op, var o)         -> evalUnary(op, o, ctx);

            // --- 12.3: member, ternary, calls, assign, element, array, object ---
            case TernaryNode(var c, var t, var e) -> evalTernary(c, t, e, ctx);
            case MemberNode(var o, var f, var cp) -> evalMember(o, f, cp, ctx);
            case CallNode(var c, var a)           -> evalCall(c, a, ctx);
            case BuiltinCallNode(var n, var a)    -> evalBuiltin(n, a, ctx);
            case AssignNode(var t, var e)         -> new ReactiveAssign(t, e);
            case ElementNode e                    -> e.hasDynamicProps() ? evalElement(e, ctx) : e;
            case ArrayNode(var els)               -> evalArray(els, ctx);
            case ObjectNode(var entries)          -> evalObject(entries, ctx);
        };
    }

    // -------------------------------------------------------------------------
    // 12.2 — state resolution
    // -------------------------------------------------------------------------

    /**
     * Resolve a {@code $state} ref.
     *
     * <p>If the prop schema is marked reactive, return a {@link ReactiveAssign}
     * instead of reading the current value. Extra scope takes precedence over
     * {@link EvaluationContext#getState}.
     *
     * Ref: Req 11 AC3, AC8
     */
    private Object resolveState(String name, EvaluationContext ctx) {
        Object schema = ctx.getPropSchema(name);
        if (ReactiveSchemas.isReactiveSchema(schema)) {
            return new ReactiveAssign(name, new StateRefNode(name));
        }
        return ctx.extraScope().getOrDefault(name, ctx.getState(name));
    }

    // -------------------------------------------------------------------------
    // 12.2 — binary operators
    // -------------------------------------------------------------------------

    /**
     * Evaluate a binary expression.
     *
     * Ref: Req 3 AC3-7
     */
    private Object evalBinary(String op, Node left, Node right, EvaluationContext ctx) {
        // Short-circuit logical operators (Req 3 AC7)
        if ("&&".equals(op)) {
            Object l = evaluate(left, ctx);
            return isTruthy(l) ? evaluate(right, ctx) : l;
        }
        if ("||".equals(op)) {
            Object l = evaluate(left, ctx);
            return isTruthy(l) ? l : evaluate(right, ctx);
        }

        Object l = evaluate(left, ctx);
        Object r = evaluate(right, ctx);

        return switch (op) {
            case "+" -> evalAdd(l, r);               // Req 3 AC3
            case "-" -> toNumber(l) - toNumber(r);
            case "*" -> toNumber(l) * toNumber(r);
            case "/" -> {                             // Req 3 AC4
                double divisor = toNumber(r);
                yield divisor == 0.0 ? 0.0 : toNumber(l) / divisor;
            }
            case "%" -> {                             // Req 3 AC4
                double divisor = toNumber(r);
                yield divisor == 0.0 ? 0.0 : toNumber(l) % divisor;
            }
            case "==" -> looseEquals(l, r);           // Req 3 AC5
            case "!=" -> !((boolean) looseEquals(l, r));
            case ">"  -> toNumber(l) > toNumber(r);   // Req 3 AC6
            case "<"  -> toNumber(l) < toNumber(r);
            case ">=" -> toNumber(l) >= toNumber(r);
            case "<=" -> toNumber(l) <= toNumber(r);
            default   -> null;
        };
    }

    /** {@code +} operator: string concat when either operand is a String (Req 3 AC3). */
    private Object evalAdd(Object l, Object r) {
        if (l instanceof String || r instanceof String) {
            return toStr(l) + toStr(r);
        }
        return toNumber(l) + toNumber(r);
    }

    /**
     * Loose equality — mirrors JavaScript {@code ==} (Req 3 AC5).
     *
     * <ul>
     *   <li>Same type → {@code equals}</li>
     *   <li>String vs Number → coerce string to number, compare numerically</li>
     *   <li>Otherwise → compare as strings</li>
     * </ul>
     */
    private Object looseEquals(Object l, Object r) {
        if (l == null && r == null) return true;
        if (l == null || r == null) return false;
        if (l.equals(r)) return true;
        // String vs Number: coerce string to number (JavaScript semantics)
        if (l instanceof String s && r instanceof Number n) {
            try { return Double.parseDouble(s) == n.doubleValue(); }
            catch (NumberFormatException e) { return false; }
        }
        if (r instanceof String s && l instanceof Number n) {
            try { return Double.parseDouble(s) == n.doubleValue(); }
            catch (NumberFormatException e) { return false; }
        }
        return String.valueOf(l).equals(String.valueOf(r));
    }

    // -------------------------------------------------------------------------
    // 12.2 — unary operators
    // -------------------------------------------------------------------------

    private Object evalUnary(String op, Node operand, EvaluationContext ctx) {
        Object v = evaluate(operand, ctx);
        return switch (op) {
            case "!" -> !isTruthy(v);
            case "-" -> -toNumber(v);
            default  -> null;
        };
    }

    // -------------------------------------------------------------------------
    // 12.3 — ternary
    // -------------------------------------------------------------------------

    /** Short-circuit ternary. Ref: Req 3 AC2 (level 1) */
    protected Object evalTernary(Node cond, Node cons, Node alt, EvaluationContext ctx) {
        return isTruthy(evaluate(cond, ctx)) ? evaluate(cons, ctx) : evaluate(alt, ctx);
    }

    // -------------------------------------------------------------------------
    // 12.3 — member access  (Req 3 AC9-10)
    // -------------------------------------------------------------------------

    /**
     * Evaluate member access.
     *
     * <ul>
     *   <li>Non-computed ({@code .field}): on arrays → pluck / {@code .length}; on objects → field get</li>
     *   <li>Computed ({@code [expr]}): on arrays → numeric index; on objects → string key</li>
     * </ul>
     *
     * Ref: Req 3 AC9-10
     */
    @SuppressWarnings("unchecked")
    protected Object evalMember(Node objNode, Node fieldNode, boolean computed, EvaluationContext ctx) {
        Object obj = evaluate(objNode, ctx);

        if (!computed) {
            // .field — property is a LiteralNode(String fieldName)
            String field = fieldNode instanceof LiteralNode lit ? String.valueOf(lit.value()) : "";
            if (obj instanceof List<?> list) {
                if ("length".equals(field)) return list.size();
                // pluck: map each element
                List<Object> plucked = new ArrayList<>(list.size());
                for (Object item : list) {
                    plucked.add(getField(item, field));
                }
                return plucked;
            }
            return getField(obj, field);
        } else {
            // [expr]
            Object key = evaluate(fieldNode, ctx);
            if (obj instanceof List<?> list) {
                int idx = (int) toNumber(key);
                if (idx < 0 || idx >= list.size()) return null;
                return list.get(idx);
            }
            if (obj instanceof Map<?, ?> map) {
                return map.get(String.valueOf(key));
            }
            return null;
        }
    }

    /** Get a named field from an object (Map). Returns null for non-maps. */
    @SuppressWarnings("unchecked")
    private Object getField(Object obj, String field) {
        if (obj instanceof Map<?, ?> map) return map.get(field);
        return null;
    }

    // -------------------------------------------------------------------------
    // 12.3 — CallNode dispatch (Action)  (Req 10 AC13)
    // -------------------------------------------------------------------------

    /**
     * Evaluate a {@code CallNode}. Currently only {@code Action([...])} is valid
     * as a runtime call; all other callee names return {@code null}.
     *
     * Ref: Req 10 AC13
     */
    protected Object evalCall(String callee, List<Node> args, EvaluationContext ctx) {
        if ("Action".equals(callee)) {
            if (args.isEmpty()) return new ActionPlan(List.of());
            Object stepsVal = evaluate(args.get(0), ctx);
            List<ActionStep> steps = new ArrayList<>();
            if (stepsVal instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof ActionStep as) steps.add(as);
                }
            }
            return new ActionPlan(List.copyOf(steps));
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // 12.3 — BuiltinCallNode dispatch  (Req 10 AC1-18)
    // -------------------------------------------------------------------------

    /**
     * Dispatch {@code @Name(args)} builtins.
     *
     * <p>Eager builtins (AC1-11) are implemented in {@link Builtins} (Task 13.1).
     * {@code @Each} (AC12) is implemented in Task 13.2.
     * Action-step builtins (AC14-18) are fully implemented here.
     *
     * Ref: Req 10 AC1-18
     */
    protected Object evalBuiltin(String name, List<Node> args, EvaluationContext ctx) {
        return switch (name) {
            // --- Eager builtins (Task 13.1) ---
            case "Count"  -> Builtins.count(evalArg(args, 0, ctx));
            case "First"  -> Builtins.first(evalArg(args, 0, ctx));
            case "Last"   -> Builtins.last(evalArg(args, 0, ctx));
            case "Sum"    -> Builtins.sum(evalArg(args, 0, ctx));
            case "Avg"    -> Builtins.avg(evalArg(args, 0, ctx));
            case "Min"    -> Builtins.min(evalArg(args, 0, ctx));
            case "Max"    -> Builtins.max(evalArg(args, 0, ctx));
            case "Sort"   -> Builtins.sort(evalArg(args, 0, ctx), evalArg(args, 1, ctx), evalArg(args, 2, ctx));
            case "Filter" -> Builtins.filter(evalArg(args, 0, ctx), evalArg(args, 1, ctx), evalArg(args, 2, ctx), evalArg(args, 3, ctx));
            case "Round"  -> Builtins.round(evalArg(args, 0, ctx), evalArg(args, 1, ctx));
            case "Abs"    -> Builtins.abs(evalArg(args, 0, ctx));
            case "Floor"  -> Builtins.floor(evalArg(args, 0, ctx));
            case "Ceil"   -> Builtins.ceil(evalArg(args, 0, ctx));

            // --- Lazy builtin @Each (Task 13.2) ---
            case "Each"   -> evalEach(args, ctx);

            // --- Action-step builtins (Req 10 AC14-18) ---
            case "Run"         -> evalRun(args, ctx);
            case "ToAssistant" -> evalToAssistant(args, ctx);
            case "OpenUrl"     -> evalOpenUrl(args, ctx);
            case "Set"         -> evalSet(args, ctx);
            case "Reset"       -> evalReset(args, ctx);

            default -> null;
        };
    }

    // -------------------------------------------------------------------------
    // Action-step builtin implementations  (Req 10 AC14-18)
    // -------------------------------------------------------------------------

    /** @Run(ref) → RunStep. Ref: Req 10 AC14 */
    private Object evalRun(List<Node> args, EvaluationContext ctx) {
        if (args.isEmpty()) return null;
        Node arg = args.get(0);
        // arg is expected to be a RuntimeRefNode directly in the AST
        if (arg instanceof RuntimeRefNode rr) {
            return new ActionStep.RunStep(rr.name(), rr.refType());
        }
        // Try evaluating — some runtime contexts resolve to RuntimeRefNode
        Object v = evaluate(arg, ctx);
        if (v instanceof RuntimeRefNode rr) {
            return new ActionStep.RunStep(rr.name(), rr.refType());
        }
        return null;
    }

    /** @ToAssistant(message, context?) → ToAssistantStep. Ref: Req 10 AC15 */
    private Object evalToAssistant(List<Node> args, EvaluationContext ctx) {
        String message = toStr(evalArg(args, 0, ctx));
        String context = args.size() > 1 ? toStr(evalArg(args, 1, ctx)) : null;
        return new ActionStep.ToAssistantStep(message, context);
    }

    /** @OpenUrl(url) → OpenUrlStep. Ref: Req 10 AC16 */
    private Object evalOpenUrl(List<Node> args, EvaluationContext ctx) {
        return new ActionStep.OpenUrlStep(toStr(evalArg(args, 0, ctx)));
    }

    /** @Set($var, valueExpr) → SetStep. Ref: Req 10 AC17 */
    private Object evalSet(List<Node> args, EvaluationContext ctx) {
        if (args.size() < 2) return null;
        Node first = args.get(0);
        if (!(first instanceof StateRefNode sr)) return null;
        return new ActionStep.SetStep(sr.name(), args.get(1));
    }

    /** @Reset($var1, ...) → ResetStep. Ref: Req 10 AC18 */
    private Object evalReset(List<Node> args, EvaluationContext ctx) {
        List<String> targets = new ArrayList<>();
        for (Node arg : args) {
            if (arg instanceof StateRefNode sr) targets.add(sr.name());
        }
        if (targets.isEmpty()) return null;
        return new ActionStep.ResetStep(List.copyOf(targets));
    }

    /**
     * Lazy {@code @Each(array, varName, template)} builtin.
     *
     * <ol>
     *   <li>Evaluate the array argument eagerly.</li>
     *   <li>Resolve {@code varName} — a bare {@link RefNode} identifier or a string literal.</li>
     *   <li>For each element, substitute all {@code RefNode(varName)} in the template with
     *       {@code LiteralNode(element)}, then evaluate the substituted template.</li>
     * </ol>
     *
     * Ref: Req 10 AC12, Req 11 AC10
     */
    protected Object evalEach(List<Node> args, EvaluationContext ctx) {
        if (args.size() < 3) return List.of();

        // 1. Evaluate array eagerly
        Object arrVal = evaluate(args.get(0), ctx);
        if (!(arrVal instanceof List<?> list)) return List.of();

        // 2. Resolve varName — either a RefNode (bare identifier) or a string LiteralNode
        Node varArg = args.get(1);
        String varName;
        if (varArg instanceof RefNode ref) {
            varName = ref.name();
        } else if (varArg instanceof LiteralNode lit && lit.value() instanceof String s) {
            varName = s;
        } else {
            varName = Evaluator.toStr(evaluate(varArg, ctx));
        }

        Node template = args.get(2);

        // 3. For each element: substitute RefNode(varName) → LiteralNode(element), then evaluate
        List<Object> results = new ArrayList<>(list.size());
        for (Object element : list) {
            Node substituted = Builtins.substituteRef(template, varName, element);
            results.add(evaluate(substituted, ctx));
        }
        return results;
    }

    // -------------------------------------------------------------------------
    // 12.3 — element, array, object  (Req 11 AC6-7)
    // -------------------------------------------------------------------------

    /**
     * Evaluate a dynamic {@link ElementNode}: re-evaluate each prop value.
     *
     * <p>AC9: if a prop evaluates to a {@link ReactiveAssign} but its schema is
     * NOT marked reactive, strip it to the current concrete state value so the
     * renderer never sees a stray reactive marker.
     *
     * Ref: Req 11 AC6-7, AC8-9
     */
    protected Object evalElement(ElementNode elem, EvaluationContext ctx) {
        Map<String, Node> evaluatedProps = new LinkedHashMap<>();
        for (Map.Entry<String, Node> entry : elem.props().entrySet()) {
            Object val = evaluate(entry.getValue(), ctx);

            // AC9: strip stray ReactiveAssign when schema is not reactive
            if (val instanceof ReactiveAssign ra) {
                Object schema = ctx.getPropSchema(ra.target());
                if (!ReactiveSchemas.isReactiveSchema(schema)) {
                    val = ctx.extraScope().getOrDefault(ra.target(), ctx.getState(ra.target()));
                }
            }

            evaluatedProps.put(entry.getKey(), new LiteralNode(val));
        }
        return new ElementNode(elem.typeName(), evaluatedProps, elem.partial(), false, elem.statementId());
    }

    /** Evaluate each element of an array. */
    protected Object evalArray(List<Node> elements, EvaluationContext ctx) {
        List<Object> result = new ArrayList<>(elements.size());
        for (Node el : elements) result.add(evaluate(el, ctx));
        return result;
    }

    /** Evaluate each value of an object literal. */
    protected Object evalObject(Map<String, Node> entries, EvaluationContext ctx) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Node> e : entries.entrySet()) {
            result.put(e.getKey(), evaluate(e.getValue(), ctx));
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    /** Evaluate the nth arg if present, else null. */
    private Object evalArg(List<Node> args, int i, EvaluationContext ctx) {
        return i < args.size() ? evaluate(args.get(i), ctx) : null;
    }

    // -------------------------------------------------------------------------
    // Shared helpers
    // -------------------------------------------------------------------------

    /**
     * Coerce a value to {@code double}.
     *
     * <ul>
     *   <li>{@link Number} → {@code doubleValue()}</li>
     *   <li>{@link String} → {@code parseDouble} (0.0 on failure)</li>
     *   <li>{@link Boolean} → 1.0 / 0.0</li>
     *   <li>otherwise → 0.0</li>
     * </ul>
     */
    static double toNumber(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException e) { return 0.0; }
        }
        if (v instanceof Boolean b) return b ? 1.0 : 0.0;
        return 0.0;
    }

    /** JavaScript-style truthiness: {@code false}, {@code null}, {@code 0}, {@code ""} are falsy. */
    static boolean isTruthy(Object v) {
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.doubleValue() != 0.0;
        if (v instanceof String s) return !s.isEmpty();
        return true;
    }

    /** Coerce to string; {@code null} → {@code ""}. */
    static String toStr(Object v) {
        return v == null ? "" : String.valueOf(v);
    }
}
