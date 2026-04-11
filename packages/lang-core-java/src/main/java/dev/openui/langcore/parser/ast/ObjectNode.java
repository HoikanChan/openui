package dev.openui.langcore.parser.ast;

import java.util.Map;

public record ObjectNode(Map<String, Node> entries) implements Node {}
