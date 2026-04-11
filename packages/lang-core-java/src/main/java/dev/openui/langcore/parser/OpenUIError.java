package dev.openui.langcore.parser;

public record OpenUIError(
    String source,
    String code,
    String message,
    String statementId,
    String component,
    String toolName,
    String hint
) {}
