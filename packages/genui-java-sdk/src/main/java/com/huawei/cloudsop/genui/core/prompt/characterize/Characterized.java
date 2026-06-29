package com.huawei.cloudsop.genui.core.prompt.characterize;

/**
 * The result of characterizing a host-data value: a same-shape, possibly-downsampled {@code
 * sample} tree suitable for prompt embedding, paired with the inferred {@code shape} schema tree.
 *
 * <p>{@code shape} may be {@code null} when characterization did not run (pass-through case).
 */
public record Characterized(Object sample, ShapeNode shape) {}
