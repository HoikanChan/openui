package dev.openui.langcore.parser;

import dev.openui.langcore.parser.ast.Node;

public record QueryStatement(String id, Node toolAST, Node argsAST,
                             Node defaultsAST, Node refreshAST) implements Statement {}
