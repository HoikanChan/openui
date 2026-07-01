package com.huawei.cloudsop.genui.core.validation;

import java.util.Objects;

/**
 * Top-level validation configuration for the GenUI generation pipeline.
 *
 * <p>Exposes exactly two strategy axes: <em>when</em> to validate
 * ({@link ValidationConfigMode}) and <em>what to do on failure</em>
 * ({@link RepairPolicyKind}). Use the preset factories rather than constructing directly.
 *
 * <pre>
 *   GenUiValidationConfig cfg = GenUiValidationConfig.finalOnly();   // default
 *   GenUiValidationConfig cfg = GenUiValidationConfig.streamingGateWithReask();
 * </pre>
 */
public record GenUiValidationConfig(
    ValidationConfigMode validationMode, RepairPolicyKind repairPolicy) {

  public GenUiValidationConfig {
    Objects.requireNonNull(validationMode, "validationMode");
    Objects.requireNonNull(repairPolicy, "repairPolicy");
  }

  // -----------------------------------------------------------------------
  // Presets
  // -----------------------------------------------------------------------

  /** Validate after generation completes; no repair. This is the default. */
  public static GenUiValidationConfig finalOnly() {
    return new GenUiValidationConfig(ValidationConfigMode.FINAL_ONLY, RepairPolicyKind.NONE);
  }

  /** Validate at streaming checkpoints and at the final result; no repair. */
  public static GenUiValidationConfig streamingGate() {
    return new GenUiValidationConfig(ValidationConfigMode.STREAMING_GATE, RepairPolicyKind.NONE);
  }

  /** Validate at streaming checkpoints; abort and re-ask on failure. */
  public static GenUiValidationConfig streamingGateWithReask() {
    return new GenUiValidationConfig(
        ValidationConfigMode.STREAMING_GATE, RepairPolicyKind.FAIL_FAST_REASK);
  }

  /** Disable all validation. */
  public static GenUiValidationConfig disabled() {
    return new GenUiValidationConfig(ValidationConfigMode.DISABLED, RepairPolicyKind.NONE);
  }
}
