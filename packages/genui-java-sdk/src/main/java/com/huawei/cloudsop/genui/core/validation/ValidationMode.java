package com.huawei.cloudsop.genui.core.validation;

/**
 * Tells the validator which rule set to apply for a single validation call.
 * Distinct from {@link ValidationConfigMode} which governs when validation is triggered.
 */
public enum ValidationMode {
  /** Validate the complete, final DSL string. */
  FINAL,
  /** Validate an in-progress streaming DSL fragment (relaxed rules). */
  STREAMING
}
