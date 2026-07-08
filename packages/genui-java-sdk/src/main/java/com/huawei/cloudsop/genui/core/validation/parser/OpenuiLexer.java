/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.parser;

import java.util.ArrayList;
import java.util.List;

/**
 * openui-lang 的手写词法分析器。
 *
 * <p>
 * 精确对应 {@code packages/lang-core/src/parser/lexer.ts} 的分词规则（字符串转义、数字格式、{@code $state}、
 * {@code @builtin}、帕斯卡命名的 {@link TokenType#TYPE} 与小写的 {@link TokenType#IDENT}、双字符运算符
 * {@code == != >= <= && || ??}）。
 *
 * <p>
 * Java 扩展：每个 {@link Token} 都携带从 1 开始的行/列以及从 0 开始的偏移/长度，供下游阶段附加诊断信息。
 * 换行符是有意义的 token（语句分隔符）。
 *
 * @since 2026
 */
public final class OpenuiLexer {

    private OpenuiLexer() {
    }

    /**
     * 将源字符串分词为以 {@link TokenType#EOF} 结尾的扁平 token 列表。
     *
     * @param src
     *            待分词的源字符串
     * @return token 列表，末尾为 EOF token
     */
    public static List<Token> tokenize(String src) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        int n = src.length();
        int line = 1;
        int col = 1;

        while (i < n) {
            // Skip horizontal whitespace (not newlines — they are significant).
            while (i < n && (src.charAt(i) == ' ' || src.charAt(i) == '\t' || src.charAt(i) == '\r')) {
                i++;
                col++;
            }
            if (i >= n) {
                break;
            }

            char c = src.charAt(i);
            int startLine = line;
            int startCol = col;
            int startOff = i;

            // ── Newline ────────────────────────────────────────────────────────
            if (c == '\n') {
                tokens.add(Token.of(TokenType.NEWLINE, startLine, startCol, startOff, 1));
                i++;
                line++;
                col = 1;
                continue;
            }

            // ── Single-character punctuation ───────────────────────────────────
            TokenType punct = singleCharPunct(c);
            if (punct != null) {
                tokens.add(Token.of(punct, startLine, startCol, startOff, 1));
                i++;
                col++;
                continue;
            }

            // ── Two-char / single-char operators ───────────────────────────────
            if (c == '=') {
                if (peek(src, i + 1) == '=') {
                    tokens.add(Token.of(TokenType.EQ_EQ, startLine, startCol, startOff, 2));
                    i += 2;
                    col += 2;
                } else {
                    tokens.add(Token.of(TokenType.EQUALS, startLine, startCol, startOff, 1));
                    i++;
                    col++;
                }
                continue;
            }
            if (c == '!') {
                if (peek(src, i + 1) == '=') {
                    tokens.add(Token.of(TokenType.NOT_EQ, startLine, startCol, startOff, 2));
                    i += 2;
                    col += 2;
                } else {
                    tokens.add(Token.of(TokenType.NOT, startLine, startCol, startOff, 1));
                    i++;
                    col++;
                }
                continue;
            }
            if (c == '>') {
                if (peek(src, i + 1) == '=') {
                    tokens.add(Token.of(TokenType.GREATER_EQ, startLine, startCol, startOff, 2));
                    i += 2;
                    col += 2;
                } else {
                    tokens.add(Token.of(TokenType.GREATER, startLine, startCol, startOff, 1));
                    i++;
                    col++;
                }
                continue;
            }
            if (c == '<') {
                if (peek(src, i + 1) == '=') {
                    tokens.add(Token.of(TokenType.LESS_EQ, startLine, startCol, startOff, 2));
                    i += 2;
                    col += 2;
                } else {
                    tokens.add(Token.of(TokenType.LESS, startLine, startCol, startOff, 1));
                    i++;
                    col++;
                }
                continue;
            }
            // '&' and '|' always tokenize as And/Or (matches lexer.ts, single or double char).
            if (c == '&') {
                int len = peek(src, i + 1) == '&' ? 2 : 1;
                tokens.add(Token.of(TokenType.AND, startLine, startCol, startOff, len));
                i += len;
                col += len;
                continue;
            }
            if (c == '|') {
                int len = peek(src, i + 1) == '|' ? 2 : 1;
                tokens.add(Token.of(TokenType.OR, startLine, startCol, startOff, len));
                i += len;
                col += len;
                continue;
            }
            if (c == '.') {
                tokens.add(Token.of(TokenType.DOT, startLine, startCol, startOff, 1));
                i++;
                col++;
                continue;
            }
            if (c == '?') {
                if (peek(src, i + 1) == '?') {
                    tokens.add(Token.of(TokenType.NULL_COAL, startLine, startCol, startOff, 2));
                    i += 2;
                    col += 2;
                } else {
                    tokens.add(Token.of(TokenType.QUESTION, startLine, startCol, startOff, 1));
                    i++;
                    col++;
                }
                continue;
            }
            if (c == '+') {
                tokens.add(Token.of(TokenType.PLUS, startLine, startCol, startOff, 1));
                i++;
                col++;
                continue;
            }
            if (c == '*') {
                tokens.add(Token.of(TokenType.STAR, startLine, startCol, startOff, 1));
                i++;
                col++;
                continue;
            }
            if (c == '/') {
                tokens.add(Token.of(TokenType.SLASH, startLine, startCol, startOff, 1));
                i++;
                col++;
                continue;
            }
            if (c == '%') {
                tokens.add(Token.of(TokenType.PERCENT, startLine, startCol, startOff, 1));
                i++;
                col++;
                continue;
            }

            // ── Double-quoted string literal ───────────────────────────────────
            if (c == '"') {
                int start = i;
                i++;
                boolean closed = false;
                while (i < n) {
                    char sc = src.charAt(i);
                    if (sc == '\\') {
                        i += 2;
                    } else if (sc == '"') {
                        i++;
                        closed = true;
                        break;
                    } else {
                        i++;
                    }
                }
                String raw = src.substring(start, Math.min(i, n));
                String value = decodeDoubleQuoted(closed ? raw : raw + '"');
                int len = i - start;
                tokens.add(Token.ofText(TokenType.STR, value, startLine, startCol, startOff, len));
                col += len;
                continue;
            }

            // ── Single-quoted string literal ───────────────────────────────────
            if (c == '\'') {
                int start = i;
                i++;
                StringBuilder sb = new StringBuilder();
                while (i < n) {
                    char sc = src.charAt(i);
                    if (sc == '\\') {
                        i++;
                        if (i < n) {
                            char esc = src.charAt(i);
                            switch (esc) {
                                case '\'' -> sb.append('\'');
                                case '\\' -> sb.append('\\');
                                case 'n' -> sb.append('\n');
                                case 't' -> sb.append('\t');
                                default -> sb.append(esc);
                            }
                            i++;
                        }
                    } else if (sc == '\'') {
                        i++;
                        break;
                    } else {
                        sb.append(sc);
                        i++;
                    }
                }
                int len = i - start;
                tokens.add(Token.ofText(TokenType.STR, sb.toString(), startLine, startCol, startOff, len));
                col += len;
                continue;
            }

            // ── Minus: negative number literal or subtraction operator ─────────
            if (c == '-') {
                Token prev = tokens.isEmpty() ? null : tokens.get(tokens.size() - 1);
                boolean afterValue = prev != null && isValueToken(prev.type());
                boolean digitFollows = i + 1 < n && isDigit(src.charAt(i + 1));
                if (afterValue || !digitFollows) {
                    tokens.add(Token.of(TokenType.MINUS, startLine, startCol, startOff, 1));
                    i++;
                    col++;
                    continue;
                }
                // else: negative number literal — fall through to number parsing.
            }

            // ── Number literal ─────────────────────────────────────────────────
            boolean isNegDigit = c == '-' && i + 1 < n && isDigit(src.charAt(i + 1));
            if (isDigit(c) || isNegDigit) {
                int start = i;
                if (src.charAt(i) == '-') {
                    i++;
                }
                while (i < n && isDigit(src.charAt(i))) {
                    i++;
                }
                if (i < n && src.charAt(i) == '.' && i + 1 < n && isDigit(src.charAt(i + 1))) {
                    i++;
                    while (i < n && isDigit(src.charAt(i))) {
                        i++;
                    }
                }
                if (i < n && (src.charAt(i) == 'e' || src.charAt(i) == 'E')) {
                    i++;
                    if (i < n && (src.charAt(i) == '+' || src.charAt(i) == '-')) {
                        i++;
                    }
                    while (i < n && isDigit(src.charAt(i))) {
                        i++;
                    }
                }
                int len = i - start;
                double value = Double.parseDouble(src.substring(start, i));
                tokens.add(Token.ofNumber(value, startLine, startCol, startOff, len));
                col += len;
                continue;
            }

            // ── State variable: $identifier ────────────────────────────────────
            if (c == '$' && i + 1 < n && isIdentStart(src.charAt(i + 1))) {
                int start = i;
                i++;
                while (i < n && isIdentPart(src.charAt(i))) {
                    i++;
                }
                int len = i - start;
                tokens.add(
                        Token.ofText(TokenType.STATE_VAR, src.substring(start, i), startLine, startCol, startOff, len));
                col += len;
                continue;
            }

            // ── Keyword or identifier ──────────────────────────────────────────
            if (isIdentStart(c)) {
                int start = i;
                while (i < n && isIdentPart(src.charAt(i))) {
                    i++;
                }
                String word = src.substring(start, i);
                int len = i - start;
                switch (word) {
                    case "true" -> tokens.add(Token.of(TokenType.TRUE, startLine, startCol, startOff, len));
                    case "false" -> tokens.add(Token.of(TokenType.FALSE, startLine, startCol, startOff, len));
                    case "null" -> tokens.add(Token.of(TokenType.NULL, startLine, startCol, startOff, len));
                    default -> {
                        TokenType kind = (c >= 'A' && c <= 'Z') ? TokenType.TYPE : TokenType.IDENT;
                        tokens.add(Token.ofText(kind, word, startLine, startCol, startOff, len));
                    }
                }
                col += len;
                continue;
            }

            // ── Builtin call: @identifier ──────────────────────────────────────
            if (c == '@' && i + 1 < n && isIdentStart(src.charAt(i + 1))) {
                i++; // skip @
                int start = i;
                while (i < n && isIdentPart(src.charAt(i))) {
                    i++;
                }
                int len = i - startOff;
                tokens.add(Token.ofText(TokenType.BUILTIN_CALL, src.substring(start, i), startLine, startCol, startOff,
                        len));
                col += len;
                continue;
            }

            // ── Any other character (e.g. #, emoji) — skip. ────────────────────
            i++;
            col++;
        }

        tokens.add(Token.of(TokenType.EOF, line, col, i, 0));
        return tokens;
    }

    private static TokenType singleCharPunct(char c) {
        return switch (c) {
            case '(' -> TokenType.L_PAREN;
            case ')' -> TokenType.R_PAREN;
            case '[' -> TokenType.L_BRACK;
            case ']' -> TokenType.R_BRACK;
            case '{' -> TokenType.L_BRACE;
            case '}' -> TokenType.R_BRACE;
            case ',' -> TokenType.COMMA;
            case ':' -> TokenType.COLON;
            default -> null;
        };
    }

    private static char peek(String src, int idx) {
        return idx < src.length() ? src.charAt(idx) : '\0';
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isIdentStart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private static boolean isIdentPart(char c) {
        return isIdentStart(c) || isDigit(c);
    }

    private static boolean isValueToken(TokenType t) {
        return switch (t) {
            case NUM, STR, IDENT, TYPE, R_PAREN, R_BRACK, TRUE, FALSE, NULL, STATE_VAR, BUILTIN_CALL -> true;
            default -> false;
        };
    }

    /**
     * Decode a double-quoted raw literal (including surrounding quotes) into its runtime string, mirroring the TS path
     * that delegates to {@code JSON.parse}. On malformed escapes it falls back to stripping the surrounding quotes
     * (parity with the TS catch branch).
     */
    private static String decodeDoubleQuoted(String raw) {
        if (raw.length() < 2) {
            return raw.replaceAll("^\"|\"$", "");
        }
        String body = raw.substring(1, raw.length() - 1);
        StringBuilder sb = new StringBuilder(body.length());
        try {
            for (int i = 0; i < body.length(); i++) {
                char c = body.charAt(i);
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                i++;
                if (i >= body.length()) {
                    throw new IllegalArgumentException("dangling escape");
                }
                char esc = body.charAt(i);
                switch (esc) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> {
                        if (i + 4 >= body.length() + 1 || i + 5 > body.length()) {
                            throw new IllegalArgumentException("bad unicode escape");
                        }
                        String hex = body.substring(i + 1, i + 5);
                        sb.append((char) Integer.parseInt(hex, 16));
                        i += 4;
                    }
                    default -> throw new IllegalArgumentException("bad escape \\" + esc);
                }
            }
            return sb.toString();
        } catch (RuntimeException ex) {
            // Fallback parity with TS: strip leading/trailing quotes, return raw text.
            return raw.replaceAll("^\"|\"$", "");
        }
    }
}
