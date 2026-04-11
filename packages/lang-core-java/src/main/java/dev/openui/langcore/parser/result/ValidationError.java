package dev.openui.langcore.parser.result;

public record ValidationError(
    String code,
    String component,
    String statementId,
    String message
) {}
