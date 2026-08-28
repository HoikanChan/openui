package com.huawei.cloudsop.genui.core.validation;

import java.util.List;

/**
 * Outcome of a single DSL validation call.
 *
 * <p>Use the static factory methods ({@link #valid}, {@link #invalid}, {@link #partial}) rather
 * than the canonical constructor when building results from validation logic.
 */
public record ValidationResult(
    ValidationStatus status,
    /** Normalized (whitespace-trimmed / auto-closed) DSL text, or {@code null} when unavailable. */
    String normalizedDsl,
    /** All issues found; never {@code null}. */
    List<ValidationIssue> issues,
    ValidationMetadata metadata) {

  public ValidationResult {
    issues = issues == null ? List.of() : List.copyOf(issues);
  }

  /** {@code true} iff {@link #status()} is {@link ValidationStatus#VALID}. */
  public boolean isValid() {
    return status == ValidationStatus.VALID;
  }

  /**
   * {@code true} iff any issue has {@link ValidationSeverity#ERROR} severity.
   * A partial result with only warnings/info is not "blocking".
   */
  public boolean hasBlockingIssues() {
    return issues.stream().anyMatch(i -> i.severity() == ValidationSeverity.ERROR);
  }

  /**
   * Low-noise diagnostics for repair and user-facing reports.
   *
   * <p>{@link #issues()} remains the complete parser-parity view. This view removes exact duplicates
   * and diagnostics dominated by a more actionable root cause.
   */
  public List<ValidationIssue> actionableIssues() {
    return ValidationIssueReducer.actionable(issues);
  }

  // -----------------------------------------------------------------------
  // Static factories
  // -----------------------------------------------------------------------

  /** Build a {@link ValidationStatus#VALID} result. */
  public static ValidationResult valid(
      String normalizedDsl, List<ValidationIssue> issues, ValidationMetadata metadata) {
    return new ValidationResult(ValidationStatus.VALID, normalizedDsl, issues, metadata);
  }

  /** Build a {@link ValidationStatus#INVALID} result (no normalized DSL). */
  public static ValidationResult invalid(
      List<ValidationIssue> issues, ValidationMetadata metadata) {
    return new ValidationResult(ValidationStatus.INVALID, null, issues, metadata);
  }

  /** Build a {@link ValidationStatus#PARTIAL} result (streaming mode). */
  public static ValidationResult partial(
      String normalizedDsl, List<ValidationIssue> issues, ValidationMetadata metadata) {
    return new ValidationResult(ValidationStatus.PARTIAL, normalizedDsl, issues, metadata);
  }
}
