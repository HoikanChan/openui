/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.contract.GenerationContractLoader;
import com.huawei.cloudsop.genui.core.validation.DefaultOpenuiLangValidator;
import com.huawei.cloudsop.genui.core.validation.ValidationIssue;
import com.huawei.cloudsop.genui.core.validation.ValidationMode;
import com.huawei.cloudsop.genui.core.validation.ValidationRequest;
import com.huawei.cloudsop.genui.core.validation.ValidationResult;
import com.huawei.cloudsop.genui.core.validation.ValidationStatus;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * #86 主验收：数组层级、投影、Flatten、First/Last 与 Chart 检查——全部断言经公开 validator，不手工拼装内部快照。
 *
 * <p>
 * 语义依据：#77 §7.2/§7.5/§10.5/§11.1 与 #79 运行时语义矩阵。空 First/Last、缺字段投影等未定精确值只记为
 * UNPROVEN（未证明），不猜 null/undefined，也不伪装成确定通过。
 */
class ArrayPipelineChartValidationTest {

    // ---- 投影层级与缺失/null 事实 ----

    @Test
    void projectionKeepsNestedLevelsAndNeedsFlattenForTable() {
        Map<String, Object> data = Map.of("groups",
                List.of(Map.of("items", List.of(Map.of("name", "a"))),
                        Map.of("items", List.of(Map.of("name", "b")))));

        // data.groups.items 是 items[][]：Table 行不是对象，必须显式 @Flatten 一层。
        ValidationResult nested = validate("root = Table([nameCol], data.groups.items)\n"
                + "nameCol = Col(\"Name\", \"name\")", data);
        issue(nested, "type-table-row-shape-mismatch");

        ValidationResult flattened = validate("root = Table([nameCol], @Flatten(data.groups.items))\n"
                + "nameCol = Col(\"Name\", \"name\")", data);
        assertEquals(ValidationStatus.VALID, flattened.status(), () -> String.valueOf(flattened.issues()));
        assertFalse(hasCode(flattened, "type-table-row-shape-mismatch"),
                () -> "flattened rows must be object rows: " + flattened.issues());
    }

    @Test
    void explicitNullInProjectionIsKeptAsPossibleValue() {
        // 显式 null 不是缺失：投影结果保留 null 分支，所以 number[] 要求不能被静默证明通过。
        Map<String, Object> data = Map.of("users", List.of(row("score", 1), nullScoreRow(), row("score", 2)));

        ValidationResult result = validate("root = TextContent(@String(@Sum(data.users.score)))", data);

        issue(result, "builtin-argument-type-mismatch");
    }

    @Test
    void missingFieldBranchCannotClaimCompletePass() {
        // 缺失行的运行时处理未定义：保留存在性证据并记 UNKNOWN_INPUT，结论只能 UNPROVEN，不能静默 MATCH。
        Map<String, Object> data = Map.of("users", List.of(row("score", 1), row("other", 2)));

        ValidationResult result = validate("root = TextContent(@String(@Sum(data.users.score)))", data);

        assertEquals(ValidationStatus.VALID, result.status(), () -> String.valueOf(result.issues()));
        assertFalse(hasCode(result, "builtin-argument-type-mismatch"),
                () -> "missing branch is undefined, not a proven mismatch: " + result.issues());
        assertTrue(unprovenReasons(result).contains("UNKNOWN_INPUT="),
                () -> "missing field branch must stay unproven: " + result.metadata().extra());
    }

    @Test
    void emptyArrayAndKnownElementArrayStayDistinct() {
        Map<String, Object> data = emptyAwareData();

        // 空数组：没有可违反的元素，不能凭空报元素冲突；空集上的要求可以完整证明。
        ValidationResult empty = validate("root = TextContent(@String(@Sum(data.emptyRows)))", data);
        assertEquals(ValidationStatus.VALID, empty.status(), () -> String.valueOf(empty.issues()));
        assertFalse(hasCode(empty, "builtin-argument-type-mismatch"),
                () -> "empty array must not fabricate element conflicts: " + empty.issues());
        assertEquals("0", unproven(empty), () -> "empty array cannot hide a proof gap: " + empty.metadata().extra());

        // 元素类型已知为 object：不能因为“数组长度未知”就当成空数组放行。
        ValidationResult known = validate("root = TextContent(@String(@Sum(data.loose)))", data);
        issue(known, "builtin-argument-type-mismatch");
    }

    // ---- Flatten ----

    @Test
    void flattenRemovesExactlyOneLevel() {
        // 每个 group 的 items 是 object[][]，投影后 groups.items 是三层；一次 Flatten 只去掉一层。
        Map<String, Object> data = Map.of("groups",
                List.of(Map.of("items", List.of(List.of(Map.of("name", "a")))),
                        Map.of("items", List.of(List.of(Map.of("name", "b"))))));

        ValidationResult once = validate("root = Table([nameCol], @Flatten(data.groups.items))\n"
                + "nameCol = Col(\"Name\", \"name\")", data);
        issue(once, "type-table-row-shape-mismatch");

        ValidationResult twice = validate(
                "root = Table([nameCol], @Flatten(@Flatten(data.groups.items)))\n"
                        + "nameCol = Col(\"Name\", \"name\")",
                data);
        assertEquals(ValidationStatus.VALID, twice.status(), () -> String.valueOf(twice.issues()));
        assertFalse(hasCode(twice, "type-table-row-shape-mismatch"),
                () -> "two flatten levels must produce object rows: " + twice.issues());
    }

    @Test
    void flattenRejectsKnownMixedElementsByArrayContract() {
        ValidationResult result = validate("root = TextContent(@String(@Count(@Flatten([[1], 2]))))", Map.of());

        issue(result, "builtin-argument-type-mismatch");
    }

    @Test
    void flattenUnknownInputStaysUnknownInsteadOfEmptyArray() {
        // Filter 后元素存在未证明 -> First 为 Unknown；未知输入不能误推成空数组（那会让 Sum 静默通过）。
        Map<String, Object> data = Map.of("rows", List.of(row("on", true, "items", List.of(List.of(1)))));

        ValidationResult result = validate(
                "root = TextContent(@String(@Sum(@Flatten(@First(@Filter(data.rows, \"on\", \"==\", true)).items))))",
                data);

        assertEquals(ValidationStatus.VALID, result.status(), () -> String.valueOf(result.issues()));
        assertFalse(hasCode(result, "builtin-argument-type-mismatch"), () -> String.valueOf(result.issues()));
        assertTrue(unprovenReasons(result).contains("UNKNOWN_INPUT="),
                () -> "unknown flatten input must stay unproven: " + result.metadata().extra());
    }

    // ---- First / Last ----

    @Test
    void firstOnEmptyArrayIsUnknownWithReasonNotNever() {
        // 空数组的实际返回值未定：不能返回 Never（Never 会让任意要求都“通过”），也不补 null。
        ValidationResult result = validate("root = TextContent(@String(@Abs(@First([]))))", Map.of());

        assertEquals(ValidationStatus.VALID, result.status(), () -> String.valueOf(result.issues()));
        assertFalse(hasCode(result, "builtin-argument-type-mismatch"), () -> String.valueOf(result.issues()));
        assertTrue(unprovenReasons(result).contains("NO_ELEMENT_EVIDENCE="),
                () -> "empty selection must carry the empty-result gap: " + result.metadata().extra());
    }

    @Test
    void firstOnNonEmptyLiteralKeepsElementType() {
        ValidationResult result = validate("root = TextContent(@String(@Abs(@First([1, 2]))))", Map.of());

        assertEquals(ValidationStatus.VALID, result.status(), () -> String.valueOf(result.issues()));
        assertFalse(unprovenReasons(result).contains("NO_ELEMENT_EVIDENCE="),
                () -> "non-empty literal selection must keep the element type: " + result.metadata().extra());
    }

    @Test
    void firstOnPossiblyEmptyInputCoversTheUnknownOutcome() {
        // 输入含可能为空的分支（Filter 下界为 0）时同样必须覆盖空结果。
        Map<String, Object> data = Map.of("rows", List.of(row("on", true)));

        ValidationResult result = validate(
                "root = TextContent(@First(@Filter(data.rows, \"on\", \"==\", true)).value)", data);

        assertEquals(ValidationStatus.VALID, result.status(), () -> String.valueOf(result.issues()));
        assertTrue(unprovenReasons(result).contains("NO_ELEMENT_EVIDENCE="),
                () -> "possibly empty input must keep the unknown outcome: " + result.metadata().extra());
    }

    @Test
    void firstOnObservedNonEmptyDataPreservesElementType() {
        Map<String, Object> data = Map.of("rows", List.of(row("name", "Ada")));

        ValidationResult result = validate("root = CardHeader(@First(data.rows).name)", data);

        assertEquals(ValidationStatus.VALID, result.status(), () -> String.valueOf(result.issues()));
        assertFalse(hasCode(result, "type-field-missing"), () -> String.valueOf(result.issues()));
    }

    // ---- Chart：元素与长度分开检查 ----

    @Test
    void chartChecksElementRequirementSeparatelyFromLength() {
        // 元素类型错误、长度一致：只报元素冲突，不报长度。
        ValidationResult result = validate("root = BarChart([\"A\", \"B\"], [Series(\"V\", [\"x\", \"y\"])])",
                Map.of());

        issueAt(result, "type-prop-mismatch", "/series/0/values");
        assertFalse(hasCode(result, "type-chart-length-mismatch"), () -> String.valueOf(result.issues()));
    }

    @Test
    void chartChecksLengthSeparatelyFromElementRequirement() {
        // 长度冲突、元素类型正确：只报长度。
        ValidationResult result = validate("root = BarChart([\"A\", \"B\"], [Series(\"V\", data.values)])",
                Map.of("values", List.of(1)));

        issue(result, "type-chart-length-mismatch");
        assertFalse(hasElementIssueAt(result, "/series/0/values"), () -> String.valueOf(result.issues()));
    }

    @Test
    void chartElementIsStillCheckedWhenLengthCannotBeProven() {
        // 数量不可证明（缺失行）不等于类型未知：元素冲突仍要检出。
        Map<String, Object> data = Map.of("rows",
                List.of(row("v", 1), row("v", "x"), row("other", true)));

        ValidationResult result = validate(
                "root = BarChart([\"A\", \"B\", \"C\"], [Series(\"V\", data.rows.v)])", data);

        issueAt(result, "type-prop-mismatch", "/series/0/values");
        assertFalse(hasCode(result, "type-chart-length-mismatch"),
                () -> "unknown length must not become a length error: " + result.issues());
    }

    @Test
    void identicalNonExactIntervalsDoNotProveEqualLength() {
        // 两侧区间都是 [0..*]，但来源不同：不能证明相等，也不能报长度错误。
        Map<String, Object> data = Map.of("rows", List.of(row("on", true, "label", "A", "value", 1)));

        ValidationResult result = validate("root = BarChart(labels, [series])\n"
                + "active = @Filter(data.rows, \"on\", \"==\", true)\n"
                + "inactive = @Filter(data.rows, \"on\", \"==\", false)\n"
                + "labels = @Each(active, \"r\", r.label)\n"
                + "series = Series(\"V\", @Each(inactive, \"r\", r.value))", data);

        assertEquals(ValidationStatus.VALID, result.status(), () -> String.valueOf(result.issues()));
        assertFalse(hasCode(result, "type-chart-length-mismatch"),
                () -> "identical non-exact intervals must not prove inequality: " + result.issues());
        assertTrue(unprovenReasons(result).contains("INSUFFICIENT_EVIDENCE="),
                () -> "length equality without a shared source stays unproven: " + result.metadata().extra());
    }

    @Test
    void sameLengthPreservingSourceDoesNotReportLengthMismatch() {
        // 两侧都从同一个符号经 @Each 派生：Each 保留输入数量，长度相等。
        Map<String, Object> data = Map.of("rows", List.of(row("on", true, "label", "A", "value", 1)));

        ValidationResult result = validate("root = BarChart(labels, [series])\n"
                + "rows = @Filter(data.rows, \"on\", \"==\", true)\n"
                + "labels = @Each(rows, \"r\", r.label)\n"
                + "series = Series(\"V\", @Each(rows, \"r\", r.value))", data);

        assertEquals(ValidationStatus.VALID, result.status(), () -> String.valueOf(result.issues()));
        assertFalse(hasCode(result, "type-chart-length-mismatch"),
                () -> "shared length-preserving source must not report a mismatch: " + result.issues());
    }

    @Test
    void exactEqualLengthsPassChartLengthRelation() {
        Map<String, Object> data = Map.of("values", List.of(1, 2));

        ValidationResult result = validate("root = BarChart([\"A\", \"B\"], [Series(\"V\", data.values)])", data);

        assertEquals(ValidationStatus.VALID, result.status(), () -> String.valueOf(result.issues()));
        assertFalse(hasCode(result, "type-chart-length-mismatch"), () -> String.valueOf(result.issues()));
    }

    @Test
    void keepsDuplicateDerivedSeriesValueCheck() {
        Map<String, Object> data = Map.of("rows", List.of(row("status", "active")));

        ValidationResult result = validate("root = BarChart([\"UP/UP\", \"UP/DOWN\"], [Series(\"Count\", [upUp, upDown])])\n"
                + "upUp = @Count(@Filter(data.rows, \"status\", \"==\", \"active\"))\n"
                + "upDown = @Count(@Filter(data.rows, \"status\", \"==\", \"active\"))", data);

        issue(result, "type-chart-duplicate-series-value");
    }

    @Test
    void flattenThenEachThenTablePipelineIsAccepted() {
        Map<String, Object> data = Map.of("groups",
                List.of(Map.of("items", List.of(row("name", "a", "score", 1))),
                        Map.of("items", List.of(row("name", "b", "score", 2)))));

        ValidationResult result = validate("root = Table([nameCol, scoreCol], "
                + "@Each(@Flatten(data.groups.items), \"item\", item))\n"
                + "nameCol = Col(\"Name\", \"name\")\n" + "scoreCol = Col(\"Score\", \"score\")", data);

        assertEquals(ValidationStatus.VALID, result.status(), () -> String.valueOf(result.issues()));
        assertFalse(hasCode(result, "type-table-column-missing"), () -> String.valueOf(result.issues()));
        assertFalse(hasCode(result, "type-table-row-shape-mismatch"), () -> String.valueOf(result.issues()));
    }

    // ---- 索引与数组构造使用同一事实模型 ----

    @Test
    void literalIndexOutOfRangeIsNotANormalElement() {
        ValidationResult result = validate("root = TextContent(@String(@Abs([1, 2][5])))", Map.of());

        assertEquals(ValidationStatus.VALID, result.status(), () -> String.valueOf(result.issues()));
        assertFalse(hasCode(result, "builtin-argument-type-mismatch"),
                () -> "out-of-range index must not masquerade as an element: " + result.issues());
        assertTrue(unprovenReasons(result).contains("NO_ELEMENT_EVIDENCE="),
                () -> "out-of-range index must stay unproven: " + result.metadata().extra());
    }

    @Test
    void literalIndexInRangeKeepsElementType() {
        ValidationResult result = validate("root = TextContent(@String(@Abs([1, 2][0])))", Map.of());

        assertEquals(ValidationStatus.VALID, result.status(), () -> String.valueOf(result.issues()));
        assertFalse(unprovenReasons(result).contains("NO_ELEMENT_EVIDENCE="),
                () -> "in-range index keeps the element type: " + result.metadata().extra());
    }

    @Test
    void dynamicStringIndexUsesTheFieldValueUnion() {
        // 动态键不是“确定缺失”：类型上界是全部字段值的并集，string 字段不能被隐藏成 Never。
        Map<String, Object> data = Map.of("obj", Map.of("a", "x", "b", "y"), "key", "a");

        ValidationResult result = validate("root = TextContent(@String(@Abs(data.obj[@String(data.key)])))", data);

        issue(result, "builtin-argument-type-mismatch");
    }

    // ---- helpers ----

    private static ValidationResult validate(String dsl, Map<String, Object> dataModel) {
        return DefaultOpenuiLangValidator.INSTANCE
                .validate(ValidationRequest.builder().dsl(dsl).contract(GenerationContractLoader.loadDefault())
                        .dataModel(dataModel).mode(ValidationMode.FINAL).build());
    }

    /** 构造允许显式 null 的行。 */
    private static Map<String, Object> row(String key, Object value) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put(key, value);
        return row;
    }

    private static Map<String, Object> row(String firstKey, Object firstValue, String secondKey, Object secondValue) {
        Map<String, Object> row = row(firstKey, firstValue);
        row.put(secondKey, secondValue);
        return row;
    }

    private static Map<String, Object> row(String firstKey, Object firstValue, String secondKey, Object secondValue,
            String thirdKey, Object thirdValue) {
        Map<String, Object> row = row(firstKey, firstValue, secondKey, secondValue);
        row.put(thirdKey, thirdValue);
        return row;
    }

    private static Map<String, Object> nullScoreRow() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("score", null);
        return row;
    }

    private static Map<String, Object> emptyAwareData() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("emptyRows", List.of());
        data.put("loose", List.of(row("anything", 1)));
        return data;
    }

    private static boolean hasCode(ValidationResult result, String code) {
        return result.issues().stream().anyMatch(candidate -> code.equals(candidate.code()));
    }

    private static boolean hasElementIssueAt(ValidationResult result, String path) {
        return result.issues().stream()
                .anyMatch(issue -> "type-prop-mismatch".equals(issue.code()) && path.equals(issue.path()));
    }

    private static ValidationIssue issue(ValidationResult result, String code) {
        ValidationIssue issue = result.issues().stream().filter(candidate -> code.equals(candidate.code())).findFirst()
                .orElse(null);
        assertNotNull(issue, () -> "missing " + code + " in " + result.issues());
        return issue;
    }

    private static ValidationIssue issueAt(ValidationResult result, String code, String path) {
        ValidationIssue issue = result.issues().stream()
                .filter(candidate -> code.equals(candidate.code()) && path.equals(candidate.path())).findFirst()
                .orElse(null);
        assertNotNull(issue, () -> "missing " + code + " at " + path + " in " + result.issues());
        return issue;
    }

    private static String unproven(ValidationResult result) {
        return result.metadata().extra().getOrDefault("typeChecks.unproven", "0");
    }

    private static String unprovenReasons(ValidationResult result) {
        return result.metadata().extra().getOrDefault("typeChecks.unprovenReasons", "");
    }
}
