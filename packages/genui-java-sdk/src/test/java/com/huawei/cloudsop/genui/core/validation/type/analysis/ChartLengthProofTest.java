/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.type.analysis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.validation.parser.ExpressionParser;
import com.huawei.cloudsop.genui.core.validation.parser.OpenuiLexer;

import org.junit.jupiter.api.Test;

/** Chart 长度关系的证明规则：#77 §11.1 —— 精确长度相同或同一保长度来源，才证明长度相等。 */
class ChartLengthProofTest {

    private static final long[] UNBOUNDED = {0, Type.Sequence.UNBOUNDED};

    @Test
    void exactEqualLengthsProveEquality() {
        assertTrue(AnalysisSession.provenSameLength(expr("[\"A\", \"B\"]"), new long[]{2, 2},
                expr("[1, 2]"), new long[]{2, 2}));
    }

    @Test
    void exactDifferentLengthsDoNotProveEquality() {
        assertFalse(AnalysisSession.provenSameLength(expr("[\"A\", \"B\"]"), new long[]{2, 2},
                expr("[1, 2, 3]"), new long[]{3, 3}));
    }

    @Test
    void oneExactOneNonExactDoesNotProveEquality() {
        assertFalse(AnalysisSession.provenSameLength(expr("[\"A\", \"B\"]"), new long[]{2, 2},
                expr("@Each(rows, \"r\", r.value)"), UNBOUNDED));
    }

    @Test
    void identicalNonExactIntervalsFromDifferentSourcesProveNothing() {
        assertFalse(AnalysisSession.provenSameLength(expr("@Each(active, \"r\", r.label)"), UNBOUNDED,
                expr("@Each(inactive, \"r\", r.value)"), UNBOUNDED));
    }

    @Test
    void sameLengthPreservingSourceProvesEquality() {
        assertTrue(AnalysisSession.provenSameLength(expr("@Each(rows, \"r\", r.label)"), UNBOUNDED,
                expr("@Each(rows, \"r\", r.value)"), UNBOUNDED));
        assertTrue(AnalysisSession.provenSameLength(expr("@Sort(rows, \"k\")"), UNBOUNDED,
                expr("@Each(@Sort(rows, \"k\"), \"r\", r.value)"), UNBOUNDED));
    }

    @Test
    void sameDataPathProvesEqualityWithoutAnyDerivation() {
        assertTrue(AnalysisSession.provenSameLength(expr("data.labels"), UNBOUNDED, expr("data.labels"), UNBOUNDED));
        assertFalse(AnalysisSession.provenSameLength(expr("data.labels"), UNBOUNDED, expr("data.other"), UNBOUNDED));
    }

    private static com.huawei.cloudsop.genui.core.validation.parser.AstNode expr(String source) {
        return new ExpressionParser(OpenuiLexer.tokenize(source)).parse();
    }
}
