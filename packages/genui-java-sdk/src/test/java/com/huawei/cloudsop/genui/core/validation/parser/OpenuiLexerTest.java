/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

class OpenuiLexerTest {

    private static List<TokenType> types(String src) {
        return OpenuiLexer.tokenize(src).stream().map(Token::type).collect(Collectors.toList());
    }

    private static Token first(String src, TokenType type) {
        return OpenuiLexer.tokenize(src).stream().filter(t -> t.type() == type).findFirst().orElseThrow();
    }

    @Test
    void endsWithEof() {
        List<TokenType> t = types("");
        assertEquals(List.of(TokenType.EOF), t);
    }

    @Test
    void pascalCaseIsTypeLowercaseIsIdent() {
        assertEquals(TokenType.TYPE, first("Table", TokenType.TYPE).type());
        assertEquals("Table", first("Table", TokenType.TYPE).text());
        assertEquals(TokenType.IDENT, first("rows", TokenType.IDENT).type());
        assertEquals("rows", first("rows", TokenType.IDENT).text());
    }

    @Test
    void booleanAndNullKeywords() {
        assertEquals(List.of(TokenType.TRUE, TokenType.FALSE, TokenType.NULL, TokenType.EOF), types("true false null"));
    }

    @Test
    void stateVarIncludesDollar() {
        Token t = first("$count", TokenType.STATE_VAR);
        assertEquals("$count", t.text());
    }

    @Test
    void builtinCallDropsAt() {
        Token t = first("@Render", TokenType.BUILTIN_CALL);
        assertEquals("Render", t.text());
    }

    @Test
    void twoCharOperators() {
        assertEquals(List.of(TokenType.EQ_EQ, TokenType.NOT_EQ, TokenType.GREATER_EQ, TokenType.LESS_EQ, TokenType.AND,
                TokenType.OR, TokenType.NULL_COAL, TokenType.EOF), types("== != >= <= && || ??"));
    }

    @Test
    void singleCharOperatorsAndPunctuation() {
        assertEquals(
                List.of(TokenType.EQUALS, TokenType.NOT, TokenType.GREATER, TokenType.LESS, TokenType.QUESTION,
                        TokenType.PLUS, TokenType.MINUS, TokenType.STAR, TokenType.SLASH, TokenType.PERCENT,
                        TokenType.DOT, TokenType.COLON, TokenType.COMMA, TokenType.EOF),
                types("= ! > < ? + - * / % . : ,"));
    }

    @Test
    void brackets() {
        assertEquals(List.of(TokenType.L_PAREN, TokenType.R_PAREN, TokenType.L_BRACK, TokenType.R_BRACK,
                TokenType.L_BRACE, TokenType.R_BRACE, TokenType.EOF), types("()[]{}"));
    }

    @Test
    void doubleQuotedStringWithEscapes() {
        Token t = first("\"line\\nbreak\"", TokenType.STR);
        assertEquals("line\nbreak", t.text());
    }

    @Test
    void singleQuotedString() {
        Token t = first("'hello'", TokenType.STR);
        assertEquals("hello", t.text());
    }

    @Test
    void numberFormats() {
        assertEquals(42d, first("42", TokenType.NUM).number());
        assertEquals(3.14d, first("3.14", TokenType.NUM).number());
        assertEquals(1e3d, first("1e3", TokenType.NUM).number());
    }

    @Test
    void minusIsNegativeNumberInValuePositionButOperatorAfterValue() {
        // Leading minus before a digit => negative number literal
        assertEquals(-3d, first("-3", TokenType.NUM).number());
        // After a value it is subtraction
        List<TokenType> t = types("a - 3");
        assertTrue(t.contains(TokenType.MINUS), "expected MINUS operator between value and number");
    }

    @Test
    void newlinesAreSignificant() {
        assertEquals(List.of(TokenType.IDENT, TokenType.NEWLINE, TokenType.IDENT, TokenType.EOF), types("a\nb"));
    }

    @Test
    void componentCallSequence() {
        // Table([col], data.rows)
        assertEquals(List.of(TokenType.TYPE, TokenType.L_PAREN, TokenType.L_BRACK, TokenType.IDENT, TokenType.R_BRACK,
                TokenType.COMMA, TokenType.IDENT, TokenType.DOT, TokenType.IDENT, TokenType.R_PAREN, TokenType.EOF),
                types("Table([col], data.rows)"));
    }

    @Test
    void tracksLineAndColumn() {
        List<Token> toks = OpenuiLexer.tokenize("a\n  Table");
        Token table = toks.stream().filter(t -> t.type() == TokenType.TYPE).findFirst().orElseThrow();
        assertEquals(2, table.line());
        assertEquals(3, table.column());
    }
}
