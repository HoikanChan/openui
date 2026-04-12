package dev.openui.langcore.runtime;

import dev.openui.langcore.parser.ast.ArrayNode;
import dev.openui.langcore.parser.ast.AssignNode;
import dev.openui.langcore.parser.ast.BinaryNode;
import dev.openui.langcore.parser.ast.BuiltinCallNode;
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
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Evaluator} and {@link PropEvaluator}.
 * Ref: Req 11.*
 */
class EvaluatorTest {

    // -------------------------------------------------------------------------
    // Test context helper
    // -------------------------------------------------------------------------

    private static EvaluationContext ctx(Map<String, Object> state) {
        return ctx(state, Map.of(), null);
    }

    private static EvaluationContext ctx(Map<String, Object> state,
                                          Map<String, Object> extraScope,
                                          Object propSchema) {
        return new EvaluationContext() {
            @Override public Object getState(String name)     { return state.get(name); }
            @Override public Object resolveRef(String name)   { return state.get(name); }
            @Override public Map<String, Object> extraScope() { return extraScope; }
            @Override public Object getPropSchema(String name){ return propSchema; }
            @Override public EvaluationContext withExtraScope(Map<String, Object> scope) {
                return ctx(state, scope, propSchema);
            }
        };
    }

    private static final EvaluationContext EMPTY = ctx(Map.of());

    private final Evaluator ev = new Evaluator();

    private Object eval(Node node) { return ev.evaluate(node, EMPTY); }
    private Object eval(Node node, EvaluationContext c) { return ev.evaluate(node, c); }

    private static LiteralNode lit(Object v) { return new LiteralNode(v); }

    // -------------------------------------------------------------------------
    // AC1 — literal evaluation
    // -------------------------------------------------------------------------

    @Test void literal_string()  { assertEquals("hi",  eval(lit("hi"))); }
    @Test void literal_number()  { assertEquals(42.0,  eval(lit(42.0))); }
    @Test void literal_bool()    { assertEquals(true,  eval(lit(true))); }
    @Test void literal_null()    { assertNull(eval(lit(null))); }

    // -------------------------------------------------------------------------
    // AC2 — state and ref resolution
    // -------------------------------------------------------------------------

    @Test
    void stateRef_resolvesFromContext() {
        EvaluationContext c = ctx(Map.of("$count", 5.0));
        assertEquals(5.0, eval(new StateRefNode("$count"), c));
    }

    @Test
    void ref_resolvesFromContext() {
        EvaluationContext c = ctx(Map.of("myVar", "hello"));
        assertEquals("hello", eval(new RefNode("myVar"), c));
    }

    @Test
    void runtimeRef_resolvesFromContext() {
        EvaluationContext c = ctx(Map.of("data", List.of(1, 2)));
        assertEquals(List.of(1, 2), eval(new RuntimeRefNode("data", "query"), c));
    }

    // -------------------------------------------------------------------------
    // AC3 — extraScope takes precedence over getState
    // -------------------------------------------------------------------------

    @Test
    void extraScope_takesPrecedenceOverState() {
        EvaluationContext c = ctx(Map.of("$x", "from-state"), Map.of("$x", "from-scope"), null);
        assertEquals("from-scope", eval(new StateRefNode("$x"), c));
    }

    // -------------------------------------------------------------------------
    // AC4 — AssignNode → ReactiveAssign
    // -------------------------------------------------------------------------

    @Test
    void assignNode_returnsReactiveAssign() {
        Node expr = lit("newVal");
        Object result = eval(new AssignNode("$count", expr));
        assertInstanceOf(ReactiveAssign.class, result);
        ReactiveAssign ra = (ReactiveAssign) result;
        assertEquals("$count", ra.target());
        assertEquals(expr, ra.expr());
    }

    // -------------------------------------------------------------------------
    // Req 3 AC3 — + string concat with null coercion
    // -------------------------------------------------------------------------

    @Test
    void add_stringConcat() {
        Object r = eval(new BinaryNode("+", lit("hello "), lit("world")));
        assertEquals("hello world", r);
    }

    @Test
    void add_nullConcatToEmptyString() {
        Object r = eval(new BinaryNode("+", lit("value="), lit(null)));
        assertEquals("value=", r);
    }

    @Test
    void add_numericWhenBothNumbers() {
        Object r = eval(new BinaryNode("+", lit(3.0), lit(4.0)));
        assertEquals(7.0, r);
    }

    // -------------------------------------------------------------------------
    // Req 3 AC4 — div/mod by zero → 0
    // -------------------------------------------------------------------------

    @Test
    void divide_byZero_returnsZero() {
        Object r = eval(new BinaryNode("/", lit(10.0), lit(0.0)));
        assertEquals(0.0, r);
    }

    @Test
    void modulo_byZero_returnsZero() {
        Object r = eval(new BinaryNode("%", lit(10.0), lit(0.0)));
        assertEquals(0.0, r);
    }

    // -------------------------------------------------------------------------
    // Req 3 AC5 — loose ==
    // -------------------------------------------------------------------------

    @Test
    void looseEquals_sameType_equal()    { assertEquals(true,  eval(new BinaryNode("==", lit(1.0), lit(1.0)))); }
    @Test
    void looseEquals_stringVsNum()       { assertEquals(true,  eval(new BinaryNode("==", lit("1"), lit(1.0)))); }
    @Test
    void looseNotEquals()                { assertEquals(true,  eval(new BinaryNode("!=", lit("a"), lit("b")))); }

    // -------------------------------------------------------------------------
    // Req 3 AC6 — comparison coercion
    // -------------------------------------------------------------------------

    @Test
    void comparison_greaterThan()   { assertEquals(true,  eval(new BinaryNode(">",  lit(5.0),  lit(3.0)))); }
    @Test
    void comparison_lessOrEqual()   { assertEquals(true,  eval(new BinaryNode("<=", lit(3.0),  lit(3.0)))); }

    // -------------------------------------------------------------------------
    // Req 3 AC7 — short-circuit &&/||
    // -------------------------------------------------------------------------

    @Test
    void and_shortCircuitFalse() {
        // left is false → right should not be evaluated (returns left)
        Object r = eval(new BinaryNode("&&", lit(false), lit("never")));
        assertEquals(false, r);
    }

    @Test
    void or_shortCircuitTrue() {
        Object r = eval(new BinaryNode("||", lit("first"), lit("never")));
        assertEquals("first", r);
    }

    // -------------------------------------------------------------------------
    // Unary ops
    // -------------------------------------------------------------------------

    @Test
    void unary_not_true()  { assertEquals(false, eval(new UnaryNode("!", lit(true)))); }
    @Test
    void unary_not_false() { assertEquals(true,  eval(new UnaryNode("!", lit(false)))); }
    @Test
    void unary_negate()    { assertEquals(-5.0,  eval(new UnaryNode("-", lit(5.0)))); }

    // -------------------------------------------------------------------------
    // Ternary
    // -------------------------------------------------------------------------

    @Test
    void ternary_trueBranch()  { assertEquals("yes", eval(new TernaryNode(lit(true),  lit("yes"), lit("no")))); }
    @Test
    void ternary_falseBranch() { assertEquals("no",  eval(new TernaryNode(lit(false), lit("yes"), lit("no")))); }

    // -------------------------------------------------------------------------
    // Req 3 AC9 — member access pluck / .length
    // -------------------------------------------------------------------------

    @Test
    void member_dotLength_onList() {
        Node arr = new ArrayNode(List.of(lit("a"), lit("b"), lit("c")));
        Object r = eval(new MemberNode(arr, lit("length"), false));
        assertEquals(3, r);
    }

    @Test
    void member_dotField_onList_plucks() {
        Map<String, Node> e1 = new LinkedHashMap<>(); e1.put("name", lit("Alice"));
        Map<String, Node> e2 = new LinkedHashMap<>(); e2.put("name", lit("Bob"));
        Node arr = new ArrayNode(List.of(new ObjectNode(e1), new ObjectNode(e2)));
        Object r = eval(new MemberNode(arr, lit("name"), false));
        assertEquals(List.of("Alice", "Bob"), r);
    }

    @Test
    void member_dotField_onObject() {
        Map<String, Node> entries = new LinkedHashMap<>();
        entries.put("x", lit(42.0));
        Node obj = new ObjectNode(entries);
        Object r = eval(new MemberNode(obj, lit("x"), false));
        assertEquals(42.0, r);
    }

    // -------------------------------------------------------------------------
    // Req 3 AC10 — computed index access
    // -------------------------------------------------------------------------

    @Test
    void member_computedIndex_onList() {
        Node arr = new ArrayNode(List.of(lit("a"), lit("b"), lit("c")));
        Object r = eval(new MemberNode(arr, lit(1.0), true));
        assertEquals("b", r);
    }

    @Test
    void member_computedKey_onMap() {
        Map<String, Node> entries = new LinkedHashMap<>();
        entries.put("foo", lit("bar"));
        Node obj = new ObjectNode(entries);
        Object r = eval(new MemberNode(obj, lit("foo"), true));
        assertEquals("bar", r);
    }

    // -------------------------------------------------------------------------
    // Array and Object nodes
    // -------------------------------------------------------------------------

    @Test
    void arrayNode_evaluatesElements() {
        Node arr = new ArrayNode(List.of(lit(1.0), lit(2.0), lit(3.0)));
        assertEquals(List.of(1.0, 2.0, 3.0), eval(arr));
    }

    @Test
    void objectNode_evaluatesValues() {
        Map<String, Node> entries = new LinkedHashMap<>();
        entries.put("a", lit(1.0));
        entries.put("b", lit("two"));
        Object r = eval(new ObjectNode(entries));
        assertInstanceOf(Map.class, r);
        Map<?, ?> m = (Map<?, ?>) r;
        assertEquals(1.0, m.get("a"));
        assertEquals("two", m.get("b"));
    }

    // -------------------------------------------------------------------------
    // AC7 — hasDynamicProps=false skips evaluation
    // -------------------------------------------------------------------------

    @Test
    void staticElement_returnedUnchanged() {
        Map<String, Node> props = new LinkedHashMap<>();
        props.put("label", lit("hello"));
        ElementNode elem = new ElementNode("Button", props, false, false, "root");
        Object r = eval(elem);
        assertSame(elem, r, "Static element (hasDynamicProps=false) must be returned as-is");
    }

    // -------------------------------------------------------------------------
    // AC8 — reactive prop emits ReactiveAssign
    // -------------------------------------------------------------------------

    @Test
    void reactiveState_emitsReactiveAssign() {
        Object schema = new Object();
        ReactiveSchemas.markReactive(schema);
        EvaluationContext c = ctx(Map.of("$val", "current"), Map.of(), schema);
        Object r = eval(new StateRefNode("$val"), c);
        assertInstanceOf(ReactiveAssign.class, r);
        assertEquals("$val", ((ReactiveAssign) r).target());
    }

    // -------------------------------------------------------------------------
    // AC9 — non-reactive prop strips stray ReactiveAssign
    // -------------------------------------------------------------------------

    @Test
    void nonReactiveProp_stripsReactiveAssign() {
        // A dynamic element whose prop is a $state ref, but schema is NOT reactive
        Map<String, Node> props = new LinkedHashMap<>();
        props.put("value", new StateRefNode("$count"));
        ElementNode elem = new ElementNode("Input", props, false, true, "root");

        EvaluationContext c = ctx(Map.of("$count", 7.0), Map.of(), null /* no reactive schema */);
        Object result = eval(elem, c);

        assertInstanceOf(ElementNode.class, result);
        ElementNode evaluated = (ElementNode) result;
        // The prop should be the concrete state value, not a ReactiveAssign
        Object propVal = ((LiteralNode) evaluated.props().get("value")).value();
        assertEquals(7.0, propVal);
    }

    // -------------------------------------------------------------------------
    // PropEvaluator — AC6-7
    // -------------------------------------------------------------------------

    @Test
    void propEvaluator_staticNode_returnedUnchanged() {
        Map<String, Node> props = new LinkedHashMap<>();
        props.put("label", lit("hi"));
        ElementNode elem = new ElementNode("Button", props, false, false, "root");
        PropEvaluator pe = new PropEvaluator();
        assertSame(elem, pe.evaluateElementProps(elem, EMPTY));
    }

    @Test
    void propEvaluator_dynamicNode_evaluatesProps() {
        Map<String, Node> props = new LinkedHashMap<>();
        props.put("label", new StateRefNode("$title"));
        ElementNode elem = new ElementNode("Button", props, false, true, "root");

        EvaluationContext c = ctx(Map.of("$title", "Click me"));
        PropEvaluator pe = new PropEvaluator();
        ElementNode result = pe.evaluateElementProps(elem, c);

        assertNotSame(elem, result);
        assertEquals("Click me", ((LiteralNode) result.props().get("label")).value());
    }

    // -------------------------------------------------------------------------
    // Action builtins
    // -------------------------------------------------------------------------

    @Test
    void action_emptyList_returnsEmptyPlan() {
        // Action([]) — the argument is an empty array node
        Node call = new dev.openui.langcore.parser.ast.CallNode("Action",
                List.of(new ArrayNode(List.of())));
        Object r = eval(call);
        assertInstanceOf(ActionPlan.class, r);
        assertTrue(((ActionPlan) r).steps().isEmpty());
    }

    @Test
    void builtin_toAssistant_returnsStep() {
        Node call = new BuiltinCallNode("ToAssistant", List.of(lit("hello"), lit("ctx")));
        Object r = eval(call);
        assertInstanceOf(ActionStep.ToAssistantStep.class, r);
        ActionStep.ToAssistantStep step = (ActionStep.ToAssistantStep) r;
        assertEquals("hello", step.message());
        assertEquals("ctx", step.context());
    }

    @Test
    void builtin_openUrl_returnsStep() {
        Node call = new BuiltinCallNode("OpenUrl", List.of(lit("https://example.com")));
        Object r = eval(call);
        assertInstanceOf(ActionStep.OpenUrlStep.class, r);
        assertEquals("https://example.com", ((ActionStep.OpenUrlStep) r).url());
    }

    @Test
    void builtin_set_returnsSetStep() {
        Node call = new BuiltinCallNode("Set", List.of(new StateRefNode("$x"), lit(42.0)));
        Object r = eval(call);
        assertInstanceOf(ActionStep.SetStep.class, r);
        ActionStep.SetStep step = (ActionStep.SetStep) r;
        assertEquals("$x", step.target());
        assertEquals(lit(42.0), step.valueAST());
    }

    @Test
    void builtin_set_nonStateRef_returnsNull() {
        Node call = new BuiltinCallNode("Set", List.of(lit("notAStateRef"), lit(1.0)));
        assertNull(eval(call));
    }

    @Test
    void builtin_reset_returnsResetStep() {
        Node call = new BuiltinCallNode("Reset", List.of(new StateRefNode("$a"), new StateRefNode("$b")));
        Object r = eval(call);
        assertInstanceOf(ActionStep.ResetStep.class, r);
        assertEquals(List.of("$a", "$b"), ((ActionStep.ResetStep) r).targets());
    }

    @Test
    void builtin_reset_noStateArgs_returnsNull() {
        Node call = new BuiltinCallNode("Reset", List.of(lit("x")));
        assertNull(eval(call));
    }

    @Test
    void builtin_run_runtimeRef_returnsRunStep() {
        Node call = new BuiltinCallNode("Run", List.of(new RuntimeRefNode("fetchData", "query")));
        Object r = eval(call);
        assertInstanceOf(ActionStep.RunStep.class, r);
        ActionStep.RunStep step = (ActionStep.RunStep) r;
        assertEquals("fetchData", step.statementId());
        assertEquals("query", step.refType());
    }
}
