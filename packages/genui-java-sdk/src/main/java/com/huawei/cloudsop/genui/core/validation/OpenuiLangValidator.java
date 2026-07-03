package com.huawei.cloudsop.genui.core.validation;

/**
 * Top-level DSL validator API.
 *
 * <p>Receives a {@link ValidationRequest} and returns a {@link ValidationResult} whose
 * {@link ValidationStatus} is computed by the implementation:
 * <ul>
 *   <li>{@link ValidationStatus#INVALID} — any issue with severity {@code ERROR}.</li>
 *   <li>{@link ValidationStatus#PARTIAL} — only in {@link ValidationMode#STREAMING} when there are
 *       non-blocking (non-ERROR) issues and no blocking ERROR.</li>
 *   <li>{@link ValidationStatus#VALID} — no issues or only informational/non-blocking.</li>
 * </ul>
 *
 * <p>Implementations must be stateless/thread-safe unless documented otherwise.
 */
public interface OpenuiLangValidator {

  /**
   * Validate the DSL in {@code request} and return a {@link ValidationResult}.
   *
   * @param request the validation input; never {@code null}
   * @return validation result; never {@code null}
   */
  ValidationResult validate(ValidationRequest request);
}
