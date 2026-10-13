package com.huawei.cloudsop.genui.core.validation;

import com.huawei.cloudsop.genui.core.validation.semantic.RepairHints;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Produces a stable, low-noise issue view while preserving complete raw validator diagnostics. */
public final class ValidationIssueReducer {

    private static final Set<String> ROOT_TAIL_CODES = Set.of("root-missing", "root-not-renderable");

    private ValidationIssueReducer() {
    }

    /**
     * Return the actionable subset of {@code rawIssues} in original order.
     *
     * <p>The result is immutable. This operation never invents diagnostics and never changes validation
     * status; callers that need parser parity should continue using the raw issue list.
     */
    public static List<ValidationIssue> actionable(List<ValidationIssue> rawIssues) {
        if (rawIssues == null || rawIssues.isEmpty()) {
            return List.of();
        }

        List<ValidationIssue> distinct = new ArrayList<>(new LinkedHashSet<>(rawIssues));
        Map<String, ValidationIssue> firstSyntaxError = firstSyntaxErrors(distinct);
        Set<IssueLocation> tableRowShapeLocations = tableRowShapeLocations(distinct);
        List<ValidationIssue> kept = new ArrayList<>();

        for (ValidationIssue issue : distinct) {
            if (issue == null) {
                continue;
            }
            if (isRedundantSyntaxError(issue, firstSyntaxError)) {
                continue;
            }
            if (isDerivedFromSyntaxFailure(issue, firstSyntaxError.keySet())) {
                continue;
            }
            if (isGenericTableRowMismatch(issue, tableRowShapeLocations)) {
                continue;
            }
            kept.add(issue);
        }

        boolean hasExplainingError = kept.stream().anyMatch(issue -> issue.severity() == ValidationSeverity.ERROR
                && !ROOT_TAIL_CODES.contains(issue.code()));
        if (hasExplainingError) {
            kept.removeIf(issue -> ROOT_TAIL_CODES.contains(issue.code()));
        }
        return List.copyOf(kept);
    }

    private static Map<String, ValidationIssue> firstSyntaxErrors(List<ValidationIssue> issues) {
        Map<String, ValidationIssue> first = new LinkedHashMap<>();
        for (ValidationIssue issue : issues) {
            if (isBlockingSyntaxIssue(issue) && issue.statementId() != null) {
                first.putIfAbsent(issue.statementId(), issue);
            }
        }
        return first;
    }

    private static Set<IssueLocation> tableRowShapeLocations(List<ValidationIssue> issues) {
        Set<IssueLocation> locations = new LinkedHashSet<>();
        for (ValidationIssue issue : issues) {
            if (issue != null && "type-table-row-shape-mismatch".equals(issue.code())) {
                locations.add(IssueLocation.of(issue));
            }
        }
        return locations;
    }

    private static boolean isRedundantSyntaxError(ValidationIssue issue,
            Map<String, ValidationIssue> firstSyntaxError) {
        return isBlockingSyntaxIssue(issue) && issue.statementId() != null
                && !issue.equals(firstSyntaxError.get(issue.statementId()));
    }

    private static boolean isDerivedFromSyntaxFailure(ValidationIssue issue, Set<String> syntaxBrokenStatements) {
        if (issue.severity() != ValidationSeverity.ERROR || "syntax".equals(issue.source())
                || issue.statementId() == null || !syntaxBrokenStatements.contains(issue.statementId())) {
            return false;
        }
        return !("unresolved-ref".equals(issue.code()) && RepairHints.isRootCauseHint(issue.hint()));
    }

    private static boolean isGenericTableRowMismatch(ValidationIssue issue, Set<IssueLocation> specificLocations) {
        return "type-prop-mismatch".equals(issue.code()) && specificLocations.contains(IssueLocation.of(issue));
    }

    private static boolean isBlockingSyntaxIssue(ValidationIssue issue) {
        return issue != null && issue.severity() == ValidationSeverity.ERROR && "syntax".equals(issue.source());
    }

    private record IssueLocation(String statementId, String component, String path) {

        private static IssueLocation of(ValidationIssue issue) {
            return new IssueLocation(issue.statementId(), issue.component(), issue.path());
        }
    }
}
