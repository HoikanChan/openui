/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import com.huawei.cloudsop.genui.core.contract.GenerationContractLoader;
import com.huawei.cloudsop.genui.core.validation.DefaultOpenuiLangValidator;
import com.huawei.cloudsop.genui.core.validation.ValidationMode;
import com.huawei.cloudsop.genui.core.validation.ValidationRequest;
import com.huawei.cloudsop.genui.core.validation.ValidationResult;
import com.huawei.cloudsop.genui.core.validation.ValidationStatus;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * #88 格式化 builtin 的契约规则验收:FormatDate 标量/数组分别传播,其余四个不伪造固定 string。
 *
 * <p>
 * 规则全部来自契约 {@code typeRule}(词表与规则同源),测试只经公开 validator 断言。语义依据为 #79 运行时语义矩阵:
 * FormatNumber 的无效输入结果种类、FormatPercent/FormatDuration 的 null/无效输出未定义——这类结果按未证明
 * (UNKNOWN_INPUT)处理,不补成 string。
 */
class FormatBuiltinValidationTest {

    // ---- FormatDate:标量 → string,数组 → string[] ----

    @Test
    void formatDateMapsArraysToArrays() {
        Map<String, Object> data = Map.of("timestamps", List.of(1700000000L, 1700000001L));

        // Count 只接受数组:通过说明数组输入得到 string[],而不是被压成单个 string。
        ValidationResult result = validate("root = TextContent(@String(@Count(@FormatDate(data.timestamps))))", data);

        assertEquals(ValidationStatus.VALID, result.status(), () -> String.valueOf(result.issues()));
        assertFalse(hasCode(result, "builtin-argument-type-mismatch"), () -> String.valueOf(result.issues()));
    }

    @Test
    void formatDateFormatsScalarsToAString() {
        // Count 只接受数组:标量输入得到 string 而不是数组。
        ValidationResult result = validate("root = TextContent(@String(@Count(@FormatDate(1700000000))))", Map.of());

        assertTrue(hasCode(result, "builtin-argument-type-mismatch"), () -> String.valueOf(result.issues()));
    }

    // ---- 其余四个:不得伪造固定 string ----

    @Test
    void formatNumberAndSiblingsKeepUndefinedOutcomesUnproven() {
        for (String builtin : List.of("FormatBytes", "FormatNumber", "FormatPercent", "FormatDuration")) {
            // Abs 只接受 number:若规则伪造 string 会报类型冲突;未证明只记 UNKNOWN_INPUT。
            ValidationResult result = validate(
                    "root = TextContent(@String(@Abs(@" + builtin + "(data.value))))", Map.of("value", 1.5));

            assertEquals(ValidationStatus.VALID, result.status(), () -> builtin + ": " + result.issues());
            assertFalse(hasCode(result, "builtin-argument-type-mismatch"),
                    () -> builtin + " must not claim a fixed string result: " + result.issues());
            assertTrue(unprovenReasons(result).contains("UNKNOWN_INPUT="),
                    () -> builtin + " must expose the unproven outcome: " + result.metadata().extra());
        }
    }

    // ---- 参数负例 ----

    @Test
    void formatDateStyleMustBeAString() {
        ValidationResult result = validate("root = TextContent(@FormatDate(data.value, 1))", Map.of("value", 1));

        assertTrue(hasCode(result, "builtin-argument-type-mismatch"), () -> String.valueOf(result.issues()));
    }

    @Test
    void formatNumberDecimalsMustBeANumber() {
        ValidationResult result = validate("root = TextContent(@FormatNumber(1, \"x\"))", Map.of());

        assertTrue(hasCode(result, "builtin-argument-type-mismatch"), () -> String.valueOf(result.issues()));
    }

    @Test
    void formatDurationUnitMustBeAString() {
        ValidationResult result = validate("root = TextContent(@FormatDuration(1, 2))", Map.of());

        assertTrue(hasCode(result, "builtin-argument-type-mismatch"), () -> String.valueOf(result.issues()));
    }

    @Test
    void formattingArityIsEnforced() {
        ValidationResult missing = validate("root = TextContent(@FormatBytes())", Map.of());
        assertTrue(hasCode(missing, "builtin-argument-count-mismatch"), () -> String.valueOf(missing.issues()));

        ValidationResult extra = validate("root = TextContent(@FormatBytes(1, 2))", Map.of());
        assertTrue(hasCode(extra, "builtin-argument-count-mismatch"), () -> String.valueOf(extra.issues()));
    }

    // ---- 下游组合 ----

    @Test
    void formattingBuiltinsComposeInsideTableCells() {
        Map<String, Object> data = Map.of("rows",
                List.of(row("name", "a", "joinedAt", 1700000000L), row("name", "b", "joinedAt", 1700000001L)));

        ValidationResult result = validate("root = Table([nameCol, whenCol, pctCol], data.rows)\n"
                + "nameCol = Col(\"Name\", \"name\")\n"
                + "whenCol = Col(\"Joined\", \"joinedAt\", {cell: @Render(\"v\", TextContent(@FormatDate(v, \"date\")))})\n"
                + "pctCol = Col(\"Ratio\", \"joinedAt\", {cell: @Render(\"v\", TextContent(@FormatPercent(v)))})", data);

        assertEquals(ValidationStatus.VALID, result.status(), () -> String.valueOf(result.issues()));
        assertFalse(hasCode(result, "builtin-argument-type-mismatch"), () -> String.valueOf(result.issues()));
        assertFalse(hasCode(result, "type-table-column-missing"), () -> String.valueOf(result.issues()));
    }

    // ---- helpers ----

    private static ValidationResult validate(String dsl, Map<String, Object> dataModel) {
        return validateWithContract(dsl, dataModel, GenerationContractLoader.loadDefault());
    }

    private static ValidationResult validateWithContract(String dsl, Map<String, Object> dataModel,
            GenerationContract contract) {
        return DefaultOpenuiLangValidator.INSTANCE.validate(ValidationRequest.builder().dsl(dsl).contract(contract)
                .dataModel(dataModel).mode(ValidationMode.FINAL).build());
    }

    private static Map<String, Object> row(String key, Object value, String secondKey, Object secondValue) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put(key, value);
        row.put(secondKey, secondValue);
        return row;
    }

    private static boolean hasCode(ValidationResult result, String code) {
        return result.issues().stream().anyMatch(candidate -> code.equals(candidate.code()));
    }

    private static String unprovenReasons(ValidationResult result) {
        return result.metadata().extra().getOrDefault("typeChecks.unprovenReasons", "");
    }
}
