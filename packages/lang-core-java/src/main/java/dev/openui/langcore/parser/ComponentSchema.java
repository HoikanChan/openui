package dev.openui.langcore.parser;

import java.util.List;

/**
 * Holds an ordered list of property definitions for a single component type.
 * The list order matches the key order in the JSON Schema {@code properties} object,
 * which defines positional argument order.
 *
 * Ref: Design §7
 */
public record ComponentSchema(List<PropDef> props) {}
