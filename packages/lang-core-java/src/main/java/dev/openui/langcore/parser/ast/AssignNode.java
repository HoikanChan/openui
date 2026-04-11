package dev.openui.langcore.parser.ast;

public record AssignNode(String target, Node expr) implements Node {}
