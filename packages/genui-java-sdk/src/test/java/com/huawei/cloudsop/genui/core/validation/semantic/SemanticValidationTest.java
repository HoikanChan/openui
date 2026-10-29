/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.contract.ComponentPromptSpec;
import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import com.huawei.cloudsop.genui.core.contract.GenerationContractLoader;
import com.huawei.cloudsop.genui.core.validation.ValidationIssue;
import com.huawei.cloudsop.genui.core.validation.ValidationIssueReducer;
import com.huawei.cloudsop.genui.core.validation.ValidationSeverity;
import com.huawei.cloudsop.genui.core.validation.parser.OpenuiParser;
import com.huawei.cloudsop.genui.core.validation.parser.ParseMode;
import com.huawei.cloudsop.genui.core.validation.parser.Program;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

class SemanticValidationTest {

    // ── contract builders ──────────────────────────────────────────────────────

    private static Map<String, Object> obj(Object... kv) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private static ComponentPromptSpec spec(Map<String, Object> properties, List<String> required) {
        return new ComponentPromptSpec(
                "comp", obj("type", "object", "properties", properties, "required", required));
    }

    /** Catalog with Header(title, subtitle?), Table(columns, rows), Col(title, field, options?). */
    private static ContractCatalog catalog() {
        Map<String, ComponentPromptSpec> comps = new LinkedHashMap<>();
        comps.put(
                "Header",
                spec(
                        obj("title", obj("type", "string"), "subtitle", obj("type", "string")),
                        List.of("title")));
        comps.put(
                "Table",
                spec(
                        obj("columns", obj("type", "array"), "rows", obj("type", "array")),
                        List.of("columns", "rows")));
        // Col.options is a closed object (additionalProperties:false) so removed `format` is
        // flagged.
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
                        obj(
                                "title",
                                obj("type", "string"),
                                "field",
                                obj("type", "string"),
                                "options",
                                optionsSchema),
                        List.of("title", "field")));
        return ContractCatalog.from(
                new GenerationContract(
                        "v1", "Header", comps, List.of(), List.of(), List.of(), List.of()));
    }

    private static ProgramAnalysis analyze(String dsl) {
        Program program = OpenuiParser.parse(dsl, ParseMode.FINAL);
        return new ProgramAnalyzer(catalog()).analyze(program, null, Set.of());
    }

    private static ValidationIssue issue(ProgramAnalysis a, String code) {
        return a.issues().stream().filter(i -> i.code().equals(code)).findFirst().orElse(null);
    }

    // ── 3.6 required coverage ───────────────────────────────────────────────────

    @Test
    void unknownComponent() {
        ProgramAnalysis a = analyze("root = Bogus(\"x\")");
        ValidationIssue i = issue(a, "unknown-component");
        assertNotNull(i, "expected unknown-component issue");
        assertEquals("Bogus", i.component());
        assertEquals("", i.path());
        assertEquals("root", i.statementId());
        assertEquals(ValidationSeverity.ERROR, i.severity());
    }

    @Test
    void missingRequired() {
        ProgramAnalysis a = analyze("root = Header()");
        ValidationIssue i = issue(a, "missing-required");
        assertNotNull(i, "expected missing-required issue");
        assertEquals("Header", i.component());
        assertEquals("/title", i.path());
        assertEquals("root", i.statementId());
        assertEquals("missing required field \"title\"", i.message());
    }

    @Test
    void nullRequired() {
        ProgramAnalysis a = analyze("root = Header(null)");
        ValidationIssue i = issue(a, "null-required");
        assertNotNull(i, "expected null-required issue");
        assertEquals("Header", i.component());
        assertEquals("/title", i.path());
        assertEquals("required field \"title\" cannot be null", i.message());
    }

    @Test
    void colOptionsFormatRemoved() {
        ProgramAnalysis a = analyze("root = Col(\"Name\", \"name\", { format: \"date\" })");
        ValidationIssue i = issue(a, "invalid-prop");
        assertNotNull(i, "expected invalid-prop issue");
        assertEquals("Col", i.component());
        assertEquals("/options/format", i.path());
        assertEquals(ComponentContractValidator.COL_OPTIONS_FORMAT_MESSAGE, i.message());
    }

    @Test
    void excessArgs() {
        ProgramAnalysis a = analyze("root = Header(\"Title\", \"Sub\", \"Extra\")");
        ValidationIssue i = issue(a, "excess-args");
        assertNotNull(i, "expected excess-args issue");
        assertEquals("Header", i.component());
        assertEquals("", i.path());
        assertEquals("Header takes 2 arg(s), got 3 (1 excess dropped)", i.message());
    }

    @Test
    void rootMissingFinal() {
        // Only a $state statement — no renderable component.
        ProgramAnalysis a = analyze("$count = 0");
        ValidationIssue i = issue(a, "root-not-renderable");
        ValidationIssue m = issue(a, "root-missing");
        assertTrue(i != null || m != null, "expected a root structural issue");
        assertFalse(a.rootResolved());
    }

    @Test
    void emptyProgramRootMissingFinal() {
        Program program = OpenuiParser.parse("", ParseMode.FINAL);
        ProgramAnalysis a = new ProgramAnalyzer(catalog()).analyze(program, null, Set.of());
        ValidationIssue i = issue(a, "root-missing");
        assertNotNull(i, "expected root-missing on empty program");
        assertEquals(ValidationSeverity.ERROR, i.severity());
    }

    @Test
    void finalUnresolvedRefIsBlocking() {
        ProgramAnalysis a = analyze("root = Header(\"Hi\", missingRef)");
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
        ProgramAnalysis a = analyze(dsl);
        ValidationIssue i = issue(a, "unresolved-ref");
        assertNotNull(i, "expected unresolved-ref issue");
        assertEquals("kpi", i.statementId());
        assertEquals(1, i.line());
        assertTrue(i.column() > 0);
    }

    @Test
    void unresolvedJsGlobalHintRoutedThroughRepairHints() {
        // `Math` leaked from JS → the FINAL unresolved-ref hint must be the root-cause hint,
        // not "define a statement named Math or pass it as an external ref".
        ProgramAnalysis a = analyze("root = Header(\"T\", Math)");
        ValidationIssue i = issue(a, "unresolved-ref");
        assertNotNull(i, "expected unresolved-ref issue");
        assertEquals(RepairHints.unresolvedRefHint("Math"), i.hint());
        assertTrue(RepairHints.isRootCauseHint(i.hint()));
    }

    @Test
    void unresolvedBuiltinNameHintSuggestsAtPrefix() {
        ProgramAnalysis a = analyze("root = Header(\"T\", FormatNumber)");
        ValidationIssue i = issue(a, "unresolved-ref");
        assertNotNull(i, "expected unresolved-ref issue");
        assertEquals(RepairHints.unresolvedRefHint("FormatNumber"), i.hint());
    }

    @Test
    void lastWinsDuplicateId() {
        // Two statements with id `root`: the LAST wins → Table (renderable, valid).
        // The first (Header()) would be missing-required; if last-wins works, no missing-required
        // fires.
        String dsl = "root = Header()\nroot = Table([], [])";
        ProgramAnalysis a = analyze(dsl);
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
                        new GenerationContract(
                                "v1", "Box", comps, List.of(), List.of(), List.of(), List.of()));
        Program program = OpenuiParser.parse("root = Box()", ParseMode.FINAL);
        ProgramAnalysis a = new ProgramAnalyzer(cat).analyze(program, null, Set.of());
        assertNull(
                a.issues().stream()
                        .filter(i -> i.code().equals("missing-required"))
                        .findFirst()
                        .orElse(null));
        assertTrue(a.rootResolved());
    }

    @Test
    void signatureOnlyComponentSkipsPropChecks() {
        Map<String, ComponentPromptSpec> comps = new LinkedHashMap<>();
        comps.put("Widget", new ComponentPromptSpec("Widget(a, b)", "a widget"));
        ContractCatalog cat =
                ContractCatalog.from(
                        new GenerationContract(
                                "v1", "Widget", comps, List.of(), List.of(), List.of(), List.of()));
        Program program = OpenuiParser.parse("root = Widget()", ParseMode.FINAL);
        ProgramAnalysis a = new ProgramAnalyzer(cat).analyze(program, null, Set.of());
        // Existence OK, no prop-level false positives.
        assertTrue(a.issues().stream().noneMatch(i -> i.code().equals("missing-required")));
        assertTrue(a.issues().stream().noneMatch(i -> i.code().equals("unknown-component")));
        assertTrue(a.rootResolved());
    }

    @Test
    void validProgramHasNoBlockingIssues() {
        ProgramAnalysis a = analyze("root = Header(\"Hello\", \"World\")");
        assertTrue(
                a.issues().stream().noneMatch(i -> i.severity() == ValidationSeverity.ERROR),
                "clean program should have no blocking issues: " + a.issues());
        assertTrue(a.rootResolved());
        assertEquals("root", a.entryId());
    }

    // ── Lazy builtin binder scoping (@Render/@Each) — mirrors materialize.ts ───

    /**
     * {@code @Render("v", template)}: the string-literal binder "v" must be in scope while the
     * template argument is walked, so {@code v} inside the template is NOT an unresolved reference.
     */
    @Test
    void renderStringBinderResolvesInsideTemplate() {
        ProgramAnalysis a = analyze("root = Header(\"T\", @Render(\"v\", Header(v)))");
        assertNull(
                issue(a, "unresolved-ref"),
                "@Render binder \"v\" must be scoped inside its template: " + a.issues());
        assertTrue(a.rootResolved());
    }

    /**
     * {@code @Each(array, varName, template)}: with three args the second arg is the binder; refs
     * to it inside the template argument resolve.
     */
    @Test
    void eachStringBinderResolvesInsideTemplate() {
        String dsl = "rows = [1, 2]\nroot = Header(\"T\", @Each(rows, \"item\", Header(item)))";
        ProgramAnalysis a = analyze(dsl);
        assertNull(
                issue(a, "unresolved-ref"),
                "@Each binder \"item\" must be scoped inside its template: " + a.issues());
        assertTrue(a.rootResolved());
    }

    /** The binder may also be written as a bare identifier (Ref form), matching the TS oracle. */
    @Test
    void eachRefBinderResolvesInsideTemplate() {
        String dsl = "rows = [1, 2]\nroot = Header(\"T\", @Each(rows, item, Header(item)))";
        ProgramAnalysis a = analyze(dsl);
        assertNull(
                issue(a, "unresolved-ref"),
                "@Each ref-form binder \"item\" must be scoped inside its template: " + a.issues());
    }

    /**
     * A two-arg {@code @Each} has no binder position — its second arg is an ordinary ref and stays
     * unresolved.
     */
    @Test
    void eachWithoutTemplateArgHasNoBinderScope() {
        String dsl = "rows = [1, 2]\nroot = Header(\"T\", @Each(rows, item))";
        ProgramAnalysis a = analyze(dsl);
        ValidationIssue i = issue(a, "unresolved-ref");
        assertNotNull(i, "two-arg @Each must not create a binder scope");
        assertTrue(a.unresolvedRefs().contains("item"));
    }

    /**
     * The binder scope covers ONLY the lazy builtin's own arguments — it must not leak to sibling
     * args.
     */
    @Test
    void binderNotVisibleOutsideTemplate() {
        ProgramAnalysis a = analyze("root = Header(@Render(\"v\", Header(v)), v)");
        ValidationIssue i = issue(a, "unresolved-ref");
        assertNotNull(i, "binder \"v\" must not leak outside the @Render call");
        assertTrue(a.unresolvedRefs().contains("v"));
    }

    /**
     * Nested lazy builtins stack scopes: the inner scope pops without dropping the outer binder.
     */
    @Test
    void nestedLazyBuiltinsStackScopes() {
        String dsl =
                "root = Header(\"T\", @Render(\"a\", Header(@Each([1], \"b\", Header(b)), a)))";
        ProgramAnalysis a = analyze(dsl);
        assertNull(
                issue(a, "unresolved-ref"),
                "outer binder \"a\" must survive the inner @Each scope pop: " + a.issues());
    }

    /**
     * The binder stays visible when the template references a NAMED statement whose expression uses
     * it (TS materialize.ts propagates scopedRefs through resolveRef in expr mode).
     */
    @Test
    void binderVisibleInsideReferencedStatementTemplate() {
        String dsl = "cell = Header(\"C\", v)\nroot = Header(\"T\", @Render(\"v\", cell))";
        ProgramAnalysis a = analyze(dsl);
        assertNull(
                issue(a, "unresolved-ref"),
                "binder \"v\" must remain visible inside the referenced statement's template: "
                        + a.issues());
    }

    // ── #75: unknown-component 3-tier routing & cascade suppression exemption ──

    @Test
    void unknownComponentRoutesThreeTiersFromCurrentContract() {
        ContractCatalog defaultCat = ContractCatalog.from(GenerationContractLoader.loadDefault());
        ProgramAnalyzer analyzer = new ProgramAnalyzer(defaultCat);

        // Tier 1: Action step called without leading '@'
        ProgramAnalysis tier1 =
                analyzer.analyze(
                        OpenuiParser.parse("root = Set($count, 1)", ParseMode.FINAL),
                        null,
                        Set.of());
        ValidationIssue i1 = issue(tier1, "unknown-component");
        assertNotNull(i1);
        assertEquals("Unknown component \"Set\" — not found in catalog or builtins", i1.message());
        assertEquals(
                "did you mean \"@Set\"? openui-lang builtins must be called with a leading '@'",
                i1.hint());
        assertTrue(RepairHints.isRootCauseHint(i1.hint()));

        // Tier 2: capabilityGaps alias (both @GroupBy and @ObjectFromEntries)
        ProgramAnalysis tier2 =
                analyzer.analyze(
                        OpenuiParser.parse(
                                "grouped = @GroupBy(data.items, \"model\")\nroot = Stack([grouped])",
                                ParseMode.FINAL),
                        "Stack",
                        Set.of("data"));
        ValidationIssue i2 = issue(tier2, "unknown-component");
        assertNotNull(i2);
        assertEquals(
                "Unknown component \"GroupBy\" — not found in catalog or builtins",
                i2.message(),
                "ADR-0003 D2: message must stay unchanged");
        assertNotNull(i2.hint());
        assertTrue(
                i2.hint().contains("分组展示"), "tier-2 hint must identify group-by gap: " + i2.hint());
        assertTrue(RepairHints.isRootCauseHint(i2.hint()), "tier-2 hint must be a root-cause hint");

        // Tier 3: ordinary unknown component name
        ProgramAnalysis tier3 =
                analyzer.analyze(
                        OpenuiParser.parse("root = ChartCard(\"T\")", ParseMode.FINAL),
                        null,
                        Set.of());
        ValidationIssue i3 = issue(tier3, "unknown-component");
        assertNotNull(i3);
        assertEquals(
                "Unknown component \"ChartCard\" — not found in catalog or builtins", i3.message());
        assertNotNull(i3.hint());
        assertFalse(
                RepairHints.isRootCauseHint(i3.hint()),
                "tier-3 hint must not be a root-cause hint");
    }

    @Test
    void capabilityGapUnknownComponentIsExemptFromSyntaxCascadeSuppression() {
        ContractCatalog defaultCat = ContractCatalog.from(GenerationContractLoader.loadDefault());
        ProgramAnalyzer analyzer = new ProgramAnalyzer(defaultCat);

        // Statement `grouped` has both a JS arrow function syntax error (`=>`) AND `@GroupBy(...)`.
        Program program =
                OpenuiParser.parse(
                        "grouped = @GroupBy(data.items, item => item.model)\nroot = Stack([grouped])",
                        ParseMode.FINAL);
        ProgramAnalysis analysis = analyzer.analyze(program, "Stack", Set.of("data"));
        List<ValidationIssue> actionable = ValidationIssueReducer.actionable(analysis.issues());

        assertTrue(
                actionable.stream()
                        .anyMatch(
                                i ->
                                        "unknown-component".equals(i.code())
                                                && "GroupBy".equals(i.component())),
                "tier-2 capability gap unknown-component must survive syntax cascade suppression; got: "
                        + actionable);
    }
}
