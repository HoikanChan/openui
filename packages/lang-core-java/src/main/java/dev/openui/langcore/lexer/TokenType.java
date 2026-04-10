package dev.openui.langcore.lexer;

/**
 * All token types produced by the OpenUI Lang lexer.
 * Ref: Req 1, Design §1
 */
public enum TokenType {
    // Literals
    Num,
    Str,
    True,
    False,
    Null,

    // Identifiers
    Ident,       // lowercase identifier
    Type,        // PascalCase identifier
    StateVar,    // $identifier
    BuiltinCall, // @identifier

    // Punctuation
    LParen,
    RParen,
    LBrack,
    RBrack,
    LBrace,
    RBrace,
    Comma,
    Colon,
    Dot,

    // Operators — comparison / equality
    Equals,      // =
    EqEq,        // ==
    Not,         // !
    NotEq,       // !=
    Greater,     // >
    GreaterEq,   // >=
    Less,        // <
    LessEq,      // <=

    // Operators — logical
    And,         // && or &
    Or,          // || or |

    // Operators — arithmetic / misc
    Plus,        // +
    Minus,       // -
    Star,        // *
    Slash,       // /
    Percent,     // %
    Question,    // ?

    // Structure
    Newline,
    EOF
}
