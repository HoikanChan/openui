package dev.openui.langcore.prompt;

import java.util.List;
import java.util.Map;

/**
 * Full specification for generating an LLM system prompt via
 * {@link PromptGenerator#generatePrompt(PromptSpec)}.
 *
 * <p>Field semantics:
 * <ul>
 *   <li>{@code root} — the root component name shown in the grammar overview.</li>
 *   <li>{@code components} — map of component name → {@link ComponentPromptSpec}.</li>
 *   <li>{@code componentGroups} — optional groupings for the components section.</li>
 *   <li>{@code tools} — list of {@link String} tool names or {@link ToolSpec} descriptors.</li>
 *   <li>{@code editMode} — include the incremental-edit grammar section.</li>
 *   <li>{@code inlineMode} — allow inline component expressions (no root statement required).</li>
 *   <li>{@code toolCalls} — include the tool-call grammar section.</li>
 *   <li>{@code bindings} — include the {@code $binding<>} reactive prop annotation section.</li>
 *   <li>{@code preamble} — prepended verbatim before all generated sections.</li>
 *   <li>{@code examples} — component usage examples appended after the components section.</li>
 *   <li>{@code toolExamples} — tool-call usage examples appended after the tools section.</li>
 *   <li>{@code additionalRules} — extra rule strings appended at the end.</li>
 * </ul>
 *
 * Ref: Design §11
 */
public record PromptSpec(
        String root,
        Map<String, ComponentPromptSpec> components,
        List<ComponentGroup> componentGroups,
        List<Object> tools,
        boolean editMode,
        boolean inlineMode,
        boolean toolCalls,
        boolean bindings,
        String preamble,
        List<String> examples,
        List<String> toolExamples,
        List<String> additionalRules
) {}
