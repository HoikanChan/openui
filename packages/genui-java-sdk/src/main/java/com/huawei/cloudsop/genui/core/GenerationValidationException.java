package com.huawei.cloudsop.genui.core;

import com.huawei.cloudsop.genui.core.validation.ValidationResult;
import java.util.Objects;

/**
 * Thrown by {@code GenUiGenerator.generate()} when final DSL validation fails (INVALID status)
 * and the configured repair policy does not attempt recovery.
 *
 * <p>Callers that want to inspect the issues can pattern-match on this subtype:
 * <pre>{@code
 *   try {
 *     generator.generate(request);
 *   } catch (GenerationValidationException e) {
 *     ValidationResult result = e.validationResult();
 *     // inspect result.issues(), result.status(), etc.
 *   }
 * }</pre>
 */
public final class GenerationValidationException extends GenerationSdkException {

  private final ValidationResult validationResult;

  public GenerationValidationException(String message, ValidationResult validationResult) {
    super(message);
    this.validationResult = Objects.requireNonNull(validationResult, "validationResult");
  }

  /** The full validation result, including issues and status. Never {@code null}. */
  public ValidationResult validationResult() {
    return validationResult;
  }
}
