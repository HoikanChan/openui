/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.repair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.contract.ComponentPromptSpec;
import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import com.huawei.cloudsop.genui.core.llm.protocol.ChatMessage;
import com.huawei.cloudsop.genui.core.validation.ValidationIssue;
import com.huawei.cloudsop.genui.core.validation.ValidationSeverity;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Pure/deterministic assertions on {@link ReaskPromptBuilder} output.
 *
 * @since 2026
 */
class ReaskPromptBuilderTest {

    private static final String GOLDEN_INTENT = "show {{name}} <b>$ \\ 中文";
    private static final String GOLDEN_DSL = "root = Stack([kpiValue])\nkpiValue = Bogus(title: \"x\", \"extra\")";

    private static GenerationContract contractWith(String name, String signature) {
        return new GenerationContract("1.0", "Stack", Map.of(name, new ComponentPromptSpec(signature, "desc")),
                List.of(), List.of(), List.of(), List.of());
    }

    private static ValidationIssue issue(String code, String message, String component, String hint) {
        return new ValidationIssue(code, ValidationSeverity.ERROR, "contract", message, null, component,
                "root.children[0]", 3, 5, hint, false);
    }

    private static String join(List<ChatMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage m : messages) {
            sb.append(m.role()).append(':').append(m.content()).append('\n');
        }
        return sb.toString();
    }

    @Test
    void fullRepairContainsIssueCodeHintPrefixAndSignature() {
        ValidationIssue issue = issue("unknown-component", "Component 'Bogus' is not defined", "Bogus",
                "did you mean Card?");
        GenerationContract contract = contractWith("Bogus", "Bogus(title: string)");

        List<ChatMessage> messages = ReaskPromptBuilder.buildFullRepair("show a bogus widget", "root = Bogus(\"x\")",
                List.of(issue), contract);
        String text = join(messages);

        assertTrue(text.contains(ReaskPromptBuilder.FULL_REPAIR_MARKER), "full-repair marker present");
        assertTrue(text.contains("unknown-component"), "issue code present");
        assertTrue(text.contains("Component 'Bogus' is not defined"), "issue message present");
        assertTrue(text.contains("did you mean Card?"), "issue hint present");
        assertTrue(text.contains("root.children[0]"), "issue path present");
        assertTrue(text.contains("root = Bogus(\"x\")"), "the invalid (accepted-context) DSL present");
        assertTrue(text.contains("Bogus(title: string)"), "component signature hint present");
        assertTrue(text.contains("show a bogus widget"), "original user intent present");
    }

    @Test
    void twoMessagesSystemThenUser() {
        List<ChatMessage> messages = ReaskPromptBuilder.buildFullRepair("x", "root = Stack([])", List.of(), null);
        assertEquals(2, messages.size());
        assertEquals("system", messages.get(0).role());
        assertEquals("user", messages.get(1).role());
    }

    @Test
    void issueLineCarriesStatementIdAndPosition() {
        // Decision 11.3: multi-statement documents need per-issue location context.
        ValidationIssue located = new ValidationIssue("syntax-unexpected-token", ValidationSeverity.ERROR, "syntax",
                "Unexpected token R_PAREN", "kpiValue", null, null, 3, 51, null, false);
        String text = join(ReaskPromptBuilder.buildFullRepair("x", "root = Stack([])", List.of(located), null));
        assertTrue(text.contains("(stmt=kpiValue, line 3:51)"), "stmt and line:col rendered");
    }

    @Test
    void issueLineOmitsUnknownLocationParts() {
        ValidationIssue stmtOnly = new ValidationIssue("unresolved-ref", ValidationSeverity.ERROR, "reference",
                "unresolved reference \"x\"", "kpi", null, null, -1, -1, null, false);
        ValidationIssue neither = new ValidationIssue("root-missing", ValidationSeverity.ERROR, "root",
                "no root statement", null, null, null, -1, -1, null, false);
        String text = join(
                ReaskPromptBuilder.buildFullRepair("x", "root = Stack([])", List.of(stmtOnly, neither), null));
        assertTrue(text.contains("(stmt=kpi)"), "stmt-only location rendered without line part");
        assertFalse(text.contains("-1"), "unknown line/column never rendered");
    }

    // ── cascade suppression (Decision 11.4) ─────────────────────────────────────

    private static ValidationIssue syntaxError(String stmtId) {
        return new ValidationIssue("syntax-unexpected-token", ValidationSeverity.ERROR, "syntax",
                "Unexpected token R_PAREN", stmtId, null, null, 3, 51, null, false);
    }

    private static ValidationIssue derived(String code, String message, String stmtId, String hint) {
        return new ValidationIssue(code, ValidationSeverity.ERROR,
                code.equals("unresolved-ref") ? "reference" : "contract", message, stmtId, null, null, -1, -1, hint,
                false);
    }

    @Test
    void derivedIssuesOfSyntaxBrokenStatementAreSuppressed() {
        // Parse tore one expression into 3 args → fake excess-args + fake null-required.
        // Only the syntax root cause should reach the repair model.
        List<ValidationIssue> issues = List.of(syntaxError("kpiValue"),
                derived("excess-args", "TextContent takes 2 arg(s), got 3 (1 excess dropped)", "kpiValue", null),
                derived("null-required", "required field \"text\" cannot be null", "kpiValue", null),
                derived("unresolved-ref", "unresolved reference \"d\"", "kpiValue",
                        "define a statement named \"d\" earlier in the document"));
        String text = join(ReaskPromptBuilder.buildFullRepair("x", "root = Stack([])", issues, null));
        assertTrue(text.contains("syntax-unexpected-token"), "syntax root cause kept");
        assertFalse(text.contains("excess-args"), "derived excess-args suppressed");
        assertFalse(text.contains("null-required"), "derived null-required suppressed");
        assertFalse(text.contains("unresolved reference \"d\""), "derived unresolved-ref suppressed");
    }

    @Test
    void rootCauseHintedUnresolvedRefSurvivesSuppression() {
        // `Math.abs(v).toFixed(1)` → same statement carries a syntax ERROR and an unresolved-ref
        // whose hint IS the root-cause explanation (JS global → @Abs). It must not be suppressed.
        List<ValidationIssue> issues = List.of(syntaxError("kpiValue"),
                derived("unresolved-ref", "unresolved reference \"Math\"", "kpiValue",
                        com.huawei.cloudsop.genui.core.validation.semantic.RepairHints.unresolvedRefHint("Math")));
        String text = join(ReaskPromptBuilder.buildFullRepair("x", "root = Stack([])", issues, null));
        assertTrue(text.contains("unresolved reference \"Math\""), "root-cause-hinted ref kept");
        assertTrue(text.contains("JavaScript global"), "root-cause hint text kept");
    }

    @Test
    void derivedIssuesOfHealthyStatementsAreKept() {
        // Suppression is per-statement: a real excess-args on a statement WITHOUT syntax errors stays.
        List<ValidationIssue> issues = List.of(syntaxError("kpiValue"),
                derived("excess-args", "Header takes 2 arg(s), got 3", "header", null));
        String text = join(ReaskPromptBuilder.buildFullRepair("x", "root = Stack([])", issues, null));
        assertTrue(text.contains("excess-args"), "independent excess-args kept");
    }

    @Test
    void rootNotRenderableSuppressedWhenAnotherErrorExplainsIt() {
        List<ValidationIssue> issues = List.of(derived("unknown-component", "Unknown component \"Div\"", "root", null),
                derived("root-not-renderable", "root did not materialize", "root", null));
        String text = join(ReaskPromptBuilder.buildFullRepair("x", "root = Div()", issues, null));
        assertTrue(text.contains("unknown-component"), "explaining error kept");
        assertFalse(text.contains("root-not-renderable"), "cascade tail suppressed");
    }

    @Test
    void rootNotRenderableKeptWhenSoleError() {
        List<ValidationIssue> issues = List
                .of(derived("root-not-renderable", "root did not materialize", "root", null));
        String text = join(ReaskPromptBuilder.buildFullRepair("x", "root = $count", issues, null));
        assertTrue(text.contains("root-not-renderable"), "sole structural error kept");
    }

    @Test
    void nonErrorIssuesAreNotRendered() {
        ValidationIssue warning = new ValidationIssue("soft-warn", ValidationSeverity.WARNING, "x", "just a warning",
                null, null, null, -1, -1, null, true);
        List<ChatMessage> messages = ReaskPromptBuilder.buildFullRepair("x", "root = Stack([])", List.of(warning),
                null);
        String text = join(messages);
        assertFalse(text.contains("soft-warn"), "non-ERROR issues must not drive repair");
    }

    // ── resource migration goldens ──────────────────────────────────────────────

    private static ValidationIssue syntaxIssue(String code, String message, String statementId, int line, int column) {
        return new ValidationIssue(code, ValidationSeverity.ERROR, "syntax", message, statementId, null, null, line,
                column, null, false);
    }

    /**
     * 迁移前的完整 system/user 消息原样留作黄金样本：整份修复改为资源加载后逐字符比对，覆盖正常场景、原始 system prompt、全部语法提示与空输入兜底。
     */
    @Test
    void completeMessagesMatchBeforeResourceMigration() throws Exception {
        GenerationContract contract = contractWith("Bogus", "Bogus(title: string)");
        List<ValidationIssue> issues = List.of(
                syntaxIssue("syntax-unexpected-token", "Unexpected token COLON", "kpiValue", 3, 51),
                syntaxIssue("syntax-unclosed-bracket", "unclosed bracket", "kpiValue", 4, 2),
                syntaxIssue("syntax-arrow", "unexpected ARROW token", "row", -1, -1),
                syntaxIssue("syntax-template", "unexpected TEMPLATE literal", "row", -1, -1),
                issue("unknown-component", "Component 'Bogus' is not defined", "Bogus", "did you mean Card?"));

        assertGolden("full-normal", ReaskPromptBuilder.buildFullRepair(GOLDEN_INTENT, GOLDEN_DSL, issues, contract));
        assertGolden("full-original-prompt", ReaskPromptBuilder.buildFullRepair(GOLDEN_INTENT, GOLDEN_DSL, issues,
                contract, "ORIGINAL SYSTEM PROMPT: 中文 {{value}} <>& $ \\\nsecond line"));

        // 每个 statement 只保留首条 syntax 错误，因此五个提示文案各占一条语句。
        List<ValidationIssue> onePerStatement = List.of(
                syntaxIssue("syntax-unexpected-token", "Unexpected token COLON", "namedArgs", 10, 7),
                syntaxIssue("syntax-unclosed-bracket", "unclosed bracket", "unclosed", 11, 1),
                syntaxIssue("syntax-arrow", "unexpected ARROW token", "arrow", 12, 3),
                syntaxIssue("syntax-template", "unexpected TEMPLATE literal", "template", 13, 5));
        assertGolden("full-syntax-hints",
                ReaskPromptBuilder.buildFullRepair("hints", "root = Stack([])", onePerStatement, null));

        assertGolden("full-missing", ReaskPromptBuilder.buildFullRepair(null, null, List.of(), null, "   "));
    }

    /**
     * CRLF 是调用方输入里的真实内容：旧实现只 trim 两端，内部 {@code \r\n} 原样保留。该场景的黄金文件按字面存储， 因此不做行尾归一化。
     */
    @Test
    void crlfInputsArePreservedVerbatim() throws Exception {
        GenerationContract contract = contractWith("Header", "Header(title: string)");
        List<ValidationIssue> issues = List.of(new ValidationIssue("unknown-component", ValidationSeverity.ERROR,
                "contract", "Component 'Header' arg mismatch", null, "Header", "root.children[0]", 2, 1, null, false));
        assertGoldenExact("full-crlf",
                ReaskPromptBuilder.buildFullRepair("第一行 {{name}} <>& $ \\\r\n第二行",
                        "root = Stack([header])\r\nheader = Header(\"中文 {{value}}\")", issues, contract,
                        "ORIGINAL SYSTEM PROMPT\r\n中文 {{value}} <>& $ \\"));
    }

    /**
     * 与 {@link #assertGolden} 相同，但对 {@code -text} 黄金文件按字节比较，不做行尾归一化。
     */
    private static void assertGoldenExact(String name, List<ChatMessage> messages) throws Exception {
        assertEquals(2, messages.size());
        for (int i = 0; i < messages.size(); i++) {
            String role = i == 0 ? "system" : "user";
            assertEquals(role, messages.get(i).role());
            String path = "repair-prompts/baseline/" + name + "-" + role + ".txt";
            try (var stream = ReaskPromptBuilderTest.class.getClassLoader().getResourceAsStream(path)) {
                assertNotNull(stream, path);
                assertEquals(new String(stream.readAllBytes(), StandardCharsets.UTF_8), messages.get(i).content(),
                        path);
            }
        }
    }

    private static void assertGolden(String name, List<ChatMessage> messages) throws Exception {
        assertEquals(2, messages.size());
        for (int i = 0; i < messages.size(); i++) {
            String role = i == 0 ? "system" : "user";
            assertEquals(role, messages.get(i).role());
            String path = "repair-prompts/baseline/" + name + "-" + role + ".txt";
            try (var stream = ReaskPromptBuilderTest.class.getClassLoader().getResourceAsStream(path)) {
                assertNotNull(stream, path);
                assertEquals(new String(stream.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n"),
                        messages.get(i).content(), path);
            }
        }
    }
}
