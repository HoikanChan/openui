package com.huawei.cloudsop.genui.core.validation.repair;

import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import com.huawei.cloudsop.genui.core.llm.extract.OpenuiCodeExtractor;
import com.huawei.cloudsop.genui.core.llm.protocol.ChatCompletionResponse;
import com.huawei.cloudsop.genui.core.llm.protocol.ChatMessage;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransport;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransportException;
import com.huawei.cloudsop.genui.core.validation.OpenuiLangValidator;
import com.huawei.cloudsop.genui.core.validation.ValidationMode;
import com.huawei.cloudsop.genui.core.validation.ValidationRequest;
import com.huawei.cloudsop.genui.core.validation.ValidationResult;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Coordinates reflection repair (design Decision #6 / #8). Owns NO validation or gate logic of its
 * own — it reuses the injected {@link OpenuiLangValidator} for full-repair re-validation and, for
 * streaming reask, drives a caller-supplied {@link StreamConsumer} that feeds the new stream through
 * the SAME {@code StreamingValidationSession}/gate (no duplicated gate logic, no parallel streams).
 *
 * <p>Two entry points:
 *
 * <ul>
 *   <li>{@link #repairFull} — SYNC whole-DSL repair via {@code transport.post}. Re-validates each
 *       attempt in {@link ValidationMode#FINAL}; returns as soon as an attempt is VALID or the
 *       attempt budget is exhausted.
 *   <li>{@link #reaskStream} — STREAMING repair-and-continue via {@code transport.postStream}: opens
 *       ONE new stream (cancel-then-reask) and hands its {@link InputStream} to the caller's
 *       {@link StreamConsumer}, which runs it through the existing session/gate.
 * </ul>
 */
public final class RepairCoordinator {

  private final LlmTransport transport;
  private final OpenuiLangValidator validator;
  private final RepairPolicy policy;

  public RepairCoordinator(
      LlmTransport transport, OpenuiLangValidator validator, RepairPolicy policy) {
    this.transport = Objects.requireNonNull(transport, "transport");
    this.validator = Objects.requireNonNull(validator, "validator");
    this.policy = Objects.requireNonNull(policy, "policy");
  }

  // ── SYNC full repair ──────────────────────────────────────────────────────

  /**
   * Attempt full-DSL repair up to {@link RepairPolicy#maxAttempts()} times. Each attempt builds a
   * fresh full-repair prompt from the LAST failing DSL + issues, posts it, extracts the corrected
   * DSL, and re-validates in FINAL mode. Returns the first VALID outcome, otherwise the last
   * (still-invalid / errored) outcome.
   *
   * @param userIntent original user intent
   * @param invalidDsl the DSL that failed the initial FINAL validation
   * @param initialResult the failing ValidationResult (source of issues for attempt 1)
   * @param contract merged contract used for both signature hints and re-validation
   * @param rootName expected root component name
   */
  public FullRepairOutcome repairFull(
      String userIntent,
      String invalidDsl,
      ValidationResult initialResult,
      GenerationContract contract,
      String rootName) {
    return repairFull(userIntent, invalidDsl, initialResult, contract, rootName, null);
  }

  /** Full repair that preserves the Render Data Model across every re-validation attempt. */
  public FullRepairOutcome repairFull(
      String userIntent,
      String invalidDsl,
      ValidationResult initialResult,
      GenerationContract contract,
      String rootName,
      Map<String, Object> dataModel) {
    String currentDsl = invalidDsl;
    ValidationResult currentResult = initialResult;
    long deadlineNanos = deadline(policy.timeout());

    for (int attempt = 1; attempt <= policy.maxAttempts(); attempt++) {
      // NOTE: timeout is checked BETWEEN attempts; a single in-flight transport.post is not
      // independently time-bounded here (needs transport read-timeout).
      if (timedOut(deadlineNanos)) {
        return FullRepairOutcome.timedOut(currentDsl, currentResult, attempt - 1);
      }

      List<ChatMessage> messages =
          ReaskPromptBuilder.buildFullRepair(
              userIntent,
              currentDsl,
              currentResult == null ? List.of() : currentResult.issues(),
              contract);

      String extracted;
      try {
        String body = bodyBuilder.build(messages, false);
        String response = transport.post(body);
        String content = ChatCompletionResponse.parse(response).firstContent();
        extracted = OpenuiCodeExtractor.extract(content);
      } catch (LlmTransportException error) {
        // Transport failure during repair — surface the last known result, mark not repaired.
        return FullRepairOutcome.failed(currentDsl, currentResult, attempt);
      }

      ValidationResult revalidated = validateFinal(extracted, contract, rootName, dataModel);
      currentDsl = extracted;
      currentResult = revalidated;

      if (revalidated.isValid()) {
        return FullRepairOutcome.repaired(extracted, revalidated, attempt);
      }
    }
    return FullRepairOutcome.exhausted(currentDsl, currentResult, policy.maxAttempts());
  }

  // ── STREAMING reask-and-continue ──────────────────────────────────────────

  /**
   * Open ONE new continuation stream (cancel-then-reask) and hand it to {@code consumer}, which is
   * responsible for feeding it through the existing session/gate. Bounded by the reask timeout; the
   * ATTEMPT budget (how many reask rounds) is enforced by the caller across successive calls.
   *
   * @param userIntent original user intent
   * @param acceptedPrefix already-accepted valid DSL so far
   * @param invalidStatement the withheld invalid statement
   * @param issues issues explaining the withheld statement
   * @param contract merged contract (signature hints)
   * @param consumer caller-supplied bridge that runs the new stream through the session/gate
   * @return {@code true} if the new stream was opened and consumed cleanly (no fail-fast trigger
   *     inside the consumer); {@code false} if opening the stream failed or the consumer reported a
   *     failure.
   */
  public boolean reaskStream(
      String userIntent,
      String acceptedPrefix,
      String invalidStatement,
      List<com.huawei.cloudsop.genui.core.validation.ValidationIssue> issues,
      GenerationContract contract,
      StreamConsumer consumer) {
    // TODO: statementRepairTimeout is not yet enforced here — a hung SSE read blocks until the
    // transport closes it. Enforcing a wall-clock budget needs transport-level read-timeout
    // cooperation.
    List<ChatMessage> messages =
        ReaskPromptBuilder.buildRepairAndContinue(
            userIntent, acceptedPrefix, invalidStatement, issues, contract);
    String body = bodyBuilder.build(messages, true);
    try (InputStream stream = transport.postStream(body)) {
      return consumer.consume(stream);
    } catch (LlmTransportException | IOException error) {
      return false;
    }
  }

  // ── request-body assembly seam ────────────────────────────────────────────

  /**
   * Builds the transport request body from repair chat messages. Injected by the generator so the
   * coordinator reuses the SAME {@code ChatCompletionRequest.of(config, ...).toJson()} shape without
   * depending on the generator's private config.
   */
  @FunctionalInterface
  public interface BodyBuilder {
    String build(List<ChatMessage> messages, boolean stream);
  }

  /** Consumes the reask continuation stream through the existing session/gate. */
  @FunctionalInterface
  public interface StreamConsumer {
    /**
     * @return {@code true} if consumed cleanly, {@code false} if a fail-fast/validation failure was
     *     hit while consuming.
     */
    boolean consume(InputStream stream) throws IOException;
  }

  private BodyBuilder bodyBuilder = (messages, stream) -> {
    throw new IllegalStateException("BodyBuilder not configured");
  };

  /** Configure how repair request bodies are serialized (generator injects this once). */
  public RepairCoordinator withBodyBuilder(BodyBuilder bodyBuilder) {
    this.bodyBuilder = Objects.requireNonNull(bodyBuilder, "bodyBuilder");
    return this;
  }

  public RepairPolicy policy() {
    return policy;
  }

  // ── internals ─────────────────────────────────────────────────────────────

  private ValidationResult validateFinal(
      String dsl,
      GenerationContract contract,
      String rootName,
      Map<String, Object> dataModel) {
    ValidationRequest request =
        ValidationRequest.builder()
            .dsl(dsl)
            .contract(contract)
            .rootName(rootName)
            .dataModel(dataModel)
            .mode(ValidationMode.FINAL)
            .build();
    return validator.validate(request);
  }

  private static long deadline(Duration timeout) {
    if (timeout == null || timeout.isZero() || timeout.isNegative()) {
      return 0L; // no deadline
    }
    return System.nanoTime() + timeout.toNanos();
  }

  private static boolean timedOut(long deadlineNanos) {
    return deadlineNanos != 0L && System.nanoTime() >= deadlineNanos;
  }

  /**
   * Outcome of a full (sync) repair attempt sequence.
   *
   * @param repaired {@code true} iff a repaired DSL re-validated VALID
   * @param dsl the last repaired DSL text (VALID text when {@code repaired})
   * @param result the last ValidationResult (VALID when {@code repaired})
   * @param attempts number of attempts actually performed
   * @param timedOut {@code true} iff the sequence stopped because the timeout elapsed
   */
  public record FullRepairOutcome(
      boolean repaired, String dsl, ValidationResult result, int attempts, boolean timedOut) {

    static FullRepairOutcome repaired(String dsl, ValidationResult result, int attempts) {
      return new FullRepairOutcome(true, dsl, result, attempts, false);
    }

    static FullRepairOutcome exhausted(String dsl, ValidationResult result, int attempts) {
      return new FullRepairOutcome(false, dsl, result, attempts, false);
    }

    static FullRepairOutcome timedOut(String dsl, ValidationResult result, int attempts) {
      return new FullRepairOutcome(false, dsl, result, attempts, true);
    }

    static FullRepairOutcome failed(String dsl, ValidationResult result, int attempts) {
      return new FullRepairOutcome(false, dsl, result, attempts, false);
    }
  }
}
