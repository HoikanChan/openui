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
import dev.openui.langcore.parser.ast.TernaryNode;
import dev.openui.langcore.parser.ast.UnaryNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Eager built-in functions for {@code @Name(...)} expressions.
 *
 * <p>All methods receive already-evaluated (resolved) arguments.
 *
 * Ref: Req 10 AC1-11
 */
public final class Builtins {

    private Builtins() {}

    // -------------------------------------------------------------------------
    // AC1 — @Count
    // -------------------------------------------------------------------------

    /** Returns the array's length, or {@code 0} if not an array. Ref: Req 10 AC1 */
    public static Object count(Object arr) {
        if (arr instanceof List<?> list) return list.size();
        return 0;
    }

    // -------------------------------------------------------------------------
    // AC2 — @First
    // -------------------------------------------------------------------------

    /** Returns the first element, or {@code null} if empty / not an array. Ref: Req 10 AC2 */
    public static Object first(Object arr) {
        if (arr instanceof List<?> list && !list.isEmpty()) return list.get(0);
        return null;
    }

    // -------------------------------------------------------------------------
    // AC3 — @Last
    // -------------------------------------------------------------------------

    /** Returns the last element, or {@code null} if empty / not an array. Ref: Req 10 AC3 */
    public static Object last(Object arr) {
        if (arr instanceof List<?> list && !list.isEmpty()) return list.get(list.size() - 1);
        return null;
    }

    // -------------------------------------------------------------------------
    // AC4 — @Sum
    // -------------------------------------------------------------------------

    /** Returns sum of elements coerced to numbers, or {@code 0} if not an array. Ref: Req 10 AC4 */
    public static Object sum(Object arr) {
        if (!(arr instanceof List<?> list)) return 0.0;
        double total = 0.0;
        for (Object item : list) total += Evaluator.toNumber(item);
        return total;
    }

    // -------------------------------------------------------------------------
    // AC5 — @Avg
    // -------------------------------------------------------------------------

    /** Returns numeric average, or {@code 0} if empty / not an array. Ref: Req 10 AC5 */
    public static Object avg(Object arr) {
        if (!(arr instanceof List<?> list) || list.isEmpty()) return 0.0;
        double total = 0.0;
        for (Object item : list) total += Evaluator.toNumber(item);
        return total / list.size();
    }

    // -------------------------------------------------------------------------
    // AC6 — @Min / @Max
    // -------------------------------------------------------------------------

    /** Returns numeric minimum, or {@code 0} if empty / not an array. Ref: Req 10 AC6 */
    public static Object min(Object arr) {
        if (!(arr instanceof List<?> list) || list.isEmpty()) return 0.0;
        double result = Evaluator.toNumber(list.get(0));
        for (int i = 1; i < list.size(); i++) result = Math.min(result, Evaluator.toNumber(list.get(i)));
        return result;
    }

    /** Returns numeric maximum, or {@code 0} if empty / not an array. Ref: Req 10 AC6 */
    public static Object max(Object arr) {
        if (!(arr instanceof List<?> list) || list.isEmpty()) return 0.0;
        double result = Evaluator.toNumber(list.get(0));
        for (int i = 1; i < list.size(); i++) result = Math.max(result, Evaluator.toNumber(list.get(i)));
        return result;
    }

    // -------------------------------------------------------------------------
    // AC7 — @Sort
    // -------------------------------------------------------------------------

    /**
     * Returns a new sorted array.
     *
     * <p>If {@code field} is a non-empty string, elements are sorted by that dot-path field.
     * Numeric strings sort numerically. Direction {@code "desc"} reverses the order.
     *
     * Ref: Req 10 AC7
     */
    @SuppressWarnings("unchecked")
    public static Object sort(Object arr, Object field, Object dir) {
        if (!(arr instanceof List<?> list)) return arr;
        List<Object> copy = new ArrayList<>((List<Object>) list);

        String fieldPath = (field instanceof String s && !s.isEmpty()) ? s : null;
        boolean desc = "desc".equals(dir);

        Comparator<Object> cmp = (a, b) -> {
            Object va = fieldPath != null ? dotPath(a, fieldPath) : a;
            Object vb = fieldPath != null ? dotPath(b, fieldPath) : b;
            // Numeric sort when both parse as numbers
            try {
                double da = Double.parseDouble(String.valueOf(va));
                double db = Double.parseDouble(String.valueOf(vb));
                return Double.compare(da, db);
            } catch (NumberFormatException e) {
                return String.valueOf(va).compareTo(String.valueOf(vb));
            }
        };

        copy.sort(desc ? cmp.reversed() : cmp);
        return copy;
    }

    // -------------------------------------------------------------------------
    // AC8 — @Filter
    // -------------------------------------------------------------------------

    /**
     * Returns elements where the comparison holds.
     *
     * <p>If {@code field} is non-empty, comparisons are on {@code element[field]};
     * otherwise on the element itself.
     * Operators: {@code ==}, {@code !=}, {@code >}, {@code <}, {@code >=}, {@code <=}
     * (numeric-coerced), {@code "contains"} (substring).
     *
     * Ref: Req 10 AC8
     */
    @SuppressWarnings("unchecked")
    public static Object filter(Object arr, Object field, Object op, Object val) {
        if (!(arr instanceof List<?> list)) return List.of();
        String fieldPath = (field instanceof String s && !s.isEmpty()) ? s : null;
        String operator  = op instanceof String s ? s : "==";

        List<Object> result = new ArrayList<>();
        for (Object item : list) {
            Object itemVal = fieldPath != null ? dotPath(item, fieldPath) : item;
            if (matchesFilter(itemVal, operator, val)) result.add(item);
        }
        return result;
    }

    private static boolean matchesFilter(Object itemVal, String op, Object filterVal) {
        return switch (op) {
            case "contains" -> Evaluator.toStr(itemVal).contains(Evaluator.toStr(filterVal));
            case "==" -> looseEq(itemVal, filterVal);
            case "!=" -> !looseEq(itemVal, filterVal);
            case ">"  -> Evaluator.toNumber(itemVal) >  Evaluator.toNumber(filterVal);
            case "<"  -> Evaluator.toNumber(itemVal) <  Evaluator.toNumber(filterVal);
            case ">=" -> Evaluator.toNumber(itemVal) >= Evaluator.toNumber(filterVal);
            case "<=" -> Evaluator.toNumber(itemVal) <= Evaluator.toNumber(filterVal);
            default   -> false;
        };
    }

    private static boolean looseEq(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        if (a.equals(b)) return true;
        return String.valueOf(a).equals(String.valueOf(b));
    }

    // -------------------------------------------------------------------------
    // AC9 — @Round
    // -------------------------------------------------------------------------

    /**
     * Returns {@code n} rounded to {@code decimals} decimal places (default 0).
     * Ref: Req 10 AC9
     */
    public static Object round(Object n, Object dec) {
        double val      = Evaluator.toNumber(n);
        int    decimals = dec != null ? (int) Evaluator.toNumber(dec) : 0;
        double factor   = Math.pow(10, decimals);
        return Math.round(val * factor) / factor;
    }

    // -------------------------------------------------------------------------
    // AC10 — @Abs
    // -------------------------------------------------------------------------

    /** Returns the absolute value. Ref: Req 10 AC10 */
    public static Object abs(Object n) {
        return Math.abs(Evaluator.toNumber(n));
    }

    // -------------------------------------------------------------------------
    // AC11 — @Floor / @Ceil
    // -------------------------------------------------------------------------

    /** Returns the floor. Ref: Req 10 AC11 */
    public static Object floor(Object n) {
        return Math.floor(Evaluator.toNumber(n));
    }

    /** Returns the ceiling. Ref: Req 10 AC11 */
    public static Object ceil(Object n) {
        return Math.ceil(Evaluator.toNumber(n));
    }

    // -------------------------------------------------------------------------
    // @Each — AST ref substitution helper (Req 10 AC12)
    // -------------------------------------------------------------------------

    /**
     * Walk {@code node}, replacing every {@link RefNode} whose name equals
     * {@code varName} with {@code LiteralNode(value)}.
     *
     * <p>All other nodes are returned unchanged or recursively substituted.
     * This substitution happens BEFORE evaluation so that deferred expressions
     * (e.g. {@code Action} steps) capture the correct per-item value.
     *
     * Ref: Req 10 AC12, Req 11 AC10
     */
    static Node substituteRef(Node node, String varName, Object value) {
        return switch (node) {
            case RefNode r when r.name().equals(varName) -> new LiteralNode(value);
            case ArrayNode a -> {
                List<Node> els = new ArrayList<>(a.elements().size());
                for (Node el : a.elements()) els.add(substituteRef(el, varName, value));
                yield new ArrayNode(els);
            }
            case ObjectNode o -> {
                Map<String, Node> entries = new LinkedHashMap<>();
                for (Map.Entry<String, Node> e : o.entries().entrySet())
                    entries.put(e.getKey(), substituteRef(e.getValue(), varName, value));
                yield new ObjectNode(entries);
            }
            case ElementNode e -> {
                Map<String, Node> props = new LinkedHashMap<>();
                for (Map.Entry<String, Node> p : e.props().entrySet())
                    props.put(p.getKey(), substituteRef(p.getValue(), varName, value));
                yield new ElementNode(e.typeName(), props, e.partial(), e.hasDynamicProps(), e.statementId());
            }
            case CallNode c -> {
                List<Node> args = new ArrayList<>(c.args().size());
                for (Node a : c.args()) args.add(substituteRef(a, varName, value));
                yield new CallNode(c.callee(), args);
            }
            case BuiltinCallNode bc -> {
                List<Node> args = new ArrayList<>(bc.args().size());
                for (Node a : bc.args()) args.add(substituteRef(a, varName, value));
                yield new BuiltinCallNode(bc.name(), args);
            }
            case BinaryNode b -> new BinaryNode(b.op(),
                    substituteRef(b.left(), varName, value),
                    substituteRef(b.right(), varName, value));
            case UnaryNode u  -> new UnaryNode(u.op(), substituteRef(u.operand(), varName, value));
            case MemberNode m -> new MemberNode(
                    substituteRef(m.object(), varName, value), m.property(), m.computed());
            case TernaryNode t -> new TernaryNode(
                    substituteRef(t.condition(), varName, value),
                    substituteRef(t.consequent(), varName, value),
                    substituteRef(t.alternate(), varName, value));
            case AssignNode a -> new AssignNode(a.target(), substituteRef(a.expr(), varName, value));
            default -> node; // LiteralNode, StateRefNode, RuntimeRefNode — no substitution
        };
    }

    // -------------------------------------------------------------------------
    // Dot-path helper (used by @Sort and @Filter)
    // -------------------------------------------------------------------------

    /**
     * Resolve a dot-separated path on an object (e.g. {@code "address.city"}).
     * Returns {@code null} if any segment is missing.
     */
    @SuppressWarnings("unchecked")
    static Object dotPath(Object obj, String path) {
        Object current = obj;
        for (String segment : path.split("\\.", -1)) {
            if (current instanceof java.util.Map<?, ?> map) {
                current = map.get(segment);
            } else {
                return null;
            }
        }
        return current;
    }
}
