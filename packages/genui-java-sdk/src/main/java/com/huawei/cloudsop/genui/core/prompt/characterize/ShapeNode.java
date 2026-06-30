package com.huawei.cloudsop.genui.core.prompt.characterize;

/**
 * Inferred schema tree produced alongside the sample tree by {@link ShapeWalker}.
 *
 * <p>Every {@link ShapeNode} mirrors the shape of the host-data value it describes: objects keep
 * their field order, arrays describe their (possibly downsampled) element shape plus the true
 * element count, and scalars/enums describe leaf types.
 */
public sealed interface ShapeNode permits ObjectShape, ArrayShape, ScalarShape, EnumShape {}
