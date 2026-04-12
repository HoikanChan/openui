package dev.openui.langcore.util;

/**
 * A single parsed validation rule, e.g. {@code ParsedRule("min", 8)} or
 * {@code ParsedRule("required", null)}.
 *
 * Ref: Design §19
 */
public record ParsedRule(String name, Object param) {}
