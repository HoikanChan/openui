package dev.openui.langcore.parser.ast;

import java.util.List;

public record BuiltinCallNode(String name, List<Node> args) implements Node {}
