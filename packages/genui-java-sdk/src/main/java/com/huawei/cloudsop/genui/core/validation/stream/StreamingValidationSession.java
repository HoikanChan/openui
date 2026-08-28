package com.huawei.cloudsop.genui.core.validation.stream;

import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import com.huawei.cloudsop.genui.core.validation.OpenuiLangValidator;
import com.huawei.cloudsop.genui.core.validation.ValidationMode;
import com.huawei.cloudsop.genui.core.validation.ValidationRequest;
import com.huawei.cloudsop.genui.core.validation.ValidationResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Stateful, per-generation streaming gate (design Decision #5 / #7 scenes A-C).
 *
 * <p>Accumulates completed openui-lang statements from a {@link StatementBoundaryScanner}, validates
 * each against the contract in {@link ValidationMode#STREAMING} (with everything already accepted as
 * symbol context), and classifies it:
 *
 * <ul>
 *   <li><b>render-safe</b> (no blocking ERROR, no temporary-unresolved warning) → {@link
 *       GateDecision.Kind#EMIT}; appended to the accepted prefix.
 *   <li><b>accepted-but-temporary-unresolved</b> (only non-blocking / retryable warnings) → {@link
 *       GateDecision.Kind#BUFFER}; held in {@code acceptedButBuffered}. When a later statement
 *       resolves the dependency it re-surfaces as EMIT (via {@link #tryFlushBuffered}); leftover
 *       buffered statements flush at {@link #onEnd()}.
 *   <li><b>definitively invalid</b> (blocking ERROR — unknown-component / inline-reserved /
 *       invalid-prop on a complete statement) → {@link GateDecision.Kind#WITHHOLD}; never emitted.
 *       Fail-Fast trigger point recorded in {@link #withheldStatement()} / {@link #withheldResult()}.
 * </ul>
 *
 * <p>Not thread-safe: one instance per stream, driven from the single SSE-consumer thread.
 */
public final class StreamingValidationSession {

  private final StatementBoundaryScanner scanner = new StatementBoundaryScanner();
  private final OpenuiLangValidator validator;
  private final GenerationContract contract;
  private final String rootName;
  private final Map<String, Object> dataModel;

  /** Concatenated accepted+emitted statements — the symbol context passed to the validator. */
  private final StringBuilder acceptedPrefix = new StringBuilder();

  /** Statements that validated OK but are held pending a temporarily-unresolved dependency. */
  private final List<String> acceptedButBuffered = new ArrayList<>();

  private ValidationResult latestValidationResult;

  /** First definitively-invalid statement + its issues (for the Section 7 reask). */
  private String withheldStatement;
  private ValidationResult withheldResult;

  public StreamingValidationSession(
      OpenuiLangValidator validator, GenerationContract contract, String rootName) {
    this(validator, contract, rootName, null);
  }

  public StreamingValidationSession(
      OpenuiLangValidator validator,
      GenerationContract contract,
      String rootName,
      Map<String, Object> dataModel) {
    this.validator = Objects.requireNonNull(validator, "validator");
    this.contract = contract;
    this.rootName = rootName;
    this.dataModel =
        dataModel == null
            ? null
            : Collections.unmodifiableMap(new LinkedHashMap<>(dataModel));
  }

  /** Feed one raw LLM delta; return the ordered gate decisions produced by it. */
  public List<GateDecision> onDelta(String delta) {
    List<GateDecision> decisions = new ArrayList<>();
    for (String candidate : scanner.onDelta(delta)) {
      classify(candidate, decisions);
      if (withheldStatement != null) {
        // Fail-fast: stop processing further completed statements once one is invalid.
        break;
      }
    }
    return decisions;
  }

  /**
   * End of stream. Drains the last pending statement, then runs a FINAL validation over the full
   * accepted DSL (accepted prefix + still-buffered). Flushes remaining buffered statements as EMIT
   * when final-valid, or emits a terminal {@link GateDecision.Kind#FAIL} if a blocking error remains.
   */
  public List<GateDecision> onEnd() {
    List<GateDecision> decisions = new ArrayList<>();
    if (withheldStatement != null) {
      return decisions; // already failed fast
    }
    for (String candidate : scanner.drainAtEnd()) {
      classify(candidate, decisions);
      if (withheldStatement != null) {
        return decisions;
      }
    }

    // FINAL validation over accepted prefix + remaining buffered statements (in order).
    String finalDsl = joinFinalDsl();
    ValidationResult finalResult =
        validate(finalDsl, ValidationMode.FINAL);
    latestValidationResult = finalResult;

    if (finalResult.hasBlockingIssues()) {
      // Buffered dependency never arrived (or something else broke). Terminal failure.
      String failed = acceptedButBuffered.isEmpty()
          ? finalDsl
          : String.join("\n", acceptedButBuffered);
      withheldStatement = failed;
      withheldResult = finalResult;
      decisions.add(GateDecision.fail(failed, finalResult));
      return decisions;
    }

    // Clean end: flush any remaining buffered statements in order.
    for (String buffered : acceptedButBuffered) {
      appendAccepted(buffered);
      decisions.add(GateDecision.emit(buffered, finalResult));
    }
    acceptedButBuffered.clear();
    return decisions;
  }

  private void classify(String candidate, List<GateDecision> decisions) {
    String probe = joinAcceptedWith(candidate);
    ValidationResult result = validate(probe, ValidationMode.STREAMING);
    latestValidationResult = result;

    if (result.hasBlockingIssues()) {
      // Definitively invalid completed statement → WITHHOLD (never emit).
      withheldStatement = candidate;
      withheldResult = result;
      decisions.add(GateDecision.withhold(candidate, result));
      return;
    }

    if (hasTemporaryUnresolved(result)) {
      // Accepted but depends on something not yet streamed → hold.
      acceptedButBuffered.add(candidate);
      decisions.add(GateDecision.buffer(candidate, result));
      return;
    }

    // Render-safe: accept, emit, then see if it unblocks anything buffered earlier.
    appendAccepted(candidate);
    decisions.add(GateDecision.emit(candidate, result));
    tryFlushBuffered(decisions);
  }

  /** Re-validate buffered statements against the grown prefix; promote resolved ones to EMIT. */
  private void tryFlushBuffered(List<GateDecision> decisions) {
    boolean progressed = true;
    while (progressed && !acceptedButBuffered.isEmpty()) {
      progressed = false;
      for (int i = 0; i < acceptedButBuffered.size(); i++) {
        String buffered = acceptedButBuffered.get(i);
        ValidationResult result = validate(joinAcceptedWith(buffered), ValidationMode.STREAMING);
        if (!result.hasBlockingIssues() && !hasTemporaryUnresolved(result)) {
          acceptedButBuffered.remove(i);
          appendAccepted(buffered);
          latestValidationResult = result;
          decisions.add(GateDecision.emit(buffered, result));
          progressed = true;
          break; // restart scan; prefix changed
        }
      }
    }
  }

  private ValidationResult validate(String dsl, ValidationMode mode) {
    ValidationRequest request =
        ValidationRequest.builder()
            .dsl(dsl)
            .contract(contract)
            .rootName(rootName)
            .dataModel(dataModel)
            .mode(mode)
            .build();
    return validator.validate(request);
  }

  /** A streaming result is "temporary-unresolved" when it has retryable non-blocking issues. */
  private static boolean hasTemporaryUnresolved(ValidationResult result) {
    return result.issues().stream().anyMatch(i -> i.retryable());
  }

  private void appendAccepted(String statement) {
    if (acceptedPrefix.length() > 0) {
      acceptedPrefix.append('\n');
    }
    acceptedPrefix.append(statement);
  }

  private String joinAcceptedWith(String candidate) {
    if (acceptedPrefix.length() == 0) {
      return candidate;
    }
    return acceptedPrefix + "\n" + candidate;
  }

  private String joinFinalDsl() {
    StringBuilder sb = new StringBuilder(acceptedPrefix);
    for (String buffered : acceptedButBuffered) {
      if (sb.length() > 0) {
        sb.append('\n');
      }
      sb.append(buffered);
    }
    return sb.toString();
  }

  // ── accessors for the generator + Section 7 ────────────────────────────────

  /** The render-safe accepted DSL emitted so far (excludes still-buffered statements). */
  public String acceptedDsl() {
    return acceptedPrefix.toString();
  }

  /** Latest validation result observed (streaming per-statement, or the final one at end). */
  public ValidationResult latestValidationResult() {
    return latestValidationResult;
  }

  /** {@code true} once a definitively-invalid statement has been withheld / failed. */
  public boolean hasWithheld() {
    return withheldStatement != null;
  }

  /** The first withheld (definitively-invalid) statement text, or {@code null}. */
  public String withheldStatement() {
    return withheldStatement;
  }

  /** The validation result explaining the withheld statement, or {@code null}. */
  public ValidationResult withheldResult() {
    return withheldResult;
  }

  /** Statements currently held pending a dependency (test/inspection aid). */
  public List<String> bufferedStatements() {
    return List.copyOf(acceptedButBuffered);
  }

  /**
   * Prepare this session to continue from its accepted prefix on a Section 7 reask stream. Clears
   * the fail-fast state ({@link #withheldStatement} / {@link #withheldResult}) and discards the
   * abandoned original-stream tail from the boundary scanner, WITHOUT touching {@link
   * #acceptedPrefix} or {@link #acceptedButBuffered}. After this the same instance can be driven by
   * {@link #onDelta}/{@link #onEnd} over a fresh continuation stream, so accepted DSL and seq
   * monotonicity carry across the takeover.
   */
  public void resetForReask() {
    withheldStatement = null;
    withheldResult = null;
    scanner.reset();
  }
}
