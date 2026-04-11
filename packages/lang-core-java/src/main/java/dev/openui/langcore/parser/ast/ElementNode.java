package dev.openui.langcore.parser.ast;

import java.util.Map;

public record ElementNode(
    String typeName,
    Map<String, Node> props,
    boolean partial,
    boolean hasDynamicProps,
    String statementId
) implements Node {}
