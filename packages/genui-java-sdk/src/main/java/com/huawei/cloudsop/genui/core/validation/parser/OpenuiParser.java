/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.parser;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 顶层编排器：预处理 → 分词 → 切分 → 解析 → 分类 → {@link Program}。
 *
 * <p>
 * 对应 {@code packages/lang-core/src/parser/parser.ts} 中直到语法层（语句分类）为止的流水线，在 schema 物化
 * 之前停止（后续章节负责语义层）。语句分类遵循 {@code classifyStatement}：{@code Query(...)} /
 * {@code Mutation(...)} 会在 {@code $var} 状态判断<em>之前</em>被检测，其次是 {@code $var} → 状态，其余情况
 * 为取值声明。
 *
 * <p>
 * 每个阶段的语法问题都会作为 {@link ParseDiagnostic} 收集到 {@link Program} 上；不会抛出异常。格式错误的语句会
 * 降级为一条诊断信息并被跳过，使后续语句仍可继续解析。
 *
 * @since 2026
 */
public final class OpenuiParser {

    private OpenuiParser() {
    }

    /**
     * 解析完整（非流式）输入。未闭合的结构会作为诊断信息暴露。
     *
     * @param input
     *            待解析的源字符串
     * @return 解析得到的程序
     */
    public static Program parse(String input) {
        return parse(input, ParseMode.FINAL);
    }

    /**
     * 按指定的 {@link ParseMode} 解析输入。
     *
     * @param input
     *            待解析的源字符串
     * @param mode
     *            解析模式
     * @return 解析得到的程序
     */
    public static Program parse(String input, ParseMode mode) {
        OpenuiPreprocessor.Result pre = OpenuiPreprocessor.process(input, mode);
        List<ParseDiagnostic> diagnostics = new ArrayList<>();
        List<Statement> statements = new ArrayList<>();

        if (pre.text().isBlank()) {
            return new Program(statements, diagnostics, pre.wasIncomplete());
        }

        List<Token> tokens = OpenuiLexer.tokenize(pre.text());
        StatementSplitter.SplitResult split = StatementSplitter.splitDetailed(tokens);

        // Map skipped lines into diagnostics (streaming tolerance — parser never throws).
        for (StatementSplitter.SkippedLine skip : split.skipped()) {
            ParseErrorCode code = skip.reason() == StatementSplitter.SkipReason.MISSING_ASSIGNMENT
                    ? ParseErrorCode.MISSING_ASSIGNMENT
                    : ParseErrorCode.INVALID_STATEMENT;
            String msg = skip.reason() == StatementSplitter.SkipReason.MISSING_ASSIGNMENT
                    ? "Statement '" + skip.idText() + "' is missing '='"
                    : "Line does not start a valid statement";
            diagnostics.add(ParseDiagnostic.at(code, msg, skip.idText(), skip.span()));
        }

        for (StatementSplitter.RawStmt raw : split.statements()) {
            ExpressionParser exprParser = new ExpressionParser(raw.tokens(), raw.id());
            AstNode expr = exprParser.parse();
            diagnostics.addAll(exprParser.diagnostics());
            statements.add(classify(raw, expr));
        }

        return new Program(statements, diagnostics, pre.wasIncomplete());
    }

    /** 对原始语句与已解析表达式做分类，对应 {@code classifyStatement}。 */
    private static Statement classify(StatementSplitter.RawStmt raw, AstNode expr) {
        // Query(...) → query — checked BEFORE $var so `$foo = Query(...)` is a query.
        if (expr instanceof AstNode.Comp comp && comp.name().equals(Builtins.QUERY)) {
            List<String> deps = comp.args().size() > 1 ? collectStateDeps(comp.args().get(1)) : List.of();
            return new Statement.Query(raw.id(), new Statement.CallNode(Builtins.QUERY, comp.args()), expr, deps,
                    raw.span());
        }
        // Mutation(...) → mutation.
        if (expr instanceof AstNode.Comp comp && comp.name().equals(Builtins.MUTATION)) {
            return new Statement.Mutation(raw.id(), new Statement.CallNode(Builtins.MUTATION, comp.args()), expr,
                    raw.span());
        }
        // $var → state declaration.
        if (raw.idTokenType() == TokenType.STATE_VAR) {
            return new Statement.State(raw.id(), expr, raw.span());
        }
        // Everything else → value declaration.
        return new Statement.Value(raw.id(), expr, raw.span());
    }

    /** 收集某节点可达的全部去重 {@code $state} 引用（用于预先计算查询依赖）。 */
    static List<String> collectStateDeps(AstNode node) {
        Set<String> refs = new LinkedHashSet<>();
        walk(node, refs);
        return List.copyOf(refs);
    }

    private static void walk(AstNode node, Set<String> refs) {
        if (node == null) {
            return;
        }
        switch (node) {
            case AstNode.StateRef s -> refs.add(s.n());
            case AstNode.Comp c -> {
                c.args().forEach(a -> walk(a, refs));
                c.mappedProps().values().forEach(v -> walk(v, refs));
            }
            case AstNode.Arr a -> a.els().forEach(e -> walk(e, refs));
            case AstNode.Obj o -> o.entries().forEach(e -> walk(e.value(), refs));
            case AstNode.BinOp b -> {
                walk(b.left(), refs);
                walk(b.right(), refs);
            }
            case AstNode.UnaryOp u -> walk(u.operand(), refs);
            case AstNode.Ternary t -> {
                walk(t.cond(), refs);
                walk(t.then(), refs);
                walk(t.otherwise(), refs);
            }
            case AstNode.Member m -> walk(m.obj(), refs);
            case AstNode.Index ix -> {
                walk(ix.obj(), refs);
                walk(ix.index(), refs);
            }
            case AstNode.Assign as -> walk(as.value(), refs);
            default -> {
                // leaf node — nothing to walk
            }
        }
    }
}
