package com.huawei.cloudsop.genui.core.validation.type.analysis;

/**
 * 单次分析会话的集中预算(#77 第 13.1 节)。
 *
 * <p>
 * 检查点必须在分配或展开之前,不能生成巨大结果后才截断。预算耗尽只损失精度:调用方改用覆盖原可能值的
 * 安全摘要并记录 {@link Fact.Loss#PRECISION_BUDGET},不能因超限制造通过或错误的证据。预算不以墙钟为分支,
 * 同一输入与预算产生稳定结果。
 *
 * @param maxTypeNodes
 *            联合展开/结构运算允许的最大类型节点数
 * @param maxUnionBranches
 *            单个联合允许的最大分支数
 * @param maxObjectDepth
 *            数据值类型化的最大对象深度
 * @param maxArraySample
 *            单数组类型化的最大扫描元素数(超出部分进入上界)
 * @param maxChecks
 *            允许发现的最大检查要求数
 *
 * @since 2026
 */
public record AnalysisBudget(long maxTypeNodes, long maxUnionBranches, long maxObjectDepth, long maxArraySample,
        long maxChecks) {

    /** 默认预算(工程配置,非语言语义;阈值按现有 corpus 基线设定)。 */
    public static AnalysisBudget defaults() {
        return new AnalysisBudget(4_096, 64, 16, 256, 2_048);
    }

    /** 跟踪一次分析会话预算消耗的计数器;每次分析独立实例,不跨会话复用。 */
    public static final class Tracker {
        private final AnalysisBudget budget;
        private long typeNodes;
        private long checks;
        private boolean precisionLost;

        /** 会话级精度损失,独立于检查是否 MATCH 及事实是否继续传播。 */
        public void markPrecisionLost() {
            precisionLost = true;
        }

        public boolean precisionLost() {
            return precisionLost;
        }

        Tracker(AnalysisBudget budget) {
            this.budget = budget;
        }

        /** 当前会话的预算配置。 */
        public AnalysisBudget budget() {
            return budget;
        }

        /** 在创建 {@code count} 个类型节点之前检查;返回 false 表示预算耗尽。 */
        public boolean tryConsumeTypeNodes(long count) {
            if (typeNodes + count > budget.maxTypeNodes()) {
                markPrecisionLost();
                return false;
            }
            typeNodes += count;
            return true;
        }

        /** 联合展开前检查分支数是否超限。 */
        public boolean unionBranchesWithinBudget(long branches) {
            if (branches > budget.maxUnionBranches()) {
                markPrecisionLost();
                return false;
            }
            return true;
        }

        /** 发现一条检查要求前检查;返回 false 表示不能再发现新检查。 */
        public boolean tryConsumeCheck() {
            if (checks + 1 > budget.maxChecks()) {
                markPrecisionLost();
                return false;
            }
            checks++;
            return true;
        }
    }

    /** 开启一个会话级预算跟踪器。 */
    public Tracker tracker() {
        return new Tracker(this);
    }
}
