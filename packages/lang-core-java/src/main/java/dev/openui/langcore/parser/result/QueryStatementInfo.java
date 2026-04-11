package dev.openui.langcore.parser.result;

import dev.openui.langcore.parser.ast.Node;
import java.util.List;

public record QueryStatementInfo(
    String statementId,
    Node toolAST,
    Node argsAST,
    Node defaultsAST,
    Node refreshAST,
    List<String> deps,
    boolean complete
) {}
