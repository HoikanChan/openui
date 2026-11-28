/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.eval.repair;

import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import com.huawei.cloudsop.genui.core.validation.ValidationIssue;
import com.huawei.cloudsop.genui.core.validation.ValidationResult;
import com.huawei.cloudsop.genui.core.validation.parser.AstNode;
import com.huawei.cloudsop.genui.core.validation.parser.BuiltinCatalog;
import com.huawei.cloudsop.genui.core.validation.parser.OpenuiParser;
import com.huawei.cloudsop.genui.core.validation.parser.Program;
import com.huawei.cloudsop.genui.core.validation.parser.Statement;
import com.huawei.cloudsop.genui.core.validation.semantic.ContractCatalog;
import com.huawei.cloudsop.genui.core.validation.semantic.ProgramAnalysis;
import com.huawei.cloudsop.genui.core.validation.semantic.ProgramAnalyzer;
import com.huawei.cloudsop.genui.core.validation.semantic.RepairHints;
import com.huawei.cloudsop.genui.core.validation.stream.RoundEndResult;
import com.huawei.cloudsop.genui.core.validation.type.analysis.BuiltinAnalysis;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 能力边界与未知组件复发检查器（纯函数），与 {@code RepairBenchRunner} 保持完全一致的可达语句语义。
 *
 * @since 2026
 */
public final class CapabilityInspector {
    private CapabilityInspector() {}

    /**
     * 最终 DSL 的能力边界检查结论。
     *
     * @param capabilityNoticePresent 可达语句中是否声明 {@code capabilityNotice = TextCallout(...)}
     * @param remainingGapAliases 可达语句中残留的能力缺口组件别名列表
     * @since 2026
     */
    public record InspectResult(
            boolean capabilityNoticePresent, List<String> remainingGapAliases) {}

    /**
     * 判断初始校验结果是否命中能力缺口提示。
     *
     * @param result 校验结果
     * @return 命中时为 true
     */
    public static boolean hasCapabilityGapIssue(ValidationResult result) {
        if (result == null) {
            return false;
        }
        return result.actionableIssues().stream()
                .anyMatch(
                        i ->
                                "unknown-component".equals(i.code())
                                        && RepairHints.isCapabilityGapHint(i.hint()));
    }

    /**
     * 检查最终 DSL 可达语句中的 {@code capabilityNotice} 声明与残留能力缺口别名。
     *
     * @param dsl 最终 DSL
     * @param contract 生效契约
     * @param externalRefs 外部引用集合
     * @return 检查结论
     */
    public static InspectResult inspectFinalDsl(
            String dsl, GenerationContract contract, Set<String> externalRefs) {
        if (dsl == null || dsl.isBlank() || contract == null) {
            return new InspectResult(false, List.of());
        }
        BuiltinCatalog builtins = BuiltinCatalog.fromContract(contract);
        Program program = OpenuiParser.parse(dsl, builtins);
        Map<String, Statement> stmts = program.currentById();
        if (stmts.isEmpty()) {
            return new InspectResult(false, List.of());
        }
        String rootName = contract.root() == null ? "root" : contract.root();
        ProgramAnalysis analysis =
                new ProgramAnalyzer(ContractCatalog.from(contract), builtins)
                        .analyze(program, rootName, externalRefs);
        Set<String> reachable = reachableStatementIds(stmts, analysis.entryId(), builtins);

        Statement noticeStmt = stmts.get("capabilityNotice");
        boolean noticeDeclared =
                noticeStmt instanceof Statement.Value val
                        && val.expr() instanceof AstNode.Comp comp
                        && "TextCallout".equals(comp.name());
        boolean noticePresent = noticeDeclared && reachable.contains("capabilityNotice");

        Set<String> remaining = new TreeSet<>();
        for (Map.Entry<String, Statement> entry : stmts.entrySet()) {
            if (reachable.contains(entry.getKey())) {
                collectGapAliases(entry.getValue().expr(), contract, remaining);
            }
        }
        return new InspectResult(noticePresent, List.copyOf(remaining));
    }

    /**
     * 统计连续两轮校验中重复出现且未命中能力缺口别名的未知组件名。
     *
     * @param rounds 轮次记录列表
     * @return 连续重复的未知组件名列表（升序去重）
     */
    public static List<String> consecutiveUnmatchedUnknownComponents(List<RoundEndResult> rounds) {
        if (rounds == null || rounds.size() < 2) {
            return List.of();
        }
        Set<String> repeated = new TreeSet<>();
        Set<String> prevUnmatched = null;
        for (RoundEndResult round : rounds) {
            if (round == null || round.validationResult() == null) {
                continue;
            }
            Set<String> current = collectUnmatchedUnknowns(round.validationResult());
            if (prevUnmatched != null) {
                for (String name : current) {
                    if (prevUnmatched.contains(name)) {
                        repeated.add(name);
                    }
                }
            }
            prevUnmatched = current;
        }
        return List.copyOf(repeated);
    }

    private static Set<String> collectUnmatchedUnknowns(ValidationResult validationResult) {
        Set<String> current = new LinkedHashSet<>();
        for (ValidationIssue issue : validationResult.actionableIssues()) {
            if ("unknown-component".equals(issue.code())
                    && issue.component() != null
                    && !issue.component().isBlank()
                    && !RepairHints.isMissingAtHint(issue.hint())
                    && !RepairHints.isCapabilityGapHint(issue.hint())) {
                current.add(issue.component());
            }
        }
        return current;
    }

    private static Set<String> reachableStatementIds(
            Map<String, Statement> stmts, String entryId, BuiltinCatalog builtins) {
        Set<String> reachable = new HashSet<>();
        if (entryId == null || !stmts.containsKey(entryId)) {
            return reachable;
        }
        Deque<Set<String>> scopeFrames = new ArrayDeque<>();
        Deque<String> pending = new ArrayDeque<>();
        pending.add(entryId);
        reachable.add(entryId);
        while (!pending.isEmpty()) {
            String statementId = pending.poll();
            Statement statement = stmts.get(statementId);
            if (!statementId.equals(entryId)
                    && (statement instanceof Statement.Query
                            || statement instanceof Statement.Mutation)) {
                continue;
            }
            Set<String> refs = new LinkedHashSet<>();
            collectRefs(statement.expr(), refs, scopeFrames, builtins);
            for (String ref : refs) {
                if (stmts.containsKey(ref) && reachable.add(ref)) {
                    pending.add(ref);
                }
            }
        }
        return reachable;
    }

    private static void collectRefs(
            AstNode node,
            Set<String> out,
            Deque<Set<String>> scopeFrames,
            BuiltinCatalog builtins) {
        if (node == null) {
            return;
        }
        if (node instanceof AstNode.Ref ref) {
            if (!inScope(scopeFrames, ref.n())) {
                out.add(ref.n());
            }
        } else if (node instanceof AstNode.Comp comp) {
            collectCompRefs(comp, out, scopeFrames, builtins);
        } else {
            forEachChild(node, child -> collectRefs(child, out, scopeFrames, builtins));
        }
    }

    private static void collectCompRefs(
            AstNode.Comp comp,
            Set<String> out,
            Deque<Set<String>> scopeFrames,
            BuiltinCatalog builtins) {
        BuiltinAnalysis.walkArguments(
                builtins,
                comp,
                BuiltinAnalysis.BinderPolicy.REQUIRE_ALL_VALID,
                new BuiltinAnalysis.ArgumentVisitor() {
                    @Override
                    public void value(AstNode argument, String binderName) {
                        if (binderName == null) {
                            collectRefs(argument, out, scopeFrames, builtins);
                        }
                    }

                    @Override
                    public void template(AstNode body, Set<String> binderNames) {
                        scopeFrames.push(binderNames);
                        try {
                            collectRefs(body, out, scopeFrames, builtins);
                        } finally {
                            scopeFrames.pop();
                        }
                    }
                });
    }

    private static void collectGapAliases(
            AstNode node, GenerationContract contract, Set<String> out) {
        if (node == null) {
            return;
        }
        if (node instanceof AstNode.Comp comp) {
            if (RepairHints.findCapabilityGapByAlias(comp.name(), contract.capabilityGaps())
                    != null) {
                out.add(comp.name());
            }
            for (AstNode arg : comp.args()) {
                collectGapAliases(arg, contract, out);
            }
            return;
        }
        forEachChild(node, child -> collectGapAliases(child, contract, out));
    }

    private static void forEachChild(AstNode node, java.util.function.Consumer<AstNode> visitor) {
        if (node instanceof AstNode.Arr arr) {
            arr.els().forEach(visitor);
        } else if (node instanceof AstNode.Obj obj) {
            obj.entries().forEach(e -> visitor.accept(e.value()));
        } else if (node instanceof AstNode.UnaryOp u) {
            visitor.accept(u.operand());
        } else if (node instanceof AstNode.BinOp b) {
            visitor.accept(b.left());
            visitor.accept(b.right());
        } else if (node instanceof AstNode.Ternary t) {
            visitor.accept(t.cond());
            visitor.accept(t.then());
            visitor.accept(t.otherwise());
        } else if (node instanceof AstNode.Member m) {
            visitor.accept(m.obj());
        } else if (node instanceof AstNode.Index idx) {
            visitor.accept(idx.obj());
            visitor.accept(idx.index());
        } else if (node instanceof AstNode.Assign a) {
            visitor.accept(a.value());
        }
    }

    private static boolean inScope(Deque<Set<String>> scopeFrames, String name) {
        for (Set<String> frame : scopeFrames) {
            if (frame.contains(name)) {
                return true;
            }
        }
        return false;
    }
}
