package com.huawei.cloudsop.genui.core.validation.type.analysis;

import com.huawei.cloudsop.genui.core.validation.ValidationIssue;
import com.huawei.cloudsop.genui.core.validation.parser.BuiltinCatalog;
import com.huawei.cloudsop.genui.core.validation.parser.Program;
import com.huawei.cloudsop.genui.core.validation.semantic.ContractCatalog;

import java.util.List;
import java.util.Map;

/**
 * 类型分析对外出口(#80 子集):一次完整 FINAL 分析,返回诊断与覆盖报告。
 *
 * <p>
 * 输入由 validation 层在同一次请求内绑定(程序、组件目录、builtin 词表、Render Data Model、入口语句),
 * 调用方不能分别拼装独立的程序与契约。无 I/O、不执行 DSL、不修改输入;同样的输入与预算产生稳定结果。
 * 类型检查不完整(UNPROVEN)不产生 {@link ValidationIssue},不单独触发修复或降级;VALID 不能解读为完整
 * 证明——完整性看 {@link CoverageReport}。
 *
 * @since 2026
 */
public final class TypeAnalysis {

    private TypeAnalysis() {
    }

    /** 分析结果:诊断(仅已证明错误)与覆盖报告。 */
    public record Result(List<ValidationIssue> issues, CoverageReport coverage) {

        public Result {
            issues = issues == null ? List.of() : List.copyOf(issues);
        }
    }

    /**
     * 对当前有效程序执行一次完整类型分析。
     *
     * @param program
     *            解析后的程序(同名 last-wins 生效口径由 Program 提供)
     * @param catalog
     *            生效契约的组件目录
     * @param builtins
     *            生效契约的 builtin 词表(与目录来自同一次契约解析)
     * @param dataModel
     *            Render Data Model 快照;{@code null} 表示无数据——静态可知部分仍检查,数据依赖部分记未证明
     * @param entryId
     *            语义分析选定的入口语句 id(可达性复用该结论)
     * @param budget
     *            分析预算;{@code null} 用默认
     * @return 诊断与覆盖报告
     */
    public static Result analyze(Program program, ContractCatalog catalog, BuiltinCatalog builtins,
            Map<String, Object> dataModel, String entryId, AnalysisBudget budget) {
        AnalysisSession.Output output = AnalysisSession
                .analyze(new AnalysisSession.Input(program, catalog, builtins, dataModel, entryId, budget));
        return new Result(output.issues(), output.coverage());
    }
}
