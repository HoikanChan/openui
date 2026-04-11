package dev.openui.langcore.parser;

import dev.openui.langcore.parser.ast.ElementNode;
import java.util.List;
import java.util.Map;

public record ParseResult(
    ElementNode root,
    ParseMeta meta,
    Map<String, Object> stateDeclarations,
    List<QueryStatementInfo> queryStatements,
    List<MutationStatementInfo> mutationStatements
) {}
