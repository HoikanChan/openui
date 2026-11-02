/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.parser;

import java.util.ArrayList;
import java.util.List;

/**
 * openui-lang 的手写词法分析器。
 *
 * <p>精确对应 {@code packages/lang-core/src/parser/lexer.ts} 的分词规则（字符串转义、数字格式、{@code $state}、
 * {@code @builtin}、帕斯卡命名的 {@link TokenType#TYPE} 与小写的 {@link TokenType#IDENT}、双字符运算符 {@code == !=
 * >= <= && || ??}）。
 *
 * <p>Java 扩展：每个 {@link Token} 都携带从 1 开始的行/列以及从 0 开始的偏移/长度，供下游阶段附加诊断信息。 换行符是有意义的 token（语句分隔符）。
 *
 * @since 2026
 */
public final class OpenuiLexer {
    /** A unicode escape is {@code u} followed by exactly four hex digits. */
    private static final int UNICODE_HEX_DIGITS = 4;

    private static final int HEX_RADIX = 16;

    private OpenuiLexer() {}

    /**
     * 将源字符串分词为以 {@link TokenType#EOF} 结尾的扁平 token 列表。
     *
     * @param src 待分词的源字符串
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
            while (i < n
                    && (src.charAt(i) == ' ' || src.charAt(i) == '\t' || src.charAt(i) == '\r')) {
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

            Scan scan = scanToken(src, tokens, startLine, startCol, startOff);
            if (scan != null) {
                i = scan.pos();
                col = scan.col();
                continue;
            }

            // ── Any other character (e.g. #, emoji) — skip. ────────────────────
            i++;
            col++;
        }

        tokens.add(Token.of(TokenType.EOF, line, col, i, 0));
        return tokens;
    }

    private static Scan scanToken(String src, List<Token> tokens, int line, int col, int off) {
        TokenType punct = singleCharPunct(src.charAt(off));
        if (punct != null) {
            tokens.add(Token.of(punct, line, col, off, 1));
            return new Scan(off + 1, col + 1);
        }
        Scan scan = scanDoubleCharOperator(src, tokens, line, col, off);
        if (scan == null) {
            scan = scanSimpleOperator(src, tokens, line, col, off);
        }
        if (scan == null) {
            scan = scanDoubleQuoted(src, tokens, line, col, off);
        }
        if (scan == null) {
            scan = scanSingleQuoted(src, tokens, line, col, off);
        }
        if (scan == null) {
            scan = scanNumber(src, tokens, line, col, off);
        }
        if (scan == null) {
            scan = scanStateVariable(src, tokens, line, col, off);
        }
        if (scan == null) {
            scan = scanIdentifierOrKeyword(src, tokens, line, col, off);
        }
        if (scan == null) {
            scan = scanBuiltinCall(src, tokens, line, col, off);
        }
        return scan;
    }

    private static Scan scanDoubleCharOperator(
            String src, List<Token> tokens, int line, int col, int off) {
        char c = src.charAt(off);
        TokenType one;
        TokenType two;
        char doubled;
        switch (c) {
            case '=' -> {
                one = TokenType.EQUALS;
                two = TokenType.EQ_EQ;
                doubled = '=';
            }
            case '!' -> {
                one = TokenType.NOT;
                two = TokenType.NOT_EQ;
                doubled = '=';
            }
            case '>' -> {
                one = TokenType.GREATER;
                two = TokenType.GREATER_EQ;
                doubled = '=';
            }
            case '<' -> {
                one = TokenType.LESS;
                two = TokenType.LESS_EQ;
                doubled = '=';
            }
            case '?' -> {
                one = TokenType.QUESTION;
                two = TokenType.NULL_COAL;
                doubled = '?';
            }
            default -> {
                return null;
            }
        }
        boolean pair = peek(src, off + 1) == doubled;
        TokenType type = pair ? two : one;
        int len = pair ? 2 : 1;
        tokens.add(Token.of(type, line, col, off, len));
        return new Scan(off + len, col + len);
    }

    private static Scan scanSimpleOperator(
            String src, List<Token> tokens, int line, int col, int off) {
        char c = src.charAt(off);
        TokenType type;
        int len;
        switch (c) {
            case '&' -> {
                type = TokenType.AND;
                len = peek(src, off + 1) == '&' ? 2 : 1;
            }
            case '|' -> {
                type = TokenType.OR;
                len = peek(src, off + 1) == '|' ? 2 : 1;
            }
            case '.' -> {
                type = TokenType.DOT;
                len = 1;
            }
            case '+' -> {
                type = TokenType.PLUS;
                len = 1;
            }
            case '*' -> {
                type = TokenType.STAR;
                len = 1;
            }
            case '/' -> {
                type = TokenType.SLASH;
                len = 1;
            }
            case '%' -> {
                type = TokenType.PERCENT;
                len = 1;
            }
            case '-' -> {
                if (!minusIsOperator(src, tokens, off)) {
                    return null;
                }
                type = TokenType.MINUS;
                len = 1;
            }
            default -> {
                return null;
            }
        }
        tokens.add(Token.of(type, line, col, off, len));
        return new Scan(off + len, col + len);
    }

    private static boolean minusIsOperator(String src, List<Token> tokens, int off) {
        Token prev = tokens.isEmpty() ? null : tokens.get(tokens.size() - 1);
        boolean afterValue = prev != null && isValueToken(prev.type());
        boolean digitFollows = off + 1 < src.length() && isDigit(src.charAt(off + 1));
        return afterValue || !digitFollows;
    }

    private static Scan scanDoubleQuoted(
            String src, List<Token> tokens, int line, int col, int off) {
        int n = src.length();
        if (src.charAt(off) != '"') {
            return null;
        }
        int start = off;
        int i = off + 1;
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
        i = Math.min(i, n);
        int len = i - start;
        tokens.add(Token.ofText(TokenType.STR, value, line, col, off, len));
        return new Scan(i, col + len);
    }

    private static Scan scanSingleQuoted(
            String src, List<Token> tokens, int line, int col, int off) {
        int n = src.length();
        if (src.charAt(off) != '\'') {
            return null;
        }
        int start = off;
        int i = off + 1;
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
        tokens.add(Token.ofText(TokenType.STR, sb.toString(), line, col, off, len));
        return new Scan(i, col + len);
    }

    private static Scan scanNumber(String src, List<Token> tokens, int line, int col, int off) {
        char c = src.charAt(off);
        boolean isNegDigit = isNegativeNumberStart(src, off);
        if (!isDigit(c) && !isNegDigit) {
            return null;
        }
        int start = off;
        int i = off;
        if (src.charAt(i) == '-') {
            i++;
        }
        i = skipDigits(src, i);
        if (hasFraction(src, i)) {
            i = skipDigits(src, i + 1);
        }
        if (hasExponent(src, i)) {
            i++;
            if (hasExponentSign(src, i)) {
                i++;
            }
            i = skipDigits(src, i);
        }
        int len = i - start;
        double value = Double.parseDouble(src.substring(start, i));
        tokens.add(Token.ofNumber(value, line, col, off, len));
        return new Scan(i, col + len);
    }

    private static boolean isNegativeNumberStart(String src, int off) {
        return src.charAt(off) == '-' && off + 1 < src.length() && isDigit(src.charAt(off + 1));
    }

    private static int skipDigits(String src, int i) {
        int n = src.length();
        while (i < n && isDigit(src.charAt(i))) {
            i++;
        }
        return i;
    }

    private static boolean hasFraction(String src, int i) {
        int n = src.length();
        return i < n && src.charAt(i) == '.' && i + 1 < n && isDigit(src.charAt(i + 1));
    }

    private static boolean hasExponent(String src, int i) {
        return i < src.length() && (src.charAt(i) == 'e' || src.charAt(i) == 'E');
    }

    private static boolean hasExponentSign(String src, int i) {
        return i < src.length() && (src.charAt(i) == '+' || src.charAt(i) == '-');
    }

    private static Scan scanStateVariable(
            String src, List<Token> tokens, int line, int col, int off) {
        int n = src.length();
        if (src.charAt(off) != '$' || off + 1 >= n || !isIdentStart(src.charAt(off + 1))) {
            return null;
        }
        int start = off;
        int i = off + 1;
        while (i < n && isIdentPart(src.charAt(i))) {
            i++;
        }
        int len = i - start;
        tokens.add(Token.ofText(TokenType.STATE_VAR, src.substring(start, i), line, col, off, len));
        return new Scan(i, col + len);
    }

    private static Scan scanIdentifierOrKeyword(
            String src, List<Token> tokens, int line, int col, int off) {
        int n = src.length();
        if (!isIdentStart(src.charAt(off))) {
            return null;
        }
        int start = off;
        int i = off;
        while (i < n && isIdentPart(src.charAt(i))) {
            i++;
        }
        String word = src.substring(start, i);
        int len = i - start;
        switch (word) {
            case "true" -> tokens.add(Token.of(TokenType.TRUE, line, col, off, len));
            case "false" -> tokens.add(Token.of(TokenType.FALSE, line, col, off, len));
            case "null" -> tokens.add(Token.of(TokenType.NULL, line, col, off, len));
            default -> {
                TokenType kind =
                        (src.charAt(off) >= 'A' && src.charAt(off) <= 'Z')
                                ? TokenType.TYPE
                                : TokenType.IDENT;
                tokens.add(Token.ofText(kind, word, line, col, off, len));
            }
        }
        return new Scan(i, col + len);
    }

    private static Scan scanBuiltinCall(
            String src, List<Token> tokens, int line, int col, int off) {
        int n = src.length();
        if (src.charAt(off) != '@' || off + 1 >= n || !isIdentStart(src.charAt(off + 1))) {
            return null;
        }
        int start = off + 1;
        int i = start;
        while (i < n && isIdentPart(src.charAt(i))) {
            i++;
        }
        int len = i - off;
        tokens.add(
                Token.ofText(TokenType.BUILTIN_CALL, src.substring(start, i), line, col, off, len));
        return new Scan(i, col + len);
    }

    private record Scan(int pos, int col) {}

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
            case NUM,
                            STR,
                            IDENT,
                            TYPE,
                            R_PAREN,
                            R_BRACK,
                            TRUE,
                            FALSE,
                            NULL,
                            STATE_VAR,
                            BUILTIN_CALL ->
                    true;
            default -> false;
        };
    }

    /**
     * Decode a double-quoted raw literal (including surrounding quotes) into its runtime string,
     * mirroring the TS path that delegates to {@code JSON.parse}. On malformed escapes it falls
     * back to stripping the surrounding quotes (parity with the TS catch branch).
     *
     * @param raw raw literal including its surrounding quotes
     * @return decoded runtime string, or the quote-stripped raw text on malformed escapes
     */
    private static String decodeDoubleQuoted(String raw) {
        if (raw.length() < 2) {
            return raw.replaceAll("^\"|\"$", "");
        }
        String body = raw.substring(1, raw.length() - 1);
        StringBuilder sb = new StringBuilder(body.length());
        try {
            int i = 0;
            while (i < body.length()) {
                char c = body.charAt(i);
                if (c != '\\') {
                    sb.append(c);
                    i++;
                    continue;
                }
                int escapeIndex = i + 1;
                if (escapeIndex >= body.length()) {
                    throw new IllegalArgumentException("dangling escape");
                }
                char esc = body.charAt(escapeIndex);
                int nextIndex = escapeIndex + 1;
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
                        if (escapeIndex + UNICODE_HEX_DIGITS + 1 > body.length()) {
                            throw new IllegalArgumentException("bad unicode escape");
                        }
                        String hex =
                                body.substring(
                                        escapeIndex + 1, escapeIndex + 1 + UNICODE_HEX_DIGITS);
                        sb.append((char) Integer.parseInt(hex, HEX_RADIX));
                        nextIndex = escapeIndex + 1 + UNICODE_HEX_DIGITS;
                    }
                    default -> throw new IllegalArgumentException("bad escape \\" + esc);
                }
                i = nextIndex;
            }
            return sb.toString();
        } catch (RuntimeException ex) {
            // Fallback parity with TS: strip leading/trailing quotes, return raw text.
            return raw.replaceAll("^\"|\"$", "");
        }
    }
}
