/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.type;

import java.util.List;

/**
 * A check requirement compiled from a component-schema fragment (#82), kept separate from proven program value types.
 *
 * <p>
 * Per design §5.3, an {@code Expectation} is NOT a second value type: {@code required}, cardinality, {@code anyOf} and
 * {@code oneOf} keep their own logic instead of being pressed into a union. Composite requirements
 * ({@link AnyOf}/{@link ExactlyOne}/{@link AllOf}) combine their sub-requirements on the SAME observed value or an
 * aligned partition before aggregation — they are never evaluated by stitching together per-candidate summaries.
 * {@link Unsupported} preserves a schema keyword this SDK cannot prove, so it is never silently dropped and reported
 * as a full MATCH.
 */
public sealed interface Expectation
        permits Expectation.Accept, Expectation.RequireField, Expectation.AllOf, Expectation.AnyOf,
        Expectation.ExactlyOne, Expectation.Predicate, Expectation.Unsupported,
        SchemaExpectationCompiler.ElementRequirement, SchemaExpectationCompiler.ExtraFieldsRequirement {

    /** A value-type constraint: the observed value must be provably included in {@code type}. */
    record Accept(ValueType type) implements Expectation {
    }

    /** The target object must have field {@code name} (present), whose value must satisfy {@code expectation}. */
    record RequireField(String name, Expectation expectation) implements Expectation {
    }

    /** Every sub-requirement must hold (on the same value / aligned partition). */
    record AllOf(List<Expectation> parts) implements Expectation {
        public AllOf {
            parts = List.copyOf(parts);
        }
    }

    /** At least one sub-requirement must hold on the same value (JSON Schema {@code anyOf}). */
    record AnyOf(List<Expectation> candidates) implements Expectation {
        public AnyOf {
            candidates = List.copyOf(candidates);
        }
    }

    /** Exactly one sub-requirement must hold on the same value (JSON Schema {@code oneOf}); NOT a plain union. */
    record ExactlyOne(List<Expectation> candidates) implements Expectation {
        public ExactlyOne {
            candidates = List.copyOf(candidates);
        }
    }

    /** A non-type predicate (e.g. integer, cardinality) kept as its own requirement. */
    record Predicate(String kind, List<String> parameters) implements Expectation {
        public Predicate {
            parameters = List.copyOf(parameters);
        }
    }

    /**
     * A schema keyword this SDK does not support proving. Never treated as "no constraint"; always yields UNPROVEN so
     * an incomplete check is visible rather than a fabricated MATCH.
     */
    record Unsupported(String schemaPath, String keyword) implements Expectation {
    }

    /** Human/model-facing rendering of this requirement for diagnostics. */
    default String displayName() {
        if (this instanceof Accept accept) {
            return accept.type().displayName();
        }
        if (this instanceof RequireField req) {
            return "required field \"" + req.name() + "\": " + req.expectation().displayName();
        }
        if (this instanceof AllOf all) {
            return all.parts().stream().map(Expectation::displayName).reduce((a, b) -> a + " & " + b).orElse("all");
        }
        if (this instanceof AnyOf any) {
            return any.candidates().stream().map(Expectation::displayName).reduce((a, b) -> a + " | " + b)
                    .orElse("anyOf");
        }
        if (this instanceof ExactlyOne one) {
            return one.candidates().stream().map(Expectation::displayName).reduce((a, b) -> a + " ^ " + b)
                    .orElse("oneOf");
        }
        if (this instanceof Predicate predicate) {
            return predicate.kind();
        }
        if (this instanceof Unsupported unsupported) {
            return "unsupported(" + unsupported.keyword() + ")";
        }
        if (this instanceof SchemaExpectationCompiler.ElementRequirement element) {
            return element.displayName();
        }
        if (this instanceof SchemaExpectationCompiler.ExtraFieldsRequirement extra) {
            return extra.displayName();
        }
        return "expectation";
    }
}
