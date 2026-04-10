package dev.openui.langcore.lexer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.openui.langcore.lexer.TokenType.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Lexer} — one test per AC (AC1-18).
 * Ref: Req 1.*
 */
class LexerTest {

    private List<Token> lex(String src) {
        return new Lexer(src).tokenize();
    }

    private TokenType type(List<Token> tokens, int i) {
        return tokens.get(i).type();
    }

    // AC1 — horizontal whitespace is skipped
    @Test
    void ac1_horizontalWhitespaceSkipped() {
        List<Token> tokens = lex("  \t  ");
        assertEquals(1, tokens.size());
        assertEquals(EOF, tokens.get(0).type());
    }

    // AC2 — newline emits Newline token
    @Test
    void ac2_newlineEmitsToken() {
        List<Token> tokens = lex("\n");
        assertEquals(List.of(Newline, EOF), tokens.stream().map(Token::type).toList());
    }

    // AC3 — punctuation tokens
    @Test
    void ac3_punctuation() {
        List<Token> tokens = lex("()[]{},:");
        List<TokenType> expected = List.of(LParen, RParen, LBrack, RBrack, LBrace, RBrace, Comma, Colon, EOF);
        assertEquals(expected, tokens.stream().map(Token::type).toList());
    }

    // AC4 — = vs ==
    @Test
    void ac4_equalsAndEqEq() {
        List<Token> tokens = lex("= ==");
        assertEquals(Equals, type(tokens, 0));
        assertEquals(EqEq,   type(tokens, 1));
    }

    // AC5 — ! vs !=
    @Test
    void ac5_notAndNotEq() {
        List<Token> tokens = lex("! !=");
        assertEquals(Not,   type(tokens, 0));
        assertEquals(NotEq, type(tokens, 1));
    }

    // AC6 — comparison operators
    @Test
    void ac6_comparisonOperators() {
        List<Token> tokens = lex("> >= < <=");
        assertEquals(List.of(Greater, GreaterEq, Less, LessEq, EOF),
                tokens.stream().map(Token::type).toList());
    }

    // AC7 — && and || (both forms)
    @Test
    void ac7_logicalOperators() {
        List<Token> tokens = lex("&& & || |");
        assertEquals(List.of(And, And, Or, Or, EOF),
                tokens.stream().map(Token::type).toList());
    }

    // AC8 — single-character operators
    @Test
    void ac8_singleCharOps() {
        List<Token> tokens = lex(". ? + * / %");
        assertEquals(List.of(Dot, Question, Plus, Star, Slash, Percent, EOF),
                tokens.stream().map(Token::type).toList());
    }

    // AC9 — double-quoted string unescaping
    @Test
    void ac9_doubleQuotedString() {
        List<Token> tokens = lex("\"hello\\nworld\"");
        assertEquals(1, tokens.stream().filter(t -> t.type() == Str).count());
        assertEquals("hello\nworld", tokens.get(0).stringValue());
    }

    // AC9 — unterminated double-quoted string (streaming)
    @Test
    void ac9_unterminatedDoubleQuoted() {
        List<Token> tokens = lex("\"hello");
        assertEquals(Str, tokens.get(0).type());
        assertEquals("hello", tokens.get(0).stringValue());
    }

    // AC10 — single-quoted string
    @Test
    void ac10_singleQuotedString() {
        List<Token> tokens = lex("'it\\'s fine'");
        assertEquals(Str, tokens.get(0).type());
        assertEquals("it's fine", tokens.get(0).stringValue());
    }

    // AC11 — numeric literals: integer, decimal, scientific
    @Test
    void ac11_numericLiterals() {
        List<Token> tokens = lex("42 3.14 1e10");
        assertEquals(42.0,   tokens.get(0).numValue());
        assertEquals(3.14,   tokens.get(1).numValue());
        assertEquals(1e10,   tokens.get(2).numValue());
    }

    // AC12 — negative number literal
    @Test
    void ac12_negativeNumber() {
        List<Token> tokens = lex("-5");
        assertEquals(Num, tokens.get(0).type());
        assertEquals(-5.0, tokens.get(0).numValue());
    }

    // AC12 — binary minus after value-producing token
    @Test
    void ac12_binaryMinus() {
        List<Token> tokens = lex("10 -3");
        assertEquals(Num,   type(tokens, 0));
        assertEquals(Minus, type(tokens, 1));
        assertEquals(Num,   type(tokens, 2));
    }

    // AC13 — $stateVar
    @Test
    void ac13_stateVar() {
        List<Token> tokens = lex("$count $filter");
        assertEquals(StateVar, type(tokens, 0));
        assertEquals("$count", tokens.get(0).stringValue());
        assertEquals("$filter", tokens.get(1).stringValue());
    }

    // AC14 — @builtinCall
    @Test
    void ac14_builtinCall() {
        List<Token> tokens = lex("@Count @Each");
        assertEquals(BuiltinCall, type(tokens, 0));
        assertEquals("@Count", tokens.get(0).stringValue());
        assertEquals("@Each",  tokens.get(1).stringValue());
    }

    // AC15 — PascalCase → Type, lowercase → Ident
    @Test
    void ac15_identifiers() {
        List<Token> tokens = lex("Button item");
        assertEquals(Type,  type(tokens, 0));
        assertEquals("Button", tokens.get(0).stringValue());
        assertEquals(Ident, type(tokens, 1));
        assertEquals("item", tokens.get(1).stringValue());
    }

    // AC16 — keywords
    @Test
    void ac16_keywords() {
        List<Token> tokens = lex("true false null");
        assertEquals(List.of(True, False, Null, EOF),
                tokens.stream().map(Token::type).toList());
    }

    // AC17 — unknown characters (emoji, #) are skipped silently
    @Test
    void ac17_unknownCharsSkipped() {
        // '#' and the emoji codepoint are both unknown — only 42 survives
        List<Token> tokens = lex("# \uD83D\uDE00 42");
        assertEquals(Num, tokens.get(0).type());
        assertEquals(42.0, tokens.get(0).numValue());
    }

    // AC18 — EOF always appended
    @Test
    void ac18_eofAlwaysAppended() {
        List<Token> tokens = lex("");
        assertEquals(1, tokens.size());
        assertEquals(EOF, tokens.get(0).type());
    }

    // Edge case — unterminated string followed by other tokens
    @Test
    void edge_unterminatedStringThenMore() {
        List<Token> tokens = lex("\"hello 42");
        assertEquals(Str, tokens.get(0).type());
        // The number is consumed inside the string
        assertEquals(EOF, tokens.get(tokens.size() - 1).type());
    }

    // Edge case — negative number vs binary minus
    @Test
    void edge_negativeVsBinaryMinus() {
        List<Token> tokens = lex("x-3");
        assertEquals(Ident, type(tokens, 0));
        assertEquals(Minus, type(tokens, 1));
        assertEquals(Num,   type(tokens, 2));
        assertEquals(3.0, tokens.get(2).numValue());
    }
}
