package dev.openui.langcore.library;

import java.util.List;

/**
 * A named grouping of components for prompt organisation.
 *
 * Ref: Design §13
 */
public record ComponentGroup(String name, List<String> components, List<String> notes) {}
