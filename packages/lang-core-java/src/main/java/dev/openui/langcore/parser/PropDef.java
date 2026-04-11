package dev.openui.langcore.parser;

/**
 * Describes a single property from a component's JSON Schema definition.
 * The {@code defaultValue} may be a {@link String}, {@link Double}, {@link Boolean},
 * or {@code null} (absent — not the same as a JSON null default).
 *
 * Ref: Design §7
 */
public record PropDef(String name, boolean required, Object defaultValue) {}
