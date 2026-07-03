package com.huawei.cloudsop.genui.core.validation;

/**
 * Overall validity status of a DSL string.
 *
 * <ul>
 *   <li>{@link #VALID} – no blocking issues found.
 *   <li>{@link #PARTIAL} – streaming mode only: pending statement, temporary unresolved refs, or
 *       auto-closeable input.
 *   <li>{@link #INVALID} – has syntax/contract/root/final-unresolved blocking issues.
 * </ul>
 */
public enum ValidationStatus {
  VALID,
  PARTIAL,
  INVALID
}
