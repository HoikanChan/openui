package com.huawei.cloudsop.genui.core;

/**
 * Runtime-free prompt documentation for a single openui-lang builtin.
 *
 * <p>Loaded from the top-level {@code builtins} array of {@code base-contract.json}, which the
 * frontend exports via {@code getBuiltinsManifest()} in lang-core. The Java {@code PromptAssembler}
 * filters on {@link #templateBuiltin()} to reproduce the {@code ## Template Built-ins} and
 * {@code ## Data Built-ins} prompt sections byte-for-byte, without re-deriving them.
 */
public record BuiltinSpec(String signature, String description, boolean templateBuiltin) {}
