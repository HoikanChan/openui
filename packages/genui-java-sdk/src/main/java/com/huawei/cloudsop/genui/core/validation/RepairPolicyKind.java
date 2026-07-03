package com.huawei.cloudsop.genui.core.validation;

/**
 * Coarse-grained repair strategy. Section 7 will wrap this in a full {@code RepairPolicy} value
 * object with attempt limits and timeout params; keep advanced params out of this type.
 */
public enum RepairPolicyKind {
  /** No repair: surface validation failures directly to the caller. */
  NONE,
  /** Attempt rule-based repair after final validation failure. */
  FINAL_REPAIR,
  /** Abort the current stream and immediately re-ask the LLM with the error context. */
  FAIL_FAST_REASK
}
