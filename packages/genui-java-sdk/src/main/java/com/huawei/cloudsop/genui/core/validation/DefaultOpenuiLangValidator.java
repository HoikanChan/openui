package com.huawei.cloudsop.genui.core.validation;

import com.huawei.cloudsop.genui.core.validation.parser.OpenuiParser;
import com.huawei.cloudsop.genui.core.validation.parser.ParseMode;
import com.huawei.cloudsop.genui.core.validation.parser.Program;
import com.huawei.cloudsop.genui.core.validation.semantic.ContractCatalog;
import com.huawei.cloudsop.genui.core.validation.semantic.ProgramAnalysis;
import com.huawei.cloudsop.genui.core.validation.semantic.ProgramAnalyzer;
import java.util.List;
import java.util.Objects;

/**
 * Default Java-native implementation of {@link OpenuiLangValidator}.
 *
 * <p>Pipeline:
 * <ol>
 *   <li>Pre-process and parse the DSL via {@link OpenuiParser#parse(String, ParseMode)}.</li>
 *   <li>Build a {@link ContractCatalog} from the request contract (null → empty catalog, so every
 *       component is reported as unknown-component; not a supported "syntax-only" mode).</li>
 *   <li>Run {@link ProgramAnalyzer#analyze(Program, ValidationMode, String, java.util.Set)}.</li>
 *   <li>Map issues to {@link ValidationStatus}: {@code INVALID} if any issue is
 *       {@link ValidationSeverity#ERROR}; {@code PARTIAL} only in
 *       {@link ValidationMode#STREAMING} with non-ERROR issues; else {@code VALID}.</li>
 * </ol>
 *
 * <p>Stateless; safe for concurrent use.
 */
public final class DefaultOpenuiLangValidator implements OpenuiLangValidator {

  /** Singleton — stateless, no configuration. */
  public static final DefaultOpenuiLangValidator INSTANCE = new DefaultOpenuiLangValidator();

  public DefaultOpenuiLangValidator() {
    // public so callers can inject via new; INSTANCE is the preferred no-arg handle.
  }

  @Override
  public ValidationResult validate(ValidationRequest request) {
    Objects.requireNonNull(request, "request");

    // 1. Preprocess + parse (ParseMode mirrors ValidationMode 1:1).
    ParseMode parseMode = request.mode() == ValidationMode.STREAMING
        ? ParseMode.STREAMING
        : ParseMode.FINAL;
    Program program = OpenuiParser.parse(request.dsl() != null ? request.dsl() : "", parseMode);

    // 2. Build catalog. A null contract yields an EMPTY catalog, so every component is reported
    //    as unknown-component; callers validating real generations must pass the merged
    //    GenerationContract. Contract-less syntax-only validation is not supported.
    ContractCatalog catalog = ContractCatalog.from(request.contract());

    // 3. Semantic analysis.
    ProgramAnalyzer analyzer = new ProgramAnalyzer(catalog);
    ProgramAnalysis analysis = analyzer.analyze(
        program, request.mode(), request.rootName(), request.externalRefs());

    // The normalized DSL is the preprocessed text (with whitespace trimmed / auto-close applied in
    // STREAMING). OpenuiParser internally calls OpenuiPreprocessor.process() but does not expose the
    // cleaned text directly. We re-derive it the same way: call preprocess() directly.
    String normalizedDsl = com.huawei.cloudsop.genui.core.validation.parser.OpenuiPreprocessor
        .process(request.dsl() != null ? request.dsl() : "", parseMode)
        .text();

    // 4. Compute status from issue severities.
    List<ValidationIssue> issues = analysis.issues();
    boolean hasError = issues.stream().anyMatch(i -> i.severity() == ValidationSeverity.ERROR);

    ValidationMetadata metadata = new ValidationMetadata(
        analysis.statementCount(),
        analysis.entryId(),
        request.mode(),
        null);

    if (hasError) {
      return ValidationResult.invalid(issues, metadata);
    }

    boolean hasWarningsOrInfo = !issues.isEmpty();
    if (request.mode() == ValidationMode.STREAMING && hasWarningsOrInfo) {
      return ValidationResult.partial(normalizedDsl, issues, metadata);
    }

    return ValidationResult.valid(normalizedDsl, issues, metadata);
  }
}
