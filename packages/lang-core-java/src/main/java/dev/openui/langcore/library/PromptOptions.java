package dev.openui.langcore.library;

import java.util.List;

/**
 * Options passed to {@link Library#prompt(PromptOptions)} to customise the
 * generated LLM system prompt.
 *
 * Ref: Design §13
 */
public record PromptOptions(
        String preamble,
        List<String> additionalRules,
        List<String> examples,
        List<String> toolExamples,
        List<Object> tools,       // String | ToolSpec
        boolean editMode,
        boolean inlineMode,
        boolean toolCalls,
        boolean bindings
) {
    /** Convenience factory — all fields null/false. */
    public static PromptOptions empty() {
        return new PromptOptions(null, List.of(), List.of(), List.of(), List.of(),
                false, false, false, false);
    }
}
