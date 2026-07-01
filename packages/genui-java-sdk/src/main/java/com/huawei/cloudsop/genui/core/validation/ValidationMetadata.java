package com.huawei.cloudsop.genui.core.validation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lightweight telemetry/generation metadata attached to a {@link ValidationResult}.
 * Keep minimal — advanced fields belong in Section 7+ types.
 */
public record ValidationMetadata(
    int statementCount,
    /** Root component name found in the DSL, or {@code null}. */
    String rootName,
    /** Validation mode used for this result. */
    ValidationMode mode,
    /** Arbitrary extension data; never {@code null}, never mutable. */
    Map<String, String> extra) {

  public ValidationMetadata {
    extra =
        extra == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(extra));
  }
}
