package dev.openui.langcore.prompt;

/**
 * Prompt-generation descriptor for a single component.
 *
 * <p>{@code signature} is the component's call signature shown in the grammar overview
 * (e.g. {@code "Button(label: string, onClick?: Action)"}). {@code description} is the
 * natural-language description shown in the components section.
 *
 * Ref: Design §11
 */
public record ComponentPromptSpec(String signature, String description) {}
