package dev.openui.langcore.parser;

import dev.openui.langcore.parser.ast.Node;

public record StateStatement(String id, Node defaultExpr) implements Statement {}
