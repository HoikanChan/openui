/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.semantic;

import com.huawei.cloudsop.genui.core.contract.BuiltinSpec;
import com.huawei.cloudsop.genui.core.validation.ValidationIssue;
import com.huawei.cloudsop.genui.core.validation.ValidationSeverity;
import com.huawei.cloudsop.genui.core.validation.parser.AstNode;
import com.huawei.cloudsop.genui.core.validation.parser.BuiltinCatalog;
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 对已解析的 {@link Program} 针对 {@link ContractCatalog} 做语义分析。
 *
 * <p>对应 TS 侧 {@code parser.ts#buildResult} + {@code materialize.ts} 实现的流水线：
 *
 * <ol>
 *   <li>构建符号表，对重复 id 采用<b>后者覆盖</b>语义 —— 解析器刻意将重复 id 的语句保留为有序列表（语法层面）； 我们在此处去重，对应 TS 侧 {@code
 *       stmtMap}（一个 {@code Map.set}）。
 *   <li>通过 {@link #pickEntryId} 选择根节点（与 {@code pickEntryId} 完全一致）。
 *   <li>从根节点递归物化，产出合约问题（unknown-component、inline-reserved、missing/null-required、
 *       excess-args、invalid-prop），并跟踪未解析引用 （通过 {@code visited} 环检测）。
 *   <li>应用结构性规则（root-missing / root-not-renderable / unresolved-ref），并将解析器的语法诊断 折叠进问题列表。
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
    private final BuiltinCatalog builtins;

    /**
     * 构造语义分析器（builtin 词表取 SDK 默认契约派生集）。
     *
     * @param catalog 用于合约校验的组件目录
     */
    public ProgramAnalyzer(ContractCatalog catalog) {
        this(catalog, BuiltinCatalog.getDefault());
    }

    /**
     * 构造语义分析器。
     *
     * @param catalog 用于合约校验的组件目录
     * @param builtins builtin 函数词表（从契约派生）
     */
    public ProgramAnalyzer(ContractCatalog catalog, BuiltinCatalog builtins) {
        this.catalog = catalog;
        this.builtins = builtins;
    }

    /**
     * 针对目录分析 {@code program}。
     *
     * @param program 已解析的程序
     * @param rootName 可选的首选根节点名（组件名或语句 id）
     * @param externalRefs 在运行时解析（数据结果）因而不算未解析的名称集合
     * @return 语义分析结果
     */
    public ProgramAnalysis analyze(Program program, String rootName, Set<String> externalRefs) {
        List<ValidationIssue> issues = new ArrayList<>();

        // ── Symbol table: LAST-WINS on duplicate ids (mirrors TS `stmtMap` Map.set). ──
        // Preserve first-seen insertion order for the id, but keep the last statement value.
        LinkedHashMap<String, Statement> stmtMap = program.currentById();
        String firstId = program.statements().isEmpty() ? null : program.statements().get(0).id();

        foldParserDiagnostics(program, issues);

        if (stmtMap.isEmpty()) {
            emitRootMissing(issues, "no statements produced a root component");
            return new ProgramAnalysis(issues, null, false, List.of(), 0);
        }

        List<Statement> typedStmts = new ArrayList<>(stmtMap.values());
        String entryId = pickEntryId(stmtMap, typedStmts, firstId, rootName);

        // Expression node per statement id (state → init, else → expr).
        Map<String, AstNode> syms = new LinkedHashMap<>();
        for (Map.Entry<String, Statement> e : stmtMap.entrySet()) {
            syms.put(e.getKey(), e.getValue().expr());
        }

        WalkCtx ctx = new WalkCtx(syms, issues, externalRefs == null ? Set.of() : externalRefs);
        ctx.currentStatementId = entryId;

        AstNode entryNode = syms.get(entryId);
        Object materialized = materializeValue(entryNode, ctx);
        boolean rootResolved = materialized instanceof ElementValue;

        // ── Structural rules ─────────────────────────────────────────────────────
        if (!rootResolved) {
            // Distinguish "entry produced nothing renderable" (root-not-renderable) from truly
            // absent.
            // entryNode is always present here (stmtMap non-empty); if it isn't a component that
            // materialized to an element, it's not renderable.
            emitRootNotRenderable(issues, entryId, materialized);
        }
        for (UnresolvedRef ref : ctx.unresolved) {
            String refStmtId = ref.statementId() != null ? ref.statementId() : entryId;
            SourceSpan refSpan = spanOf(stmtMap.get(refStmtId));
            issues.add(
                    new ValidationIssue(
                            "unresolved-ref",
                            ValidationSeverity.ERROR,
                            SOURCE_REFERENCE,
                            "unresolved reference \"" + ref.name() + "\"",
                            refStmtId,
                            null,
                            null,
                            refSpan.line(),
                            refSpan.column(),
                            RepairHints.unresolvedRefHint(ref.name()),
                            false));
        }

        return new ProgramAnalysis(
                issues,
                entryId,
                rootResolved,
                ctx.unresolved.stream()
                        .map(UnresolvedRef::name)
                        .collect(java.util.stream.Collectors.toCollection(ArrayList::new)),
                stmtMap.size());
    }

    /**
     * An unresolved reference plus the id of the statement whose expression used it (the walk's
     * {@code currentStatementId} at the moment of resolution failure), so the emitted issue can
     * point at the referencing statement instead of the entry/root statement.
     *
     * @since 2026
     */
    private record UnresolvedRef(String name, String statementId) {}

    /** Statement start span, or {@link SourceSpan#UNKNOWN} when the statement is not in the map. */
    private static SourceSpan spanOf(Statement s) {
        return s == null || s.span() == null ? SourceSpan.UNKNOWN : s.span();
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Root selection — mirrors pickEntryId EXACTLY.
    // ───────────────────────────────────────────────────────────────────────────

    private String pickEntryId(
            Map<String, Statement> stmtMap,
            List<Statement> typedStmts,
            String firstId,
            String rootName) {
        if (stmtMap.containsKey(DEFAULT_ROOT_STATEMENT_ID)) {
            return DEFAULT_ROOT_STATEMENT_ID;
        }
        if (rootName != null && stmtMap.containsKey(rootName)) {
            return rootName;
        }

        if (rootName != null) {
            for (Statement s : typedStmts) {
                if (isComponentStatement(s)
                        && ((Statement.Value) s).expr() instanceof AstNode.Comp comp
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
        return !builtins.isBuiltin(comp.name()) && !BuiltinCatalog.isReservedCall(comp.name());
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Materialization walk — mirrors materialize.ts (validation-only, plain values).
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Sentinel wrapper for a resolved renderable component (TS ElementNode).
     *
     * @since 2026
     */
    private record ElementValue(String typeName) {}

    private static final class WalkCtx {
        final Map<String, AstNode> syms;
        final List<ValidationIssue> issues;
        final Set<String> externalRefs;

        final List<UnresolvedRef> unresolved = new ArrayList<>();
        final Set<String> visited = new HashSet<>();

        /**
         * Stack of lazy-builtin binder scopes (mirrors materialize.ts scopedRefs). Each frame holds
         * the binder names of one enclosing {@code @Each}/{@code @Render} call and lives only while
         * that call's template args are walked. The stack stays live across named-statement
         * resolution, matching TS expr-mode scopedRefs propagation.
         */
        final Deque<Set<String>> scopeFrames = new ArrayDeque<>();

        String currentStatementId;

        boolean inScope(String name) {
            for (Set<String> frame : scopeFrames) {
                if (frame.contains(name)) {
                    return true;
                }
            }
            return false;
        }

        WalkCtx(Map<String, AstNode> syms, List<ValidationIssue> issues, Set<String> externalRefs) {
            this.syms = syms;
            this.issues = issues;
            this.externalRefs = externalRefs;
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
        if (node instanceof AstNode.Arr arr) {
            List<Object> items = new ArrayList<>();
            for (AstNode e : arr.els()) {
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
        // Runtime-expression nodes (BinOp, Ternary, Member, StateRef, ...): walk children
        // for nested refs/components but produce no plain value (treated as dynamic → non-null
        // marker).
        walkExpr(node, ctx);
        return DYNAMIC;
    }

    private Object materializeComp(AstNode.Comp comp, WalkCtx ctx) {
        String name = comp.name();

        if (builtins.isBuiltin(name)) {
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

        if (BuiltinCatalog.isReservedCall(name)) {
            // Inline Query/Mutation used as a value → definitively invalid.
            emit(
                    ctx,
                    "inline-reserved",
                    ValidationSeverity.ERROR,
                    "contract",
                    name,
                    "",
                    name
                            + "() must be declared as a top-level statement, not used inline as a value",
                    ctx.currentStatementId,
                    false);
            return null;
        }

        ComponentDef def = catalog == null ? null : catalog.get(name);
        if (def == null) {
            String hint =
                    RepairHints.unknownComponentHint(
                            name, builtins, catalog == null ? List.of() : catalog.capabilityGaps());
            emit(
                    ctx,
                    "unknown-component",
                    ValidationSeverity.ERROR,
                    "contract",
                    name,
                    "",
                    "Unknown component \"" + name + "\" — not found in catalog or builtins",
                    ctx.currentStatementId,
                    hint,
                    false);
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

        ComponentContractValidator validator =
                new ComponentContractValidator(ValidationSeverity.ERROR, false, ctx.issues);
        boolean renderable = validator.validate(name, def, argValues, ctx.currentStatementId);
        if (!renderable) {
            return null; // missing/null required → dropped (TS returns null)
        }
        return new ElementValue(name);
    }

    /**
     * Mirrors {@code materialize.ts#materializeLazyBuiltin}: for {@code @Each(array, varName,
     * template)} and {@code @Render(binder..., template)}, push the binder names onto the scope
     * stack ONLY while the non-binder args are walked (binder args themselves are never resolved as
     * refs). Returns {@code false} when the call introduces no binder scope — e.g. too few args or
     * a non-literal binder — so the caller falls back to the generic builtin walk.
     */
    private boolean walkLazyBuiltin(AstNode.Comp comp, WalkCtx ctx) {
        List<Integer> binderIndexes = LazyBuiltinBinders.binderIndexes(comp, builtins);
        if (binderIndexes.isEmpty()) {
            return false;
        }
        BuiltinSpec spec = builtins.spec(comp.name());
        Set<String> binders = new LinkedHashSet<>();
        for (int index : binderIndexes) {
            String binderName = LazyBuiltinBinders.binderName(spec, comp, index);
            if (binderName == null) {
                return false;
            }
            binders.add(binderName);
        }
        // binder 只作用于 body(#77 §10.1):body 恒为最后一个实参,其余实参(Each 的 source)在外层作用域解析,
        // source 里出现同名 binder 不得被压制。
        int bodyIndex = LazyBuiltinBinders.bodyIndex(spec, comp);
        for (int i = 0; i < comp.args().size(); i++) {
            if (i != bodyIndex && !binderIndexes.contains(i)) {
                walkExpr(comp.args().get(i), ctx);
            }
        }
        ctx.scopeFrames.push(binders);
        try {
            walkExpr(comp.args().get(bodyIndex), ctx);
        } finally {
            ctx.scopeFrames.pop();
        }
        return true;
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
                return DYNAMIC; // external ref → resolves at runtime
            }
            ctx.unresolved.add(new UnresolvedRef(name, ctx.currentStatementId));
            return null;
        }
        AstNode target = ctx.syms.get(name);
        if (target instanceof AstNode.Comp comp && BuiltinCatalog.isReservedCall(comp.name())) {
            return DYNAMIC; // Query/Mutation declaration → runtime value
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
        // Literals, StateRef → nothing to resolve.
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Issue helpers.
    // ───────────────────────────────────────────────────────────────────────────

    private void foldParserDiagnostics(Program program, List<ValidationIssue> issues) {
        for (ParseDiagnostic d : program.diagnostics()) {
            issues.add(
                    new ValidationIssue(
                            "syntax-" + d.code().name().toLowerCase(Locale.ROOT).replace('_', '-'),
                            ValidationSeverity.ERROR,
                            SOURCE_SYNTAX,
                            d.message(),
                            d.statementId(),
                            null,
                            null,
                            d.line(),
                            d.column(),
                            null,
                            false));
        }
    }

    private void emitRootMissing(List<ValidationIssue> issues, String detail) {
        issues.add(
                new ValidationIssue(
                        "root-missing",
                        ValidationSeverity.ERROR,
                        SOURCE_ROOT,
                        "no renderable root component (" + detail + ")",
                        null,
                        null,
                        null,
                        -1,
                        -1,
                        "the program must contain at least one renderable component as its entry",
                        false));
    }

    private void emitRootNotRenderable(
            List<ValidationIssue> issues, String entryId, Object materialized) {
        // materialized is null (dropped/unknown/missing-required) or a non-element value.
        issues.add(
                new ValidationIssue(
                        "root-not-renderable",
                        ValidationSeverity.ERROR,
                        SOURCE_ROOT,
                        "root statement \""
                                + entryId
                                + "\" did not resolve to a renderable component",
                        entryId,
                        null,
                        null,
                        -1,
                        -1,
                        "the entry statement must be a valid catalog component",
                        false));
    }

    private void emit(
            WalkCtx ctx,
            String code,
            ValidationSeverity severity,
            String source,
            String component,
            String path,
            String message,
            String statementId,
            boolean retryable) {
        emit(ctx, code, severity, source, component, path, message, statementId, null, retryable);
    }

    private void emit(
            WalkCtx ctx,
            String code,
            ValidationSeverity severity,
            String source,
            String component,
            String path,
            String message,
            String statementId,
            String hint,
            boolean retryable) {
        ctx.issues.add(
                new ValidationIssue(
                        code,
                        severity,
                        source,
                        message,
                        statementId,
                        component,
                        path,
                        -1,
                        -1,
                        hint,
                        retryable));
    }
}
