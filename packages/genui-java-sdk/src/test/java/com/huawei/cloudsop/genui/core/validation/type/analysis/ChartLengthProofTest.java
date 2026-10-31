/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.type.analysis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Chart 长度关系的证明规则：#77 §11.1 —— 精确长度相同，或两侧事实携带同一组长度来源，才证明长度相等。
 *
 * <p>长度来源由表达式分析写入 {@link Fact.Evidence.LengthSource}（符号引用、数据路径），并只经 Each/Sort 这类
 * 保持数量的运算传递；消费方不再按函数名回溯表达式。
 */
class ChartLengthProofTest {

    private static final long[] UNBOUNDED = {0, Type.Sequence.UNBOUNDED};

    @Test
    void exactEqualLengthsProveEquality() {
        assertTrue(
                AnalysisSession.provenSameLength(
                        new long[] {2, 2}, withoutSource(), new long[] {2, 2}, withoutSource()));
    }

    @Test
    void exactDifferentLengthsDoNotProveEquality() {
        assertFalse(
                AnalysisSession.provenSameLength(
                        new long[] {2, 2}, withoutSource(), new long[] {3, 3}, withoutSource()));
    }

    @Test
    void oneExactOneNonExactDoesNotProveEquality() {
        assertFalse(
                AnalysisSession.provenSameLength(
                        new long[] {2, 2}, withoutSource(), UNBOUNDED, withSources("ref:rows")));
    }

    @Test
    void identicalNonExactIntervalsFromDifferentSourcesProveNothing() {
        assertFalse(
                AnalysisSession.provenSameLength(
                        UNBOUNDED,
                        withSources("ref:active"),
                        UNBOUNDED,
                        withSources("ref:inactive")));
    }

    @Test
    void sameLengthPreservingSourceProvesEquality() {
        // 两侧都从同一个符号派生:Each/Sort 把输入的长度来源传递到结果,比较的是事实而不是表达式名称。
        TypeOps ops = new TypeOps(AnalysisBudget.defaults().tracker());
        Fact source = withSources("ref:rows").withEvidence(new Fact.Evidence.KnownLength(3));
        Fact sorted = ops.preserve(source);
        Fact each = ops.map(sorted, Fact.of(Types.scalar(Type.ScalarKind.STRING)));

        assertTrue(
                AnalysisSession.provenSameLength(UNBOUNDED, each, UNBOUNDED, each),
                "Each/Sort 必须保留输入的长度来源");
        assertTrue(each.lengthSourceIdentities().contains("ref:rows"));
        assertTrue(each.knownLength().isPresent() && each.knownLength().getAsLong() == 3);
    }

    @Test
    void filterDoesNotClaimTheInputLength() {
        // Filter 改变数量:不得把输入的长度来源带到结果,否则两个不同谓词的过滤会被误判为等长。
        TypeOps ops = new TypeOps(AnalysisBudget.defaults().tracker());
        Fact filtered = ops.subset(withSources("ref:rows"));
        assertTrue(filtered.lengthSourceIdentities().isEmpty());
        assertTrue(filtered.knownLength().isEmpty());
    }

    @Test
    void sameDataPathProvesEqualityWithoutAnyDerivation() {
        assertTrue(
                AnalysisSession.provenSameLength(
                        UNBOUNDED,
                        withSources("path:data.labels"),
                        UNBOUNDED,
                        withSources("path:data.labels")));
        assertFalse(
                AnalysisSession.provenSameLength(
                        UNBOUNDED,
                        withSources("path:data.labels"),
                        UNBOUNDED,
                        withSources("path:data.other")));
    }

    private static Fact withoutSource() {
        return Fact.of(Types.emptySequence());
    }

    private static Fact withSources(String... identities) {
        Set<Fact.Evidence> evidence = new LinkedHashSet<>();
        for (String identity : identities) {
            evidence.add(new Fact.Evidence.LengthSource(identity));
        }
        return new Fact(Types.emptySequence(), evidence, Set.of(), Set.of());
    }
}
