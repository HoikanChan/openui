package dev.openui.langcore.parser;

import dev.openui.langcore.lexer.Lexer;
import dev.openui.langcore.lexer.Token;
import dev.openui.langcore.parser.result.ParseResult;
import dev.openui.langcore.parser.stmt.Statement;

import java.util.List;
import java.util.Map;

/**
 * One-shot parser that wires all pipeline stages:
 * PreProcessor → Lexer → StatementParser → Materializer.
 * Ref: Design §8
 */
public final class OneShot {

    private final SchemaRegistry schema;

    public OneShot(SchemaRegistry schema) {
        this.schema = schema;
    }

    public ParseResult parse(String input) {
        String stripped = PreProcessor.stripComments(PreProcessor.stripFences(input));
        AutoCloseResult closed = PreProcessor.autoClose(stripped);
        List<Token> tokens = new Lexer(closed.text()).tokenize();
        Map<String, Statement> stmts = new StatementParser().parse(tokens);
        return new Materializer(schema, closed.wasIncomplete()).materialize(stmts);
    }
}
