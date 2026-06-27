package com.huawei.cloudsop.genui.core.prompt.characterize;

/**
 * Describes one field within an {@link ObjectShape}.
 *
 * @param node the field's inferred shape
 * @param optional true if at least one observed row was missing this key
 * @param nullable true if at least one observed row had this key present but set to {@code null}
 */
public record FieldShape(ShapeNode node, boolean optional, boolean nullable) {}
