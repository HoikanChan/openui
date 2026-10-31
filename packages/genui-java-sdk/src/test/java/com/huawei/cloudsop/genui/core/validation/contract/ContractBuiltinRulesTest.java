/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.contract.BuiltinSpec;
import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import com.huawei.cloudsop.genui.core.contract.GenerationContractLoader;
import com.huawei.cloudsop.genui.core.validation.DefaultOpenuiLangValidator;
import com.huawei.cloudsop.genui.core.validation.ValidationIssue;
import com.huawei.cloudsop.genui.core.validation.ValidationRequest;
import com.huawei.cloudsop.genui.core.validation.ValidationResult;
import com.huawei.cloudsop.genui.core.validation.ValidationStatus;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** #81 主验收：普通 builtin 通过结构化契约完成检查与单次调用内泛型传播——全部断言经公开 validator， 不手工拼装内部分析快照。 */
class ContractBuiltinRulesTest {

    // ---- 正例：合法调用不产生 builtin 参数错误 ----

    @Test
    void acceptsAggregateAndScalarChains() {
        ValidationResult result =
                validate(
                        "root = TextContent(@String(@Sum(@Each(data.points, \"p\", p.value))))\n"
                                + "avg = TextContent(@String(@Round(@Avg(data.values), 2)))",
                        Map.of(
                                "points",
                                List.of(Map.of("value", 1), Map.of("value", 2)),
                                "values",
                                List.of(1.5, 2.5)));

        assertEquals(
                ValidationStatus.VALID, result.status(), () -> String.valueOf(result.issues()));
    }

    @Test
    void acceptsJoinSortFilterPipeline() {
        ValidationResult result =
                validate(
                        "root = TextContent(@Join(@Each(@Sort(@Filter(data.rows, \"status\", \"==\", \"active\"), \"name\"), \"r\", r.name)))",
                        Map.of(
                                "rows",
                                List.of(
                                        Map.of("name", "a", "status", "active"),
                                        Map.of("name", "b", "status", "down"))));

        assertEquals(
                ValidationStatus.VALID, result.status(), () -> String.valueOf(result.issues()));
    }

    // ---- 负例：实参模式由结构化规则检查 ----

    @Test
    void rejectsScalarIntoSequenceElementRule() {
        ValidationResult result =
                validate("root = TextContent(@String(@Sum(data.total)))", Map.of("total", 3));

        issue(result, "builtin-argument-type-mismatch");
        assertEquals(ValidationStatus.INVALID, result.status());
    }

    @Test
    void rejectsNonNumberIntoScalarRule() {
        ValidationResult result =
                validate("root = TextContent(@Abs(data.name))", Map.of("name", "Ada"));

        issue(result, "builtin-argument-type-mismatch");
    }

    @Test
    void rejectsMissingRequiredArgumentByStructuredArity() {
        ValidationResult result = validate("root = TextContent(@Join())", Map.of());

        issue(result, "builtin-argument-count-mismatch");
    }

    // ---- 泛型传播：单次调用内求解，结果类型进入下游检查 ----

    @Test
    void propagatesElementTypeThroughSortAndFilter() {
        // Sort/Filter 后元素仍是对象行；对 number 列喂 Count 必须报实参不匹配（类型事实被保留到下游）。
        ValidationResult result =
                validate(
                        "root = Table([nameCol], @Sort(@Filter(data.rows, \"status\", \"==\", \"active\"), \"name\"))\n"
                                + "nameCol = Col(\"Name\", \"name\", {cell: @Render(\"v\", TextContent(@Count(v)))})",
                        Map.of("rows", List.of(Map.of("name", "a", "status", "active"))));

        issue(result, "builtin-argument-type-mismatch");
    }

    @Test
    void lowerBoundsMergeAcrossVariadicArguments() {
        // 重复变量合并下界：任何已知实参都不能被后续未知输入盖掉——这里通过下游类型事实验证：
        // Each 的 T 来自数组元素；body 把元素喂给 number 参数，元素为 string 时必须被发现。
        ValidationResult result =
                validate(
                        "root = TextContent(@Join(@Each(data.names, \"n\", @Abs(n))))",
                        Map.of("names", List.of("a", "b")));

        issue(result, "builtin-argument-type-mismatch");
    }

    // ---- 证据行为：Sort 保留元素存在证据，Filter 不声称保留某一行 ----

    @Test
    void sortPreservesElementPresenceEvidence() {
        // 非空数据经 Sort 仍证明非空：LineChart 单点检查应看到 2 个值，不触发 insufficient-data。
        ValidationResult result =
                validate(
                        "root = LineChart(labels, [series])\n"
                                + "labels = @Each(data.points, \"p\", p.label)\n"
                                + "series = Series(\"V\", @Each(@Sort(data.points, \"value\"), \"p\", p.value))",
                        Map.of(
                                "points",
                                List.of(
                                        Map.of("label", "A", "value", 1),
                                        Map.of("label", "B", "value", 2))));

        assertFalse(
                hasCode(result, "type-chart-insufficient-data"),
                () -> String.valueOf(result.issues()));
        assertEquals(
                ValidationStatus.VALID, result.status(), () -> String.valueOf(result.issues()));
    }

    @Test
    void filterDropsElementPresenceEvidence() {
        // Filter 删除"某行必然保留"的证据（TypeOps.subset：下界降 0、清空证据），而 Sort 保留数量
        // (#77 第 6.5/7.5 节)：同一数据经 Sort 满足 LineChart 两点要求；经 Filter 不得沿用行存在证据
        // 报错，也不得静默 MATCH——数据依赖的证明缺口进入覆盖报告的 unproven 计数。
        ValidationResult filtered =
                validate(
                        "root = LineChart(labels, [series])\n"
                                + "labels = @Each(data.points, \"p\", p.label)\n"
                                + "series = Series(\"V\", @Each(@Filter(data.points, \"flag\", \"==\", true), \"p\", p.value))",
                        Map.of(
                                "points",
                                List.of(
                                        Map.of("label", "A", "value", 1, "flag", true),
                                        Map.of("label", "B", "value", 2, "flag", true))));

        assertEquals(
                ValidationStatus.VALID, filtered.status(), () -> String.valueOf(filtered.issues()));
        assertFalse(
                hasCode(filtered, "type-chart-insufficient-data"),
                () ->
                        "subset must not fabricate a proven-short-series error: "
                                + filtered.issues());
        long unproven = Long.parseLong(result_unproven(filtered));
        assertTrue(
                unproven > 0,
                () ->
                        "Filter path must surface proof gaps in coverage: "
                                + filtered.metadata().extra());
    }

    private static String result_unproven(ValidationResult result) {
        return result.metadata().extra().getOrDefault("typeChecks.unproven", "0");
    }

    // ---- 新增 builtin 只需契约与行为案例，不改 Java 分支 ----

    @Test
    void identityBuiltinWorksFromContractAlone() {
        GenerationContract base = GenerationContractLoader.loadDefault();
        List<BuiltinSpec> builtins = new ArrayList<>(base.builtins());
        // 简洁声明即可:参数与返回共用命名类型变量 T,不需要任何 Java 专用分支。
        com.huawei.cloudsop.genui.core.contract.typeexpr.TypeExpr identityParamType =
                com.huawei.cloudsop.genui.core.contract.typeexpr.TypeExprParser.parse(
                        "T", "Identity.value");
        builtins.add(
                new BuiltinSpec(
                        "Identity",
                        "Identity(value) -> value",
                        "identity",
                        false,
                        List.of(
                                new BuiltinSpec.ParamSpec(
                                        "value", "T", identityParamType, false, false)),
                        identityParamType));
        GenerationContract contract =
                new GenerationContract(
                        base.contractVersion(),
                        base.root(),
                        base.components(),
                        base.componentGroups(),
                        base.tools(),
                        base.examples(),
                        base.additionalRules(),
                        builtins,
                        base.capabilityGaps());

        // 合法：Identity 返回输入类型，数组长度事实传播到 LineChart。
        ValidationResult ok =
                validate(
                        contract,
                        "root = LineChart(labels, [series])\n"
                                + "labels = @Identity([\"A\", \"B\"])\n"
                                + "series = Series(\"V\", @Identity(data.values))",
                        Map.of("values", List.of(1, 2)));
        assertEquals(ValidationStatus.VALID, ok.status(), () -> String.valueOf(ok.issues()));

        // 负例：Identity(number) 的结果喂给 number[] 参数——代入的类型事实经下游参数检查发现。
        ValidationResult bad =
                validate(
                        contract,
                        "root = TextContent(@String(@Sum(@Identity(data.total))))",
                        Map.of("total", 3));
        issue(bad, "builtin-argument-type-mismatch");
    }

    // ---- MergeObject：有序覆盖与字段传播 ----

    @Test
    void mergeObjectMergesFieldsForDownstreamTable() {
        // 合并后的原字段与注入字段都能到达 Table 列检查：name 来自 row，vendor 来自注入对象。
        ValidationResult result =
                validate(
                        "root = Table([nameCol, vendorCol], @Each(data.rows, \"row\", @MergeObject(row, {vendor: data.vendor})))\n"
                                + "nameCol = Col(\"Name\", \"name\")\n"
                                + "vendorCol = Col(\"Vendor\", \"vendor\")",
                        Map.of("rows", List.of(Map.of("name", "leaf-1")), "vendor", "huawei"));

        assertEquals(
                ValidationStatus.VALID, result.status(), () -> String.valueOf(result.issues()));
    }

    @Test
    void mergeObjectRejectsNonObjectVariadicArgument() {
        // variadic 每个实参都检查：第三个实参是 number 也必须被发现。
        ValidationResult result =
                validate(
                        "root = TextContent(@String(@MergeObject(data.obj, data.obj, data.total)))",
                        Map.of("obj", Map.of("a", 1), "total", 3));

        issue(result, "builtin-argument-type-mismatch");
    }

    @Test
    void mergeObjectDetectsMissingInjectedColumn() {
        // 合并形状保留字段事实：列引用既不在原行也不在注入对象里的字段时必须浮现——
        // 确定缺失报 type-table-column-missing；缺乏行存在证据时按 #77 用户故事 8 记未证明，
        // 不接受静默 MATCH。
        ValidationResult result =
                validate(
                        "root = Table([vendorCol], @Each(data.rows, \"row\", @MergeObject(row, {vendor: data.vendor})))\n"
                                + "vendorCol = Col(\"Vendor\", \"missingField\")",
                        Map.of("rows", List.of(Map.of("name", "leaf-1")), "vendor", "huawei"));

        boolean reported = hasCode(result, "type-table-column-missing");
        boolean unproven =
                !"0".equals(String.valueOf(result.metadata().extra().get("typeChecks.unproven")));
        assertTrue(
                reported || unproven,
                () ->
                        "missing column must surface as error or UNPROVEN: "
                                + result.issues()
                                + " / "
                                + result.metadata().extra());
    }

    // ---- 变量求解跨调用隔离 ----

    @Test
    void typeVariablesDoNotLeakAcrossCalls() {
        // 第一个 Sort 的 T=行对象；第二个 Sum 的 number[] 独立求解——跨调用串 T 会把行对象解成 number 漏检，
        // 或把 number 解成行对象产生虚假错误。合法管线必须 VALID。
        ValidationResult result =
                validate(
                        "root = Stack([table, total])\n"
                                + "table = Table([nameCol], @Sort(data.rows, \"name\"))\n"
                                + "nameCol = Col(\"Name\", \"name\")\n"
                                + "total = TextContent(@String(@Sum(data.values)))",
                        Map.of("rows", List.of(Map.of("name", "a")), "values", List.of(1, 2)));

        assertEquals(
                ValidationStatus.VALID, result.status(), () -> String.valueOf(result.issues()));
    }

    // ---- 覆盖统计可见 ----

    @Test
    void coverageCountersSurfaceInMetadata() {
        ValidationResult result =
                validate("root = TextContent(@Sum(data.total))", Map.of("total", 3));

        // 覆盖计数由 #80 CoverageReport 承载：已证明不匹配可见；VALID 不解读为完整证明。
        assertEquals(
                "1",
                result.metadata().extra().get("typeChecks.mismatched"),
                () -> String.valueOf(result.metadata().extra()));
    }

    private static ValidationResult validate(String dsl, Map<String, Object> dataModel) {
        return validate(GenerationContractLoader.loadDefault(), dsl, dataModel);
    }

    private static ValidationResult validate(
            GenerationContract contract, String dsl, Map<String, Object> dataModel) {
        return DefaultOpenuiLangValidator.INSTANCE.validate(
                ValidationRequest.builder()
                        .dsl(dsl)
                        .contract(contract)
                        .dataModel(dataModel)
                        .build());
    }

    private static boolean hasCode(ValidationResult result, String code) {
        return result.issues().stream().anyMatch(candidate -> code.equals(candidate.code()));
    }

    private static ValidationIssue issue(ValidationResult result, String code) {
        ValidationIssue issue =
                result.issues().stream()
                        .filter(candidate -> code.equals(candidate.code()))
                        .findFirst()
                        .orElse(null);
        assertNotNull(issue, () -> "missing " + code + " in " + result.issues());
        return issue;
    }
}
