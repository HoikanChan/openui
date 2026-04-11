package dev.openui.langcore.parser.result;

import java.util.List;

public record ParseMeta(
    boolean incomplete,
    List<String> unresolved,
    List<String> orphaned,
    int statementCount,
    List<ValidationError> errors
) {}
