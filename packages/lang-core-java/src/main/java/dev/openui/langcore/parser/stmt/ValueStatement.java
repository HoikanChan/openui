package dev.openui.langcore.parser.stmt;

import dev.openui.langcore.parser.ast.Node;

public record ValueStatement(String id, Node expr) implements Statement {}
