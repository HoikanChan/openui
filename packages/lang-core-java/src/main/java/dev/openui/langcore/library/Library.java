package dev.openui.langcore.library;

import java.util.List;
import java.util.Map;

/**
 * A compiled component library that provides prompt generation and JSON Schema export.
 *
 * Ref: Design §13
 */
public interface Library {

    /** All component definitions keyed by name. */
    Map<String, ComponentDef> components();

    /** Optional groupings for prompt organisation. */
    List<ComponentGroup> componentGroups();

    /** Name of the root component. */
    String root();

    /**
     * Generate an LLM system prompt for this library.
     *
     * @param options customisation options (preamble, examples, flags, …)
     * @return the generated prompt string
     */
    String prompt(PromptOptions options);

    /**
     * Export this library as a JSON Schema {@code Map} (suitable for
     * {@link dev.openui.langcore.parser.SchemaRegistry#fromJson}).
     *
     * @return a {@code Map} representing the root JSON Schema object with
     *         a {@code $defs} block containing one entry per component
     */
    Map<String, Object> toJSONSchema();
}
