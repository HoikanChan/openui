package dev.openui.langcore.lexer;

/**
 * A single token emitted by the lexer.
 * {@code value} is a {@link String} for Str/Ident/Type/StateVar/BuiltinCall tokens,
 * a {@link Double} for Num tokens, and {@code null} for all other token types.
 * Ref: Req 1, Design §1
 */
public record Token(TokenType type, Object value) {

    /**
     * Returns the token value as a String, or an empty string if not a string token.
     */
    public String stringValue() {
        return value instanceof String s ? s : "";
    }

    /**
     * Returns the token value as a double, or 0.0 if not a numeric token.
     */
    public double numValue() {
        return value instanceof Number n ? n.doubleValue() : 0.0;
    }

    /** Convenience factory for tokens with no payload (punctuation, keywords, etc.). */
    public static Token of(TokenType type) {
        return new Token(type, null);
    }

    /** Convenience factory for string-valued tokens. */
    public static Token ofString(TokenType type, String value) {
        return new Token(type, value);
    }

    /** Convenience factory for numeric tokens. */
    public static Token ofNum(double value) {
        return new Token(TokenType.Num, value);
    }

    @Override
    public String toString() {
        return value != null ? type + "(" + value + ")" : type.name();
    }
}
