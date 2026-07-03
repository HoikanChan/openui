package com.huawei.cloudsop.genui.core.validation.repair;

import com.huawei.cloudsop.genui.core.validation.RepairPolicyKind;
import java.time.Duration;
import java.util.Objects;

/**
 * Rich repair policy value object (design Decision #8 / #9). Wraps the coarse-grained {@link
 * RepairPolicyKind} that lives on {@code GenUiValidationConfig} together with the advanced tuning
 * knobs — attempt limits and per-request timeouts — that intentionally do NOT belong on the
 * top-level public config.
 *
 * <p>The generator maps {@code config.repairPolicy()} (a KIND) to a {@code RepairPolicy} via
 * {@link #from(RepairPolicyKind)} which applies sensible defaults ({@code maxAttempts = 1}).
 *
 * @param kind coarse strategy (NONE / FINAL_REPAIR / FAIL_FAST_REASK)
 * @param maxAttempts maximum repair attempts (whole-DSL repairs for sync, reask rounds for
 *     streaming). Must be {@code >= 1}. Default {@code 1}.
 * @param timeout budget for a single full-repair request; {@code Duration.ZERO} means "no explicit
 *     timeout" (rely on the transport's own timeout).
 * @param statementRepairTimeout budget for a single streaming reask round; {@code Duration.ZERO}
 *     means "no explicit timeout".
 */
public record RepairPolicy(
    RepairPolicyKind kind, int maxAttempts, Duration timeout, Duration statementRepairTimeout) {

  /** Default attempt budget per Decision #8: a single repair attempt. */
  public static final int DEFAULT_MAX_ATTEMPTS = 1;

  public RepairPolicy {
    Objects.requireNonNull(kind, "kind");
    if (maxAttempts < 1) {
      throw new IllegalArgumentException("maxAttempts must be >= 1, got: " + maxAttempts);
    }
    timeout = timeout == null ? Duration.ZERO : timeout;
    statementRepairTimeout =
        statementRepairTimeout == null ? Duration.ZERO : statementRepairTimeout;
    if (timeout.isNegative()) {
      throw new IllegalArgumentException("timeout must not be negative: " + timeout);
    }
    if (statementRepairTimeout.isNegative()) {
      throw new IllegalArgumentException(
          "statementRepairTimeout must not be negative: " + statementRepairTimeout);
    }
  }

  /**
   * Map a bare {@link RepairPolicyKind} from the top-level config to a {@code RepairPolicy} with
   * default advanced params ({@code maxAttempts = 1}, no explicit timeouts).
   */
  public static RepairPolicy from(RepairPolicyKind kind) {
    return new RepairPolicy(kind, DEFAULT_MAX_ATTEMPTS, Duration.ZERO, Duration.ZERO);
  }

  /** Convenience factory for a KIND with a custom attempt budget (defaults for the timeouts). */
  public static RepairPolicy of(RepairPolicyKind kind, int maxAttempts) {
    return new RepairPolicy(kind, maxAttempts, Duration.ZERO, Duration.ZERO);
  }

  /** {@code true} if this policy has an explicit (non-zero) full-repair timeout. */
  public boolean hasTimeout() {
    return !timeout.isZero();
  }

  /** {@code true} if this policy has an explicit (non-zero) streaming reask timeout. */
  public boolean hasStatementRepairTimeout() {
    return !statementRepairTimeout.isZero();
  }
}
