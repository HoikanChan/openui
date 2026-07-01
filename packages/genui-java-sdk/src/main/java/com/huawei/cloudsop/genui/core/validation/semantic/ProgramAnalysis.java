package com.huawei.cloudsop.genui.core.validation.semantic;

import com.huawei.cloudsop.genui.core.validation.ValidationIssue;
import java.util.List;

/**
 * The semantic analysis of a parsed {@code Program} against a {@link ContractCatalog}.
 *
 * <p>This is the PUBLIC type Section 4 consumes to compute a {@code ValidationStatus}. It carries the
 * mode-aware {@link ValidationIssue} list plus the structural facts the top-level validator needs:
 * which statement was chosen as root, whether that root resolved to a renderable element, and the
 * unresolved/orphaned reference sets.
 *
 * @param issues all issues (syntax + contract + structural), already severity-graded for the mode
 * @param entryId the statement id chosen as root ({@code null} if the program has no statements)
 * @param rootResolved {@code true} iff {@code entryId} resolved to a renderable catalog/unknown
 *     component (mirrors TS {@code root !== null} — an {@code ElementNode})
 * @param unresolvedRefs names referenced but not defined/external (TS {@code meta.unresolved}); order
 *     of first encounter, duplicates preserved to mirror TS {@code unres} array semantics
 * @param orphaned value statement ids never reached from the root (TS {@code meta.orphaned})
 * @param statementCount number of deduplicated statements (TS {@code meta.statementCount})
 */
public record ProgramAnalysis(
    List<ValidationIssue> issues,
    String entryId,
    boolean rootResolved,
    List<String> unresolvedRefs,
    List<String> orphaned,
    int statementCount) {

  public ProgramAnalysis {
    issues = issues == null ? List.of() : List.copyOf(issues);
    unresolvedRefs = unresolvedRefs == null ? List.of() : List.copyOf(unresolvedRefs);
    orphaned = orphaned == null ? List.of() : List.copyOf(orphaned);
  }
}
