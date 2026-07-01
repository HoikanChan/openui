package com.huawei.cloudsop.genui.core.validation;

/**
 * Severity level of a {@link ValidationIssue}. Only {@link #ERROR} is considered blocking
 * (i.e. causes {@link ValidationStatus#INVALID}).
 */
public enum ValidationSeverity {
  ERROR,
  WARNING,
  INFO
}
