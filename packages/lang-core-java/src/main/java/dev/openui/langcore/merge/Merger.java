package dev.openui.langcore.merge;

import dev.openui.langcore.lexer.Lexer;
import dev.openui.langcore.lexer.Token;
import dev.openui.langcore.parser.PreProcessor;
import dev.openui.langcore.parser.SchemaRegistry;
import dev.openui.langcore.parser.StatementParser;
import dev.openui.langcore.parser.ast.ArrayNode;
import dev.openui.langcore.parser.ast.AssignNode;
import dev.openui.langcore.parser.ast.BinaryNode;
import dev.openui.langcore.parser.ast.BuiltinCallNode;
import dev.openui.langcore.parser.ast.CallNode;
import dev.openui.langcore.parser.ast.ElementNode;
import dev.openui.langcore.parser.ast.MemberNode;
import dev.openui.langcore.parser.ast.Node;
import dev.openui.langcore.parser.ast.ObjectNode;
import dev.openui.langcore.parser.ast.RefNode;
import dev.openui.langcore.parser.ast.RuntimeRefNode;
import dev.openui.langcore.parser.ast.TernaryNode;
import dev.openui.langcore.parser.ast.UnaryNode;
import dev.openui.langcore.parser.stmt.MutationStatement;
import dev.openui.langcore.parser.stmt.NullStatement;
import dev.openui.langcore.parser.stmt.QueryStatement;
import dev.openui.langcore.parser.stmt.Statement;
import dev.openui.langcore.parser.stmt.ValueStatement;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Merges a patch program into an existing statement map.
 *
 * <p>Steps:
 * <ol>
 *   <li>Strip fences + comments from patch text and parse it into a statement map.</li>
 *   <li>Copy {@code existing} into a new {@link LinkedHashMap}.</li>
 *   <li>Apply patch: {@link NullStatement} entries cause removal; all others overwrite.</li>
 *   <li>BFS garbage-collection from {@code "root"} via {@link RefNode}/{@link RuntimeRefNode}
 *       edges; {@code $state} IDs (starting with {@code $}) are always retained.</li>
 * </ol>
 *
 * Ref: Req 12 AC1-8
 */
public final class Merger {

    private Merger() {}

    /**
     * Merge a patch program into an existing statement map.
     *
     * @param existing  immutable or mutable map of current statements (insertion-order preserved)
     * @param patchText OpenUI Lang text (may be markdown-fenced)
     * @param schema    schema registry used to parse the patch
     * @return new {@link LinkedHashMap} with the merged, GC-cleaned result
     */
    public static Map<String, Statement> mergeStatements(
            Map<String, Statement> existing,
            String patchText,
            SchemaRegistry schema
    ) {
        // --- AC8: empty patch → return existing unchanged (copy) ---
        if (patchText == null || patchText.isBlank()) {
            return new LinkedHashMap<>(existing);
        }

        // Step 1: strip fences + comments → lex → parse patch (AC6)
        String stripped = PreProcessor.stripComments(PreProcessor.stripFences(patchText));
        List<Token> tokens = new Lexer(stripped).tokenize();
        Map<String, Statement> patchStmts = new StatementParser().parse(tokens);

        // --- AC7: empty existing → return patch statements only ---
        if (existing == null || existing.isEmpty()) {
            // Remove any NullStatements from the patch (they are deletions, nothing to delete)
            Map<String, Statement> result = new LinkedHashMap<>();
            for (Map.Entry<String, Statement> e : patchStmts.entrySet()) {
                if (!(e.getValue() instanceof NullStatement)) {
                    result.put(e.getKey(), e.getValue());
                }
            }
            return gcStatements(result);
        }

        // Step 2: copy existing
        Map<String, Statement> merged = new LinkedHashMap<>(existing);

        // Step 3: apply patch (AC1-3)
        for (Map.Entry<String, Statement> e : patchStmts.entrySet()) {
            String id = e.getKey();
            Statement stmt = e.getValue();
            if (stmt instanceof NullStatement) {
                // AC2: delete
                merged.remove(id);
            } else {
                // AC1: overwrite / AC3: append new
                merged.put(id, stmt);
            }
        }

        // Step 4: BFS GC (AC4-5)
        return gcStatements(merged);
    }

    // -------------------------------------------------------------------------
    // Garbage collection
    // -------------------------------------------------------------------------

    /**
     * Remove all non-{@code $state} statements not reachable from {@code "root"}
     * via {@link RefNode}/{@link RuntimeRefNode} traversal.
     *
     * <p>$state IDs (starting with {@code $}) are always retained (AC5).
     * If no {@code "root"} statement exists, all non-$state statements are GC'd.
     *
     * Ref: Req 12 AC4-5
     */
    private static Map<String, Statement> gcStatements(Map<String, Statement> stmts) {
        // BFS from "root"
        Set<String> reachable = new HashSet<>();
        if (stmts.containsKey("root")) {
            Queue<String> queue = new ArrayDeque<>();
            queue.add("root");
            reachable.add("root");

            while (!queue.isEmpty()) {
                String id = queue.poll();
                Statement stmt = stmts.get(id);
                if (stmt == null) continue;

                Set<String> refs = collectRefs(stmt);
                for (String ref : refs) {
                    if (!reachable.contains(ref) && stmts.containsKey(ref)) {
                        reachable.add(ref);
                        queue.add(ref);
                    }
                }
            }
        }

        // Build output: retain reachable + $state IDs + Query/Mutation statements
        Map<String, Statement> result = new LinkedHashMap<>();
        for (Map.Entry<String, Statement> e : stmts.entrySet()) {
            String id = e.getKey();
            Statement stmt = e.getValue();
            if (reachable.contains(id)
                    || id.startsWith("$")              // AC5: always retain $state
                    || stmt instanceof QueryStatement
                    || stmt instanceof MutationStatement) {
                result.put(id, stmt);
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Ref collection helpers
    // -------------------------------------------------------------------------

    /**
     * Collect all {@link RefNode} and {@link RuntimeRefNode} names referenced
     * by the expression(s) within a statement.
     */
    private static Set<String> collectRefs(Statement stmt) {
        Set<String> refs = new HashSet<>();
        switch (stmt) {
            case ValueStatement vs -> collectNodeRefs(vs.expr(), refs);
            case QueryStatement qs -> {
                if (qs.toolAST() != null) collectNodeRefs(qs.toolAST(), refs);
                if (qs.argsAST() != null) collectNodeRefs(qs.argsAST(), refs);
                if (qs.defaultsAST() != null) collectNodeRefs(qs.defaultsAST(), refs);
                if (qs.refreshAST() != null) collectNodeRefs(qs.refreshAST(), refs);
            }
            case MutationStatement ms -> {
                if (ms.toolAST() != null) collectNodeRefs(ms.toolAST(), refs);
                if (ms.argsAST() != null) collectNodeRefs(ms.argsAST(), refs);
            }
            default -> { /* StateStatement, NullStatement — no Ref edges */ }
        }
        return refs;
    }

    /**
     * Recursively walk a node tree collecting {@link RefNode} and
     * {@link RuntimeRefNode} names.
     */
    private static void collectNodeRefs(Node node, Set<String> refs) {
        switch (node) {
            case RefNode r -> refs.add(r.name());
            case RuntimeRefNode rr -> refs.add(rr.name());
            case ElementNode elem -> {
                for (Node prop : elem.props().values()) {
                    collectNodeRefs(prop, refs);
                }
            }
            case CallNode call -> {
                for (Node arg : call.args()) {
                    collectNodeRefs(arg, refs);
                }
            }
            case ArrayNode a -> {
                for (Node el : a.elements()) {
                    collectNodeRefs(el, refs);
                }
            }
            case ObjectNode o -> {
                for (Node val : o.entries().values()) {
                    collectNodeRefs(val, refs);
                }
            }
            case BinaryNode b -> {
                collectNodeRefs(b.left(), refs);
                collectNodeRefs(b.right(), refs);
            }
            case UnaryNode u -> collectNodeRefs(u.operand(), refs);
            case MemberNode m -> collectNodeRefs(m.object(), refs);
            case TernaryNode t -> {
                collectNodeRefs(t.condition(), refs);
                collectNodeRefs(t.consequent(), refs);
                collectNodeRefs(t.alternate(), refs);
            }
            case AssignNode a -> collectNodeRefs(a.expr(), refs);
            case BuiltinCallNode bc -> {
                for (Node arg : bc.args()) {
                    collectNodeRefs(arg, refs);
                }
            }
            default -> { /* LiteralNode, StateRefNode — no Ref edges */ }
        }
    }
}
