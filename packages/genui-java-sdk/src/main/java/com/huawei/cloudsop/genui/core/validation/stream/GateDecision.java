package com.huawei.cloudsop.genui.core.validation.stream;

import com.huawei.cloudsop.genui.core.validation.ValidationResult;

/**
 * A single decision produced by the streaming statement gate for a completed openui-lang statement
 * (or a batch flushed together).
 *
 * <p>Decisions are the only way statement text leaves the {@link StreamingValidationSession}. The
 * generator translates {@link Kind#EMIT} decisions into {@code dsl} envelopes and {@link Kind#FAIL}
 * / {@link Kind#WITHHOLD} into the fail-fast path. {@link Kind#BUFFER} statements are held inside the
 * session until a later dependency resolves them (then re-surface as {@link Kind#EMIT}) or the stream
 * ends.
 */
public record GateDecision(Kind kind, String statementText, ValidationResult validationResult) {

  /** The kind of gate decision. */
  public enum Kind {
    /** Statement validated render-safe: forward as a {@code dsl} envelope. */
    EMIT,
    /**
     * Statement validated OK but held pending a temporarily-unresolved dependency. Never leaves the
     * session as its own frame — it is re-issued as {@link #EMIT} once flushed.
     */
    BUFFER,
    /**
     * Completed statement is definitively invalid (blocking ERROR). Its text is NEVER emitted. This
     * is the Fail-Fast trigger point (Section 7 will cancel + reask here).
     */
    WITHHOLD,
    /**
     * Terminal failure produced at {@code onEnd()} when accumulated DSL still has a blocking error
     * (e.g. buffered statements never resolved). Equivalent to WITHHOLD at end-of-stream.
     */
    FAIL
  }

  /** An EMIT decision carrying the render-safe statement text. */
  public static GateDecision emit(String statementText, ValidationResult result) {
    return new GateDecision(Kind.EMIT, statementText, result);
  }

  /** A BUFFER decision (statement held internally; text not forwarded yet). */
  public static GateDecision buffer(String statementText, ValidationResult result) {
    return new GateDecision(Kind.BUFFER, statementText, result);
  }

  /** A WITHHOLD decision (definitively invalid; text must never be forwarded). */
  public static GateDecision withhold(String statementText, ValidationResult result) {
    return new GateDecision(Kind.WITHHOLD, statementText, result);
  }

  /** A terminal FAIL decision at end-of-stream. */
  public static GateDecision fail(String statementText, ValidationResult result) {
    return new GateDecision(Kind.FAIL, statementText, result);
  }
}
