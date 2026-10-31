/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.type.analysis;

/**
 * 单次分析会话的集中预算(#77 第 13.1 节)。
 *
 * <p>检查点必须在分配或展开之前,不能生成巨大结果后才截断。预算耗尽只损失精度:调用方改用覆盖原可能值的 安全摘要并记录 {@link
 * Fact.Loss#PRECISION_BUDGET},不能因超限制造通过或错误的证据。预算不以墙钟为分支, 同一输入与预算产生稳定结果。
 *
 * @param maxTypeNodes 联合展开/结构运算允许的最大类型节点数
 * @param maxUnionBranches 单个联合允许的最大分支数
 * @param maxObjectDepth 数据值类型化的最大对象深度
 * @param maxArraySample 单数组类型化的最大扫描元素数(超出部分进入上界)
 * @param maxChecks 允许发现的最大检查要求数
 * @since 2026
 */
public record AnalysisBudget(
        long maxTypeNodes,
        long maxUnionBranches,
        long maxObjectDepth,
        long maxArraySample,
        long maxChecks) {

    private static final long DEFAULT_MAX_TYPE_NODES = 4_096L;

    private static final long DEFAULT_MAX_UNION_BRANCHES = 64L;

    private static final long DEFAULT_MAX_OBJECT_DEPTH = 16L;

    private static final long DEFAULT_MAX_ARRAY_SAMPLE = 256L;

    private static final long DEFAULT_MAX_CHECKS = 2_048L;

    /** 默认预算(工程配置,非语言语义;阈值按现有 corpus 基线设定)。 */
    public static AnalysisBudget defaults() {
        return new AnalysisBudget(
                DEFAULT_MAX_TYPE_NODES,
                DEFAULT_MAX_UNION_BRANCHES,
                DEFAULT_MAX_OBJECT_DEPTH,
                DEFAULT_MAX_ARRAY_SAMPLE,
                DEFAULT_MAX_CHECKS);
    }

    /**
     * 跟踪一次分析会话预算消耗的计数器;每次分析独立实例,不跨会话复用。
     *
     * @since 2026
     */
    public static final class Tracker {
        private final AnalysisBudget budget;
        private long typeNodes;
        private long checks;
        private boolean precisionLost;

        /** 会话级精度损失,独立于检查是否 MATCH 及事实是否继续传播。 */
        public void markPrecisionLost() {
            precisionLost = true;
        }

        /**
         * 会话是否发生过精度损失。
         *
         * @return 任一预算检查超限时为 true
         */
        public boolean precisionLost() {
            return precisionLost;
        }

        Tracker(AnalysisBudget budget) {
            this.budget = budget;
        }

        /**
         * 当前会话的预算配置。
         *
         * @return 创建跟踪器时绑定的预算
         */
        public AnalysisBudget budget() {
            return budget;
        }

        /**
         * 在创建 {@code count} 个类型节点之前检查;返回 false 表示预算耗尽。
         *
         * @param count 本次即将创建的类型节点数
         * @return 累计未超过 {@code maxTypeNodes} 时为 true;否则标记精度损失并返回 false
         */
        public boolean tryConsumeTypeNodes(long count) {
            if (typeNodes + count > budget.maxTypeNodes()) {
                markPrecisionLost();
                return false;
            }
            typeNodes += count;
            return true;
        }

        /**
         * 联合展开前检查分支数是否超限。
         *
         * @param branches 即将展开的联合分支数
         * @return 未超过 {@code maxUnionBranches} 时为 true;否则标记精度损失并返回 false
         */
        public boolean unionBranchesWithinBudget(long branches) {
            if (branches > budget.maxUnionBranches()) {
                markPrecisionLost();
                return false;
            }
            return true;
        }

        /**
         * 发现一条检查要求前检查;返回 false 表示不能再发现新检查。
         *
         * @return 未达到 {@code maxChecks} 时为 true;否则标记精度损失并返回 false
         */
        public boolean tryConsumeCheck() {
            if (checks + 1 > budget.maxChecks()) {
                markPrecisionLost();
                return false;
            }
            checks++;
            return true;
        }
    }

    /**
     * 开启一个会话级预算跟踪器。
     *
     * @return 绑定本预算、计数归零的新跟踪器
     */
    public Tracker tracker() {
        return new Tracker(this);
    }
}
