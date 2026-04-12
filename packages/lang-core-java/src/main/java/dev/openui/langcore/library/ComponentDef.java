package dev.openui.langcore.library;

import java.util.List;

/**
 * Definition of a UI component — name, ordered prop list, optional description,
 * and an opaque renderer reference for framework adapters.
 *
 * Ref: Design §13
 */
public record ComponentDef(
        String name,
        List<PropDef> props,    // ordered — defines positional argument mapping
        String description,
        Object component        // opaque — stored for framework adapters
) {}
