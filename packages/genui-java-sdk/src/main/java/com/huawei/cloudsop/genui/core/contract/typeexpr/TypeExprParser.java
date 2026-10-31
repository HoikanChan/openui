/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.contract.typeexpr;

import com.huawei.cloudsop.genui.core.GenerationSdkException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * TS 风格类型串解析器：词法 + 递归下降，文法封闭为——
 *
 * <pre>
 * expr    := term ("|" term)*
 * term    := factor ("[]")*
 * factor  := base | "Record" "&lt;" "string" "," expr "&gt;" | "{" fields "}" | UPPER
 * UPPER   := 单个大写字母(命名泛型变量,如 T、D)
 * base    := any | number | string | boolean | null | object | array
 * fields  := (ident ":" expr ("," ident ":" expr)*)? ","?
 * </pre>
 *
 * <p>已删除的 {@code param(n)} 与 {@code A when param(n) is array, else B} 不在文法内;出现时按未知 token 或 多余 token
 * 在加载期失败,错误信息带条目名与原始串。
 *
 * <p>文法之外的构造（未知 token、括号不配对、非递归下降可接受的形状）在解析期抛出 {@link GenerationSdkException}，错误信息带条目名与原始串——契约加载期
 * fail-fast 的基础。
 *
 * @since 2026
 */
public final class TypeExprParser {

    private static final Set<String> BASE_TYPES =
            Set.of("any", "number", "string", "boolean", "null", "object", "array");

    private TypeExprParser() {}

    /**
     * 解析 {@code source} 为 {@link TypeExpr};{@code entry} 仅用于错误信息定位。
     *
     * @param source 待解析的类型表达式原文
     * @param entry 契约条目名,出错时拼入异常信息
     * @return 解析后的类型表达式
     * @throws GenerationSdkException 出现未知 token、括号不配对或存在多余 token
     */
    public static TypeExpr parse(String source, String entry) {
        Lexer lexer = new Lexer(source, entry);
        List<String> tokens = lexer.lex();
        Parser parser = new Parser(tokens, source, entry);
        TypeExpr parsed = parser.parseExpr();
        if (!parser.atEnd()) {
            throw parser.error("unexpected trailing tokens");
        }
        return parsed;
    }

    private static final class Lexer {
        private final String source;
        private final String entry;

        Lexer(String source, String entry) {
            this.source = source;
            this.entry = entry;
        }

        List<String> lex() {
            List<String> tokens = new ArrayList<>();
            int i = 0;
            int n = source.length();
            while (i < n) {
                char c = source.charAt(i);
                if (Character.isWhitespace(c)) {
                    i++;
                    continue;
                }
                if ("|[]{},:<>".indexOf(c) >= 0) {
                    tokens.add(String.valueOf(c));
                    i++;
                    continue;
                }
                if (Character.isJavaIdentifierStart(c)) {
                    int start = i;
                    while (i < n && Character.isJavaIdentifierPart(source.charAt(i))) {
                        i++;
                    }
                    tokens.add(source.substring(start, i));
                    continue;
                }
                throw error("unexpected character '" + c + "'");
            }
            return tokens;
        }

        GenerationSdkException error(String message) {
            return new GenerationSdkException(
                    "Invalid type expression in builtin '"
                            + entry
                            + "': "
                            + message
                            + " (source: \""
                            + source
                            + "\")");
        }
    }

    private static final class Parser {
        private final List<String> tokens;
        private final String source;
        private final String entry;
        private int position;

        Parser(List<String> tokens, String source, String entry) {
            this.tokens = tokens;
            this.source = source;
            this.entry = entry;
        }

        TypeExpr parseExpr() {
            TypeExpr first = parseTerm();
            TypeExpr combined = first;
            if (peek().equals("|")) {
                List<TypeExpr> alternatives = new ArrayList<>();
                alternatives.add(first);
                while (peek().equals("|")) {
                    next();
                    alternatives.add(parseTerm());
                }
                combined = new TypeExpr.UnionOf(alternatives);
            }
            return combined;
        }

        private TypeExpr parseTerm() {
            TypeExpr factor = parseFactor();
            while (peek().equals("[")) {
                next();
                expect("]");
                factor = new TypeExpr.ArrayOf(factor);
            }
            return factor;
        }

        private TypeExpr parseFactor() {
            String token = next();
            switch (token) {
                case "Record":
                    expect("<");
                    expect("string");
                    expect(",");
                    TypeExpr value = parseExpr();
                    expect(">");
                    return new TypeExpr.RecordOf(value);
                case "{":
                    return parseObjectLiteral();
                default:
                    if (BASE_TYPES.contains(token)) {
                        return new TypeExpr.Base(token);
                    }
                    if (isTypeVariableName(token)) {
                        return new TypeExpr.TypeVar(token);
                    }
                    throw error("unknown type token \"" + token + "\"");
            }
        }

        /** 命名泛型变量:单个大写字母(T、D 等),与基础类型名和 Record/param 关键字不冲突。 */
        private static boolean isTypeVariableName(String token) {
            return token.length() == 1 && Character.isUpperCase(token.charAt(0));
        }

        private TypeExpr parseObjectLiteral() {
            List<TypeExpr.ObjectLiteral.Field> fields = new ArrayList<>();
            if (peek().equals("}")) {
                next();
                return new TypeExpr.ObjectLiteral(fields);
            }
            while (true) {
                String name = next();
                if (!isIdentifier(name)) {
                    throw error("expected field name in object literal, got \"" + name + "\"");
                }
                expect(":");
                fields.add(new TypeExpr.ObjectLiteral.Field(name, parseExpr()));
                String separator = next();
                if (separator.equals("}")) {
                    return new TypeExpr.ObjectLiteral(fields);
                }
                if (!separator.equals(",")) {
                    throw error("expected ',' or '}' in object literal, got \"" + separator + "\"");
                }
                if (peek().equals("}")) { // trailing comma
                    next();
                    return new TypeExpr.ObjectLiteral(fields);
                }
            }
        }

        private static boolean isIdentifier(String token) {
            if (token.isEmpty() || !Character.isJavaIdentifierStart(token.charAt(0))) {
                return false;
            }
            for (int i = 1; i < token.length(); i++) {
                if (!Character.isJavaIdentifierPart(token.charAt(i))) {
                    return false;
                }
            }
            return true;
        }

        private String peek() {
            return position < tokens.size() ? tokens.get(position) : "";
        }

        private String next() {
            if (position >= tokens.size()) {
                throw error("unexpected end of type expression");
            }
            return tokens.get(position++);
        }

        private void expect(String expected) {
            String actual = next();
            if (!actual.equals(expected)) {
                throw error("expected '" + expected + "', got \"" + actual + "\"");
            }
        }

        boolean atEnd() {
            return position == tokens.size();
        }

        GenerationSdkException error(String message) {
            return new GenerationSdkException(
                    "Invalid type expression in builtin '"
                            + entry
                            + "': "
                            + message
                            + " (source: \""
                            + source
                            + "\")");
        }
    }
}
