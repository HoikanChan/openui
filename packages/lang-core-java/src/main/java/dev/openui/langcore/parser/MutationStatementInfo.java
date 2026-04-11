package dev.openui.langcore.parser;

import dev.openui.langcore.parser.ast.Node;

public record MutationStatementInfo(
    String statementId,
    Node toolAST,
    Node argsAST
) {}
