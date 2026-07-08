/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.stream;

import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import com.huawei.cloudsop.genui.core.validation.OpenuiLangValidator;
import com.huawei.cloudsop.genui.core.validation.ValidationMode;
import com.huawei.cloudsop.genui.core.validation.ValidationRequest;
import com.huawei.cloudsop.genui.core.validation.ValidationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 有状态的、按次生成维度的流式门控（设计决策 #5 / #7 场景 A-C）。
 *
 * <p>
 * 从 {@link StatementBoundaryScanner} 累积已完成的 openui-lang 语句，在 {@link ValidationMode#STREAMING}
 * 下针对合约逐条校验（以目前为止已接受的内容作为符号上下文），并将其分类：
 *
 * <ul>
 * <li><b>渲染安全</b>（无阻塞 ERROR、无临时未解析警告）→ {@link GateDecision.Kind#EMIT}；追加到已接受前缀。
 * <li><b>已接受但临时未解析</b>（仅非阻塞/可重试的警告）→ {@link GateDecision.Kind#BUFFER}；held 在 {@code acceptedButBuffered}
 * 中。当后续语句解析了该依赖后，会（通过 {@link #tryFlushBuffered}）重新以 EMIT 形式出现；遗留的缓冲语句会在 {@link #onEnd()} 时刷出。
 * <li><b>确定性无效</b>（阻塞 ERROR —— 完整语句上的 unknown-component/inline-reserved/invalid-prop）→
 * {@link GateDecision.Kind#WITHHOLD}；永不发出。Fail-Fast 触发点记录在 {@link #withheldStatement()} / {@link #withheldResult()} 中。
 * </ul>
 *
 * <p>
 * 非线程安全：每个流一个实例，由单一的 SSE 消费者线程驱动。
 *
 * @since 2026
 */
public final class StreamingValidationSession {

    private final StatementBoundaryScanner scanner = new StatementBoundaryScanner();
    private final OpenuiLangValidator validator;
    private final GenerationContract contract;
    private final String rootName;
    private final Set<String> externalRefs;

    /** Concatenated accepted+emitted statements — the symbol context passed to the validator. */
    private final StringBuilder acceptedPrefix = new StringBuilder();

    /** Statements that validated OK but are held pending a temporarily-unresolved dependency. */
    private final List<String> acceptedButBuffered = new ArrayList<>();

    private ValidationResult latestValidationResult;

    /** First definitively-invalid statement + its issues (for the Section 7 reask). */
    private String withheldStatement;
    private ValidationResult withheldResult;

    /**
     * 构造流式校验 session（无外部引用）。
     *
     * @param validator
     *            用于逐条语句校验的校验器
     * @param contract
     *            合并合约，可为 {@code null}
     * @param rootName
     *            预期的根组件名，可为 {@code null}
     */
    public StreamingValidationSession(OpenuiLangValidator validator, GenerationContract contract, String rootName) {
        this(validator, contract, rootName, Set.of());
    }

    /**
     * 构造流式校验 session。
     *
     * @param validator
     *            用于逐条语句校验的校验器
     * @param contract
     *            合并合约，可为 {@code null}
     * @param rootName
     *            预期的根组件名，可为 {@code null}
     * @param externalRefs
     *            运行时才解析的外部引用名集合（如宿主数据绑定 {@code data}），随本 session 的每次校验请求透传； 可为 {@code null}，视同空集合
     */
    public StreamingValidationSession(OpenuiLangValidator validator, GenerationContract contract, String rootName,
            Set<String> externalRefs) {
        this.validator = Objects.requireNonNull(validator, "validator");
        this.contract = contract;
        this.rootName = rootName;
        this.externalRefs = externalRefs == null ? Set.of() : Set.copyOf(externalRefs);
    }

    /**
     * 送入一段原始 LLM 增量数据；返回由此产出的有序门控决策。
     *
     * @param delta
     *            增量文本
     * @return 门控决策列表
     */
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
     * 流结束。刷出最后一条待定语句，然后对完整的已接受 DSL（已接受前缀 + 仍缓冲的部分）执行一次 FINAL 校验。 最终有效时把剩余缓冲语句作为 EMIT 刷出；若仍存在阻塞错误则发出终态
     * {@link GateDecision.Kind#FAIL}。
     *
     * @return 门控决策列表
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
        ValidationResult finalResult = validate(finalDsl, ValidationMode.FINAL);
        latestValidationResult = finalResult;

        if (finalResult.hasBlockingIssues()) {
            // Buffered dependency never arrived (or something else broke). Terminal failure.
            String failed = acceptedButBuffered.isEmpty() ? finalDsl : String.join("\n", acceptedButBuffered);
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
        ValidationRequest request = ValidationRequest.builder().dsl(dsl).contract(contract).rootName(rootName)
                .externalRefs(externalRefs).mode(mode).build();
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

    /**
     * 获取目前为止已发出的渲染安全的已接受 DSL（不含仍在缓冲的语句）。
     *
     * @return 已接受的 DSL 文本
     */
    public String acceptedDsl() {
        return acceptedPrefix.toString();
    }

    /**
     * 获取最近一次观测到的校验结果（流式逐语句结果，或结束时的最终结果）。
     *
     * @return 最新校验结果
     */
    public ValidationResult latestValidationResult() {
        return latestValidationResult;
    }

    /**
     * 判断是否已有确定性无效的语句被搁置/失败。
     *
     * @return 存在被搁置语句时返回 {@code true}
     */
    public boolean hasWithheld() {
        return withheldStatement != null;
    }

    /**
     * 获取首个被搁置（确定性无效）的语句文本。
     *
     * @return 被搁置的语句文本，或 {@code null}
     */
    public String withheldStatement() {
        return withheldStatement;
    }

    /**
     * 获取解释被搁置语句的校验结果。
     *
     * @return 校验结果，或 {@code null}
     */
    public ValidationResult withheldResult() {
        return withheldResult;
    }

    /**
     * 获取当前因依赖未解析而被缓冲的语句（用于测试/排查）。
     *
     * @return 缓冲语句列表
     */
    public List<String> bufferedStatements() {
        return List.copyOf(acceptedButBuffered);
    }

    /**
     * 准备本 session 在后续章节的重新请求流上，从其已接受前缀继续。清除 fail-fast 状态 （{@link #withheldStatement} /
     * {@link #withheldResult}），并丢弃边界扫描器中原始流已放弃的尾部， 但<em>不</em>触碰 {@link #acceptedPrefix} 或
     * {@link #acceptedButBuffered}。此后同一实例可以在一个全新的 接续流上继续被 {@link #onDelta}/{@link #onEnd} 驱动，使已接受 DSL 与序号单调性跨接管过程保持连续。
     */
    public void resetForReask() {
        withheldStatement = null;
        withheldResult = null;
        scanner.reset();
    }
}
