package com.huawei.cloudsop.genui.core.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.huawei.cloudsop.genui.core.validation.semantic.RepairHints;
import java.util.List;
import org.junit.jupiter.api.Test;

class ValidationIssueReducerTest {

    @Test
    void removesExactDuplicatesWithoutReordering() {
        ValidationIssue first = issue("unknown-component", "contract", "root", "Stack", "", "first", null);
        ValidationIssue second = issue("type-prop-mismatch", "type", "card", "Card", "/gap", "second", null);

        List<ValidationIssue> actionable = ValidationIssueReducer.actionable(List.of(first, first, second));

        assertEquals(List.of(first, second), actionable);
        assertThrows(UnsupportedOperationException.class, () -> actionable.add(first));
    }

    @Test
    void keepsFirstSyntaxRootCauseAndSuppressesSameStatementCascades() {
        ValidationIssue syntax = issue("syntax-unexpected-token", "syntax", "table", null, null,
                "Unexpected token EQUALS", null);
        ValidationIssue laterSyntax = issue("syntax-unclosed-bracket", "syntax", "table", null, null,
                "Unclosed '('", null);
        ValidationIssue unresolved = issue("unresolved-ref", "reference", "table", null, null,
                "unresolved reference columns", "define it");
        ValidationIssue type = issue("type-prop-mismatch", "type", "table", "Table", "/rows",
                "Table.rows mismatch", null);

        List<ValidationIssue> actionable = ValidationIssueReducer
                .actionable(List.of(syntax, laterSyntax, unresolved, type));

        assertEquals(List.of(syntax), actionable);
    }

    @Test
    void keepsRootCauseHintedReferenceOnSyntaxBrokenStatement() {
        ValidationIssue syntax = issue("syntax-unexpected-token", "syntax", "value", null, null,
                "Unexpected token DOT", null);
        String hint = RepairHints.unresolvedRefHint("Math");
        ValidationIssue rootCause = issue("unresolved-ref", "reference", "value", null, null,
                "unresolved reference Math", hint);

        assertEquals(List.of(syntax, rootCause), ValidationIssueReducer.actionable(List.of(syntax, rootCause)));
    }

    @Test
    void tableRowShapeDominatesGenericPropMismatchAtSameLocation() {
        ValidationIssue generic = issue("type-prop-mismatch", "type", "table", "Table", "/rows",
                "Table.rows expects object[]", null);
        ValidationIssue specific = issue("type-table-row-shape-mismatch", "type", "table", "Table", "/rows",
                "Table rows must be flat", null);

        assertEquals(List.of(specific), ValidationIssueReducer.actionable(List.of(generic, specific)));
    }

    @Test
    void rootTailIsSuppressedWhenAnotherActionableErrorExists() {
        ValidationIssue cause = issue("syntax-missing-assignment", "syntax", "root", null, null,
                "root is missing equals", null);
        ValidationIssue tail = issue("root-missing", "root", null, null, null,
                "no renderable root", null);

        assertEquals(List.of(cause), ValidationIssueReducer.actionable(List.of(cause, tail)));
    }

    @Test
    void preservesIndependentPathsAndStatements() {
        ValidationIssue model = issue("type-table-column-missing", "type", "table", "Table", "/columns/model",
                "model missing", null);
        ValidationIssue count = issue("type-table-column-missing", "type", "table", "Table", "/columns/count",
                "count missing", null);
        ValidationIssue otherTable = issue("type-table-column-missing", "type", "other", "Table", "/columns/model",
                "model missing", null);

        assertEquals(List.of(model, count, otherTable),
                ValidationIssueReducer.actionable(List.of(model, count, otherTable)));
    }

    @Test
    void nullInputProducesEmptyActionableList() {
        assertEquals(List.of(), ValidationIssueReducer.actionable(null));
    }

    private static ValidationIssue issue(String code, String source, String statementId, String component, String path,
            String message, String hint) {
        return new ValidationIssue(code, ValidationSeverity.ERROR, source, message, statementId, component, path, 1, 1,
                hint, false);
    }
}
