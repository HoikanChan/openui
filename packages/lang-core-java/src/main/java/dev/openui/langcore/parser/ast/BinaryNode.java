package dev.openui.langcore.parser.ast;

public record BinaryNode(String op, Node left, Node right) implements Node {}
