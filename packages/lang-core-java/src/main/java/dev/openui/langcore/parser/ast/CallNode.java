package dev.openui.langcore.parser.ast;

import java.util.List;

public record CallNode(String callee, List<Node> args) implements Node {}
