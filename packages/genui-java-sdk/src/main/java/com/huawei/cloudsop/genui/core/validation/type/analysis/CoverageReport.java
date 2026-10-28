package com.huawei.cloudsop.genui.core.validation.type.analysis;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 类型检查覆盖报告(#77 第 11.3 节):统计对象是稳定的检查要求,不是缓存访问次数或逐行展开的数量。
 *
 * <p>
 * {@code discovered = matched + mismatched + unproven};未完成遍历显式标记,不能以已发现检查的通过比例声称
 * 完整覆盖。简要计数进入 {@code ValidationMetadata.extra};完整结构留在内部报告。
 *
 * @since 2026
 */
public final class CoverageReport {

    /** 一条检查要求的稳定身份:语句/表达式路径 + 使用位置 + 要求种类 + 契约路径。 */
    public record Obligation(String statementId, String useSite, String kind, String contractPath) {

        /** 稳定身份键(同一次分析内去重)。 */
        String identity() {
            return statementId + "|" + useSite + "|" + kind + "|" + contractPath;
        }
    }

    /** 单条检查要求的结论记录。 */
    public record Entry(Obligation obligation, Checks.Verdict verdict, List<Fact.Loss> reasons) {

        public Entry {
            reasons = reasons == null ? List.of() : List.copyOf(reasons);
        }
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private boolean traversalTruncated;
    private boolean precisionLost;

    /** 预算生产方直接上报,不依赖检查结论及未证明原因。 */
    public void markPrecisionLost() {
        precisionLost = true;
        markTraversalTruncated();
    }

    /** 登记一条检查结论;同一身份重复登记时保留首条(同一检查不因重试重复计数)。 */
    public void record(Entry entry) {
        entries.putIfAbsent(entry.obligation().identity(), entry);
        if (entry.reasons().contains(Fact.Loss.PRECISION_BUDGET)) {
            markTraversalTruncated();
        }
    }

    /** 标记遍历被截断(预算或访问限制导致后续要求尚未发现)。 */
    public void markTraversalTruncated() {
        this.traversalTruncated = true;
    }

    public int discovered() {
        return entries.size();
    }

    public long matched() {
        return count(Checks.Verdict.MATCH);
    }

    public long mismatched() {
        return count(Checks.Verdict.MISMATCH);
    }

    public long unproven() {
        return count(Checks.Verdict.UNPROVEN);
    }

    private long count(Checks.Verdict verdict) {
        return entries.values().stream().filter(e -> e.verdict() == verdict).count();
    }

    /** 分析是否完整:遍历未截断即完整(未证明项不影响完整性,它们已被显式发现)。 */
    public boolean analysisComplete() {
        return !traversalTruncated;
    }

    /** 未证明原因计数;同一要求可计入多个原因,不依赖集合遍历顺序。 */
    public Map<Fact.Loss, Long> unprovenReasons() {
        Map<Fact.Loss, Long> counts = new EnumMap<>(Fact.Loss.class);
        for (Entry entry : entries.values()) {
            if (entry.verdict() == Checks.Verdict.UNPROVEN && !entry.reasons().isEmpty()) {
                for (Fact.Loss reason : Set.copyOf(entry.reasons())) {
                    counts.merge(reason, 1L, Long::sum);
                }
            } else if (entry.verdict() == Checks.Verdict.UNPROVEN) {
                counts.merge(Fact.Loss.INSUFFICIENT_EVIDENCE, 1L, Long::sum);
            }
        }
        return Collections.unmodifiableMap(counts);
    }

    /** 进入 {@code ValidationMetadata.extra} 的简要计数(字符串值)。 */
    public Map<String, String> metadataSummary() {
        Map<String, String> summary = new LinkedHashMap<>();
        summary.put("typeChecks.discovered", String.valueOf(discovered()));
        summary.put("typeChecks.matched", String.valueOf(matched()));
        summary.put("typeChecks.mismatched", String.valueOf(mismatched()));
        summary.put("typeChecks.unproven", String.valueOf(unproven()));
        summary.put("typeChecks.analysisComplete", String.valueOf(analysisComplete()));
        if (traversalTruncated) {
            summary.put("typeChecks.traversalTruncated", "true");
        }
        if (precisionLost) {
            summary.put("typeChecks.precisionLosses", "PRECISION_BUDGET");
        }
        Map<Fact.Loss, Long> reasons = unprovenReasons();
        if (!reasons.isEmpty()) {
            StringBuilder text = new StringBuilder();
            reasons.forEach((reason, count) -> {
                if (text.length() > 0) {
                    text.append(',');
                }
                text.append(reason.name()).append('=').append(count);
            });
            summary.put("typeChecks.unprovenReasons", text.toString());
        }
        return summary;
    }
}
