package dev.openui.langcore.runtime;

import dev.openui.langcore.parser.ast.ArrayNode;
import dev.openui.langcore.parser.ast.BuiltinCallNode;
import dev.openui.langcore.parser.ast.LiteralNode;
import dev.openui.langcore.parser.ast.Node;
import dev.openui.langcore.parser.ast.RefNode;
import dev.openui.langcore.parser.ast.StateRefNode;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Builtins} — all Req 10 ACs.
 * Ref: Req 10.*
 */
class BuiltinsTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static List<Object> list(Object... items) { return List.of(items); }
    private static Map<String, Object> obj(String k1, Object v1) {
        Map<String, Object> m = new LinkedHashMap<>(); m.put(k1, v1); return m;
    }
    private static Map<String, Object> obj(String k1, Object v1, String k2, Object v2) {
        Map<String, Object> m = new LinkedHashMap<>(); m.put(k1, v1); m.put(k2, v2); return m;
    }

    private static final EvaluationContext EMPTY = new EvaluationContext() {
        @Override public Object getState(String n)  { return null; }
        @Override public Object resolveRef(String n){ return null; }
        @Override public Map<String, Object> extraScope() { return Map.of(); }
        @Override public Object getPropSchema(String n)   { return null; }
        @Override public EvaluationContext withExtraScope(Map<String, Object> s) { return this; }
    };

    private final Evaluator ev = new Evaluator();

    // -------------------------------------------------------------------------
    // AC1 — @Count
    // -------------------------------------------------------------------------

    @Test void count_array()    { assertEquals(3, Builtins.count(list(1, 2, 3))); }
    @Test void count_empty()    { assertEquals(0, Builtins.count(list())); }
    @Test void count_nonArray() { assertEquals(0, Builtins.count("not-array")); }

    // -------------------------------------------------------------------------
    // AC2 — @First
    // -------------------------------------------------------------------------

    @Test void first_returnsFirst()     { assertEquals(1, Builtins.first(list(1, 2, 3))); }
    @Test void first_emptyReturnsNull() { assertNull(Builtins.first(list())); }
    @Test void first_nonArrayNull()     { assertNull(Builtins.first(null)); }

    // -------------------------------------------------------------------------
    // AC3 — @Last
    // -------------------------------------------------------------------------

    @Test void last_returnsLast()       { assertEquals(3, Builtins.last(list(1, 2, 3))); }
    @Test void last_emptyReturnsNull()  { assertNull(Builtins.last(list())); }

    // -------------------------------------------------------------------------
    // AC4 — @Sum
    // -------------------------------------------------------------------------

    @Test void sum_numbers()  { assertEquals(6.0, Builtins.sum(list(1.0, 2.0, 3.0))); }
    @Test void sum_strings()  { assertEquals(6.0, Builtins.sum(list("1", "2", "3"))); }
    @Test void sum_nonArray() { assertEquals(0.0, Builtins.sum(null)); }

    // -------------------------------------------------------------------------
    // AC5 — @Avg
    // -------------------------------------------------------------------------

    @Test void avg_numbers()  { assertEquals(2.0, Builtins.avg(list(1.0, 2.0, 3.0))); }
    @Test void avg_empty()    { assertEquals(0.0, Builtins.avg(list())); }

    // -------------------------------------------------------------------------
    // AC6 — @Min / @Max
    // -------------------------------------------------------------------------

    @Test void min_returns()  { assertEquals(1.0, Builtins.min(list(3.0, 1.0, 2.0))); }
    @Test void max_returns()  { assertEquals(3.0, Builtins.max(list(3.0, 1.0, 2.0))); }
    @Test void min_empty()    { assertEquals(0.0, Builtins.min(list())); }

    // -------------------------------------------------------------------------
    // AC7 — @Sort
    // -------------------------------------------------------------------------

    @Test
    void sort_ascending_numbers() {
        List<?> result = (List<?>) Builtins.sort(list(3.0, 1.0, 2.0), "", null);
        assertEquals(List.of(1.0, 2.0, 3.0), result);
    }

    @Test
    void sort_descending() {
        List<?> result = (List<?>) Builtins.sort(list(1.0, 3.0, 2.0), "", "desc");
        assertEquals(List.of(3.0, 2.0, 1.0), result);
    }

    @Test
    void sort_byField() {
        List<Object> data = list(obj("age", 30.0), obj("age", 10.0), obj("age", 20.0));
        List<?> result = (List<?>) Builtins.sort(data, "age", null);
        assertEquals(10.0, ((Map<?, ?>) result.get(0)).get("age"));
        assertEquals(30.0, ((Map<?, ?>) result.get(2)).get("age"));
    }

    @Test
    void sort_numericStrings_sortNumerically() {
        List<?> result = (List<?>) Builtins.sort(list("10", "9", "2"), "", null);
        assertEquals(List.of("2", "9", "10"), result);
    }

    @Test
    void sort_dotPath() {
        List<Object> data = list(
                obj("address", obj("zip", "20000")),
                obj("address", obj("zip", "10000")));
        List<?> result = (List<?>) Builtins.sort(data, "address.zip", null);
        assertEquals("10000", ((Map<?, ?>) ((Map<?, ?>) result.get(0)).get("address")).get("zip"));
    }

    // -------------------------------------------------------------------------
    // AC8 — @Filter
    // -------------------------------------------------------------------------

    @Test
    void filter_equals() {
        List<?> r = (List<?>) Builtins.filter(list(1.0, 2.0, 3.0), "", "==", 2.0);
        assertEquals(List.of(2.0), r);
    }

    @Test
    void filter_notEquals() {
        List<?> r = (List<?>) Builtins.filter(list(1.0, 2.0, 3.0), "", "!=", 2.0);
        assertEquals(List.of(1.0, 3.0), r);
    }

    @Test
    void filter_greaterThan() {
        List<?> r = (List<?>) Builtins.filter(list(1.0, 2.0, 3.0), "", ">", 1.0);
        assertEquals(List.of(2.0, 3.0), r);
    }

    @Test
    void filter_lessThan() {
        List<?> r = (List<?>) Builtins.filter(list(1.0, 2.0, 3.0), "", "<", 3.0);
        assertEquals(List.of(1.0, 2.0), r);
    }

    @Test
    void filter_greaterOrEqual() {
        List<?> r = (List<?>) Builtins.filter(list(1.0, 2.0, 3.0), "", ">=", 2.0);
        assertEquals(List.of(2.0, 3.0), r);
    }

    @Test
    void filter_contains() {
        List<Object> data = list("apple", "banana", "apricot");
        List<?> r = (List<?>) Builtins.filter(data, "", "contains", "ap");
        assertEquals(List.of("apple", "apricot"), r);
    }

    @Test
    void filter_byField() {
        List<Object> data = list(obj("status", "active"), obj("status", "inactive"), obj("status", "active"));
        List<?> r = (List<?>) Builtins.filter(data, "status", "==", "active");
        assertEquals(2, r.size());
    }

    // -------------------------------------------------------------------------
    // AC9 — @Round
    // -------------------------------------------------------------------------

    @Test void round_default()   { assertEquals(3.0, Builtins.round(3.14159, null)); }
    @Test void round_decimals()  { assertEquals(3.14, Builtins.round(3.14159, 2.0)); }
    @Test void round_negative()  { assertEquals(-3.0, Builtins.round(-3.14, null)); }

    // -------------------------------------------------------------------------
    // AC10 — @Abs
    // -------------------------------------------------------------------------

    @Test void abs_positive()  { assertEquals(5.0, Builtins.abs(5.0)); }
    @Test void abs_negative()  { assertEquals(5.0, Builtins.abs(-5.0)); }

    // -------------------------------------------------------------------------
    // AC11 — @Floor / @Ceil
    // -------------------------------------------------------------------------

    @Test void floor_value()  { assertEquals(3.0, Builtins.floor(3.9)); }
    @Test void ceil_value()   { assertEquals(4.0, Builtins.ceil(3.1)); }

    // -------------------------------------------------------------------------
    // AC12 — @Each loop var substitution
    // -------------------------------------------------------------------------

    @Test
    void each_substitutesLoopVar() {
        // @Each(["a","b","c"], item, item) — result should be ["a","b","c"]
        Node arr      = new ArrayNode(List.of(new LiteralNode("a"), new LiteralNode("b"), new LiteralNode("c")));
        Node varName  = new LiteralNode("item");
        Node template = new RefNode("item");

        Node call = new BuiltinCallNode("Each", List.of(arr, varName, template));
        Object result = ev.evaluate(call, EMPTY);
        assertEquals(List.of("a", "b", "c"), result);
    }

    @Test
    void each_varNameAsRefNode() {
        // varName passed as a bare RefNode identifier
        Node arr      = new ArrayNode(List.of(new LiteralNode(1.0), new LiteralNode(2.0)));
        Node varName  = new RefNode("x");
        Node template = new RefNode("x");

        Node call = new BuiltinCallNode("Each", List.of(arr, varName, template));
        Object result = ev.evaluate(call, EMPTY);
        assertEquals(List.of(1.0, 2.0), result);
    }

    @Test
    void each_emptyArray_returnsEmpty() {
        Node arr      = new ArrayNode(List.of());
        Node varName  = new LiteralNode("x");
        Node template = new RefNode("x");

        Node call = new BuiltinCallNode("Each", List.of(arr, varName, template));
        assertEquals(List.of(), ev.evaluate(call, EMPTY));
    }

    // -------------------------------------------------------------------------
    // AC13 — Action null filtering
    // -------------------------------------------------------------------------

    @Test
    void action_filtersNullSteps() {
        // @Reset with no StateRef args returns null; Action should filter it out
        Node reset   = new BuiltinCallNode("Reset", List.of(new LiteralNode("notAStateRef")));
        Node openUrl = new BuiltinCallNode("OpenUrl", List.of(new LiteralNode("https://x.com")));
        Node arr     = new ArrayNode(List.of(reset, openUrl));
        Node action  = new dev.openui.langcore.parser.ast.CallNode("Action", List.of(arr));

        Object result = ev.evaluate(action, EMPTY);
        assertInstanceOf(ActionPlan.class, result);
        ActionPlan plan = (ActionPlan) result;
        assertEquals(1, plan.steps().size());
        assertInstanceOf(ActionStep.OpenUrlStep.class, plan.steps().get(0));
    }

    // -------------------------------------------------------------------------
    // AC17 — @Set with non-StateRef first arg returns null
    // -------------------------------------------------------------------------

    @Test
    void set_nonStateRef_returnsNull() {
        Node call = new BuiltinCallNode("Set", List.of(new LiteralNode("x"), new LiteralNode(1.0)));
        assertNull(ev.evaluate(call, EMPTY));
    }

    // -------------------------------------------------------------------------
    // substituteRef — does not mutate non-matching RefNodes
    // -------------------------------------------------------------------------

    @Test
    void substituteRef_doesNotTouchOtherRefs() {
        Node template = new RefNode("other");
        Node result   = Builtins.substituteRef(template, "item", "value");
        assertSame(template, result, "Non-matching RefNode should be returned unchanged");
    }
}
