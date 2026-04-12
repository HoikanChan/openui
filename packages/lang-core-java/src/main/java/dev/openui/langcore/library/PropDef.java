package dev.openui.langcore.library;

/**
 * Definition of a single prop on a component.
 *
 * <p>Props are ordered — their index in {@link ComponentDef#props()} defines the
 * positional argument mapping used by the parser and prompt generator.
 *
 * Ref: Design §13
 */
public record PropDef(
        String name,
        boolean required,
        Object defaultValue,       // nullable
        String typeAnnotation,     // e.g. "string", "number[]", "Card[]", "$binding<string>"
        boolean isArray,
        boolean isReactive
) {}
