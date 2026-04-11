package dev.openui.langcore.parser.ast;

import java.util.List;

public record ArrayNode(List<Node> elements) implements Node {}
