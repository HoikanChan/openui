/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.huawei.cloudsop.genui.core.validation.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import com.huawei.cloudsop.genui.core.contract.GenerationContractLoader;
import com.huawei.cloudsop.genui.core.validation.DefaultOpenuiLangValidator;
import com.huawei.cloudsop.genui.core.validation.ValidationIssue;
import com.huawei.cloudsop.genui.core.validation.ValidationRequest;
import com.huawei.cloudsop.genui.core.validation.ValidationResult;
import com.huawei.cloudsop.genui.core.validation.ValidationStatus;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

class MergeObjectTableValidationTest {
    @Test
    void pipelineRetainsOriginalAndInjectedColumnsAndRejectsAbsentColumn() {
        String dsl =
                """
                root = Table([Col("Name", "name"), Col("Rank", "rank")], rows)
                rows = @Each(@Sort(@Filter(data.rows, "name", "!=", ""), "name"), "row", @MergeObject(row, {rank: 1}))
                """;
        Map<String, Object> data =
                Map.of("rows", List.of(Map.of("name", "a"), Map.of("name", "b", "extra", true)));
        valid(validate(dsl, data));
        for (String field : List.of("name", "rank")) {
            ValidationResult bad =
                    validate(dsl.replace("\"" + field + "\")", "\"missing\")"), data);
            ValidationIssue missing = issue(bad, "type-table-column-missing", "/columns/missing");
            assertTrue(missing.message().contains("absent from the proven row shape"));
        }
    }

    @Test
    void emptyArrayHasNoRowFieldEvidenceButEmptyObjectDoes() {
        String dsl = "root = Table([Col(\"Missing\", \"missing\")], data.rows)";
        valid(validate(dsl, Map.of("rows", List.of())));
        issue(
                validate(dsl, Map.of("rows", List.of(Map.of()))),
                "type-table-column-missing",
                "/columns/missing");
    }

    @Test
    void rejectsNonArrayAndNestedRowsIndependentlyOfColumnChecks() {
        for (Object rows : List.of(Map.of("name", "a"), 1, List.of(List.of(Map.of("name", "a"))))) {
            ValidationResult result =
                    validate(
                            "root = Table([Col(\"Name\", \"name\")], data.rows)",
                            Map.of("rows", rows));
            assertTrue(
                    result.issues().stream()
                            .anyMatch(
                                    i ->
                                            "/rows".equals(i.path())
                                                    && List.of(
                                                                    "type-table-row-shape-mismatch",
                                                                    "type-prop-mismatch")
                                                            .contains(i.code())),
                    () -> result.issues().toString());
        }
    }

    @Test
    void corpus118ReportsMemoryUsageAfterCompletePipeline() throws Exception {
        Corpus corpus = corpus("case-11_GEN-00118");
        ValidationResult result = validate(corpus.contract(), corpus.dsl(), corpus.data());
        ValidationIssue missing =
                issue(result, "type-table-column-missing", "/columns/memory_usage");
        assertEquals("top3Table", missing.statementId());
        assertTrue(missing.message().contains("memory_usage"));
        assertFalse(
                result.issues().stream()
                        .anyMatch(i -> "builtin-argument-type-mismatch".equals(i.code())),
                () -> result.issues().toString());
    }

    @Test
    void corpus111SyntaxRepairRetainsBothPowerFieldsAndChecksColumns() throws Exception {
        Corpus corpus = corpus("case-28_GEN-00111");
        ValidationResult original = validate(corpus.contract(), corpus.dsl(), corpus.data());
        assertTrue(
                original.issues().stream().anyMatch(i -> "syntax".equals(i.source())),
                () -> original.issues().toString());
        // Test copy only: join the multiline expression and add the missing builtin sigil.
        String repaired =
                corpus.dsl()
                        .replace("{\n  rx_power_dbm:", "{rx_power_dbm:")
                        .replace("\n}))", "}))")
                        .replace("[Number(", "[@Number(");
        valid(validate(corpus.contract(), repaired, corpus.data()));
        for (String field : List.of("laneId", "tx_power_dbm", "rx_power_dbm")) {
            ValidationResult bad =
                    validate(
                            corpus.contract(),
                            repaired.replace("\"" + field + "\")", "\"missing_" + field + "\")"),
                            corpus.data());
            issue(bad, "type-table-column-missing", "/columns/missing_" + field);
        }
    }

    @Test
    void checksEveryRestPositionAndMinimumArity() {
        for (String expression : List.of("@MergeObject()", "@MergeObject({a:1})")) {
            issue(
                    validate("root = TextContent(@String(" + expression + "))", Map.of()),
                    "builtin-argument-count-mismatch",
                    "/args");
        }
        for (int position = 1; position <= 5; position++) {
            String[] arguments = {"{}", "{}", "{}", "{}", "{}", "{}"};
            arguments[position] = position % 2 == 0 ? "null" : "42";
            ValidationResult result =
                    validate(
                            "root = TextContent(@String(@MergeObject("
                                    + String.join(",", arguments)
                                    + ")))",
                            Map.of());
            ValidationIssue mismatch =
                    issue(result, "builtin-argument-type-mismatch", "/args/" + position);
            assertTrue(mismatch.message().contains(position % 2 == 0 ? "null" : "number"));
        }
    }

    @Test
    void replacementDropsOldFieldValueEvidence() {
        valid(
                validate(
                        "root = TextContent(@Abs(@MergeObject(data.row, {value: 1}).value))",
                        Map.of("row", Map.of("value", "old"))));
        issue(
                validate(
                        "root = TextContent(@Abs(@MergeObject(data.row, {value: \"new\"}).value))",
                        Map.of("row", Map.of("value", 1))),
                "builtin-argument-type-mismatch",
                "/args/0");
    }

    @Test
    void overlayOperationWorksWithContractDeclaredName() throws Exception {
        String json;
        try (var stream = getClass().getResourceAsStream("/openui/base-contract.json")) {
            json = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        GenerationContract contract =
                GenerationContractLoader.fromJson(json.replace("MergeObject", "CombineRecords"));
        ValidationResult result =
                validate(
                        contract,
                        "root = Table([Col(\"Name\", \"name\"), Col(\"Added\", \"added\")], rows)\n"
                                + "rows = @Each(data.rows, \"row\", @CombineRecords(row, {added: true}))",
                        Map.of(
                                "rows",
                                List.of(Map.of("name", "a"), Map.of("name", "b", "other", 1))));
        valid(result);
        assertEquals("0", result.metadata().extra().get("typeChecks.mismatched"));
    }

    @Test
    void partialRowPresenceStaysValidAcrossFilterAndSort() {
        String dsl = "root = Table([Col(\"Value\", \"value\")], %s)";
        Map<String, Object> data =
                Map.of("rows", List.of(Map.of("value", 1), Map.of("other", true)));
        // 只要有一行带该字段,列引用就是合法用法(缺值行渲染空单元格)。
        valid(validate(dsl.formatted("data.rows"), data));
        ValidationResult filtered =
                validate(
                        dsl.formatted(
                                "@Sort(@Filter(data.rows, \"other\", \"==\", false), \"value\")"),
                        data);
        valid(filtered);
        assertTrue(Long.parseLong(filtered.metadata().extra().get("typeChecks.unproven")) > 0);
        assertEquals("0", filtered.metadata().extra().get("typeChecks.mismatched"));
    }

    private record Corpus(GenerationContract contract, String dsl, Map<String, Object> data) {}

    private static Corpus corpus(String directory) throws Exception {
        Path root = Path.of("packages/genui-java-sdk/repair-cases");
        if (!Files.exists(root)) {
            root = Path.of("repair-cases");
        }
        Path folder = root.resolve(directory);
        Map<String, Object> metadata =
                Json.asObject(Json.parse(Files.readString(folder.resolve("case.json"))), "case");
        return new Corpus(
                GenerationContractLoader.loadDefault(),
                Files.readString(folder.resolve("dsl.txt")),
                Json.asObject(metadata.get("dataModel"), "dataModel"));
    }

    private static ValidationResult validate(String dsl, Map<String, Object> data) {
        return validate(GenerationContractLoader.loadDefault(), dsl, data);
    }

    private static ValidationResult validate(
            GenerationContract contract, String dsl, Map<String, Object> data) {
        return DefaultOpenuiLangValidator.INSTANCE.validate(
                ValidationRequest.builder().dsl(dsl).contract(contract).dataModel(data).build());
    }

    private static void valid(ValidationResult result) {
        assertEquals(ValidationStatus.VALID, result.status(), () -> result.issues().toString());
    }

    private static ValidationIssue issue(ValidationResult result, String code, String path) {
        return result.issues().stream()
                .filter(i -> code.equals(i.code()) && path.equals(i.path()))
                .findFirst()
                .orElseThrow(
                        () ->
                                new AssertionError(
                                        "missing "
                                                + code
                                                + " at "
                                                + path
                                                + ": "
                                                + result.issues()
                                                + " / "
                                                + result.metadata().extra()));
    }
}
