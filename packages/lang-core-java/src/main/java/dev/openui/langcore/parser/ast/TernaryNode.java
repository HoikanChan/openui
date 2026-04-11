package dev.openui.langcore.parser.ast;

public record TernaryNode(Node condition, Node consequent, Node alternate) implements Node {}
