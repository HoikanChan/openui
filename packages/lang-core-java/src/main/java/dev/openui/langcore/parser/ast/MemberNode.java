package dev.openui.langcore.parser.ast;

public record MemberNode(Node object, Node property, boolean computed) implements Node {}
