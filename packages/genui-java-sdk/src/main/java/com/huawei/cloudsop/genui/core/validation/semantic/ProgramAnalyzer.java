/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.semantic;

import com.huawei.cloudsop.genui.core.validation.ValidationIssue;
import com.huawei.cloudsop.genui.core.validation.ValidationMode;
import com.huawei.cloudsop.genui.core.validation.ValidationSeverity;
import com.huawei.cloudsop.genui.core.validation.parser.AstNode;
import com.huawei.cloudsop.genui.core.validation.parser.Builtins;
import com.huawei.cloudsop.genui.core.validation.parser.ParseDiagnostic;
import com.huawei.cloudsop.genui.core.validation.parser.Program;
import com.huawei.cloudsop.genui.core.validation.parser.SourceSpan;
import com.huawei.cloudsop.genui.core.validation.parser.Statement;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 对已解析的 {@link Program} 针对 {@link ContractCatalog} 做语义分析。
 *
 * <p>
 * 对应 TS 侧 {@code parser.ts#buildResult} + {@code materialize.ts} 实现的流水线：
 *
 * <ol>
 * <li>构建符号表，对重复 id 采用<b>后者覆盖</b>语义 —— 解析器刻意将重复 id 的语句保留为有序列表（语法层面）； 我们在此处去重，对应 TS 侧 {@code stmtMap}（一个
 * {@code Map.set}）。
 * <li>通过 {@link #pickEntryId} 选择根节点（与 {@code pickEntryId} 完全一致）。
 * <li>从根节点递归物化，产出合约问题（unknown-component、inline-reserved、missing/null-required、 excess-args、invalid-prop），并跟踪未解析引用 +
 * 孤立语句（通过 {@code visited} 环检测与 {@code unreached} 集合）。
 * <li>应用按模式区分的结构性规则（root-missing / root-not-renderable / unresolved-ref），并将解析器的语法诊断 折叠进问题列表。
 * </ol>
 *
 * @since 2026
 */
public final class ProgramAnalyzer {

    private static final String DEFAULT_ROOT_STATEMENT_ID = "root";
    private static final String SOURCE_REFERENCE = "reference";
    private static final String SOURCE_ROOT = "root";
    private static final String SOURCE_SYNTAX = "syntax";

    /** Non-null marker for a runtime-expression value (dynamic, but present). */
    private static final Object DYNAMIC = new Object();

    private final ContractCatalog catalog;

    /**
     * 构造语义分析器。
     *
     * @param catalog
     *            用于合约校验的组件目录
     */
    public ProgramAnalyzer(ContractCatalog catalog) {
        this.catalog = catalog;
    }

    /**
     * 在无外部引用、无显式根节点覆盖的情况下做分析。
     *
     * @param program
     *            已解析的程序
     * @param mode
     *            校验模式
     * @return 语义分析结果
     */
    public ProgramAnalysis analyze(Program program, ValidationMode mode) {
        return analyze(program, mode, null, Set.of());
    }

    /**
     * 在给定 {@code mode} 下，针对目录分析 {@code program}。
     *
     * @param program
     *            已解析的程序
     * @param mode
     *            校验模式
     * @param rootName
     *            可选的首选根节点名（组件名或语句 id）
     * @param externalRefs
     *            在运行时解析（数据结果）因而不算未解析的名称集合
     * @return 语义分析结果
     */
    public ProgramAnalysis analyze(Program program, ValidationMode mode, String rootName, Set<String> externalRefs) {
        boolean incomplete = program.incomplete();
        List<ValidationIssue> issues = new ArrayList<>();

        // ── Symbol table: LAST-WINS on duplicate ids (mirrors TS `stmtMap` Map.set). ──
        // Preserve first-seen insertion order for the id, but keep the last statement value.
        LinkedHashMap<String, Statement> stmtMap = new LinkedHashMap<>();
        String firstId = null;
        for (Statement s : program.statements()) {
            if (firstId == null) {
                firstId = s.id();
            }
            stmtMap.put(s.id(), s); // overwrite → last-wins
        }

        // Fold parser syntax diagnostics into the issue list (mode-aware severity).
        foldParserDiagnostics(program, mode, incomplete, issues);

        // Compute the id of the LAST statement in the pre-dedup ordered list (streaming partial tail).
        // Streaming text only ever appends at the end, so every statement before the last is fully received.
        List<Statement> rawStatements = program.statements();
        String lastStatementId = rawStatements.isEmpty() ? null : rawStatements.get(rawStatements.size() - 1).id();

        if (stmtMap.isEmpty()) {
            // No statements at all → FINAL root-missing; STREAMING pending/partial.
            if (mode == ValidationMode.FINAL) {
                emitRootMissing(issues, "no statements produced a root component");
            }
            return new ProgramAnalysis(issues, null, false, List.of(), List.of(), 0);
        }

        List<Statement> typedStmts = new ArrayList<>(stmtMap.values());
        String entryId = pickEntryId(stmtMap, typedStmts, firstId, rootName);

        // Expression node per statement id (state → init, else → expr).
        Map<String, AstNode> syms = new LinkedHashMap<>();
        for (Map.Entry<String, Statement> e : stmtMap.entrySet()) {
            syms.put(e.getKey(), exprOf(e.getValue()));
        }

        // Orphan tracking: start with value statement ids (excl. entry/state/query/mutation), delete on reach.
        Set<String> unreached = new LinkedHashSet<>();
        for (Map.Entry<String, Statement> e : stmtMap.entrySet()) {
            String id = e.getKey();
            Statement s = e.getValue();
            if (id.equals(entryId)) {
                continue;
            }
            if (s instanceof Statement.State || s instanceof Statement.Query || s instanceof Statement.Mutation) {
                continue;
            }
            unreached.add(id);
        }

        WalkCtx ctx = new WalkCtx(syms, issues, mode, incomplete, externalRefs == null ? Set.of() : externalRefs,
                lastStatementId);
        ctx.unreached = unreached;
        ctx.currentStatementId = entryId;

        AstNode entryNode = syms.get(entryId);
        Object materialized = materializeValue(entryNode, ctx);
        boolean rootResolved = materialized instanceof ElementValue;

        // ── Structural / mode-aware rules ────────────────────────────────────────
        if (mode == ValidationMode.FINAL) {
            if (!rootResolved) {
                // Distinguish "entry produced nothing renderable" (root-not-renderable) from truly absent.
                // entryNode is always present here (stmtMap non-empty); if it isn't a component that
                // materialized to an element, it's not renderable.
                emitRootNotRenderable(issues, entryId, materialized);
            }
            for (UnresolvedRef ref : ctx.unresolved) {
                String refStmtId = ref.statementId() != null ? ref.statementId() : entryId;
                SourceSpan refSpan = spanOf(stmtMap.get(refStmtId));
                issues.add(new ValidationIssue("unresolved-ref", ValidationSeverity.ERROR, SOURCE_REFERENCE,
                        "unresolved reference \"" + ref.name() + "\"", refStmtId, null, null, refSpan.line(),
                        refSpan.column(), RepairHints.unresolvedRefHint(ref.name()), false));
            }
        } else {
            // STREAMING: temporary unresolved refs are non-blocking (retryable WARNING).
            for (UnresolvedRef ref : ctx.unresolved) {
                String refStmtId = ref.statementId() != null ? ref.statementId() : entryId;
                SourceSpan refSpan = spanOf(stmtMap.get(refStmtId));
                issues.add(new ValidationIssue("unresolved-ref", ValidationSeverity.WARNING, SOURCE_REFERENCE,
                        "reference \"" + ref.name() + "\" not yet defined (streaming)", refStmtId, null, null,
                        refSpan.line(), refSpan.column(), "may resolve as more of the stream arrives", true));
            }
            // Reviewer #2: a non-renderable root is only a "pending state" if the root's entry statement
            // is the last (potentially still-partial) statement. If the entry statement is definitively
            // complete (either the program is complete, or the entry id != lastStatementId), and the root
            // still failed to resolve, emit a blocking root-not-renderable.
            // This ensures `root = Bogus(...)` in a COMPLETE earlier statement stays blocking even when the
            // program is marked incomplete due to a partial tail elsewhere.
            if (!rootResolved) {
                boolean entryIsLastStatement = entryId != null && entryId.equals(ctx.lastStatementId);
                boolean entryIsDefinitivelyComplete = !incomplete || !entryIsLastStatement;
                if (entryIsDefinitivelyComplete) {
                    // Check whether the non-renderability is due to definitive contract errors already emitted
                    // (unknown-component / inline-reserved) rather than purely transient issues. If any blocking
                    // (ERROR) issue is attributed to the entry statement, that is already sufficient signal.
                    // We additionally check: if the entry node itself is a Comp whose name is definitively bad,
                    // emit root-not-renderable so the stream gate can act on it.
                    boolean hasBlockingEntryIssue = issues.stream().anyMatch(
                            iss -> iss.severity() == ValidationSeverity.ERROR && entryId.equals(iss.statementId()));
                    if (hasBlockingEntryIssue) {
                        emitRootNotRenderable(issues, entryId, materialized);
                    }
                }
            }
        }

        return new ProgramAnalysis(issues, entryId, rootResolved,
                ctx.unresolved.stream().map(UnresolvedRef::name)
                        .collect(java.util.stream.Collectors.toCollection(ArrayList::new)),
                new ArrayList<>(ctx.unreached), stmtMap.size());
    }

    /**
     * An unresolved reference plus the id of the statement whose expression used it (the walk's
     * {@code currentStatementId} at the moment of resolution failure), so the emitted issue can point at the
     * referencing statement instead of the entry/root statement.
     *
     * @since 2026
     */
    private record UnresolvedRef(String name, String statementId) {
    }

    /** Statement start span, or {@link SourceSpan#UNKNOWN} when the statement is not in the map. */
    private static SourceSpan spanOf(Statement s) {
        return s == null || s.span() == null ? SourceSpan.UNKNOWN : s.span();
    }

    private static AstNode exprOf(Statement s) {
        if (s instanceof Statement.State state) {
            return state.init();
        }
        if (s instanceof Statement.Value value) {
            return value.expr();
        }
        if (s instanceof Statement.Query query) {
            return query.expr();
        }
        if (s instanceof Statement.Mutation mutation) {
            return mutation.expr();
        }
        return null;
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Root selection — mirrors pickEntryId EXACTLY.
    // ───────────────────────────────────────────────────────────────────────────

    private String pickEntryId(Map<String, Statement> stmtMap, List<Statement> typedStmts, String firstId,
            String rootName) {
        if (stmtMap.containsKey(DEFAULT_ROOT_STATEMENT_ID)) {
            return DEFAULT_ROOT_STATEMENT_ID;
        }
        if (rootName != null && stmtMap.containsKey(rootName)) {
            return rootName;
        }

        if (rootName != null) {
            for (Statement s : typedStmts) {
                if (isComponentStatement(s) && ((Statement.Value) s).expr() instanceof AstNode.Comp comp
                        && comp.name().equals(rootName)) {
                    return s.id();
                }
            }
        }
        for (Statement s : typedStmts) {
            if (isComponentStatement(s)) {
                return s.id();
            }
        }
        return firstId;
    }

    /** 值语句，且其表达式是既非内置函数也非 Query/Mutation 的 Comp。 */
    private boolean isComponentStatement(Statement s) {
        if (!(s instanceof Statement.Value value)) {
            return false;
        }
        if (!(value.expr() instanceof AstNode.Comp comp)) {
            return false;
        }
        return !Builtins.isBuiltin(comp.name()) && !Builtins.isReservedCall(comp.name());
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Materialization walk — mirrors materialize.ts (validation-only, plain values).
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Sentinel wrapper for a resolved renderable component (TS ElementNode).
     *
     * @since 2026
     */
    private record ElementValue(String typeName) {
    }

    private static final class WalkCtx {
        final Map<String, AstNode> syms;
        final List<ValidationIssue> issues;
        final ValidationMode mode;
        final boolean incomplete;
        final Set<String> externalRefs;
        /**
         * The id of the LAST statement in {@code program.statements()} (pre-dedup, source order). {@code null} when the
         * program has no statements. Used for per-statement completeness: in STREAMING mode, only the last statement
         * may be partially received; all earlier statements are fully received and their errors are definitive.
         */
        final String lastStatementId;

        final List<UnresolvedRef> unresolved = new ArrayList<>();
        final Set<String> visited = new HashSet<>();
        /**
         * Stack of lazy-builtin binder scopes (mirrors materialize.ts scopedRefs). Each frame holds the binder names of
         * one enclosing {@code @Each}/{@code @Render} call and lives only while that call's template args are walked.
         * The stack stays live across named-statement resolution, matching TS expr-mode scopedRefs propagation.
         */
        final Deque<Set<String>> scopeFrames = new ArrayDeque<>();
        Set<String> unreached;
        String currentStatementId;

        boolean inScope(String name) {
            for (Set<String> frame : scopeFrames) {
                if (frame.contains(name)) {
                    return true;
                }
            }
            return false;
        }

        WalkCtx(Map<String, AstNode> syms, List<ValidationIssue> issues, ValidationMode mode, boolean incomplete,
                Set<String> externalRefs, String lastStatementId) {
            this.syms = syms;
            this.issues = issues;
            this.mode = mode;
            this.incomplete = incomplete;
            this.externalRefs = externalRefs;
            this.lastStatementId = lastStatementId;
        }
    }

    private Object materializeValue(AstNode node, WalkCtx ctx) {
        if (node == null) {
            return null;
        }
        if (node instanceof AstNode.Ref ref) {
            return resolveRef(ref.n(), ctx);
        }
        if (node instanceof AstNode.Str str) {
            return str.v();
        }
        if (node instanceof AstNode.Num num) {
            return num.v();
        }
        if (node instanceof AstNode.Bool bool) {
            return bool.v();
        }
        if (node instanceof AstNode.Null) {
            return null;
        }
        if (node instanceof AstNode.Ph) {
            return null;
        }
        if (node instanceof AstNode.Arr arr) {
            List<Object> items = new ArrayList<>();
            for (AstNode e : arr.els()) {
                if (e instanceof AstNode.Ph) {
                    continue;
                }
                Object value = materializeValue(e, ctx);
                if (value == null && (e instanceof AstNode.Comp || e instanceof AstNode.Ref)) {
                    continue;
                }
                items.add(value);
            }
            return items;
        }
        if (node instanceof AstNode.Obj obj) {
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            for (AstNode.Obj.Entry entry : obj.entries()) {
                map.put(entry.key(), materializeValue(entry.value(), ctx));
            }
            return map;
        }
        if (node instanceof AstNode.Comp comp) {
            return materializeComp(comp, ctx);
        }
        // Runtime-expression nodes (BinOp, Ternary, Member, StateRef, RuntimeRef, ...): walk children
        // for nested refs/components but produce no plain value (treated as dynamic → non-null marker).
        walkExpr(node, ctx);
        return DYNAMIC;
    }

    private Object materializeComp(AstNode.Comp comp, WalkCtx ctx) {
        String name = comp.name();

        if (Builtins.isBuiltin(name)) {
            // Builtin: recurse args for nested refs/components; not a renderable element.
            // Lazy builtins (@Each/@Render) scope their binder names over the template args first.
            if (walkLazyBuiltin(comp, ctx)) {
                return DYNAMIC;
            }
            for (AstNode a : comp.args()) {
                walkExpr(a, ctx);
            }
            return DYNAMIC;
        }

        if (Builtins.isReservedCall(name)) {
            // Inline Query/Mutation used as a value → definitively invalid; always blocking.
            emit(ctx, "inline-reserved", contractSeverity(ctx, "inline-reserved"), "contract", name, "",
                    name + "() must be declared as a top-level statement, not used inline as a value",
                    ctx.currentStatementId, contractRetryable(ctx, "inline-reserved"));
            return null;
        }

        ComponentDef def = catalog.get(name);
        if (def == null) {
            // unknown-component: definitively invalid (name fully received); always blocking in streaming.
            emit(ctx, "unknown-component", contractSeverity(ctx, "unknown-component"), "contract", name, "",
                    "Unknown component \"" + name + "\" — not found in catalog or builtins", ctx.currentStatementId,
                    contractRetryable(ctx, "unknown-component"));
            // Still walk args so nested unresolved refs / unknown components are reported.
            for (AstNode a : comp.args()) {
                walkExpr(a, ctx);
            }
            return null;
        }

        // Materialize positional args → values (for contract + nested checks).
        List<Object> argValues = new ArrayList<>();
        for (AstNode a : comp.args()) {
            argValues.add(materializeValue(a, ctx));
        }

        // For the remaining contract checks (missing-required, null-required, excess-args, invalid-prop)
        // use a generic non-definitive code for the per-statement severity computation.
        ComponentContractValidator validator = new ComponentContractValidator(contractSeverity(ctx, "missing-required"),
                contractRetryable(ctx, "missing-required"), ctx.issues);
        boolean renderable = validator.validate(name, def, argValues, ctx.currentStatementId);
        if (!renderable) {
            return null; // missing/null required → dropped (TS returns null)
        }
        return new ElementValue(name);
    }

    /**
     * Mirrors {@code materialize.ts#materializeLazyBuiltin}: for {@code @Each(array, varName, template)} and
     * {@code @Render(binder..., template)}, push the binder names onto the scope stack ONLY while the non-binder args
     * are walked (binder args themselves are never resolved as refs). Returns {@code false} when the call introduces no
     * binder scope — e.g. too few args or a non-literal binder — so the caller falls back to the generic builtin walk.
     */
    private boolean walkLazyBuiltin(AstNode.Comp comp, WalkCtx ctx) {
        List<Integer> binderIndexes = lazyBinderIndexes(comp);
        if (binderIndexes.isEmpty()) {
            return false;
        }
        Set<String> binders = new LinkedHashSet<>();
        for (int index : binderIndexes) {
            String binderName = binderName(comp.args().get(index));
            if (binderName == null) {
                return false;
            }
            binders.add(binderName);
        }
        ctx.scopeFrames.push(binders);
        try {
            for (int i = 0; i < comp.args().size(); i++) {
                if (!binderIndexes.contains(i)) {
                    walkExpr(comp.args().get(i), ctx);
                }
            }
        } finally {
            ctx.scopeFrames.pop();
        }
        return true;
    }

    /**
     * Binder arg positions per lazy builtin: {@code Each} binds arg 1 only in the 3-arg form; {@code Render} binds
     * every arg except the last (the template) when it has at least 2 args.
     */
    private static List<Integer> lazyBinderIndexes(AstNode.Comp comp) {
        if (!Builtins.isLazyBuiltin(comp.name())) {
            return List.of();
        }
        int argc = comp.args().size();
        if ("Each".equals(comp.name())) {
            return argc >= 3 ? List.of(1) : List.of();
        }
        if (argc < 2) {
            return List.of();
        }
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < argc - 1; i++) {
            indexes.add(i);
        }
        return indexes;
    }

    /** A binder may be a bare identifier ({@code Ref}) or a string literal ({@code Str}); anything else is invalid. */
    private static String binderName(AstNode arg) {
        if (arg instanceof AstNode.Ref ref) {
            return ref.n();
        }
        if (arg instanceof AstNode.Str str) {
            return str.v();
        }
        return null;
    }

    /** Resolve a Ref: inline from symbol table, track cycles + unresolved. Mirrors resolveRef. */
    private Object resolveRef(String name, WalkCtx ctx) {
        if (ctx.inScope(name)) {
            return DYNAMIC; // lazy-builtin binder variable — bound at render time
        }
        if (ctx.visited.contains(name)) {
            ctx.unresolved.add(new UnresolvedRef(name, ctx.currentStatementId));
            return null;
        }
        if (!ctx.syms.containsKey(name)) {
            if (ctx.externalRefs.contains(name)) {
                return DYNAMIC; // RuntimeRef → resolves at runtime
            }
            ctx.unresolved.add(new UnresolvedRef(name, ctx.currentStatementId));
            return null;
        }
        AstNode target = ctx.syms.get(name);
        if (ctx.unreached != null) {
            ctx.unreached.remove(name);
        }
        if (target instanceof AstNode.Comp comp && Builtins.isReservedCall(comp.name())) {
            return DYNAMIC; // Query/Mutation declaration → RuntimeRef
        }
        ctx.visited.add(name);
        String prev = ctx.currentStatementId;
        ctx.currentStatementId = name;
        try {
            return materializeValue(target, ctx);
        } finally {
            ctx.currentStatementId = prev;
            ctx.visited.remove(name);
        }
    }

    /** Walk a runtime-expression subtree solely to surface nested refs / unknown components. */
    private void walkExpr(AstNode node, WalkCtx ctx) {
        if (node == null) {
            return;
        }
        if (node instanceof AstNode.Ref ref) {
            resolveRef(ref.n(), ctx);
            return;
        }
        if (node instanceof AstNode.Comp comp) {
            materializeComp(comp, ctx);
            return;
        }
        if (node instanceof AstNode.Arr arr) {
            for (AstNode e : arr.els()) {
                walkExpr(e, ctx);
            }
            return;
        }
        if (node instanceof AstNode.Obj obj) {
            for (AstNode.Obj.Entry e : obj.entries()) {
                walkExpr(e.value(), ctx);
            }
            return;
        }
        if (node instanceof AstNode.BinOp binOp) {
            walkExpr(binOp.left(), ctx);
            walkExpr(binOp.right(), ctx);
            return;
        }
        if (node instanceof AstNode.UnaryOp unaryOp) {
            walkExpr(unaryOp.operand(), ctx);
            return;
        }
        if (node instanceof AstNode.Ternary ternary) {
            walkExpr(ternary.cond(), ctx);
            walkExpr(ternary.then(), ctx);
            walkExpr(ternary.otherwise(), ctx);
            return;
        }
        if (node instanceof AstNode.Member member) {
            walkExpr(member.obj(), ctx);
            return;
        }
        if (node instanceof AstNode.Index index) {
            walkExpr(index.obj(), ctx);
            walkExpr(index.index(), ctx);
            return;
        }
        if (node instanceof AstNode.Assign assign) {
            walkExpr(assign.value(), ctx);
        }
        // Literals, StateRef, RuntimeRef, Ph → nothing to resolve.
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Mode-aware severity for contract errors.
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Returns the severity for a contract issue on the current statement ({@code
     * ctx.currentStatementId}).
     *
     * <p>
     * FINAL mode: always ERROR. STREAMING mode with per-statement completeness:
     *
     * <ul>
     * <li>If the program is complete ({@code incomplete==false}): all contract errors are ERROR.
     * <li>If the program is still streaming ({@code incomplete==true}):
     * <ul>
     * <li>{@code unknown-component} and {@code inline-reserved}: always ERROR. These fire only on a fully-parsed
     * component name and can NEVER become valid by appending more tokens.
     * <li>All other contract codes: ERROR only for statements that are FULLY received (i.e., NOT the last statement in
     * the pre-dedup source order). The last statement may still be partially received, so its errors are transient →
     * WARNING.
     * </ul>
     * </ul>
     *
     * @param code
     *            the contract error code about to be emitted (e.g. "unknown-component")
     */
    private ValidationSeverity contractSeverity(WalkCtx ctx, String code) {
        if (ctx.mode == ValidationMode.FINAL) {
            return ValidationSeverity.ERROR;
        }
        if (!ctx.incomplete) {
            return ValidationSeverity.ERROR;
        }
        // Streaming + incomplete: definitively-invalid codes are always blocking.
        if (isDefinitiveContractCode(code)) {
            return ValidationSeverity.ERROR;
        }
        // Other codes: blocking only for fully-received (non-tail) statements.
        boolean isLastStatement = ctx.currentStatementId != null && ctx.currentStatementId.equals(ctx.lastStatementId);
        return isLastStatement ? ValidationSeverity.WARNING : ValidationSeverity.ERROR;
    }

    /**
     * Returns whether a contract error is retryable for the current statement in streaming mode.
     *
     * <p>
     * Retryable is the inverse of blocking: FINAL never retryable; STREAMING retryable only when the issue is transient
     * (non-definitive code on the last/partial statement).
     *
     * @param code
     *            the contract error code about to be emitted
     */
    private boolean contractRetryable(WalkCtx ctx, String code) {
        if (ctx.mode != ValidationMode.STREAMING || !ctx.incomplete) {
            return false;
        }
        if (isDefinitiveContractCode(code)) {
            return false;
        }
        boolean isLastStatement = ctx.currentStatementId != null && ctx.currentStatementId.equals(ctx.lastStatementId);
        return isLastStatement;
    }

    /**
     * Returns {@code true} for contract codes that are ALWAYS definitive — i.e., they fire on a component name that is
     * already fully received and cannot be extended into validity by more streaming tokens. These remain blocking even
     * when the program is incomplete.
     */
    private static boolean isDefinitiveContractCode(String code) {
        return "unknown-component".equals(code) || "inline-reserved".equals(code);
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Issue helpers.
    // ───────────────────────────────────────────────────────────────────────────

    private void foldParserDiagnostics(Program program, ValidationMode mode, boolean incomplete,
            List<ValidationIssue> issues) {
        for (ParseDiagnostic d : program.diagnostics()) {
            // Unclosed brackets/strings on a still-streaming program are transient in STREAMING mode.
            boolean transientSyntax = mode == ValidationMode.STREAMING && incomplete && (d
                    .code() == com.huawei.cloudsop.genui.core.validation.parser.ParseErrorCode.UNCLOSED_BRACKET
                    || d.code() == com.huawei.cloudsop.genui.core.validation.parser.ParseErrorCode.UNCLOSED_STRING);
            ValidationSeverity severity = transientSyntax ? ValidationSeverity.WARNING : ValidationSeverity.ERROR;
            issues.add(new ValidationIssue("syntax-" + d.code().name().toLowerCase().replace('_', '-'), severity,
                    SOURCE_SYNTAX, d.message(), d.statementId(), null, null, d.line(), d.column(), null,
                    transientSyntax));
        }
    }

    private void emitRootMissing(List<ValidationIssue> issues, String detail) {
        issues.add(new ValidationIssue("root-missing", ValidationSeverity.ERROR, SOURCE_ROOT,
                "no renderable root component (" + detail + ")", null, null, null, -1, -1,
                "the program must contain at least one renderable component as its entry", false));
    }

    private void emitRootNotRenderable(List<ValidationIssue> issues, String entryId, Object materialized) {
        // materialized is null (dropped/unknown/missing-required) or a non-element value.
        issues.add(new ValidationIssue("root-not-renderable", ValidationSeverity.ERROR, SOURCE_ROOT,
                "root statement \"" + entryId + "\" did not resolve to a renderable component", entryId, null, null, -1,
                -1, "the entry statement must be a valid catalog component", false));
    }

    private void emit(WalkCtx ctx, String code, ValidationSeverity severity, String source, String component,
            String path, String message, String statementId, boolean retryable) {
        ctx.issues.add(new ValidationIssue(code, severity, source, message, statementId, component, path, -1, -1, null,
                retryable));
    }
}
