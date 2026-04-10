package dev.openui.langcore.lexer;

import dev.openui.langcore.util.JsonStringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Tokenizes an OpenUI Lang program string into a list of {@link Token}s.
 * Ref: Req 1 AC1-18
 */
public final class Lexer {

    /** Token types that count as "value-producing" for the binary-minus disambiguation. */
    private static final Set<TokenType> VALUE_TYPES = Set.of(
            TokenType.Num, TokenType.Str, TokenType.Ident, TokenType.Type,
            TokenType.RParen, TokenType.RBrack, TokenType.True, TokenType.False,
            TokenType.Null, TokenType.StateVar, TokenType.BuiltinCall
    );

    private static final Set<String> KEYWORDS = Set.of("true", "false", "null");

    private final String src;
    private int pos;
    private final List<Token> tokens = new ArrayList<>();

    public Lexer(String src) {
        this.src = src;
    }

    /** Tokenize the entire source and return an immutable list of tokens. */
    public List<Token> tokenize() {
        while (pos < src.length()) {
            char c = src.charAt(pos);

            // AC1 — skip horizontal whitespace (space, tab, CR)
            if (c == ' ' || c == '\t' || c == '\r') {
                pos++;
                continue;
            }

            // AC2 — newline
            if (c == '\n') {
                emit(Token.of(TokenType.Newline));
                pos++;
                continue;
            }

            // AC9 — double-quoted string
            if (c == '"') {
                scanDoubleQuotedString();
                continue;
            }

            // AC10 — single-quoted string
            if (c == '\'') {
                scanSingleQuotedString();
                continue;
            }

            // AC13 — $stateVar
            if (c == '$') {
                scanStateVar();
                continue;
            }

            // AC14 — @builtinCall
            if (c == '@') {
                scanBuiltinCall();
                continue;
            }

            // AC11/12 — numbers and negative-number vs minus disambiguation
            if (Character.isDigit(c)) {
                scanNumber(false);
                continue;
            }
            if (c == '-') {
                if (!tokens.isEmpty() && VALUE_TYPES.contains(tokens.get(tokens.size() - 1).type())) {
                    // AC12 — binary minus after value token
                    emit(Token.of(TokenType.Minus));
                    pos++;
                } else if (pos + 1 < src.length() && Character.isDigit(src.charAt(pos + 1))) {
                    // AC12 — negative number literal
                    scanNumber(true);
                } else {
                    emit(Token.of(TokenType.Minus));
                    pos++;
                }
                continue;
            }

            // AC15 — PascalCase / lowercase identifiers and keywords
            if (Character.isLetter(c) || c == '_') {
                scanIdentOrKeyword();
                continue;
            }

            // AC3-8 — punctuation and operators
            switch (c) {
                case '(' -> { emit(Token.of(TokenType.LParen));   pos++; }
                case ')' -> { emit(Token.of(TokenType.RParen));   pos++; }
                case '[' -> { emit(Token.of(TokenType.LBrack));   pos++; }
                case ']' -> { emit(Token.of(TokenType.RBrack));   pos++; }
                case '{' -> { emit(Token.of(TokenType.LBrace));   pos++; }
                case '}' -> { emit(Token.of(TokenType.RBrace));   pos++; }
                case ',' -> { emit(Token.of(TokenType.Comma));    pos++; }
                case ':' -> { emit(Token.of(TokenType.Colon));    pos++; }
                case '.' -> { emit(Token.of(TokenType.Dot));      pos++; }
                case '?' -> { emit(Token.of(TokenType.Question)); pos++; }
                case '+' -> { emit(Token.of(TokenType.Plus));     pos++; }
                case '*' -> { emit(Token.of(TokenType.Star));     pos++; }
                case '/' -> { emit(Token.of(TokenType.Slash));    pos++; }
                case '%' -> { emit(Token.of(TokenType.Percent));  pos++; }

                // AC4 — = vs ==
                case '=' -> {
                    if (peek(1) == '=') { emit(Token.of(TokenType.EqEq));      pos += 2; }
                    else                { emit(Token.of(TokenType.Equals));     pos++;    }
                }
                // AC5 — ! vs !=
                case '!' -> {
                    if (peek(1) == '=') { emit(Token.of(TokenType.NotEq));     pos += 2; }
                    else                { emit(Token.of(TokenType.Not));        pos++;    }
                }
                // AC6 — > vs >=
                case '>' -> {
                    if (peek(1) == '=') { emit(Token.of(TokenType.GreaterEq)); pos += 2; }
                    else                { emit(Token.of(TokenType.Greater));    pos++;    }
                }
                // AC6 — < vs <=
                case '<' -> {
                    if (peek(1) == '=') { emit(Token.of(TokenType.LessEq));    pos += 2; }
                    else                { emit(Token.of(TokenType.Less));       pos++;    }
                }
                // AC7 — && or &
                case '&' -> {
                    if (peek(1) == '&') { pos++; }
                    emit(Token.of(TokenType.And));
                    pos++;
                }
                // AC7 — || or |
                case '|' -> {
                    if (peek(1) == '|') { pos++; }
                    emit(Token.of(TokenType.Or));
                    pos++;
                }
                // AC17 — skip unknown characters silently
                default -> pos++;
            }
        }

        // AC18 — EOF
        emit(Token.of(TokenType.EOF));
        return List.copyOf(tokens);
    }

    // -------------------------------------------------------------------------
    // Scanners
    // -------------------------------------------------------------------------

    private void scanDoubleQuotedString() {
        int start = pos; // includes opening '"'
        pos++; // skip opening quote
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == '\\') {
                pos += 2; // skip escape sequence (including char after \)
            } else if (c == '"') {
                pos++; // skip closing quote
                break;
            } else {
                pos++;
            }
        }
        // If unterminated, append closing quote before unescaping (AC9)
        String raw = src.substring(start, pos);
        if (!raw.endsWith("\"") || raw.length() == 1) {
            raw = raw + "\"";
        }
        emit(Token.ofString(TokenType.Str, JsonStringUtil.unescape(raw)));
    }

    private void scanSingleQuotedString() {
        pos++; // skip opening quote
        StringBuilder sb = new StringBuilder();
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == '\\' && pos + 1 < src.length()) {
                char next = src.charAt(pos + 1);
                switch (next) {
                    case '\'' -> { sb.append('\''); pos += 2; }
                    case '\\' -> { sb.append('\\'); pos += 2; }
                    case 'n'  -> { sb.append('\n'); pos += 2; }
                    case 't'  -> { sb.append('\t'); pos += 2; }
                    default   -> { sb.append('\\'); sb.append(next); pos += 2; }
                }
            } else if (c == '\'') {
                pos++; // skip closing quote
                break;
            } else {
                sb.append(c);
                pos++;
            }
        }
        emit(Token.ofString(TokenType.Str, sb.toString()));
    }

    private void scanNumber(boolean negative) {
        int start = pos;
        if (negative) pos++; // consume '-'
        while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
        if (pos < src.length() && src.charAt(pos) == '.') {
            pos++;
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
        }
        if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
            pos++;
            if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) pos++;
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
        }
        String raw = src.substring(start, pos);
        try {
            emit(Token.ofNum(Double.parseDouble(raw)));
        } catch (NumberFormatException e) {
            // Should not happen given our scanning logic, but degrade gracefully
            emit(Token.ofNum(0));
        }
    }

    private void scanStateVar() {
        pos++; // skip '$'
        int start = pos;
        while (pos < src.length() && (Character.isLetterOrDigit(src.charAt(pos)) || src.charAt(pos) == '_')) {
            pos++;
        }
        emit(Token.ofString(TokenType.StateVar, "$" + src.substring(start, pos)));
    }

    private void scanBuiltinCall() {
        pos++; // skip '@'
        int start = pos;
        while (pos < src.length() && (Character.isLetterOrDigit(src.charAt(pos)) || src.charAt(pos) == '_')) {
            pos++;
        }
        emit(Token.ofString(TokenType.BuiltinCall, "@" + src.substring(start, pos)));
    }

    private void scanIdentOrKeyword() {
        int start = pos;
        while (pos < src.length() && (Character.isLetterOrDigit(src.charAt(pos)) || src.charAt(pos) == '_')) {
            pos++;
        }
        String word = src.substring(start, pos);
        Token token = switch (word) {
            case "true"  -> Token.of(TokenType.True);
            case "false" -> Token.of(TokenType.False);
            case "null"  -> Token.of(TokenType.Null);
            default      -> Character.isUpperCase(word.charAt(0))
                    ? Token.ofString(TokenType.Type, word)
                    : Token.ofString(TokenType.Ident, word);
        };
        emit(token);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private char peek(int offset) {
        int idx = pos + offset;
        return idx < src.length() ? src.charAt(idx) : '\0';
    }

    private void emit(Token t) {
        tokens.add(t);
    }
}
