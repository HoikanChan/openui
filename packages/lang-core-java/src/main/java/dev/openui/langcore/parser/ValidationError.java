package dev.openui.langcore.parser;

public record ValidationError(
    String code,
    String component,
    String statementId,
    String message
) {}
