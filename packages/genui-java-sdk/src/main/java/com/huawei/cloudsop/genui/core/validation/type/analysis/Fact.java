/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.type.analysis;

import java.util.List;
import java.util.Set;

/**
 * 一次表达式分析得到的事实:类型上界 + 证据 + 精度损失 + 上游错误身份(#77 第 5.2 节)。
 *
 * <p>身份即全部字段:相同类型但证据、损失或上游错误不同的两个事实具有不同身份,检查缓存不得复用彼此结论 (例如同为 Unknown,一个含当前检查对象的确定反例,另一个没有,对 number
 * 要求分别是 MISMATCH 与 UNPROVEN)。
 *
 * @param type 可能值的上界
 * @param evidence 直接快照观察、局部结构证明或已声明运算保留的事实
 * @param losses 信息损失原因(未知输入、缺上下文、未支持规则、预算等)
 * @param upstreamErrors 已报告错误的身份,用于阻止连锁误报
 * @since 2026
 */
public record Fact(
        Type type, Set<Evidence> evidence, Set<Loss> losses, Set<String> upstreamErrors) {

    /**
     * 紧凑构造方法:校验类型非 null,并把证据、损失与上游错误集合归一化为不可变集合。
     *
     * @throws IllegalArgumentException 类型为 null 时
     */
    public Fact {
        if (type == null) {
            throw new IllegalArgumentException("fact requires a type");
        }
        evidence = evidence == null ? Set.of() : Set.copyOf(evidence);
        losses = losses == null ? Set.of() : Set.copyOf(losses);
        upstreamErrors = upstreamErrors == null ? Set.of() : Set.copyOf(upstreamErrors);
    }

    /** 只有类型上界的事实(无证据、无损失、无上游错误)。 */
    public static Fact of(Type type) {
        return new Fact(type, Set.of(), Set.of(), Set.of());
    }

    /** 信息不足的事实:Unknown + 至少一条损失原因。 */
    public static Fact unknown(Loss loss, Loss... more) {
        Set<Loss> losses = new java.util.LinkedHashSet<>();
        losses.add(loss);
        losses.addAll(List.of(more));
        return new Fact(Type.Unknown.INSTANCE, Set.of(), Set.copyOf(losses), Set.of());
    }

    /** 附加证据,返回新事实。 */
    public Fact withEvidence(Evidence item) {
        Set<Evidence> merged = new java.util.LinkedHashSet<>(evidence);
        merged.add(item);
        return new Fact(type, merged, losses, upstreamErrors);
    }

    /** 附加精度损失,返回新事实。 */
    public Fact withLoss(Loss loss) {
        Set<Loss> merged = new java.util.LinkedHashSet<>(losses);
        merged.add(loss);
        return new Fact(type, evidence, merged, upstreamErrors);
    }

    /** 附加上游错误身份,返回新事实。 */
    public Fact withUpstreamError(String errorIdentity) {
        Set<String> merged = new java.util.LinkedHashSet<>(upstreamErrors);
        merged.add(errorIdentity);
        return new Fact(type, evidence, losses, merged);
    }

    /** 附加上游错误身份集合,返回新事实。 */
    public Fact withUpstreamErrors(Set<String> errorIdentities) {
        Set<String> merged = new java.util.LinkedHashSet<>(upstreamErrors);
        merged.addAll(errorIdentities);
        return new Fact(type, evidence, losses, merged);
    }

    /** 附加精度损失集合,返回新事实。 */
    public Fact withLosses(Set<Loss> items) {
        Set<Loss> merged = new java.util.LinkedHashSet<>(losses);
        merged.addAll(items);
        return new Fact(type, evidence, merged, upstreamErrors);
    }

    /** 是否与上游错误关联(关联的检查结论须抑制,不能当作独立反例)。 */
    public boolean hasUpstreamErrors() {
        return !upstreamErrors.isEmpty();
    }

    /**
     * 分析证据(#77 第 6.5 节,只允许三类依据)。
     *
     * @since 2026
     */
    public sealed interface Evidence {

        /**
         * 直接快照观察到的值(Render Data Model 中的实际值;可携带确定反例)。
         *
         * @param path 观察来源的数据路径
         * @param value 观察到的 JSON 值(已不可变快照)
         * @since 2026
         */
        record ObservedValue(String path, Object value) implements Evidence {}

        /**
         * 字段存在性观察:present 数 / missing 数 / null 数 / 总行数。
         *
         * @param path 观察来源的数据路径
         * @param present 有非 null 值的成员数
         * @param missing 缺该字段的成员数
         * @param nulls 显式 null 值的成员数(null 不是缺失)
         * @param total 参与该步的成员总数
         * @since 2026
         */
        record FieldPresence(String path, int present, int missing, int nulls, int total)
                implements Evidence {

            /**
             * 是否观察到过至少一个确定缺失。
             *
             * @return 至少一行缺该字段时为 true
             */
            public boolean hasMissingCounterexample() {
                return missing > 0;
            }
        }

        /**
         * 元素存在证据:数组确定含至少一个元素(Sort 保留、Filter 删除)。
         *
         * @since 2026
         */
        record ElementPresence() implements Evidence {}
    }

    /**
     * 精度损失 / 未证明原因(#77 第 11.3 节覆盖统计口径)。
     *
     * @since 2026
     */
    public enum Loss {
        /** 外部数据类型未知或数据访问越出可证明范围。 */
        UNKNOWN_INPUT,
        /** 契约 schema 含当前子集未支持的验证要求。 */
        UNSUPPORTED_RULE,
        /** 使用位置未提供模板所需上下文(如 v/row)。 */
        MISSING_CONTEXT,
        /** 预算耗尽,改用安全摘要。 */
        PRECISION_BUDGET,
        /** 无法证明数组当前含有元素(空数组可能性不能忽略)。 */
        NO_ELEMENT_EVIDENCE,
        /** 上游已报错,本检查依赖受污染输入。 */
        BLOCKED_BY_ERROR,
        /** 未绑定类型变量按 Unknown 处理。 */
        UNBOUND_TYPE_VARIABLE,
        /** 既有可能通过也有可能失败,且无适用于当前检查对象的证据(检查层专有原因,不进入事实损失)。 */
        INSUFFICIENT_EVIDENCE
    }
}
