/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.huawei.cloudsop.genui.core.validation.type.CheckResult.Verdict;
import com.huawei.cloudsop.genui.core.validation.type.Expectation.Accept;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.Primitive;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.PrimitiveKind;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.StringDomainType;

import org.junit.jupiter.api.Test;

import java.util.Set;

/**
 * Fact-check cache keys separate type identity from evidence / context / upstream-error identity (#82): the same
 * Unknown type with different evidence must not reuse a check conclusion; pure type-inclusion cache stays fact-free.
 */
class CheckCacheSeparationTest {

    private final RequirementJudge judge = new RequirementJudge();

    @Test
    void sameTypeDifferentEvidenceYieldsDifferentConclusions() {
        Accept number = new Accept(new Primitive(PrimitiveKind.NUMBER));
        // Both facts share the same (string-domain) type shape but differ in observed evidence.
        Fact withCounterExample = Fact.adapt(new StringDomainType(Set.of("x")));
        Fact withoutCounterExample = Fact.adapt(new StringDomainType(Set.of("y"))).withEvidenceCleared();

        CheckResult a = judge.check(withCounterExample, number);
        CheckResult b = judge.check(withoutCounterExample, number);

        assertEquals(Verdict.MISMATCH, a.verdict(), "observed string violates number");
        assertEquals(Verdict.UNPROVEN, b.verdict(), "no counterexample evidence -> cannot prove mismatch");
        assertNotEquals(a.factKey(), b.factKey(), "fact keys must differ when evidence differs");
    }

    @Test
    void differentUpstreamErrorIdentityDoesNotReuse() {
        Accept number = new Accept(new Primitive(PrimitiveKind.NUMBER));
        Fact clean = Fact.adapt(new StringDomainType(Set.of("x")));
        Fact upstreamErroneous = Fact.adapt(new StringDomainType(Set.of("x"))).withUpstreamError("err-1");

        CheckResult a = judge.check(clean, number);
        CheckResult b = judge.check(upstreamErroneous, number);

        assertNotEquals(a.factKey(), b.factKey(), "upstream error identity must be part of the check key");
    }

    @Test
    void differentUseContextDoesNotReuse() {
        Accept number = new Accept(new Primitive(PrimitiveKind.NUMBER));
        Fact base = Fact.adapt(new StringDomainType(Set.of("x")));

        CheckResult inTable = judge.check(base.inContext("table-cell"), number);
        CheckResult inChart = judge.check(base.inContext("chart-label"), number);

        assertNotEquals(inTable.factKey(), inChart.factKey(), "use context must be part of the check key");
    }

    @Test
    void pureTypeInclusionCacheIsFactFree() {
        // sameType / proveIncluded depend only on the two types, never on evidence or diagnostics.
        assertEquals(RequirementJudge.typeOnlyKey(new Primitive(PrimitiveKind.NUMBER)),
                RequirementJudge.typeOnlyKey(new Primitive(PrimitiveKind.NUMBER)),
                "type-only cache key ignores evidence and is stable for equal types");
    }
}
