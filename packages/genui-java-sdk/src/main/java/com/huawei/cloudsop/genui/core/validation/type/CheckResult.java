/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.type;

import java.util.Optional;

/**
 * The outcome of checking one {@link Fact} against one {@link Expectation} (#82).
 *
 * <p>
 * Per design §6.2: MATCH = all relevant possible values provably satisfy the requirement; MISMATCH = a proven
 * violation or a real counterexample applicable to the current check target; UNPROVEN = could pass or fail, or
 * rule/context/budget is insufficient. Only MATCH and MISMATCH are terminal; UNPROVEN is recorded, never repaired on
 * its own, and never reported as a fabricated MATCH.
 */
public final class CheckResult {

    public enum Verdict {
        MATCH, MISMATCH, UNPROVEN
    }

    private final Verdict verdict;
    private final String counterExample;
    private final String reason;
    private final String factKey;

    private CheckResult(Verdict verdict, String counterExample, String reason, String factKey) {
        this.verdict = verdict;
        this.counterExample = counterExample;
        this.reason = reason;
        this.factKey = factKey;
    }

    public static CheckResult match(String factKey) {
        return new CheckResult(Verdict.MATCH, null, null, factKey);
    }

    /** A proven violation; {@code counterExample} names the single observed value (or partition) that fails. */
    public static CheckResult mismatch(String counterExample, String factKey) {
        return new CheckResult(Verdict.MISMATCH, counterExample, null, factKey);
    }

    public static CheckResult unproven(String reason, String factKey) {
        return new CheckResult(Verdict.UNPROVEN, null, reason, factKey);
    }

    public Verdict verdict() {
        return verdict;
    }

    /** The single observed value (or aligned partition) that violates the requirement, when one was proven. */
    public Optional<String> counterExample() {
        return Optional.ofNullable(counterExample);
    }

    /** Why the check could not be proven, when UNPROVEN. */
    public Optional<String> reason() {
        return Optional.ofNullable(reason);
    }

    /** The fact/cache identity this conclusion was computed under (separates evidence/context/upstream identity). */
    public String factKey() {
        return factKey;
    }

    @Override
    public String toString() {
        return verdict + (counterExample != null ? "(" + counterExample + ")" : "")
                + (reason != null ? "[" + reason + "]" : "");
    }
}
