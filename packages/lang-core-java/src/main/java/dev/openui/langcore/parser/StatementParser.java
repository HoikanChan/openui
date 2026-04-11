package dev.openui.langcore.parser;

import dev.openui.langcore.lexer.Token;
import dev.openui.langcore.lexer.TokenType;
import dev.openui.langcore.parser.ast.CallNode;
import dev.openui.langcore.parser.ast.LiteralNode;
import dev.openui.langcore.parser.ast.Node;
import dev.openui.langcore.parser.stmt.MutationStatement;
import dev.openui.langcore.parser.stmt.NullStatement;
import dev.openui.langcore.parser.stmt.QueryStatement;
import dev.openui.langcore.parser.stmt.StateStatement;
import dev.openui.langcore.parser.stmt.Statement;
import dev.openui.langcore.parser.stmt.ValueStatement;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * Parses a flat token list into an ordered map of statements.
 * Each statement has the form {@code <lhs> = <expression>}.
 * Ref: Req 2 AC1-10, Design §5
 */
public final class StatementParser {

    /**
     * Parse a token list into an ordered map of statements.
     * Returns a LinkedHashMap for insertion-order preservation.
     * Last-write-wins for duplicate ids (AC10).
     * Ref: Req 2 AC1-10
     */
    public LinkedHashMap<String, Statement> parse(List<Token> tokens) {
        LinkedHashMap<String, Statement> map = new LinkedHashMap<>();
        int pos = 0;
        int size = tokens.size();

        while (pos < size) {
            // Skip leading Newline tokens at top level
            while (pos < size && tokens.get(pos).type() == TokenType.Newline) {
                pos++;
            }
            if (pos >= size || tokens.get(pos).type() == TokenType.EOF) {
                break;
            }

            Token lhsTok = tokens.get(pos);
            TokenType lhsType = lhsTok.type();

            // AC1-3: LHS must be Ident, Type, or StateVar
            if (lhsType != TokenType.Ident
                    && lhsType != TokenType.Type
                    && lhsType != TokenType.StateVar) {
                pos = skipToNextNewlineAtDepth0(tokens, pos);
                continue;
            }

            pos++; // consume LHS token

            // Must be immediately followed by Equals (not EqEq) — AC9.
            // Do NOT skip newlines here: a trailing newline means the LHS has
            // no '=' on this logical line and should be skipped.
            if (pos >= size || tokens.get(pos).type() != TokenType.Equals) {
                pos = skipToNextNewlineAtDepth0(tokens, pos);
                continue;
            }

            pos++; // consume '='

            // Parse expression starting at current pos
            ExpressionParser ep = new ExpressionParser(tokens, pos);
            Node expr;
            try {
                expr = ep.parseExpression(0);
            } catch (IllegalStateException e) {
                // Expression parse failed — skip this line (AC9)
                pos = skipToNextNewlineAtDepth0(tokens, pos);
                continue;
            }
            pos = ep.getPos();

            // Consume any remaining tokens on this logical line until Newline/EOF
            // (ExpressionParser stops before the Newline at depth 0)
            while (pos < size
                    && tokens.get(pos).type() != TokenType.Newline
                    && tokens.get(pos).type() != TokenType.EOF) {
                pos++;
            }

            // Build the statement id
            String id = lhsTok.stringValue();

            // Classify and add statement (AC2-5, last-write-wins AC10)
            Statement stmt = classify(id, lhsType, expr);
            map.put(id, stmt);

            // Consume the trailing Newline(s)
            while (pos < size && tokens.get(pos).type() == TokenType.Newline) {
                pos++;
            }
        }

        return map;
    }

    /**
     * Classify the parsed statement based on LHS type and expression shape.
     * Ref: Req 2 AC2-5
     */
    private Statement classify(String id, TokenType lhsType, Node expr) {
        // AC4: Query(...) call → QueryStatement regardless of LHS type
        if (expr instanceof CallNode call && "Query".equals(call.callee())) {
            List<Node> args = call.args();
            Node toolAST     = argOrNull(args, 0);
            Node argsAST     = argOrNull(args, 1);
            Node defaultsAST = argOrNull(args, 2);
            Node refreshAST  = argOrNull(args, 3);
            return new QueryStatement(id, toolAST, argsAST, defaultsAST, refreshAST);
        }

        // AC5: Mutation(...) call → MutationStatement regardless of LHS type
        if (expr instanceof CallNode call && "Mutation".equals(call.callee())) {
            List<Node> args = call.args();
            Node toolAST = argOrNull(args, 0);
            Node argsAST = argOrNull(args, 1);
            return new MutationStatement(id, toolAST, argsAST);
        }

        // AC2: StateVar LHS → StateStatement
        if (lhsType == TokenType.StateVar) {
            return new StateStatement(id, expr);
        }

        // AC3: Ident or Type LHS → ValueStatement
        return new ValueStatement(id, expr);
    }

    /**
     * Return the argument at index {@code i}, or a null-literal Node if out of bounds.
     */
    private Node argOrNull(List<Node> args, int i) {
        return i < args.size() ? args.get(i) : new LiteralNode(null);
    }

    /**
     * Advance pos past tokens until a Newline token at bracket depth 0 is found,
     * then return the position AFTER that Newline (so the caller can continue).
     * Tracks bracket depth for (, ), [, ], {, } (AC7, AC9).
     */
    private int skipToNextNewlineAtDepth0(List<Token> tokens, int pos) {
        int depth = 0;
        int size = tokens.size();
        while (pos < size) {
            TokenType type = tokens.get(pos).type();
            if (type == TokenType.EOF) {
                break;
            }
            if (type == TokenType.LParen || type == TokenType.LBrack || type == TokenType.LBrace) {
                depth++;
            } else if (type == TokenType.RParen || type == TokenType.RBrack || type == TokenType.RBrace) {
                if (depth > 0) depth--;
            } else if (type == TokenType.Newline && depth == 0) {
                pos++; // consume the Newline
                break;
            }
            pos++;
        }
        return pos;
    }
}
