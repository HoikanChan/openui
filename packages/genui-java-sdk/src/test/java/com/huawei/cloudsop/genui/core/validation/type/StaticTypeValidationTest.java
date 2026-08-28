package com.huawei.cloudsop.genui.core.validation.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

import java.util.List;
import java.util.Map;

class StaticTypeValidationTest {

    @Test
    void reportsMissingRenderDataModelPath() {
        ValidationResult result = validate("root = CardHeader(data.device.displayName)",
                Map.of("device", Map.of("name", "leaf-1")));

        ValidationIssue issue = issue(result, "type-data-path-missing");
        assertEquals("data.device.displayName", issue.path());
        assertEquals(ValidationStatus.INVALID, result.status());
    }

    @Test
    void reportsInvalidScalarTraversal() {
        ValidationResult result = validate("root = CardHeader(data.device.name)", Map.of("device", "leaf-1"));

        ValidationIssue issue = issue(result, "type-data-invalid-traversal");
        assertEquals("data.device.name", issue.path());
        assertEquals(ValidationStatus.INVALID, result.status());
    }

    @Test
    void reportsUnprovableMemberTypeFromEmptyArray() {
        ValidationResult result = validate("root = CardHeader(data.users.name)", Map.of("users", List.of()));

        ValidationIssue issue = issue(result, "type-data-unprovable");
        assertEquals("data.users.name", issue.path());
    }

    @Test
    void reportsScalarPassedToArrayProp() {
        ValidationResult result = validate("root = Table([nameCol], data.total)\nnameCol = Col(\"Name\", \"name\")",
                Map.of("total", 3));

        ValidationIssue issue = issue(result, "type-prop-mismatch");
        assertEquals("Table", issue.component());
        assertEquals("/rows", issue.path());
        assertEquals(ValidationStatus.INVALID, result.status());
    }

    @Test
    void reportsWrongArrayElementType() {
        ValidationResult result = validate("root = PieChart([\"A\"], data.values, \"pie\")",
                Map.of("values", List.of("not-a-number")));

        ValidationIssue issue = issue(result, "type-prop-mismatch");
        assertEquals("PieChart", issue.component());
        assertEquals("/values", issue.path());
    }

    @Test
    void infersEachBinderAndBuiltinResultTypes() {
        String dsl = "root = LineChart(labels, [values])\n" + "labels = @Each(data.points, \"point\", point.label)\n"
                + "values = Series(\"Value\", @Each(data.points, \"point\", point.value))";

        ValidationResult result = validate(dsl,
                Map.of("points", List.of(Map.of("label", "A", "value", 1), Map.of("label", "B", "value", 2))));

        assertEquals(ValidationStatus.VALID, result.status(), () -> String.valueOf(result.issues()));
    }

    @Test
    void acceptsEachBinderAcrossReferencedTemplateStatements() {
        String dsl = "root = Tabs(@Each(data.groups, \"group\", groupTemplate))\n"
                + "groupTemplate = TabItem(group.model, group.model, [groupTable])\n"
                + "groupTable = Table([nameCol], group.devices)\n"
                + "nameCol = Col(\"Device Name\", \"name\")";
        Map<String, Object> dataModel = Map.of("groups",
                List.of(Map.of("model", "CE12804S", "devices", List.of(Map.of("name", "DC1-spine-01")))));

        ValidationResult result = validate(dsl, dataModel);

        assertEquals(ValidationStatus.VALID, result.status(), () -> String.valueOf(result.issues()));
    }

    @Test
    void rejectsReferencedTemplateWhenEachBinderIsNotInScope() {
        String dsl = "root = groupTemplate\n"
                + "groupTemplate = TabItem(group.model, group.model, [])";

        ValidationResult result = validate(dsl, Map.of("groups", List.of()));

        issue(result, "unresolved-ref");
    }

    @Test
    void acceptsRenderBindersWithoutUnresolvedReferenceNoise() {
        String dsl = "root = Table([nameCol], data.users)\n"
                + "nameCol = Col(\"Name\", \"name\", {cell: @Render(\"v\", TextContent(v))})";

        ValidationResult result = validate(dsl, Map.of("users", List.of(Map.of("name", "Ada"))));

        assertEquals(ValidationStatus.VALID, result.status(), () -> String.valueOf(result.issues()));
    }

    @Test
    void reportsBuiltinArgumentMismatch() {
        ValidationResult result = validate("root = TextContent(@Count(data.total))", Map.of("total", 3));

        issue(result, "builtin-argument-type-mismatch");
    }

    @Test
    void rejectsInvalidEnumLiteral() {
        ValidationResult result = validate("root = Tag(\"Alert\", \"purple\")", Map.of("present", true));

        ValidationIssue issue = issue(result, "type-prop-mismatch");
        assertEquals("/variant", issue.path());
    }

    @Test
    void rejectsIncompatibleComponentSlot() {
        ValidationResult result = validate("root = Descriptions([Tag(\"Wrong child\")])", Map.of("present", true));

        ValidationIssue issue = issue(result, "component-slot-type-mismatch");
        assertEquals("Descriptions", issue.component());
        assertEquals("/items", issue.path());
    }

    @Test
    void validatesTableColumnFieldsAgainstRowShape() {
        String dsl = "root = Table([nameCol], data.users)\n" + "nameCol = Col(\"Name\", \"displayName\")";

        ValidationResult result = validate(dsl, Map.of("users", List.of(Map.of("name", "Ada"))));

        ValidationIssue issue = issue(result, "type-table-column-missing");
        assertTrue(issue.message().contains("displayName"));
    }

    @Test
    void validatesNestedTableColumnFieldsAgainstRowShape() {
        String dsl = "root = Table([statusCol], data.users)\n"
                + "statusCol = Col(\"Status\", \"profile.missingStatus\")";

        ValidationResult result = validate(dsl,
                Map.of("users", List.of(Map.of("profile", Map.of("status", "active")))));

        issue(result, "type-table-column-missing");
    }

    @Test
    void rejectsArrayComparedWithScalar() {
        ValidationResult result = validate("root = TextContent(data.items.status == \"inactive\")",
                Map.of("items", List.of(Map.of("status", "inactive"))));

        issue(result, "type-operator-mismatch");
    }

    @Test
    void reportsBuiltinArgumentCountMismatch() {
        ValidationResult result = validate("root = TextContent(@Count(data.items, 2))", Map.of("items", List.of(1, 2)));

        issue(result, "builtin-argument-count-mismatch");
    }

    @Test
    void rejectsExplicitEmptyTypedComponentSlot() {
        ValidationResult result = validate("root = Descriptions([])", Map.of("present", true));

        issue(result, "empty-component-slot");
    }

    @Test
    void reportsUnprovableEmptyDataArrayForTypedComponentSlot() {
        ValidationResult result = validate("root = Descriptions(data.items)", Map.of("items", List.of()));

        ValidationIssue issue = issue(result, "type-data-unprovable");
        assertEquals("data.items", issue.path());
    }

    @Test
    void typesTableCellValueBinderFromTheSelectedField() {
        String dsl = "root = Table([nameCol], data.users)\n"
                + "nameCol = Col(\"Name\", \"name\", {cell: @Render(\"v\", TextContent(@Count(v)))})";

        ValidationResult result = validate(dsl, Map.of("users", List.of(Map.of("name", "Ada"))));

        issue(result, "builtin-argument-type-mismatch");
    }

    @Test
    void validatesDataPathsAgainstAnEmptyConcreteModel() {
        ValidationResult result = validate("root = CardHeader(data.title)", Map.of());

        issue(result, "type-data-path-missing");
    }

    @Test
    void rejectsArithmeticOnArrays() {
        ValidationResult result = validate("root = TextContent(data.left + data.right)",
                Map.of("left", List.of(1), "right", List.of(2)));

        issue(result, "type-operator-mismatch");
    }

    @Test
    void rejectsAddingEachResultsWhenOneInputIsEmpty() {
        String dsl = "root = TextContent(rows)\n"
                + "rows = @Each(data.left, \"item\", item) + @Each(data.right, \"item\", item)";
        ValidationResult result = validate(dsl, Map.of("left", List.of(Map.of("name", "Ada")), "right", List.of()));

        issue(result, "type-operator-mismatch");
    }

    @Test
    void rejectsNestedArraysAsTableRows() {
        String dsl = "root = Table([nameCol], rows)\n" + "nameCol = Col(\"Name\", \"name\")\n"
                + "rows = @Each(data.groups, \"group\", @Each(group.items, \"item\", item))";
        Map<String, Object> dataModel = Map.of("groups",
                List.of(Map.of("items", List.of(Map.of("name", "Ada"))), Map.of("items", List.of())));

        ValidationResult result = validate(dsl, dataModel);

        issue(result, "type-table-row-shape-mismatch");
    }

    @Test
    void rejectsTableColumnMissingFromSomeRows() {
        String dsl = "root = Table([aliasCol], data.users)\n" + "aliasCol = Col(\"Alias\", \"alias\")";
        Map<String, Object> dataModel = Map.of("users",
                List.of(Map.of("name", "Ada", "alias", "a"), Map.of("name", "Grace")));

        ValidationResult result = validate(dsl, dataModel);

        ValidationIssue issue = issue(result, "type-table-column-missing");
        assertTrue(issue.message().contains("some rows"));
    }

    @Test
    void rejectsLineChartWithOnlyOneKnownPoint() {
        String dsl = "root = LineChart(labels, [series])\n"
                + "labels = @Each(data.points, \"point\", point.label)\n"
                + "series = Series(\"Value\", @Each(data.points, \"point\", point.value))";
        ValidationResult result = validate(dsl, Map.of("points", List.of(Map.of("label", "A", "value", 1))));

        issue(result, "type-chart-insufficient-data");
    }

    @Test
    void rejectsChartLabelAndSeriesLengthMismatch() {
        String dsl = "root = BarChart([\"A\", \"B\"], [Series(\"Value\", data.values)])";
        ValidationResult result = validate(dsl, Map.of("values", List.of(1)));

        issue(result, "type-chart-length-mismatch");
    }

    @Test
    void rejectsDuplicateDerivedValuesInsideOneChartSeries() {
        String dsl = "root = BarChart([\"UP/UP\", \"UP/DOWN\"], [Series(\"Count\", [upUp, upDown])])\n"
                + "upUp = @Count(@Filter(data.rows, \"adminStatus\", \"==\", \"active\"))\n"
                + "upDown = @Count(@Filter(data.rows, \"adminStatus\", \"==\", \"active\"))";
        ValidationResult result = validate(dsl,
                Map.of("rows", List.of(Map.of("adminStatus", "active", "operStatus", "active"))));

        issue(result, "type-chart-duplicate-series-value");
    }

    private static ValidationResult validate(String dsl, Map<String, Object> dataModel) {
        return DefaultOpenuiLangValidator.INSTANCE
                .validate(ValidationRequest.builder().dsl(dsl).contract(GenerationContractLoader.loadDefault())
                        .dataModel(dataModel).mode(ValidationMode.FINAL).build());
    }

    private static ValidationIssue issue(ValidationResult result, String code) {
        ValidationIssue issue = result.issues().stream().filter(candidate -> code.equals(candidate.code())).findFirst()
                .orElse(null);
        assertNotNull(issue, () -> "missing " + code + " in " + result.issues());
        return issue;
    }
}
