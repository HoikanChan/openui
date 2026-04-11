package dev.openui.langcore.parser;

import dev.openui.langcore.parser.ast.Node;

public record ValueStatement(String id, Node expr) implements Statement {}
