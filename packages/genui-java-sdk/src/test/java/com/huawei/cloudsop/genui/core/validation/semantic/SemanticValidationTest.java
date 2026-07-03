package com.huawei.cloudsop.genui.core.validation.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.contract.ComponentPromptSpec;
import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import com.huawei.cloudsop.genui.core.validation.ValidationIssue;
import com.huawei.cloudsop.genui.core.validation.ValidationMode;
import com.huawei.cloudsop.genui.core.validation.ValidationSeverity;
import com.huawei.cloudsop.genui.core.validation.parser.OpenuiParser;
import com.huawei.cloudsop.genui.core.validation.parser.ParseMode;
import com.huawei.cloudsop.genui.core.validation.parser.Program;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SemanticValidationTest {

  // ── contract builders ──────────────────────────────────────────────────────

  private static Map<String, Object> obj(Object... kv) {
    LinkedHashMap<String, Object> m = new LinkedHashMap<>();
    for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
    return m;
  }

  private static ComponentPromptSpec spec(Map<String, Object> properties, List<String> required) {
    return new ComponentPromptSpec("comp", obj("type", "object", "properties", properties, "required", required));
  }

  /** Catalog with Header(title, subtitle?), Table(columns, rows), Col(title, field, options?). */
  private static ContractCatalog catalog() {
    Map<String, ComponentPromptSpec> comps = new LinkedHashMap<>();
    comps.put(
        "Header",
        spec(obj("title", obj("type", "string"), "subtitle", obj("type", "string")), List.of("title")));
    comps.put(
        "Table",
        spec(
            obj("columns", obj("type", "array"), "rows", obj("type", "array")),
            List.of("columns", "rows")));
    // Col.options is a closed object (additionalProperties:false) so removed `format` is flagged.
    Map<String, Object> optionsSchema =
        obj(
            "type",
            "object",
            "additionalProperties",
            false,
            "properties",
            obj("sortable", obj("type", "boolean")));
    comps.put(
        "Col",
        spec(
            obj("title", obj("type", "string"), "field", obj("type", "string"), "options", optionsSchema),
            List.of("title", "field")));
    return ContractCatalog.from(
        new GenerationContract("v1", "Header", comps, List.of(), List.of(), List.of(), List.of()));
  }

  private static ProgramAnalysis analyze(String dsl, ValidationMode mode) {
    ParseMode pm = mode == ValidationMode.FINAL ? ParseMode.FINAL : ParseMode.STREAMING;
    Program program = OpenuiParser.parse(dsl, pm);
    return new ProgramAnalyzer(catalog()).analyze(program, mode);
  }

  private static ValidationIssue issue(ProgramAnalysis a, String code) {
    return a.issues().stream().filter(i -> i.code().equals(code)).findFirst().orElse(null);
  }

  // ── 3.6 required coverage ───────────────────────────────────────────────────

  @Test
  void unknownComponent() {
    ProgramAnalysis a = analyze("root = Bogus(\"x\")", ValidationMode.FINAL);
    ValidationIssue i = issue(a, "unknown-component");
    assertNotNull(i, "expected unknown-component issue");
    assertEquals("Bogus", i.component());
    assertEquals("", i.path());
    assertEquals("root", i.statementId());
    assertEquals(ValidationSeverity.ERROR, i.severity());
  }

  @Test
  void missingRequired() {
    ProgramAnalysis a = analyze("root = Header()", ValidationMode.FINAL);
    ValidationIssue i = issue(a, "missing-required");
    assertNotNull(i, "expected missing-required issue");
    assertEquals("Header", i.component());
    assertEquals("/title", i.path());
    assertEquals("root", i.statementId());
    assertEquals("missing required field \"title\"", i.message());
  }

  @Test
  void nullRequired() {
    ProgramAnalysis a = analyze("root = Header(null)", ValidationMode.FINAL);
    ValidationIssue i = issue(a, "null-required");
    assertNotNull(i, "expected null-required issue");
    assertEquals("Header", i.component());
    assertEquals("/title", i.path());
    assertEquals("required field \"title\" cannot be null", i.message());
  }

  @Test
  void colOptionsFormatRemoved() {
    ProgramAnalysis a =
        analyze("root = Col(\"Name\", \"name\", { format: \"date\" })", ValidationMode.FINAL);
    ValidationIssue i = issue(a, "invalid-prop");
    assertNotNull(i, "expected invalid-prop issue");
    assertEquals("Col", i.component());
    assertEquals("/options/format", i.path());
    assertEquals(ComponentContractValidator.COL_OPTIONS_FORMAT_MESSAGE, i.message());
  }

  @Test
  void excessArgs() {
    ProgramAnalysis a =
        analyze("root = Header(\"Title\", \"Sub\", \"Extra\")", ValidationMode.FINAL);
    ValidationIssue i = issue(a, "excess-args");
    assertNotNull(i, "expected excess-args issue");
    assertEquals("Header", i.component());
    assertEquals("", i.path());
    assertEquals("Header takes 2 arg(s), got 3 (1 excess dropped)", i.message());
  }

  @Test
  void rootMissingFinal() {
    // Only a $state statement — no renderable component.
    ProgramAnalysis a = analyze("$count = 0", ValidationMode.FINAL);
    ValidationIssue i = issue(a, "root-not-renderable");
    ValidationIssue m = issue(a, "root-missing");
    assertTrue(i != null || m != null, "expected a root structural issue");
    assertFalse(a.rootResolved());
  }

  @Test
  void emptyProgramRootMissingFinal() {
    Program program = OpenuiParser.parse("", ParseMode.FINAL);
    ProgramAnalysis a = new ProgramAnalyzer(catalog()).analyze(program, ValidationMode.FINAL);
    ValidationIssue i = issue(a, "root-missing");
    assertNotNull(i, "expected root-missing on empty program");
    assertEquals(ValidationSeverity.ERROR, i.severity());
  }

  @Test
  void finalUnresolvedRefIsBlocking() {
    ProgramAnalysis a = analyze("root = Header(\"Hi\", missingRef)", ValidationMode.FINAL);
    ValidationIssue i = issue(a, "unresolved-ref");
    assertNotNull(i, "expected unresolved-ref issue");
    assertEquals(ValidationSeverity.ERROR, i.severity());
    assertFalse(i.retryable());
    assertTrue(a.unresolvedRefs().contains("missingRef"));
  }

  @Test
  void unresolvedRefAttributedToReferencingStatement() {
    // Spec (Final unresolved reference is invalid): the issue identifies the unresolved
    // reference AND the statement that used it — not the root/entry statement.
    String dsl = "kpi = Header(\"T\", missingRef)\nroot = Table([], [kpi])";
    ProgramAnalysis a = analyze(dsl, ValidationMode.FINAL);
    ValidationIssue i = issue(a, "unresolved-ref");
    assertNotNull(i, "expected unresolved-ref issue");
    assertEquals("kpi", i.statementId());
    assertEquals(1, i.line());
    assertTrue(i.column() > 0);
  }

  @Test
  void streamingTemporaryUnresolvedIsNotBlocking() {
    // Streaming + incomplete: reference to a not-yet-defined statement is a WARNING, retryable.
    Program program = OpenuiParser.parse("root = Header(\"Hi\", laterRef", ParseMode.STREAMING);
    ProgramAnalysis a = new ProgramAnalyzer(catalog()).analyze(program, ValidationMode.STREAMING);
    ValidationIssue i = issue(a, "unresolved-ref");
    assertNotNull(i, "expected unresolved-ref issue");
    assertEquals(ValidationSeverity.WARNING, i.severity());
    assertTrue(i.retryable());
  }

  @Test
  void lastWinsDuplicateId() {
    // Two statements with id `root`: the LAST wins → Table (renderable, valid).
    // The first (Header()) would be missing-required; if last-wins works, no missing-required fires.
    String dsl = "root = Header()\nroot = Table([], [])";
    ProgramAnalysis a = analyze(dsl, ValidationMode.FINAL);
    assertEquals(1, a.statementCount(), "duplicate ids collapse to one symbol");
    assertNull(issue(a, "missing-required"));
    assertTrue(a.rootResolved(), "root resolves to the last-wins Table");
  }

  @Test
  void defaultFallbackSuppressesMissingRequired() {
    // Required prop with a declared default → no missing-required.
    Map<String, ComponentPromptSpec> comps = new LinkedHashMap<>();
    comps.put(
        "Box",
        new ComponentPromptSpec(
            "box",
            obj(
                "type",
                "object",
                "properties",
                obj("label", obj("type", "string", "default", "untitled")),
                "required",
                List.of("label"))));
    ContractCatalog cat =
        ContractCatalog.from(
            new GenerationContract("v1", "Box", comps, List.of(), List.of(), List.of(), List.of()));
    Program program = OpenuiParser.parse("root = Box()", ParseMode.FINAL);
    ProgramAnalysis a = new ProgramAnalyzer(cat).analyze(program, ValidationMode.FINAL);
    assertNull(a.issues().stream().filter(i -> i.code().equals("missing-required")).findFirst().orElse(null));
    assertTrue(a.rootResolved());
  }

  @Test
  void signatureOnlyComponentSkipsPropChecks() {
    Map<String, ComponentPromptSpec> comps = new LinkedHashMap<>();
    comps.put("Widget", new ComponentPromptSpec("Widget(a, b)", "a widget"));
    ContractCatalog cat =
        ContractCatalog.from(
            new GenerationContract("v1", "Widget", comps, List.of(), List.of(), List.of(), List.of()));
    Program program = OpenuiParser.parse("root = Widget()", ParseMode.FINAL);
    ProgramAnalysis a = new ProgramAnalyzer(cat).analyze(program, ValidationMode.FINAL);
    // Existence OK, no prop-level false positives.
    assertTrue(a.issues().stream().noneMatch(i -> i.code().equals("missing-required")));
    assertTrue(a.issues().stream().noneMatch(i -> i.code().equals("unknown-component")));
    assertTrue(a.rootResolved());
  }

  @Test
  void validProgramHasNoBlockingIssues() {
    ProgramAnalysis a = analyze("root = Header(\"Hello\", \"World\")", ValidationMode.FINAL);
    assertTrue(
        a.issues().stream().noneMatch(i -> i.severity() == ValidationSeverity.ERROR),
        "clean program should have no blocking issues: " + a.issues());
    assertTrue(a.rootResolved());
    assertEquals("root", a.entryId());
  }

  // ── Streaming blocking classification (per-statement completeness) ─────────

  /**
   * When a COMPLETE statement earlier in the stream has a definitively-invalid error
   * (unknown-component) and the LAST statement is an incomplete tail, the earlier statement's error
   * must remain ERROR (blocking). Only the tail statement's non-definitive errors are tolerated.
   *
   * <p>DSL: {@code root = Bogus("x")\nheader = Header(} — "root" is fully received (not the last
   * statement); "header" is the last (partially received) tail making the program incomplete.
   * The unknown-component on "root" must be ERROR.
   */
  @Test
  void streamingCompleteInvalidStatementBeforeIncompleteTailStaysBlocking() {
    // "root" is the first statement (fully received, NOT the last).
    // "header" is the second/last statement and is incomplete (unclosed paren → program.incomplete).
    String dsl = "root = Bogus(\"x\")\nheader = Header(";
    Program program = OpenuiParser.parse(dsl, ParseMode.STREAMING);
    assertTrue(program.incomplete(), "program must be incomplete for this test to exercise the path");

    ProgramAnalysis a = new ProgramAnalyzer(catalog()).analyze(program, ValidationMode.STREAMING);

    ValidationIssue unknownComp = issue(a, "unknown-component");
    assertNotNull(unknownComp, "expected unknown-component issue on earlier complete statement");
    assertEquals("root", unknownComp.statementId());
    assertEquals("Bogus", unknownComp.component());
    assertEquals(ValidationSeverity.ERROR, unknownComp.severity(),
        "complete earlier statement's unknown-component must stay blocking (ERROR)");
    assertFalse(unknownComp.retryable(), "blocking issue must not be retryable");
  }

  /**
   * {@code unknown-component} is always blocking even when it appears on the LAST (potentially
   * partial) statement, because the component name is already fully received and cannot become valid
   * by appending more tokens.
   *
   * <p>DSL: {@code root = Bogus("x"} — single statement, incomplete (unclosed paren), but the name
   * "Bogus" is definitively unknown. Must remain ERROR.
   */
  @Test
  void streamingUnknownComponentAlwaysBlockingEvenOnLastStatement() {
    String dsl = "root = Bogus(\"x\"";
    Program program = OpenuiParser.parse(dsl, ParseMode.STREAMING);
    assertTrue(program.incomplete(), "program must be incomplete for this test");

    ProgramAnalysis a = new ProgramAnalyzer(catalog()).analyze(program, ValidationMode.STREAMING);

    ValidationIssue unknownComp = issue(a, "unknown-component");
    assertNotNull(unknownComp, "expected unknown-component issue");
    assertEquals("Bogus", unknownComp.component());
    assertEquals(ValidationSeverity.ERROR, unknownComp.severity(),
        "unknown-component must be blocking (ERROR) even on the last/partial statement");
    assertFalse(unknownComp.retryable(), "unknown-component is never retryable");
  }

  /**
   * A known component on the last (partially received) statement that is still missing a required
   * prop — while the program is incomplete — should be downgraded to a WARNING (retryable). The
   * required arg may still arrive as more tokens stream in.
   *
   * <p>DSL: {@code root = Header(} — "Header" is known but "title" is missing. The statement is
   * the last and the program is incomplete → should be WARNING/retryable, not ERROR.
   */
  @Test
  void streamingTailMissingRequiredIsTolerated() {
    String dsl = "root = Header(";
    Program program = OpenuiParser.parse(dsl, ParseMode.STREAMING);
    assertTrue(program.incomplete(), "program must be incomplete for this test");

    ProgramAnalysis a = new ProgramAnalyzer(catalog()).analyze(program, ValidationMode.STREAMING);

    ValidationIssue missingReq = issue(a, "missing-required");
    assertNotNull(missingReq, "expected missing-required issue on tail statement");
    assertEquals("Header", missingReq.component());
    assertEquals("/title", missingReq.path());
    assertEquals(ValidationSeverity.WARNING, missingReq.severity(),
        "tail statement missing-required must be tolerated (WARNING) while streaming");
    assertTrue(missingReq.retryable(), "tail missing-required must be retryable");
  }
}
