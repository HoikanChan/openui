package dev.openui.langcore.parser.ast;

public record UnaryNode(String op, Node operand) implements Node {}
