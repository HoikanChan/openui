package dev.openui.langcore.prompt;

import java.util.List;

/**
 * A named group of components shown together in the prompt.
 *
 * <p>Groups help the LLM understand which components belong to the same feature
 * area (e.g. "Form components", "Layout"). {@code notes} carries additional
 * free-text guidance shown under the group heading.
 *
 * Ref: Design §11
 */
public record ComponentGroup(String name, List<String> components, List<String> notes) {}
