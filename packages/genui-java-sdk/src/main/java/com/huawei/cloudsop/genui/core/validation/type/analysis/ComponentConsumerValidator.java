/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.type.analysis;

import com.huawei.cloudsop.genui.core.validation.parser.AstNode;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Table 与多类 Chart 组件的跨参数消费校验器:集中管理行形状、列存在性、cell 模板上下文、 图表标签/序列元素类型、长度对齐、趋势点数与重复派生值检查。
 *
 * <p>消费校验直接复用 {@link AnalysisSession} 在组件参数检查阶段已合成的实参事实，避免对 {@code rows}、{@code labels}、{@code
 * values} 重复求值；图表的类型能力由表驱动规格声明，覆盖 {@code LineChart}、{@code AreaChart}、{@code BarChart}、{@code
 * HorizontalBarChart}、{@code RadarChart} 与 {@code PieChart}。
 *
 * @since 2026
 */
final class ComponentConsumerValidator {

    /** 带 cell 模板的 Table 列形如 {@code Col(title, field, {cell: @Render(...)})}。 */
    private static final int COLUMN_OPTIONS_ARG_INDEX = 2;

    private static final int COLUMN_MIN_ARGS_FOR_OPTIONS = COLUMN_OPTIONS_ARG_INDEX + 1;
    private static final int MIN_TREND_POINTS = 2;

    private static final Expectation STRING_OR_NUMBER_LABELS =
            new Expectation.AnyOf(
                    List.of(
                            new Expectation.Accept(
                                    Types.sequence(
                                            Types.scalar(Type.ScalarKind.STRING),
                                            0L,
                                            Type.Sequence.UNBOUNDED)),
                            new Expectation.Accept(
                                    Types.sequence(
                                            Types.scalar(Type.ScalarKind.NUMBER),
                                            0L,
                                            Type.Sequence.UNBOUNDED))));

    private static final Expectation STRING_ONLY_LABELS =
            new Expectation.Accept(
                    Types.sequence(
                            Types.scalar(Type.ScalarKind.STRING), 0L, Type.Sequence.UNBOUNDED));

    private static final Expectation SERIES_VALUES_EXPECTATION =
            new Expectation.SequenceOf(
                    new Expectation.Accept(Types.scalar(Type.ScalarKind.NUMBER)));

    private static final Map<String, SeriesChartSpec> SERIES_CHARTS =
            Map.of(
                    "LineChart", new SeriesChartSpec(STRING_OR_NUMBER_LABELS, MIN_TREND_POINTS),
                    "AreaChart", new SeriesChartSpec(STRING_OR_NUMBER_LABELS, MIN_TREND_POINTS),
                    "BarChart", new SeriesChartSpec(STRING_OR_NUMBER_LABELS, 0),
                    "HorizontalBarChart", new SeriesChartSpec(STRING_ONLY_LABELS, 0),
                    "RadarChart", new SeriesChartSpec(STRING_ONLY_LABELS, 0));

    private final AnalysisSession session;

    ComponentConsumerValidator(AnalysisSession session) {
        this.session = session;
    }

    /**
     * 按组件名分派消费校验。
     *
     * @param comp 组件调用节点
     * @param argFacts 已按顺序合成的位置实参事实
     * @param statementId 语句 id
     * @param env 当前词法环境
     * @param resolving 循环引用追踪集合
     */
    void validate(
            AstNode.Comp comp,
            List<Fact> argFacts,
            String statementId,
            AnalysisSession.LexicalEnvironment env,
            Set<String> resolving) {
        if ("Table".equals(comp.name())) {
            validateTable(comp, argFacts, statementId, env, resolving);
            return;
        }
        SeriesChartSpec seriesSpec = SERIES_CHARTS.get(comp.name());
        if (seriesSpec != null) {
            validateSeriesChart(comp, argFacts, seriesSpec, statementId, env, resolving);
            return;
        }
        if ("PieChart".equals(comp.name())) {
            validatePieChart(comp, argFacts, statementId);
        }
    }

    // ───────────────────────────────────────────────────────────────────────
    // Table 消费校验
    // ───────────────────────────────────────────────────────────────────────

    private void validateTable(
            AstNode.Comp table,
            List<Fact> argFacts,
            String statementId,
            AnalysisSession.LexicalEnvironment env,
            Set<String> resolving) {
        if (table.args().size() < 2 || argFacts.size() < 2) {
            return;
        }
        Fact rows = argFacts.get(1);
        if (rows.hasUpstreamErrors()) {
            return;
        }
        Fact rowElement = session.ops().element(rows);
        boolean noRowEvidence = checkTableRowShape(rows, rowElement, statementId);
        TableColumnContext columnContext =
                new TableColumnContext(
                        rowElement, rows, statementId, env, resolving, noRowEvidence);
        // 字段集合为空不是退出条件:逐列独立检查,某一列未证明不影响其他列。
        for (AstNode column : session.arrayElements(table.args().get(0))) {
            AstNode resolvedColumn = session.resolveNode(column, new LinkedHashSet<>());
            if (!(resolvedColumn instanceof AstNode.Comp call)
                    || !"Col".equals(call.name())
                    || call.args().size() < 2) {
                continue;
            }
            AstNode resolvedField = session.resolveNode(call.args().get(1), new LinkedHashSet<>());
            if (!(resolvedField instanceof AstNode.Str field)) {
                continue;
            }
            String columnStatementId = session.statementIdOfRef(column, statementId);
            checkTableColumn(call, field.v(), columnStatementId, columnContext);
        }
    }

    /**
     * 检查 Table 行值形状,并在确定不是扁平对象数组时报错。
     *
     * @param rows 行值事实
     * @param rowElement 行元素事实
     * @param statementId 语句 id
     * @return 行元素是否缺少可用证据(空数组、元素 Never 或证据丢失)
     */
    private boolean checkTableRowShape(Fact rows, Fact rowElement, String statementId) {
        boolean noRowEvidence =
                rowElement.type() instanceof Type.Never
                        || rowElement.losses().contains(Fact.Loss.NO_ELEMENT_EVIDENCE)
                        || rowElement.losses().contains(Fact.Loss.UNKNOWN_INPUT);
        boolean rowShapeKnown = AnalysisSession.isObjectShape(rowElement.type());
        if (!rowShapeKnown
                && !(rowElement.type() instanceof Type.Unknown)
                && !(rowElement.type() instanceof Type.Never)
                && !rowElement.losses().contains(Fact.Loss.UNKNOWN_INPUT)) {
            session.emit(
                    "type-table-row-shape-mismatch",
                    "Table rows must be a flat object array, but received "
                            + rows.type().displayName()
                            + ".",
                    statementId,
                    "Table",
                    "/rows",
                    "Flatten nested @Each results before passing them to Table.");
        }
        return noRowEvidence;
    }

    private void checkTableColumn(
            AstNode.Comp column,
            String field,
            String columnStatementId,
            TableColumnContext context) {
        Checks.CheckResult existence = Checks.checkColumnField(context.rowElement(), field);
        boolean absentFromShape =
                isAbsentFromAllClosedRows(context.rows(), context.rowElement(), field);
        if (existence.verdict() == Checks.Verdict.MISMATCH
                && context.noRowEvidence()
                && !absentFromShape) {
            existence =
                    new Checks.CheckResult(
                            Checks.Verdict.UNPROVEN,
                            Set.of(Fact.Loss.NO_ELEMENT_EVIDENCE),
                            "no retained row counterexample");
        }
        session.recordCoverage(
                new TypeAnalysisReport.Obligation(
                        context.tableStatementId(),
                        "Table.columns[" + field + "]",
                        "presentInAnyRow",
                        "/components/Table/columns/" + field),
                existence);
        if (existence.verdict() == Checks.Verdict.MISMATCH) {
            session.emit(
                    "type-table-column-missing",
                    "Table column field \"" + field + "\" " + existence.detail() + ".",
                    context.tableStatementId(),
                    "Table",
                    "/columns/" + field,
                    "Remove this column from the Table if its metric/attribute is absent from the data (or replace the Table columns with Col definitions matching the actual fields in the rows); never bind a column title to a semantically different field or replace Col with ExtraCol.");
        }
        if (context.rowElement().type() instanceof Type.Never) {
            return;
        }
        Fact cellFact = session.ops().readField(context.rowElement(), field);
        validateCellTemplate(column, field, cellFact, columnStatementId, context);
    }

    private static boolean isAbsentFromAllClosedRows(Fact rows, Fact rowElement, String field) {
        return AnalysisSession.isObjectShape(rowElement.type())
                && TypeOps.flatten(rows.type()).stream()
                        .noneMatch(
                                branch ->
                                        branch instanceof Type.Sequence sequence
                                                && sequence.maxLength() == 0)
                && TypeOps.flatten(rowElement.type()).stream()
                        .allMatch(
                                branch -> {
                                    Type.RecordType record = (Type.RecordType) branch;
                                    return record.extra().isClosed()
                                            && !record.fields().containsKey(field);
                                });
    }

    private void validateCellTemplate(
            AstNode.Comp column,
            String field,
            Fact cellFact,
            String columnStatementId,
            TableColumnContext context) {
        if (column.args().size() < COLUMN_MIN_ARGS_FOR_OPTIONS
                || !(column.args().get(COLUMN_OPTIONS_ARG_INDEX) instanceof AstNode.Obj options)) {
            return;
        }
        // 对象字面量重名键遵循 last-wins 语义，取最后一个 "cell" 条目。
        AstNode cell = null;
        for (AstNode.Obj.Entry entry : options.entries()) {
            if ("cell".equals(entry.key())) {
                cell = entry.value();
            }
        }
        session.builtinAnalysis()
                .checkCellTemplate(
                        cell,
                        cellFact,
                        context.rowElement(),
                        new BuiltinAnalysis.AnalysisContext(
                                session,
                                columnStatementId,
                                context.env(),
                                context.resolving(),
                                "Table.cell[" + field + "]",
                                "/columns/" + field + "/cell"));
    }

    // ───────────────────────────────────────────────────────────────────────
    // Chart 消费校验
    // ───────────────────────────────────────────────────────────────────────

    private void validateSeriesChart(
            AstNode.Comp chart,
            List<Fact> argFacts,
            SeriesChartSpec spec,
            String statementId,
            AnalysisSession.LexicalEnvironment env,
            Set<String> resolving) {
        if (chart.args().size() < 2 || argFacts.size() < 2) {
            return;
        }
        AstNode labelsNode = chart.args().get(0);
        Fact labels = argFacts.get(0);
        checkChartElement(
                chart,
                "labels",
                labelsNode,
                labels,
                spec.labelsExpectation(),
                "/labels",
                statementId);
        long[] labelRange = session.lengthRangeOf(labels);
        int seriesIndex = 0;
        for (AstNode seriesNode : session.arrayElements(chart.args().get(1))) {
            AstNode resolvedSeries = session.resolveNode(seriesNode, new LinkedHashSet<>());
            if (resolvedSeries instanceof AstNode.Comp series
                    && "Series".equals(series.name())
                    && series.args().size() >= 2) {
                validateSingleSeries(
                        chart,
                        spec,
                        series,
                        seriesIndex,
                        labelRange,
                        labels,
                        new SeriesCheckContext(statementId, env, resolving));
            }
            seriesIndex++;
        }
    }

    private void validateSingleSeries(
            AstNode.Comp chart,
            SeriesChartSpec spec,
            AstNode.Comp series,
            int seriesIndex,
            long[] labelRange,
            Fact labels,
            SeriesCheckContext context) {
        AstNode valuesNode = series.args().get(1);
        Fact values =
                session.synth(
                        valuesNode, context.statementId(), context.env(), context.resolving());
        checkChartElement(
                chart,
                "series[" + seriesIndex + "].values",
                valuesNode,
                values,
                SERIES_VALUES_EXPECTATION,
                "/series/" + seriesIndex + "/values",
                context.statementId());
        long[] valueRange = session.lengthRangeOf(values);
        checkChartLengthRelation(
                chart,
                labelRange,
                labels,
                valueRange,
                values,
                new LengthTarget(
                        chart.name() + ".series[" + seriesIndex + "].length",
                        "/components/" + chart.name() + "/series",
                        "/series/" + seriesIndex,
                        "series " + seriesIndex + " values"),
                context.statementId());
        reportInsufficientTrendData(
                chart, spec, seriesIndex, valueRange, values, context.statementId());
        validateDuplicateSeriesValues(chart, series, seriesIndex, context.statementId());
    }

    private void validatePieChart(AstNode.Comp chart, List<Fact> argFacts, String statementId) {
        if (chart.args().size() < 2 || argFacts.size() < 2) {
            return;
        }
        Fact labels = argFacts.get(0);
        Fact values = argFacts.get(1);
        if (labels.hasUpstreamErrors() || values.hasUpstreamErrors()) {
            return;
        }
        long[] labelRange = session.lengthRangeOf(labels);
        long[] valueRange = session.lengthRangeOf(values);
        checkChartLengthRelation(
                chart,
                labelRange,
                labels,
                valueRange,
                values,
                new LengthTarget(
                        "PieChart.values.length",
                        "/components/PieChart/values",
                        "/values",
                        "values"),
                statementId);
    }

    private void reportInsufficientTrendData(
            AstNode.Comp chart,
            SeriesChartSpec spec,
            int seriesIndex,
            long[] valueRange,
            Fact values,
            String statementId) {
        if (spec.minSeriesPoints() <= 0
                || valueRange == null
                || valueRange[1] >= spec.minSeriesPoints()
                || values.hasUpstreamErrors()) {
            return;
        }
        session.emit(
                "type-chart-insufficient-data",
                chart.name()
                        + " series "
                        + seriesIndex
                        + " has only "
                        + valueRange[1]
                        + " point(s), so it cannot draw a trend line.",
                statementId,
                chart.name(),
                "/series/" + seriesIndex,
                "Provide at least two ordered points for a trend line.");
    }

    private void checkChartElement(
            AstNode.Comp chart,
            String requirement,
            AstNode node,
            Fact fact,
            Expectation expectation,
            String usagePath,
            String statementId) {
        Optional<DataPath> path = session.dataPathOf(node);
        String checkedPath = path.map(DataPath::source).orElse(null);
        Checks.CheckResult result =
                session.checkObligation(
                        new AnalysisSession.ObligationTarget(
                                statementId,
                                chart.name() + "." + requirement,
                                "chartElement",
                                "/components/" + chart.name() + "/properties/" + requirement),
                        fact,
                        expectation,
                        checkedPath);
        if (result.verdict() == Checks.Verdict.MISMATCH) {
            session.emit(
                    "type-prop-mismatch",
                    chart.name()
                            + "."
                            + requirement
                            + " expects "
                            + AnalysisSession.displayExpectation(expectation)
                            + ", but received "
                            + fact.type().displayName()
                            + ".",
                    statementId,
                    chart.name(),
                    usagePath,
                    "Provide values compatible with "
                            + AnalysisSession.displayExpectation(expectation)
                            + ".");
        }
    }

    private void checkChartLengthRelation(
            AstNode.Comp chart,
            long[] labelRange,
            Fact labels,
            long[] valueRange,
            Fact values,
            LengthTarget target,
            String statementId) {
        Checks.CheckResult result = chartLengthResult(labelRange, labels, valueRange, values);
        session.recordCoverage(
                new TypeAnalysisReport.Obligation(
                        statementId,
                        target.useSite(),
                        "chartLengthRelation",
                        target.contractPath()),
                result);
        if (result.verdict() == Checks.Verdict.MISMATCH) {
            session.emit(
                    "type-chart-length-mismatch",
                    chart.name()
                            + " labels length ["
                            + labelRange[0]
                            + ".."
                            + labelRange[1]
                            + "] cannot match "
                            + target.subject()
                            + " length ["
                            + valueRange[0]
                            + ".."
                            + valueRange[1]
                            + "].",
                    statementId,
                    chart.name(),
                    target.issuePath(),
                    "Provide one value for every chart label.");
        }
    }

    private static Checks.CheckResult chartLengthResult(
            long[] labelRange, Fact labels, long[] valueRange, Fact values) {
        if (labelRange == null || valueRange == null) {
            return Checks.CheckResult.unproven(
                    Set.of(Fact.Loss.INSUFFICIENT_EVIDENCE), "chart length cannot be proven");
        }
        if (labelRange[0] > valueRange[1] || valueRange[0] > labelRange[1]) {
            return Checks.CheckResult.mismatch(
                    "labels length ["
                            + labelRange[0]
                            + ".."
                            + labelRange[1]
                            + "] cannot match series values length ["
                            + valueRange[0]
                            + ".."
                            + valueRange[1]
                            + "]");
        }
        if (provenSameLength(labelRange, labels, valueRange, values)) {
            return Checks.CheckResult.match();
        }
        return Checks.CheckResult.unproven(
                Set.of(Fact.Loss.INSUFFICIENT_EVIDENCE),
                "overlapping length ranges do not prove equal length");
    }

    /**
     * 精确长度相同,或两侧事实携带同一组长度来源(Each/Sort 已把输入来源传递到结果),才证明长度相等。
     *
     * @param labelRange 标签长度区间
     * @param labels 标签事实
     * @param valueRange 值序列长度区间
     * @param values 值序列事实
     * @return 两侧长度可证明相等时返回 true
     */
    static boolean provenSameLength(
            long[] labelRange, Fact labels, long[] valueRange, Fact values) {
        if (labelRange[0] == labelRange[1] && valueRange[0] == valueRange[1]) {
            return labelRange[0] == valueRange[0];
        }
        Set<String> labelSources = labels.lengthSourceIdentities();
        return !labelSources.isEmpty() && labelSources.equals(values.lengthSourceIdentities());
    }

    private void validateDuplicateSeriesValues(
            AstNode.Comp chart, AstNode.Comp series, int seriesIndex, String statementId) {
        List<AstNode> values = session.arrayElements(series.args().get(1));
        Set<AstNode> derivedValues = new LinkedHashSet<>();
        for (AstNode value : values) {
            if (!(value instanceof AstNode.Ref)) {
                continue;
            }
            AstNode resolved = session.resolveNode(value, new LinkedHashSet<>());
            if (isDerivedExpression(resolved) && !derivedValues.add(resolved)) {
                session.emit(
                        "type-chart-duplicate-series-value",
                        chart.name()
                                + " series "
                                + seriesIndex
                                + " repeats the same derived expression for multiple categories.",
                        statementId,
                        chart.name(),
                        "/series/" + seriesIndex,
                        "Use a distinct data expression for each category or remove the duplicate category.");
                return;
            }
        }
    }

    private static boolean isDerivedExpression(AstNode node) {
        return node instanceof AstNode.Comp
                || node instanceof AstNode.Member
                || node instanceof AstNode.Index
                || node instanceof AstNode.BinOp
                || node instanceof AstNode.UnaryOp
                || node instanceof AstNode.Ternary;
    }

    /**
     * 多序列图表的消费检查规格。
     *
     * @param labelsExpectation 标签数组允许的元素类型要求
     * @param minSeriesPoints 序列最少所需点数;0 表示不限制最小点数
     * @since 2026
     */
    private record SeriesChartSpec(Expectation labelsExpectation, int minSeriesPoints) {}

    /**
     * 单张 Table 的逐列检查上下文。
     *
     * @param rowElement 行元素事实
     * @param rows 行集合事实
     * @param tableStatementId Table 所在语句 id
     * @param env 当前词法环境
     * @param resolving 循环引用追踪集合
     * @param noRowEvidence 行元素是否缺少可用证据
     * @since 2026
     */
    private record TableColumnContext(
            Fact rowElement,
            Fact rows,
            String tableStatementId,
            AnalysisSession.LexicalEnvironment env,
            Set<String> resolving,
            boolean noRowEvidence) {}

    /**
     * 图表长度对齐检查的覆盖与诊断目标。
     *
     * @param useSite 覆盖报告中的使用位置标识
     * @param contractPath 契约路径
     * @param issuePath 诊断定位路径
     * @param subject 诊断文案中的被比较对象描述
     * @since 2026
     */
    private record LengthTarget(
            String useSite, String contractPath, String issuePath, String subject) {}

    /**
     * 单条图表序列校验所需的语句与作用域上下文。
     *
     * @param statementId 语句 id
     * @param env 当前词法环境
     * @param resolving 循环引用追踪集合
     * @since 2026
     */
    private record SeriesCheckContext(
            String statementId, AnalysisSession.LexicalEnvironment env, Set<String> resolving) {}
}
