package com.huawei.cloudsop.genui.core.validation;

/**
 * A single diagnostic issue found during DSL validation.
 *
 * <p>All position fields ({@code line}, {@code column}) use {@code -1} to denote "unknown".
 * Nullable string fields are {@code null} when not applicable.
 */
public record ValidationIssue(
    String code,
    ValidationSeverity severity,
    /** Category of the issuing check (e.g. "syntax", "contract", "reference"). */
    String source,
    String message,
    /** DSL statement id, or {@code null} if not attributable to a specific statement. */
    String statementId,
    /** Component name involved, or {@code null}. */
    String component,
    /** JSON-pointer-style path within the DSL AST, or {@code null}. */
    String path,
    /** 1-based line number, or {@code -1} if unknown. */
    int line,
    /** 1-based column number, or {@code -1} if unknown. */
    int column,
    /** Optional human-readable suggestion, or {@code null}. */
    String hint,
    /** Whether re-sending the prompt (after repair) may resolve this issue. */
    boolean retryable) {}
