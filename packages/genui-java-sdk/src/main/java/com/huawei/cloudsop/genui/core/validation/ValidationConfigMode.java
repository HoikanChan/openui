package com.huawei.cloudsop.genui.core.validation;

/**
 * Controls when the validator is invoked in the generation pipeline.
 * Distinct from {@link ValidationMode} which is the per-call rule set selector.
 */
public enum ValidationConfigMode {
  /** Run validation only after LLM generation is complete. */
  FINAL_ONLY,
  /** Run validation on each streaming checkpoint as well as the final result. */
  STREAMING_GATE,
  /** Disable validation entirely. */
  DISABLED
}
