package com.huawei.cloudsop.genui.core.validation.parser;

/**
 * Machine-readable parser syntax-error codes.
 *
 * <p>These mirror the diagnostic <em>classes</em> the TS pipeline surfaces (see {@code
 * enrich-errors.ts}/{@code types.ts}), not exact message strings. Section 3 maps them into {@code
 * ValidationIssue} codes.
 */
public enum ParseErrorCode {
  /** A statement line had no {@code =} after its identifier. */
  MISSING_ASSIGNMENT,
  /** A line started with a token that cannot be a statement identifier. */
  INVALID_STATEMENT,
  /** An opening {@code (} / {@code [} / {@code &#123;} was never closed. */
  UNCLOSED_BRACKET,
  /** A string literal was never closed. */
  UNCLOSED_STRING,
  /** A token appeared that the expression grammar could not use. */
  UNEXPECTED_TOKEN
}
