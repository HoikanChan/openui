/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.contract.BuiltinSpec;
import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import com.huawei.cloudsop.genui.core.contract.GenerationContractLoader;
import com.huawei.cloudsop.genui.core.contract.typeexpr.TypeExpr;
import com.huawei.cloudsop.genui.core.contract.typeexpr.TypeExprParser;
import com.huawei.cloudsop.genui.core.validation.DefaultOpenuiLangValidator;
import com.huawei.cloudsop.genui.core.validation.ValidationIssue;
import com.huawei.cloudsop.genui.core.validation.ValidationRequest;
import com.huawei.cloudsop.genui.core.validation.ValidationResult;
import com.huawei.cloudsop.genui.core.validation.ValidationStatus;
import com.huawei.cloudsop.genui.core.validation.semantic.RepairHints;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 加强验证机制的行为回归测试:覆盖 Filter/Sort 语义校验、多类 Chart 一致性校验、字符串字面量下标缺字段、 Col.options.cell last-wins 以及契约级
 * builtin 词表提示对齐。
 *
 * @since 2026
 */
class ValidationHardeningTest {

    // ---- Filter 操作符与字段语义 ----

    @Test
    void filterRejectsUnsupportedLiteralOperator() {
        Map<String, Object> data = Map.of("rows", List.of(Map.of("status", "up")));

        for (String badOp : List.of("=", "===", "in", "includes")) {
            ValidationResult result =
                    validate(
                            "root = TextContent(@String(@Count(@Filter(data.rows, \"status\", \""
                                    + badOp
                                    + "\", \"up\"))))",
                            data);
            assertEquals(ValidationStatus.INVALID, result.status(), badOp + ": " + result.issues());
            assertTrue(
                    hasIssue(result, "builtin-argument-type-mismatch", "/args/2"),
                    badOp + ": " + result.issues());
        }
    }

    @Test
    void filterAcceptsAllSupportedOperatorsAndKeepsDynamicOperatorUnproven() {
        Map<String, Object> data = Map.of("rows", List.of(Map.of("score", 10, "name", "leaf-1")));

        for (String op : List.of("==", "!=", ">", "<", ">=", "<=", "contains")) {
            ValidationResult ok =
                    validate(
                            "root = TextContent(@String(@Count(@Filter(data.rows, \"name\", \""
                                    + op
                                    + "\", \"leaf\"))))",
                            data);
            assertEquals(ValidationStatus.VALID, ok.status(), op + ": " + ok.issues());
        }

        // 无 dataModel 时动态操作符未知,只能 UNPROVEN,不能误报。
        ValidationResult dynamic =
                DefaultOpenuiLangValidator.INSTANCE.validate(
                        ValidationRequest.builder()
                                .dsl(
                                        "root = TextContent(@String(@Count(@Filter(data.rows, \"name\", data.op, \"x\"))))")
                                .contract(GenerationContractLoader.loadDefault())
                                .externalRefs(java.util.Set.of("data"))
                                .build());
        assertEquals(
                ValidationStatus.VALID, dynamic.status(), () -> String.valueOf(dynamic.issues()));
        assertTrue(
                Long.parseLong(dynamic.metadata().extra().get("typeChecks.unproven")) > 0,
                () -> String.valueOf(dynamic.metadata().extra()));
    }

    @Test
    void filterRejectsMissingAndCompositeFieldsAndSupportsNestedScalarPaths() {
        Map<String, Object> data =
                Map.of(
                        "rows",
                        List.of(
                                Map.of(
                                        "name",
                                        "leaf-1",
                                        "items",
                                        List.of(1, 2),
                                        "meta",
                                        Map.of("score", 95))));

        ValidationResult missing =
                validate(
                        "root = TextContent(@String(@Count(@Filter(data.rows, \"cpu_usage\", \">\", 0))))",
                        data);
        assertTrue(
                hasIssue(missing, "builtin-argument-type-mismatch", "/args/1"),
                () -> String.valueOf(missing.issues()));

        ValidationResult arrayField =
                validate(
                        "root = TextContent(@String(@Count(@Filter(data.rows, \"items\", \">\", 0))))",
                        data);
        assertTrue(
                hasIssue(arrayField, "builtin-argument-type-mismatch", "/args/1"),
                () -> String.valueOf(arrayField.issues()));

        ValidationResult objectField =
                validate(
                        "root = TextContent(@String(@Count(@Filter(data.rows, \"meta\", \"!=\", null))))",
                        data);
        assertTrue(
                hasIssue(objectField, "builtin-argument-type-mismatch", "/args/1"),
                () -> String.valueOf(objectField.issues()));

        ValidationResult nestedValid =
                validate(
                        "root = TextContent(@String(@Count(@Filter(data.rows, \"meta.score\", \">\", 80))))",
                        data);
        assertEquals(
                ValidationStatus.VALID,
                nestedValid.status(),
                () -> String.valueOf(nestedValid.issues()));

        ValidationResult nestedMissing =
                validate(
                        "root = TextContent(@String(@Count(@Filter(data.rows, \"meta.unknown\", \">\", 80))))",
                        data);
        assertTrue(
                hasIssue(nestedMissing, "builtin-argument-type-mismatch", "/args/1"),
                () -> String.valueOf(nestedMissing.issues()));

        // 空字段名表示过滤标量数组元素自身,必须放行。
        ValidationResult scalarArray =
                validate(
                        "root = TextContent(@String(@Count(@Filter(data.nums, \"\", \">\", 1))))",
                        Map.of("nums", List.of(1, 2, 3)));
        assertEquals(
                ValidationStatus.VALID,
                scalarArray.status(),
                () -> String.valueOf(scalarArray.issues()));
    }

    // ---- Sort 方向与字段语义 ----

    @Test
    void sortValidatesDirectionAndFieldPresenceAndScalarShape() {
        Map<String, Object> data =
                Map.of("rows", List.of(Map.of("name", "a", "score", 1, "tags", List.of("x"))));

        ValidationResult validAsc =
                validate(
                        "root = Table([Col(\"Name\", \"name\")], @Sort(data.rows, \"score\", \"asc\"))",
                        data);
        assertEquals(
                ValidationStatus.VALID, validAsc.status(), () -> String.valueOf(validAsc.issues()));

        ValidationResult badDir =
                validate(
                        "root = Table([Col(\"Name\", \"name\")], @Sort(data.rows, \"score\", \"descending\"))",
                        data);
        assertTrue(
                hasIssue(badDir, "builtin-argument-type-mismatch", "/args/2"),
                () -> String.valueOf(badDir.issues()));

        ValidationResult badField =
                validate(
                        "root = Table([Col(\"Name\", \"name\")], @Sort(data.rows, \"missing\", \"desc\"))",
                        data);
        assertTrue(
                hasIssue(badField, "builtin-argument-type-mismatch", "/args/1"),
                () -> String.valueOf(badField.issues()));

        ValidationResult compositeField =
                validate(
                        "root = Table([Col(\"Name\", \"name\")], @Sort(data.rows, \"tags\", \"desc\"))",
                        data);
        assertTrue(
                hasIssue(compositeField, "builtin-argument-type-mismatch", "/args/1"),
                () -> String.valueOf(compositeField.issues()));
    }

    // ---- 多类 Chart 消费校验 ----

    @Test
    void areaChartChecksLengthMismatchAndMinimumTrendPoints() {
        ValidationResult lengthMismatch =
                validate(
                        "root = AreaChart([\"T1\", \"T2\", \"T3\"], [Series(\"CPU\", [10, 20])])",
                        Map.of());
        assertTrue(
                hasIssue(lengthMismatch, "type-chart-length-mismatch", "/series/0"),
                () -> String.valueOf(lengthMismatch.issues()));

        ValidationResult singlePoint =
                validate("root = AreaChart([\"T1\"], [Series(\"CPU\", [10])])", Map.of());
        assertTrue(
                hasIssue(singlePoint, "type-chart-insufficient-data", "/series/0"),
                () -> String.valueOf(singlePoint.issues()));

        ValidationResult validArea =
                validate(
                        "root = AreaChart([\"T1\", \"T2\"], [Series(\"CPU\", [10, 20])])",
                        Map.of());
        assertEquals(
                ValidationStatus.VALID,
                validArea.status(),
                () -> String.valueOf(validArea.issues()));
    }

    @Test
    void horizontalBarAndRadarChartsCheckLengthAndStringLabels() {
        ValidationResult hbarMismatch =
                validate(
                        "root = HorizontalBarChart([\"A\", \"B\"], [Series(\"V\", [1])])",
                        Map.of());
        assertTrue(
                hasIssue(hbarMismatch, "type-chart-length-mismatch", "/series/0"),
                () -> String.valueOf(hbarMismatch.issues()));

        ValidationResult radarMismatch =
                validate(
                        "root = RadarChart([\"CPU\", \"Mem\", \"Disk\"], [Series(\"S\", [1, 2])])",
                        Map.of());
        assertTrue(
                hasIssue(radarMismatch, "type-chart-length-mismatch", "/series/0"),
                () -> String.valueOf(radarMismatch.issues()));

        ValidationResult radarNumberLabels =
                validate("root = RadarChart([1, 2], [Series(\"S\", [10, 20])])", Map.of());
        assertTrue(
                hasIssue(radarNumberLabels, "type-prop-mismatch", "/labels"),
                () -> String.valueOf(radarNumberLabels.issues()));
    }

    @Test
    void pieChartChecksLabelsAndValuesLengthAlignment() {
        ValidationResult mismatch =
                validate("root = PieChart([\"Online\", \"Offline\"], [10], \"donut\")", Map.of());
        assertTrue(
                hasIssue(mismatch, "type-chart-length-mismatch", "/values"),
                () -> String.valueOf(mismatch.issues()));

        ValidationResult matched =
                validate(
                        "root = PieChart([\"Online\", \"Offline\"], [10, 2], \"donut\")", Map.of());
        assertEquals(
                ValidationStatus.VALID, matched.status(), () -> String.valueOf(matched.issues()));
    }

    // ---- 字符串字面量索引字段缺失与 cell last-wins ----

    @Test
    void stringLiteralIndexReportsMissingFieldConsistentlyWithMemberAccess() {
        Map<String, Object> data =
                Map.of("rows", List.of(Map.of("name", "a"), Map.of("name", "b", "extra", "x")));

        ValidationResult absentEverywhere =
                validate(
                        "root = Stack(@Each(data.rows, \"item\", TextContent(item[\"missing\"])))",
                        data);
        assertTrue(
                hasCode(absentEverywhere, "type-field-missing"),
                () -> String.valueOf(absentEverywhere.issues()));

        ValidationResult absentInObservedRow =
                validate(
                        "root = Stack(@Each(data.rows, \"item\", TextContent(item[\"extra\"])))",
                        data);
        assertTrue(
                hasCode(absentInObservedRow, "type-field-missing"),
                () -> String.valueOf(absentInObservedRow.issues()));

        ValidationResult presentEverywhere =
                validate(
                        "root = Stack(@Each(data.rows, \"item\", TextContent(item[\"name\"])))",
                        data);
        assertEquals(
                ValidationStatus.VALID,
                presentEverywhere.status(),
                () -> String.valueOf(presentEverywhere.issues()));
    }

    @Test
    void tableColumnOptionsCellFollowsLastWinsSemantics() {
        Map<String, Object> data = Map.of("rows", List.of(Map.of("name", "leaf-1")));

        // 后写覆盖前写:第二个 cell 合法,不能因为第一个无效 cell 误报。
        ValidationResult lastValid =
                validate(
                        "root = Table([nameCol], data.rows)\n"
                                + "nameCol = Col(\"Name\", \"name\", {cell: @Render(\"v\", TextContent(@Abs(v))), cell: @Render(\"v\", TextContent(v))})",
                        data);
        assertEquals(
                ValidationStatus.VALID,
                lastValid.status(),
                () -> String.valueOf(lastValid.issues()));

        // 后写覆盖前写:第二个 cell 有误,必须报出错误。
        ValidationResult lastInvalid =
                validate(
                        "root = Table([nameCol], data.rows)\n"
                                + "nameCol = Col(\"Name\", \"name\", {cell: @Render(\"v\", TextContent(v)), cell: @Render(\"v\", TextContent(@Abs(v)))})",
                        data);
        assertTrue(
                hasCode(lastInvalid, "builtin-argument-type-mismatch"),
                () -> String.valueOf(lastInvalid.issues()));
    }

    // ---- 契约级 builtin 词表提示对齐 ----

    @Test
    void unresolvedRefHintUsesActiveContractBuiltinsInsteadOfDefaultCatalog() {
        GenerationContract base = GenerationContractLoader.loadDefault();
        List<BuiltinSpec> customBuiltins = new ArrayList<>();
        for (BuiltinSpec spec : base.builtins()) {
            if (!"FormatNumber".equals(spec.name())) {
                customBuiltins.add(spec);
            }
        }
        TypeExpr numType = TypeExprParser.parse("number", "CustomOp.x");
        customBuiltins.add(
                new BuiltinSpec(
                        "CustomOp",
                        "CustomOp(number) -> number",
                        "custom op",
                        false,
                        List.of(new BuiltinSpec.ParamSpec("x", "number", numType, false, false)),
                        numType));
        GenerationContract customContract =
                new GenerationContract(
                        base.contractVersion(),
                        base.root(),
                        base.components(),
                        base.componentGroups(),
                        base.tools(),
                        base.examples(),
                        base.additionalRules(),
                        customBuiltins,
                        base.capabilityGaps());

        // 契约新增的 CustomOp 漏写 @ 时,应路由到缺失 @ 提示。
        ValidationResult customRef =
                validate(customContract, "root = TextContent(CustomOp)", Map.of());
        ValidationIssue customIssue =
                customRef.issues().stream()
                        .filter(i -> "unresolved-ref".equals(i.code()))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError(customRef.issues().toString()));
        assertTrue(
                RepairHints.isMissingAtHint(customIssue.hint()),
                () -> "custom contract builtin must get missing-@ hint: " + customIssue.hint());

        // 从契约移除的 FormatNumber 不应再误提示 did you mean "@FormatNumber"。
        ValidationResult removedRef =
                validate(customContract, "root = TextContent(FormatNumber)", Map.of());
        ValidationIssue removedIssue =
                removedRef.issues().stream()
                        .filter(i -> "unresolved-ref".equals(i.code()))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError(removedRef.issues().toString()));
        assertFalse(
                RepairHints.isMissingAtHint(removedIssue.hint()),
                () -> "removed builtin must not get missing-@ hint: " + removedIssue.hint());
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
        return result.issues().stream().anyMatch(issue -> code.equals(issue.code()));
    }

    private static boolean hasIssue(ValidationResult result, String code, String path) {
        return result.issues().stream()
                .anyMatch(issue -> code.equals(issue.code()) && path.equals(issue.path()));
    }
}
