package dev.openui.langcore.parser;

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
import dev.openui.langcore.parser.result.MutationStatementInfo;
import dev.openui.langcore.parser.result.ParseMeta;
import dev.openui.langcore.parser.result.ParseResult;
import dev.openui.langcore.parser.result.QueryStatementInfo;
import dev.openui.langcore.parser.result.ValidationError;
import dev.openui.langcore.parser.stmt.MutationStatement;
import dev.openui.langcore.parser.stmt.NullStatement;
import dev.openui.langcore.parser.stmt.QueryStatement;
import dev.openui.langcore.parser.stmt.StateStatement;
import dev.openui.langcore.parser.stmt.Statement;
import dev.openui.langcore.parser.stmt.ValueStatement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Converts CallNodes (component calls) into ElementNodes by mapping positional
 * arguments to named props via the SchemaRegistry, and validates the mapping.
 *
 * Ref: Req 4 AC1-8
 */
public final class Materializer {

    private final SchemaRegistry schema;
    private final boolean wasIncomplete;
    private final String rootName;
    private final List<ValidationError> errors = new ArrayList<>();

    public Materializer(SchemaRegistry schema, boolean wasIncomplete) {
        this(schema, wasIncomplete, null);
    }

    public Materializer(SchemaRegistry schema, boolean wasIncomplete, String rootName) {
        this.schema = schema;
        this.wasIncomplete = wasIncomplete;
        this.rootName = rootName;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Materializes all statements into a {@link ParseResult}.
     *
     * Implements root selection (Req 6 AC1-6), BFS reachability analysis,
     * unresolved ref detection, orphaned statement collection, and assembly
     * of stateDeclarations, queryStatements, mutationStatements.
     *
     * Ref: Req 5, Req 6
     */
    public ParseResult materialize(Map<String, Statement> statements) {

        // -----------------------------------------------------------------------
        // Pass 1: materialize ValueStatements; build stateDeclarations,
        //         queryStatements, mutationStatements
        // -----------------------------------------------------------------------

        Map<String, Node> materializedNodes = new LinkedHashMap<>();
        Map<String, Object> stateDeclarations = new LinkedHashMap<>();
        List<QueryStatementInfo> queryStatements = new ArrayList<>();
        List<MutationStatementInfo> mutationStatements = new ArrayList<>();

        for (Map.Entry<String, Statement> entry : statements.entrySet()) {
            String id = entry.getKey();
            Statement stmt = entry.getValue();

            switch (stmt) {
                case ValueStatement vs -> {
                    Node materialized = materializeNode(vs.expr(), id);
                    materializedNodes.put(id, materialized);
                }
                case StateStatement ss -> {
                    Object defaultValue = null;
                    if (ss.defaultExpr() instanceof LiteralNode lit) {
                        defaultValue = lit.value();
                    }
                    stateDeclarations.put(id, defaultValue);
                }
                case QueryStatement qs -> {
                    List<String> deps = collectStateRefNames(qs.argsAST());
                    boolean complete = qs.toolAST() != null && qs.argsAST() != null;
                    queryStatements.add(new QueryStatementInfo(
                            id, qs.toolAST(), qs.argsAST(), qs.defaultsAST(), qs.refreshAST(), deps, complete));
                }
                case MutationStatement ms -> {
                    mutationStatements.add(new MutationStatementInfo(id, ms.toolAST(), ms.argsAST()));
                }
                case NullStatement __ -> { /* ignore */ }
            }
        }

        // -----------------------------------------------------------------------
        // Pass 2: root selection (Req 6 AC1-6)
        // -----------------------------------------------------------------------

        String rootId = selectRootId(statements, materializedNodes);

        ElementNode root = null;
        if (rootId != null) {
            Node rootNode = materializedNodes.get(rootId);
            if (rootNode instanceof ElementNode elem) {
                root = elem;
            }
        }

        // -----------------------------------------------------------------------
        // Pass 3: BFS from root to collect reachable statement IDs,
        //         unresolved ref names, and StateRefNode names
        // -----------------------------------------------------------------------

        Set<String> reachable = new LinkedHashSet<>();
        Set<String> allRefNames = new LinkedHashSet<>();
        Set<String> encounteredStateRefs = new LinkedHashSet<>();

        if (root != null && rootId != null) {
            // BFS
            Set<String> visited = new HashSet<>();
            List<String> queue = new ArrayList<>();
            queue.add(rootId);
            visited.add(rootId);

            while (!queue.isEmpty()) {
                String currentId = queue.remove(0);
                reachable.add(currentId);

                Node currentNode = materializedNodes.get(currentId);
                if (currentNode == null) continue;

                // Walk the node tree collecting refs and state refs
                collectRefs(currentNode, allRefNames, encounteredStateRefs);

                // Enqueue unvisited neighbors that are known statement IDs
                for (String refName : new ArrayList<>(allRefNames)) {
                    if (!visited.contains(refName) && materializedNodes.containsKey(refName)) {
                        visited.add(refName);
                        queue.add(refName);
                    }
                }
            }
        }

        // -----------------------------------------------------------------------
        // unresolved: ref names not present as any statement ID
        // -----------------------------------------------------------------------

        List<String> unresolved = new ArrayList<>();
        for (String refName : allRefNames) {
            if (!statements.containsKey(refName)) {
                unresolved.add(refName);
            }
        }

        // -----------------------------------------------------------------------
        // orphaned: ValueStatement IDs not reachable from root
        // -----------------------------------------------------------------------

        List<String> orphaned = new ArrayList<>();
        for (Map.Entry<String, Statement> entry : statements.entrySet()) {
            if (entry.getValue() instanceof ValueStatement) {
                String id = entry.getKey();
                if (!reachable.contains(id)) {
                    orphaned.add(id);
                }
            }
        }

        // -----------------------------------------------------------------------
        // Req 5 AC8: add StateRefNodes encountered in reachable nodes that have
        // no StateStatement to stateDeclarations with value null
        // -----------------------------------------------------------------------

        for (String stateRefName : encounteredStateRefs) {
            if (!stateDeclarations.containsKey(stateRefName)) {
                stateDeclarations.put(stateRefName, null);
            }
        }

        // -----------------------------------------------------------------------
        // Assemble ParseMeta and ParseResult
        // -----------------------------------------------------------------------

        ParseMeta meta = new ParseMeta(
                wasIncomplete,
                Collections.unmodifiableList(unresolved),
                Collections.unmodifiableList(orphaned),
                statements.size(),
                Collections.unmodifiableList(new ArrayList<>(errors))
        );

        return new ParseResult(
                root,
                meta,
                Collections.unmodifiableMap(stateDeclarations),
                Collections.unmodifiableList(queryStatements),
                Collections.unmodifiableList(mutationStatements)
        );
    }

    // -------------------------------------------------------------------------
    // Root selection (Req 6 AC1-6)
    // -------------------------------------------------------------------------

    private String selectRootId(Map<String, Statement> statements, Map<String, Node> materializedNodes) {
        // AC1: statement named "root"
        if (statements.containsKey("root")) {
            return "root";
        }

        // AC2: rootName passed and a statement with that name exists
        if (rootName != null && statements.containsKey(rootName)) {
            return rootName;
        }

        // AC3: rootName passed and a statement whose expr is a CallNode with callee == rootName
        if (rootName != null) {
            for (Map.Entry<String, Statement> entry : statements.entrySet()) {
                if (entry.getValue() instanceof ValueStatement vs) {
                    if (vs.expr() instanceof CallNode call && rootName.equals(call.callee())) {
                        return entry.getKey();
                    }
                }
            }
        }

        // AC4: first ValueStatement whose materialized expr is an ElementNode (component call, not builtin)
        for (Map.Entry<String, Statement> entry : statements.entrySet()) {
            if (entry.getValue() instanceof ValueStatement) {
                String id = entry.getKey();
                Node node = materializedNodes.get(id);
                if (node instanceof ElementNode elem) {
                    // Not a builtin (Query/Mutation are handled as separate statement types,
                    // but guard against schema-registered builtins)
                    if (!"Query".equals(elem.typeName()) && !"Mutation".equals(elem.typeName())) {
                        return id;
                    }
                }
            }
        }

        // AC5: first statement overall
        if (!statements.isEmpty()) {
            return statements.keySet().iterator().next();
        }

        // AC6: no statements → null
        return null;
    }

    // -------------------------------------------------------------------------
    // Ref / StateRef collectors
    // -------------------------------------------------------------------------

    /**
     * Walk a node tree collecting RefNode/RuntimeRefNode names into {@code refNames}
     * and StateRefNode names into {@code stateRefNames}.
     */
    private static void collectRefs(Node node, Set<String> refNames, Set<String> stateRefNames) {
        switch (node) {
            case RefNode ref -> refNames.add(ref.name());
            case RuntimeRefNode rr -> refNames.add(rr.name());
            case StateRefNode sr -> stateRefNames.add(sr.name());
            case ElementNode elem -> {
                for (Node prop : elem.props().values()) {
                    collectRefs(prop, refNames, stateRefNames);
                }
            }
            case ArrayNode a -> {
                for (Node elem : a.elements()) {
                    collectRefs(elem, refNames, stateRefNames);
                }
            }
            case ObjectNode o -> {
                for (Node val : o.entries().values()) {
                    collectRefs(val, refNames, stateRefNames);
                }
            }
            case BinaryNode b -> {
                collectRefs(b.left(), refNames, stateRefNames);
                collectRefs(b.right(), refNames, stateRefNames);
            }
            case UnaryNode u -> collectRefs(u.operand(), refNames, stateRefNames);
            case MemberNode m -> collectRefs(m.object(), refNames, stateRefNames);
            case TernaryNode t -> {
                collectRefs(t.condition(), refNames, stateRefNames);
                collectRefs(t.consequent(), refNames, stateRefNames);
                collectRefs(t.alternate(), refNames, stateRefNames);
            }
            case AssignNode a -> collectRefs(a.expr(), refNames, stateRefNames);
            default -> { /* LiteralNode, BuiltinCallNode, etc. — no nested refs */ }
        }
    }

    /**
     * Collect all {@link StateRefNode} names from an AST (used for Query deps).
     */
    private static List<String> collectStateRefNames(Node node) {
        if (node == null) return Collections.emptyList();
        Set<String> stateRefs = new LinkedHashSet<>();
        Set<String> ignored = new LinkedHashSet<>();
        collectRefs(node, ignored, stateRefs);
        return new ArrayList<>(stateRefs);
    }

    /** Return accumulated validation errors. */
    public List<ValidationError> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    // -------------------------------------------------------------------------
    // Prop mapping (AC1-8)
    // -------------------------------------------------------------------------

    /**
     * Convert a CallNode (component call) into an ElementNode with named props.
     *
     * Returns null and records a ValidationError for unknown-component,
     * missing-required, or null-required. Drops excess args with an excess-args
     * error (AC3). isInline=true means the call appears as a prop value (not a
     * top-level statement RHS) — Query/Mutation calls in that position trigger
     * inline-reserved (AC7).
     *
     * Ref: Req 4 AC1-8
     */
    ElementNode materializeCall(CallNode call, String statementId, boolean isInline) {
        String typeName = call.callee();

        // AC7: Query or Mutation used inline
        if (isInline && ("Query".equals(typeName) || "Mutation".equals(typeName))) {
            errors.add(new ValidationError(
                    "inline-reserved",
                    typeName,
                    statementId,
                    "'" + typeName + "' cannot be used as an inline value (statement " + statementId + ")"
            ));
            return null;
        }

        // AC6: unknown component
        Optional<ComponentSchema> schemaOpt = schema.lookup(typeName);
        if (schemaOpt.isEmpty()) {
            errors.add(new ValidationError(
                    "unknown-component",
                    typeName,
                    statementId,
                    "Unknown component '" + typeName + "' (statement " + statementId + ")"
            ));
            return null;
        }

        ComponentSchema componentSchema = schemaOpt.get();
        List<PropDef> propDefs = componentSchema.props();
        List<Node> args = call.args();

        // AC3: excess args
        if (args.size() > propDefs.size()) {
            errors.add(new ValidationError(
                    "excess-args",
                    typeName,
                    statementId,
                    "Component '" + typeName + "' received " + args.size() + " args but only accepts "
                            + propDefs.size() + " (statement " + statementId + ")"
            ));
        }

        Map<String, Node> props = new LinkedHashMap<>();
        boolean invalid = false;

        for (int i = 0; i < propDefs.size(); i++) {
            PropDef propDef = propDefs.get(i);
            Node arg = (i < args.size()) ? args.get(i) : null;

            if (arg == null) {
                // AC4: missing arg
                if (propDef.required() && propDef.defaultValue() == null) {
                    errors.add(new ValidationError(
                            "missing-required",
                            typeName,
                            statementId,
                            "Required prop '" + propDef.name() + "' of '" + typeName
                                    + "' is missing (statement " + statementId + ")"
                    ));
                    invalid = true;
                } else if (propDef.defaultValue() != null) {
                    props.put(propDef.name(), new LiteralNode(propDef.defaultValue()));
                }
                // optional with no default: skip (prop omitted)
            } else {
                Node materializedArg = materializeNode(arg, statementId);

                // AC5: arg explicitly null and prop is required
                if (materializedArg instanceof LiteralNode lit && lit.value() == null && propDef.required()) {
                    if (propDef.defaultValue() != null) {
                        props.put(propDef.name(), new LiteralNode(propDef.defaultValue()));
                    } else {
                        errors.add(new ValidationError(
                                "null-required",
                                typeName,
                                statementId,
                                "Required prop '" + propDef.name() + "' of '" + typeName
                                        + "' is null (statement " + statementId + ")"
                        ));
                        invalid = true;
                    }
                } else {
                    props.put(propDef.name(), materializedArg);
                }
            }
        }

        if (invalid) {
            return null;
        }

        boolean hasDynamic = computeHasDynamicProps(props);
        return new ElementNode(typeName, props, false, hasDynamic, statementId);
    }

    /**
     * Recursively materialize all CallNodes within an expression node.
     * CallNodes become ElementNodes (or LiteralNode(null) if materialization
     * fails). All other composite nodes are walked recursively.
     */
    Node materializeNode(Node node, String statementId) {
        return switch (node) {
            case CallNode call -> {
                ElementNode elem = materializeCall(call, statementId, true);
                yield elem != null ? elem : new LiteralNode(null);
            }
            case ArrayNode a -> {
                List<Node> newElements = new ArrayList<>(a.elements().size());
                for (Node elem : a.elements()) {
                    newElements.add(materializeNode(elem, statementId));
                }
                yield new ArrayNode(newElements);
            }
            case ObjectNode o -> {
                Map<String, Node> newEntries = new LinkedHashMap<>();
                for (Map.Entry<String, Node> entry : o.entries().entrySet()) {
                    newEntries.put(entry.getKey(), materializeNode(entry.getValue(), statementId));
                }
                yield new ObjectNode(newEntries);
            }
            case BinaryNode b -> new BinaryNode(
                    b.op(),
                    materializeNode(b.left(), statementId),
                    materializeNode(b.right(), statementId)
            );
            case UnaryNode u -> new UnaryNode(u.op(), materializeNode(u.operand(), statementId));
            case MemberNode m -> new MemberNode(
                    materializeNode(m.object(), statementId),
                    m.property(),
                    m.computed()
            );
            case TernaryNode t -> new TernaryNode(
                    materializeNode(t.condition(), statementId),
                    materializeNode(t.consequent(), statementId),
                    materializeNode(t.alternate(), statementId)
            );
            case AssignNode a -> new AssignNode(a.target(), materializeNode(a.expr(), statementId));
            // Leaf nodes and BuiltinCallNode: return as-is
            default -> node;
        };
    }

    /**
     * Compute hasDynamicProps: true if any prop value node (recursively)
     * contains StateRefNode, RefNode, AssignNode, or BuiltinCallNode.
     *
     * Ref: Req 4 AC8
     */
    static boolean computeHasDynamicProps(Map<String, Node> props) {
        return props.values().stream().anyMatch(Materializer::isDynamic);
    }

    private static boolean isDynamic(Node node) {
        return switch (node) {
            case StateRefNode __ -> true;
            case RefNode __ -> true;
            case AssignNode __ -> true;
            case BuiltinCallNode __ -> true;
            case BinaryNode b -> isDynamic(b.left()) || isDynamic(b.right());
            case UnaryNode u -> isDynamic(u.operand());
            case MemberNode m -> isDynamic(m.object());
            case TernaryNode t -> isDynamic(t.condition()) || isDynamic(t.consequent()) || isDynamic(t.alternate());
            case ArrayNode a -> a.elements().stream().anyMatch(Materializer::isDynamic);
            case ObjectNode o -> o.entries().values().stream().anyMatch(Materializer::isDynamic);
            case ElementNode e -> e.hasDynamicProps();
            default -> false;
        };
    }
}
