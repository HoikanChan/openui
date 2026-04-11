package dev.openui.langcore.parser;

import dev.openui.langcore.lexer.Token;
import dev.openui.langcore.lexer.TokenType;
import dev.openui.langcore.parser.ast.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pratt (top-down operator precedence) expression parser.
 * Ref: Req 3, Design §4
 */
public final class ExpressionParser {

    private final List<Token> tokens;
    private int pos;

    public ExpressionParser(List<Token> tokens, int startPos) {
        this.tokens = tokens;
        this.pos = startPos;
    }

    /** Current position after parsing (for StatementParser to read back). */
    public int getPos() {
        return pos;
    }

    // -------------------------------------------------------------------------
    // Binding powers (Design §4)
    // -------------------------------------------------------------------------

    /** Returns the left binding power of the current token for infix/postfix use. */
    private int getLeftBp(TokenType type) {
        return switch (type) {
            case Question   -> 10;
            case Or         -> 20;
            case And        -> 30;
            case EqEq,
                 NotEq      -> 40;
            case Greater,
                 GreaterEq,
                 Less,
                 LessEq     -> 50;
            case Plus,
                 Minus      -> 60;
            case Star,
                 Slash,
                 Percent    -> 70;
            case Dot,
                 LBrack     -> 90;
            default         -> 0;
        };
    }

    // -------------------------------------------------------------------------
    // Public entry point
    // -------------------------------------------------------------------------

    /**
     * Parse an expression with minimum binding power.
     * Call with minBp=0 for a top-level expression.
     * Ref: Req 3 AC1
     */
    public Node parseExpression(int minBp) {
        Node left = parseAtom();

        while (true) {
            // Speculatively skip newlines: only if the next real token has
            // sufficient binding power (e.g. multiline ternary '?'). Otherwise
            // leave the newlines in place so StatementParser can use them as
            // statement boundaries (AC6, AC8).
            int savedPos = pos;
            skipNewlines();

            Token tok = peek();
            if (tok.type() == TokenType.EOF) {
                pos = savedPos; // restore — newlines are not part of this expr
                break;
            }

            int lbp = getLeftBp(tok.type());
            if (lbp <= minBp) {
                pos = savedPos; // restore — newlines are statement boundaries
                break;
            }

            left = parseInfix(left, tok);
        }

        return left;
    }

    // -------------------------------------------------------------------------
    // Atom (nud / prefix) parsing
    // -------------------------------------------------------------------------

    private Node parseAtom() {
        // Skip leading newlines
        skipNewlines();

        Token tok = consume();

        return switch (tok.type()) {
            // Literals
            case Num   -> new LiteralNode(tok.numValue());
            case Str   -> new LiteralNode(tok.stringValue());
            case True  -> new LiteralNode(true);
            case False -> new LiteralNode(false);
            case Null  -> new LiteralNode(null);

            // lowercase identifier — could be a call
            case Ident -> {
                String name = tok.stringValue();
                skipNewlines();
                if (peek().type() == TokenType.LParen) {
                    consume(); // consume '('
                    List<Node> args = parseArgList(TokenType.RParen);
                    yield new CallNode(name, args);
                }
                yield new RefNode(name);
            }

            // PascalCase type/component — could be a call
            case Type -> {
                String name = tok.stringValue();
                skipNewlines();
                if (peek().type() == TokenType.LParen) {
                    consume(); // consume '('
                    List<Node> args = parseArgList(TokenType.RParen);
                    yield new CallNode(name, args);
                }
                yield new RefNode(name);
            }

            // $stateVar — assignment or state reference (Req 3 AC8)
            case StateVar -> {
                String name = tok.stringValue();
                skipNewlines();
                if (peek().type() == TokenType.Equals) {
                    consume(); // consume '='
                    Node rhs = parseExpression(0);
                    yield new AssignNode(name, rhs);
                }
                yield new StateRefNode(name);
            }

            // @BuiltinCall (Req 3 AC11)
            case BuiltinCall -> {
                String rawName = tok.stringValue(); // e.g. "@Each"
                // strip '@' prefix for the node name
                String name = rawName.startsWith("@") ? rawName.substring(1) : rawName;
                skipNewlines();
                expect(TokenType.LParen);
                List<Node> args = parseArgList(TokenType.RParen);
                yield new BuiltinCallNode(name, args);
            }

            // Unary prefix operators (Req 3 AC1, level 8)
            case Not   -> new UnaryNode("!", parseExpression(80));
            case Minus -> new UnaryNode("-", parseExpression(80));

            // Grouping parentheses
            case LParen -> {
                Node inner = parseExpression(0);
                skipNewlines();
                expect(TokenType.RParen);
                yield inner;
            }

            // Array literal
            case LBrack -> {
                List<Node> elements = new ArrayList<>();
                skipNewlines();
                while (peek().type() != TokenType.RBrack && peek().type() != TokenType.EOF) {
                    elements.add(parseExpression(0));
                    skipNewlines();
                    if (peek().type() == TokenType.Comma) {
                        consume();
                        skipNewlines();
                    } else {
                        break;
                    }
                }
                expect(TokenType.RBrack);
                yield new ArrayNode(List.copyOf(elements));
            }

            // Object literal
            case LBrace -> {
                Map<String, Node> entries = new LinkedHashMap<>();
                skipNewlines();
                while (peek().type() != TokenType.RBrace && peek().type() != TokenType.EOF) {
                    // Key can be Ident, Type, or Str token
                    String key = parseObjectKey();
                    skipNewlines();
                    expect(TokenType.Colon);
                    skipNewlines();
                    Node value = parseExpression(0);
                    entries.put(key, value);
                    skipNewlines();
                    if (peek().type() == TokenType.Comma) {
                        consume();
                        skipNewlines();
                    } else {
                        break;
                    }
                }
                expect(TokenType.RBrace);
                yield new ObjectNode(Map.copyOf(entries));
            }

            default -> throw new IllegalStateException(
                    "Unexpected token in expression: " + tok);
        };
    }

    // -------------------------------------------------------------------------
    // Infix / postfix (led) parsing
    // -------------------------------------------------------------------------

    private Node parseInfix(Node left, Token opTok) {
        // consume the operator token
        consume();

        return switch (opTok.type()) {
            // Binary operators — right-hand side parsed at lbp (left-associative)
            case Or         -> new BinaryNode("||", left, parseExpression(20));
            case And        -> new BinaryNode("&&", left, parseExpression(30));
            case EqEq       -> new BinaryNode("==", left, parseExpression(40));
            case NotEq      -> new BinaryNode("!=", left, parseExpression(40));
            case Greater    -> new BinaryNode(">",  left, parseExpression(50));
            case GreaterEq  -> new BinaryNode(">=", left, parseExpression(50));
            case Less       -> new BinaryNode("<",  left, parseExpression(50));
            case LessEq     -> new BinaryNode("<=", left, parseExpression(50));
            case Plus       -> new BinaryNode("+",  left, parseExpression(60));
            case Minus      -> new BinaryNode("-",  left, parseExpression(60));
            case Star       -> new BinaryNode("*",  left, parseExpression(70));
            case Slash      -> new BinaryNode("/",  left, parseExpression(70));
            case Percent    -> new BinaryNode("%",  left, parseExpression(70));

            // Member access: obj.field (Req 3 AC9)
            case Dot -> {
                skipNewlines();
                Token field = consume();
                if (field.type() != TokenType.Ident && field.type() != TokenType.Type) {
                    throw new IllegalStateException(
                            "Expected identifier after '.', got: " + field);
                }
                yield new MemberNode(left, new LiteralNode(field.stringValue()), false);
            }

            // Computed member access: obj[expr] (Req 3 AC10)
            case LBrack -> {
                skipNewlines();
                Node index = parseExpression(0);
                skipNewlines();
                expect(TokenType.RBrack);
                yield new MemberNode(left, index, true);
            }

            // Ternary operator: cond ? consequent : alternate (Req 3 AC1, level 1)
            case Question -> {
                skipNewlines();
                // Colon has leftBp=0, so parseExpression(0) will stop at it
                Node consequent = parseExpression(0);
                skipNewlines();
                expect(TokenType.Colon);
                skipNewlines();
                Node alternate = parseExpression(0);
                yield new TernaryNode(left, consequent, alternate);
            }

            default -> throw new IllegalStateException(
                    "Unexpected infix token: " + opTok);
        };
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Parse a comma-separated list of expressions until the given closing token. */
    private List<Node> parseArgList(TokenType closing) {
        List<Node> args = new ArrayList<>();
        skipNewlines();
        if (peek().type() == closing) {
            consume(); // consume closing token
            return List.copyOf(args);
        }
        args.add(parseExpression(0));
        skipNewlines();
        while (peek().type() == TokenType.Comma) {
            consume(); // consume ','
            skipNewlines();
            if (peek().type() == closing) break; // trailing comma
            args.add(parseExpression(0));
            skipNewlines();
        }
        expect(closing);
        return List.copyOf(args);
    }

    /** Parse an object key (Ident, Type, or Str) and return its string form. */
    private String parseObjectKey() {
        Token keyTok = consume();
        return switch (keyTok.type()) {
            case Ident, Type -> keyTok.stringValue();
            case Str         -> keyTok.stringValue();
            default -> throw new IllegalStateException(
                    "Expected object key (ident or string), got: " + keyTok);
        };
    }

    /** Peek at the current token without consuming it. */
    private Token peek() {
        if (pos >= tokens.size()) return Token.of(TokenType.EOF);
        return tokens.get(pos);
    }

    /** Consume and return the current token. */
    private Token consume() {
        Token tok = peek();
        pos++;
        return tok;
    }

    /** Consume the current token, asserting it has the expected type. */
    private void expect(TokenType expected) {
        Token tok = consume();
        if (tok.type() != expected) {
            throw new IllegalStateException(
                    "Expected " + expected + " but got " + tok);
        }
    }

    /** Skip any Newline tokens at the current position. */
    private void skipNewlines() {
        while (pos < tokens.size() && tokens.get(pos).type() == TokenType.Newline) {
            pos++;
        }
    }
}
