package dev.openui.langcore.parser.stmt;

import dev.openui.langcore.parser.ast.Node;

public record MutationStatement(String id, Node toolAST, Node argsAST) implements Statement {}
