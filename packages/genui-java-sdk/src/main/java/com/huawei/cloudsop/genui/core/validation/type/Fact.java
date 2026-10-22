/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.type;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * An analysis fact (#82 minimal seam): an observed/provable value plus the evidence, precision losses and upstream
 * error identity that bear on checking it.
 *
 * <p>
 * This is the thin adapter the composite-requirement judge consumes. Per design §5.2 a Fact bundles {@code type},
 * {@code evidence}, {@code losses} and {@code upstreamErrors}; #82 introduces the shape so the judge's same-value /
 * aligned-partition logic and its cache keys already depend on evidence/context identity, while the full evidence
 * algebra (per-field evidence, budget-driven loss) is owned by #80 and plugged in at integration.
 *
 * <p>
 * Identity is content-based and covers every dimension that may change a check conclusion: two facts with the same
 * type but different evidence, context or upstream-error identity are NOT interchangeable, so a cached check result is
 * never reused across them.
 */
public final class Fact {

    private final ValueType type;
    private final boolean evidencePresent;
    private final Set<String> losses;
    private final Set<String> upstreamErrors;
    private final String context;

    private Fact(ValueType type, boolean evidencePresent, Set<String> losses, Set<String> upstreamErrors,
            String context) {
        this.type = Objects.requireNonNull(type, "type");
        this.evidencePresent = evidencePresent;
        this.losses = Collections.unmodifiableSet(new LinkedHashSet<>(losses));
        this.upstreamErrors = Collections.unmodifiableSet(new LinkedHashSet<>(upstreamErrors));
        this.context = context == null ? "" : context;
    }

    /** Adapt a proven {@link ValueType} into a fact whose evidence reflects direct observation (#80 seam). */
    public static Fact adapt(ValueType type) {
        return new Fact(type, true, Set.of(), Set.of(), "");
    }

    /** A fact carrying no usable counterexample evidence (e.g. an abstract possibility, not an observation). */
    public Fact withEvidenceCleared() {
        return new Fact(type, false, losses, upstreamErrors, context);
    }

    /** Attach an upstream-error identity so a tainted input never reuses a clean input's check conclusion. */
    public Fact withUpstreamError(String errorId) {
        Set<String> errors = new LinkedHashSet<>(upstreamErrors);
        errors.add(Objects.requireNonNull(errorId, "errorId"));
        return new Fact(type, evidencePresent, losses, errors, context);
    }

    /** Record a precision loss (budget, unsupported rule, missing context); never creates MATCH/MISMATCH evidence. */
    public Fact withLoss(String loss) {
        Set<String> next = new LinkedHashSet<>(losses);
        next.add(Objects.requireNonNull(loss, "loss"));
        return new Fact(type, evidencePresent, next, upstreamErrors, context);
    }

    /** Bind this fact to a use context (e.g. a table cell vs a chart label); contexts never share conclusions. */
    public Fact inContext(String useContext) {
        return new Fact(type, evidencePresent, losses, upstreamErrors, useContext);
    }

    public ValueType type() {
        return type;
    }

    /** Whether this fact carries a directly observed counterexample (vs. an abstract possibility). */
    public boolean hasCounterExampleEvidence() {
        return evidencePresent;
    }

    public Set<String> losses() {
        return losses;
    }

    public Set<String> upstreamErrors() {
        return upstreamErrors;
    }

    public String context() {
        return context;
    }

    /**
     * The check-cache key for this fact: type identity + evidence + losses + upstream errors + use context. Pure
     * type-inclusion caching must use {@link RequirementJudge#typeOnlyKey(ValueType)} instead, which is fact-free.
     */
    String factKey() {
        return type.displayName() + "|ev=" + evidencePresent + "|loss=" + losses + "|err=" + upstreamErrors + "|ctx="
                + context;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Fact fact)) {
            return false;
        }
        return evidencePresent == fact.evidencePresent && type.equals(fact.type) && losses.equals(fact.losses)
                && upstreamErrors.equals(fact.upstreamErrors) && context.equals(fact.context);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, evidencePresent, losses, upstreamErrors, context);
    }

    @Override
    public String toString() {
        return "Fact[" + factKey() + "]";
    }
}
