package dev.openui.langcore.library;

import java.util.List;
import java.util.Map;

/**
 * Input to {@link Libraries#createLibrary} — the full set of component definitions,
 * optional groupings, and the name of the root component.
 *
 * Ref: Design §13
 */
public record LibraryDefinition(
        Map<String, ComponentDef> components,
        List<ComponentGroup> componentGroups,
        String root
) {}
