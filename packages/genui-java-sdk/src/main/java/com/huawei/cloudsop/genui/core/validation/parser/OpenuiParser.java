/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.parser;

import java.util.ArrayList;
import java.util.List;

/**
 * 顶层编排器：预处理 → 分词 → 切分 → 解析 → 分类 → {@link Program}。
 *
 * <p>对应 {@code packages/lang-core/src/parser/parser.ts} 中直到语法层（语句分类）为止的流水线，在 schema 物化
 * 之前停止（后续章节负责语义层）。语句分类遵循 {@code classifyStatement}：{@code Query(...)} / {@code Mutation(...)} 会在
 * {@code $var} 状态判断<em>之前</em>被检测，其次是 {@code $var} → 状态，其余情况 为取值声明。
 *
 * <p>每个阶段的语法问题都会作为 {@link ParseDiagnostic} 收集到 {@link Program} 上；不会抛出异常。格式错误的语句会
 * 降级为一条诊断信息并被跳过，使后续语句仍可继续解析。
 *
 * @since 2026
 */
public final class OpenuiParser {

    private OpenuiParser() {}

    /**
     * 解析完整（非流式）输入。未闭合的结构会作为诊断信息暴露。
     *
     * @param input 待解析的源字符串
     * @return 解析得到的程序
     */
    public static Program parse(String input) {
        return parse(input, BuiltinCatalog.getDefault());
    }

    /**
     * Parse complete input using the given builtin catalog.
     *
     * @param input source text
     * @param builtins builtin catalog, or null for the default
     * @return parsed program and diagnostics
     */
    public static Program parse(String input, BuiltinCatalog builtins) {
        String text = OpenuiPreprocessor.preprocess(input);
        BuiltinCatalog effectiveBuiltins =
                builtins == null ? BuiltinCatalog.getDefault() : builtins;
        List<ParseDiagnostic> diagnostics = new ArrayList<>();
        List<Statement> statements = new ArrayList<>();

        if (text.isBlank()) {
            return new Program(statements, diagnostics);
        }

        List<Token> tokens = OpenuiLexer.tokenize(text);
        for (Token token : tokens) {
            if (token.type() == TokenType.STR && !isClosedString(text, token)) {
                diagnostics.add(
                        ParseDiagnostic.at(
                                ParseErrorCode.UNEXPECTED_TOKEN,
                                "Unclosed string literal",
                                null,
                                new SourceSpan(
                                        token.line(),
                                        token.column(),
                                        token.offset(),
                                        Math.min(text.length(), token.offset() + token.length()))));
            }
        }
        StatementSplitter.SplitResult split = StatementSplitter.splitDetailed(tokens);
        diagnostics.addAll(split.diagnostics());

        for (StatementSplitter.RawStmt raw : split.statements()) {
            ExpressionParser exprParser =
                    new ExpressionParser(raw.tokens(), raw.id(), effectiveBuiltins);
            AstNode expr = exprParser.parse();
            diagnostics.addAll(exprParser.diagnostics());
            statements.add(classify(raw, expr));
        }

        return new Program(statements, diagnostics);
    }

    private static boolean isClosedString(String text, Token token) {
        int start = token.offset();
        int end = Math.min(text.length(), start + token.length());
        if (end - start < 2 || text.charAt(start) != text.charAt(end - 1)) {
            return false;
        }
        int escapes = 0;
        for (int i = end - 2; i > start && text.charAt(i) == '\\'; i--) {
            escapes++;
        }
        return escapes % 2 == 0;
    }

    /**
     * 对原始语句与已解析表达式做分类，对应 {@code classifyStatement}。
     *
     * @param raw 切分器给出的原始语句
     * @param expr 已解析的表达式
     * @return 分类后的语句
     */
    private static Statement classify(StatementSplitter.RawStmt raw, AstNode expr) {
        // Query(...) → query — checked BEFORE $var so `$foo = Query(...)` is a query.
        if (expr instanceof AstNode.Comp comp && comp.name().equals(BuiltinCatalog.QUERY)) {
            return new Statement.Query(raw.id(), expr, raw.span());
        }
        // Mutation(...) → mutation.
        if (expr instanceof AstNode.Comp comp && comp.name().equals(BuiltinCatalog.MUTATION)) {
            return new Statement.Mutation(raw.id(), expr, raw.span());
        }
        // $var → state declaration.
        if (raw.id().startsWith("$")) {
            return new Statement.State(raw.id(), expr, raw.span());
        }
        // Everything else → value declaration.
        return new Statement.Value(raw.id(), expr, raw.span());
    }
}
