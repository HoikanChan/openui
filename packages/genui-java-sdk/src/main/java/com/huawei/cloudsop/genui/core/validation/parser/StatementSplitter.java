/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.parser;

import java.util.ArrayList;
import java.util.List;

/**
 * 将扁平的 token 流切分为独立的 {@code id = expression} 语句。
 *
 * <p>对应 {@code packages/lang-core/src/parser/statements.ts} 中的 {@code split()}：语句以深度为 0 的换行符
 * 分隔；括号/圆括号/花括号内部或多行三元表达式内部的换行符不会造成分割。接受 {@link TokenType#IDENT}、 {@link TokenType#TYPE} 与 {@link
 * TokenType#STATE_VAR} 作为标识符。没有 {@code =} 或没有标识符的行会被跳过并形成语法诊断。
 *
 * <p>Java 扩展：每个 {@link RawStmt} 都携带一个基于其 token 推导出的 {@link SourceSpan}（行/列/偏移范围）， 用于诊断信息。
 *
 * @since 2026
 */
public final class StatementSplitter {

    private StatementSplitter() {}

    /**
     * 一条原始（尚未做表达式解析）语句：左侧标识符、右侧表达式 token 列表，以及整条语句的源码位置。
     *
     * @param id 标识符
     * @param tokens 右侧表达式 token 列表
     * @param span 整条语句的源码位置范围
     * @since 2026
     */
    public record RawStmt(String id, List<Token> tokens, SourceSpan span) {

        /** 紧凑构造方法，将 token 列表归一化为不可变列表。 */
        public RawStmt {
            tokens = tokens == null ? List.of() : List.copyOf(tokens);
        }
    }

    /**
     * {@link #splitDetailed} 的输出：有效语句加被跳过行的诊断。
     *
     * @param statements 有效语句列表
     * @param diagnostics 被跳过行产生的诊断列表
     * @since 2026
     */
    public record SplitResult(List<RawStmt> statements, List<ParseDiagnostic> diagnostics) {

        /** 紧凑构造方法，将各列表归一化为不可变列表。 */
        public SplitResult {
            statements = statements == null ? List.of() : List.copyOf(statements);
            diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        }
    }

    /**
     * 便捷方法：仅返回有效语句（与 TS 侧 {@code split} 行为对齐）。
     *
     * @param tokens 输入 token 流
     * @return 有效语句列表
     */
    public static List<RawStmt> split(List<Token> tokens) {
        return splitDetailed(tokens).statements();
    }

    /**
     * 带诊断的切分，供解析器使用。
     *
     * @param tokens 输入 token 流
     * @return 切分结果，含有效语句与被跳过行的诊断
     */
    public static SplitResult splitDetailed(List<Token> tokens) {
        List<RawStmt> stmts = new ArrayList<>();
        List<ParseDiagnostic> diagnostics = new ArrayList<>();
        int pos = 0;
        int n = tokens.size();

        while (pos < n) {
            // Skip blank lines.
            while (pos < n && tokens.get(pos).type() == TokenType.NEWLINE) {
                pos++;
            }
            if (pos >= n || tokens.get(pos).type() == TokenType.EOF) {
                break;
            }

            Token idTok = tokens.get(pos);
            TokenType tt = idTok.type();
            if (tt != TokenType.IDENT && tt != TokenType.TYPE && tt != TokenType.STATE_VAR) {
                SourceSpan span = spanOf(idTok, idTok);
                diagnostics.add(
                        ParseDiagnostic.at(
                                ParseErrorCode.INVALID_STATEMENT,
                                "Line does not start a valid statement",
                                idText(idTok),
                                span));
                pos = skipToLineEnd(tokens, pos);
                continue;
            }
            String id = idTok.text();
            pos++;

            // Must be followed by `=`.
            if (pos >= n || tokens.get(pos).type() != TokenType.EQUALS) {
                diagnostics.add(
                        ParseDiagnostic.at(
                                ParseErrorCode.MISSING_ASSIGNMENT,
                                "Statement '" + id + "' is missing '='",
                                id,
                                spanOf(idTok, idTok)));
                pos = skipToLineEnd(tokens, pos);
                continue;
            }
            pos++; // consume '='

            // Collect expression tokens until a depth-0 newline / EOF, honoring ternary
            // continuation.
            List<Token> expr = new ArrayList<>();
            int depth = 0;
            int ternaryDepth = 0;
            while (pos < n && tokens.get(pos).type() != TokenType.EOF) {
                TokenType cur = tokens.get(pos).type();
                if (cur == TokenType.NEWLINE && depth <= 0 && ternaryDepth <= 0) {
                    int peek = pos + 1;
                    while (peek < n && tokens.get(peek).type() == TokenType.NEWLINE) {
                        peek++;
                    }
                    TokenType nextT = peek < n ? tokens.get(peek).type() : TokenType.EOF;
                    if (nextT == TokenType.QUESTION
                            || (nextT == TokenType.COLON && ternaryDepth > 0)) {
                        pos++; // ternary continuation — keep collecting
                        continue;
                    }
                    break; // statement boundary
                }
                if (cur == TokenType.NEWLINE) {
                    pos++; // newline inside bracket/ternary — skip
                    continue;
                }
                if (cur == TokenType.L_PAREN
                        || cur == TokenType.L_BRACK
                        || cur == TokenType.L_BRACE) {
                    depth++;
                } else if ((cur == TokenType.R_PAREN
                                || cur == TokenType.R_BRACK
                                || cur == TokenType.R_BRACE)
                        && depth > 0) {
                    depth--;
                } else if (cur == TokenType.QUESTION && depth == 0) {
                    ternaryDepth++;
                } else if (cur == TokenType.COLON && depth == 0 && ternaryDepth > 0) {
                    ternaryDepth--;
                }
                expr.add(tokens.get(pos));
                pos++;
            }

            if (!expr.isEmpty()) {
                Token last = expr.get(expr.size() - 1);
                stmts.add(new RawStmt(id, expr, spanOf(idTok, last)));
            }
        }

        return new SplitResult(stmts, diagnostics);
    }

    private static int skipToLineEnd(List<Token> tokens, int pos) {
        int n = tokens.size();
        while (pos < n
                && tokens.get(pos).type() != TokenType.NEWLINE
                && tokens.get(pos).type() != TokenType.EOF) {
            pos++;
        }
        return pos;
    }

    private static SourceSpan spanOf(Token start, Token end) {
        int endOff = end.offset() + Math.max(0, end.length());
        return new SourceSpan(start.line(), start.column(), start.offset(), endOff);
    }

    private static String idText(Token t) {
        return t.text() != null ? t.text() : t.type().name();
    }
}
