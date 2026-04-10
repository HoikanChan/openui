package dev.openui.langcore.parser;

/**
 * Result of {@link PreProcessor#autoClose(String)}.
 * {@code text} is the source with any missing closing brackets/quotes appended.
 * {@code wasIncomplete} is true when at least one closer was appended.
 * Ref: Req 8 AC1-4
 */
public record AutoCloseResult(String text, boolean wasIncomplete) {}
