/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.repair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.contract.BuiltinSpec;
import com.huawei.cloudsop.genui.core.contract.ComponentPromptSpec;
import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import com.huawei.cloudsop.genui.core.contract.ToolSpec;
import com.huawei.cloudsop.genui.core.llm.protocol.ChatMessage;
import com.huawei.cloudsop.genui.core.validation.ValidationIssue;
import com.huawei.cloudsop.genui.core.validation.ValidationResult;
import com.huawei.cloudsop.genui.core.validation.ValidationSeverity;
import com.huawei.cloudsop.genui.core.validation.stream.RoundEndResult;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 增量修复提示词的必要规则与上下文断言（不逐字匹配全文）。
 *
 * @since 2026
 */
class IncrementalRepairPromptBuilderTest {

    private static final String SYSTEM_PROMPT = "ORIGINAL SYSTEM PROMPT: use openui-lang only.";

    private static GenerationContract contract() {
        LinkedHashMap<String, ComponentPromptSpec> components = new LinkedHashMap<>();
        components.put("Stack", new ComponentPromptSpec("Stack(children: Component[])", "vertical layout"));
        components.put("Header", new ComponentPromptSpec("Header(text: string)", "page header"));

        BuiltinSpec each = new BuiltinSpec("Each", "Each(array, item, template)", "iterate an array in a template",
                true, List.of(), null);
        ToolSpec tool = new ToolSpec("get_sales", "read sales rows",
                Map.of("type", "object", "properties", Map.of("days", Map.of("type", "number"))),
                Map.of("type", "array"), null);
        return new GenerationContract("contract-7", "Stack", components, List.of(), List.of(tool),
                List.of("root = Stack([header])\nheader = Header(\"Hi\")"), List.of("Never invent tools."),
                List.of(each));
    }

    private static ValidationIssue issue(String code, String source, String message, String statementId, String hint) {
        return new ValidationIssue(code, ValidationSeverity.ERROR, source, message, statementId, null, null, 4, 9, hint,
                false);
    }

    private static RoundEndResult round(String raw, String definitions, List<ValidationIssue> issues) {
        ValidationResult result = ValidationResult.invalid(issues, null);
        return new RoundEndResult(raw, definitions, result, 12L, "contract-7", null);
    }

    private static String join(List<ChatMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage message : messages) {
            sb.append(message.role()).append(':').append(message.content()).append('\n');
        }
        return sb.toString();
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    @Test
    void buildsSystemThenUserAndKeepsOriginalSystemPromptExactlyOnce() {
        List<ChatMessage> messages = IncrementalRepairPromptBuilder.build(SYSTEM_PROMPT, "make a dashboard", contract(),
                round("header = Header(\"Hi\")", "header = Header(\"Hi\")", List.of()));

        assertEquals(2, messages.size());
        assertEquals("system", messages.get(0).role());
        assertEquals("user", messages.get(1).role());
        assertTrue(messages.get(0).content().contains(IncrementalRepairPromptBuilder.INCREMENTAL_REPAIR_MARKER));
        assertEquals(1, occurrences(join(messages), SYSTEM_PROMPT), "system prompt reproduced once");
    }

    @Test
    void carriesRequestCurrentProgramLatestDiagnosticsAndLocation() {
        String definitions = "root = Stack([header])\nheader = Header(\"Hi\")";
        ValidationIssue unresolved = issue("unresolved-ref", "reference", "unresolved reference \"title\"", "header",
                "define a statement named \"title\" earlier in the document");
        String text = join(IncrementalRepairPromptBuilder.build(SYSTEM_PROMPT, "show the header", contract(),
                round("header = Header(title)", definitions, List.of(unresolved))));

        assertTrue(text.contains("show the header"), "original request present");
        assertTrue(text.contains(definitions), "current effective DSL present");
        assertTrue(text.contains("unresolved-ref"), "latest diagnostic code present");
        assertTrue(text.contains("unresolved reference \"title\""), "latest diagnostic message present");
        assertTrue(text.contains("(stmt=header, line 4:9)"), "diagnostic location present");
        assertTrue(text.contains("define a statement named \"title\""), "diagnostic hint present");
    }

    @Test
    void includesFullCurrentContractWithoutInventingNames() {
        String text = join(IncrementalRepairPromptBuilder.build(SYSTEM_PROMPT, "x", contract(),
                round("", "root = Stack([])", List.of())));

        assertTrue(text.contains("contract-7"), "contract version present");
        assertTrue(text.contains("Stack(children: Component[])"), "component signature present");
        assertTrue(text.contains("Header(text: string)"), "all component signatures present");
        assertTrue(text.contains("@Each(array, item, template)"), "builtin signature present");
        assertTrue(text.contains("get_sales"), "tool present");
        assertTrue(text.contains("Never invent tools."), "additional rule present");
        assertTrue(text.contains("root = Stack([header])"), "example present");
    }

    @Test
    void carriesCallerDataContextWithSampleAndFabricationLimits() {
        String dataContext = "data.sales: array<{region: string, revenue: number}>\n"
                + "sample rows: 3 of 1200; scale: 1200 rows total";
        String text = join(IncrementalRepairPromptBuilder.build(SYSTEM_PROMPT, "x", contract(),
                round("", "root = Stack([])", List.of()), List.of(), dataContext));

        assertTrue(text.contains("sample rows: 3 of 1200; scale: 1200 rows total"), "data context verbatim");
        assertTrue(text.contains("may describe only part of the render data model"), "sample caveat present");
        assertTrue(text.contains("Do not treat sample rows as the full dataset"), "sample is not full data");
        assertTrue(text.contains("do not invent fields, functions, data, or statistics"), "no fabrication rule");
        assertTrue(text.contains("state the limitation in the page"), "real data + page explanation rule");
    }

    @Test
    void selectsGuidanceFromCurrentDiagnosticsOnly() {
        ValidationIssue syntax = issue("syntax-unclosed-bracket", "syntax", "unclosed '('", "header", null);
        String text = join(IncrementalRepairPromptBuilder.build(SYSTEM_PROMPT, "x", contract(),
                round("header = Header(", "header = Header(", List.of(syntax))));

        assertTrue(text.contains("Diagnostic-selected guidance"), "guidance section present");
        assertTrue(text.contains("Rewrite the whole diagnosed statement"), "syntax guidance selected");
        assertTrue(text.contains("Balance every"), "syntax mechanics selected");
        assertFalse(text.contains("data.<path> must exist"), "unrelated data guidance not selected");
    }

    @Test
    void selectsReferenceGuidanceForUnresolvedRef() {
        ValidationIssue unresolved = issue("unresolved-ref", "reference", "unresolved reference \"Each\"", "rows",
                "did you mean \"@Each\"?");
        String text = join(IncrementalRepairPromptBuilder.build(SYSTEM_PROMPT, "x", contract(),
                round("rows = Each([])", "rows = Each([])", List.of(unresolved))));

        assertTrue(text.contains("`@name(...)` is a builtin call"), "call classification guidance present");
        assertTrue(text.contains("add the leading `@`"), "missing @ guidance selected");
    }

    @Test
    void fixedRulesPinPatchOnlyScopeAndStaleLabelHandling() {
        String text = join(IncrementalRepairPromptBuilder.build(SYSTEM_PROMPT, "x", contract(),
                round("", "root = Stack([])", List.of())));

        assertTrue(text.contains("This is a patch, not a page"), "patch-only scope");
        assertTrue(text.contains("Do not output a full page, JSON"), "no full page/JSON");
        assertTrue(text.contains("Never emit a deletion"), "no deletion");
        assertTrue(text.contains("last-wins"), "override semantics");
        assertTrue(
                text.contains("A bare identifier is a statement reference")
                        || text.contains("a bare identifier is a statement reference"),
                "unknown-call syntax classification");
        assertTrue(text.contains("error code may be stale"), "historical labels not trusted");
        assertTrue(text.contains("current contract"), "contract-based classification");
    }

    @Test
    void rendersRelatedStatementsForDiagnosedId() {
        String definitions = "root = Stack([header])\nheader = Header(title)\ntitle = TextContent(\"Hi\")";
        ValidationIssue issue = issue("type-prop-mismatch", "type", "title must be string", "header", null);
        String text = join(IncrementalRepairPromptBuilder.build(SYSTEM_PROMPT, "x", contract(),
                round("header = Header(title)", definitions, List.of(issue))));

        assertTrue(text.contains("Related statements for diagnosed ids"), "related-statements section present");
        assertTrue(text.contains("upstream/downstream related statements"), "direction context present");
        assertTrue(text.contains("`header`"), "diagnosed statement id present in relation context");
    }

    @Test
    void historyKeepsRawOutputAndCompactCodesButNotFullMessages() {
        ValidationIssue old = issue("old-history-code", "contract", "HISTORY_MESSAGE_MUST_NOT_APPEAR", "header", null);
        RoundEndResult earlier = round("OLD_RAW_OUTPUT = Header(\"old\")", "header = Header(\"old\")", List.of(old));
        String text = join(IncrementalRepairPromptBuilder.build(SYSTEM_PROMPT, "x", contract(),
                round("header = Header(\"new\")", "header = Header(\"new\")", List.of()), List.of(earlier)));

        assertTrue(text.contains("OLD_RAW_OUTPUT"), "historical raw output retained");
        assertTrue(text.contains("old-history-code"), "historical issue codes retained");
        assertFalse(text.contains("HISTORY_MESSAGE_MUST_NOT_APPEAR"), "history stays compact, not full diagnostics");
        assertTrue(text.contains("never override the current DSL"), "history is subordinate to current state");
    }

    @Test
    void latestViewUsesActionableErrorsOnly() {
        ValidationIssue syntax = issue("syntax-unexpected-token", "syntax", "Unexpected token", "kpi", null);
        ValidationIssue derived = issue("excess-args", "contract", "TextContent takes 2 args", "kpi", null);
        ValidationIssue warning = new ValidationIssue("soft-warn", ValidationSeverity.WARNING, "x",
                "WARNING_MUST_NOT_APPEAR", null, null, null, -1, -1, null, true);
        String text = join(IncrementalRepairPromptBuilder.build(SYSTEM_PROMPT, "x", contract(),
                round("kpi = TextContent(\"a\", \"b\", \"c\")", "kpi = TextContent(\"a\", \"b\", \"c\")",
                        List.of(syntax, derived, warning))));

        assertTrue(text.contains("syntax-unexpected-token"), "syntax root cause kept");
        assertFalse(text.contains("excess-args"), "derived issue suppressed in latest view");
        assertFalse(text.contains("WARNING_MUST_NOT_APPEAR"), "warnings do not drive repair");
    }

    @Test
    void missingSystemPromptAndDataContextAreStatedWithoutInvention() {
        String text = join(IncrementalRepairPromptBuilder.build(null, null, null, round("", "", List.of())));

        assertTrue(text.contains("No original system prompt was supplied"), "missing system prompt is explicit");
        assertTrue(text.contains("No data context was supplied by the caller"), "missing data context is explicit");
        assertTrue(text.contains("No contract object was supplied"), "missing contract is explicit");
        assertTrue(text.contains("invent nothing"), "no invention rule present");
    }

    @Test
    void currentRoundIsRequired() {
        assertThrows(NullPointerException.class,
                () -> IncrementalRepairPromptBuilder.build(SYSTEM_PROMPT, "x", contract(), null));
    }

    @Test
    void fiveArgOverloadDefaultsToNoHistory() {
        String text = join(IncrementalRepairPromptBuilder.build(SYSTEM_PROMPT, "x", contract(),
                round("", "root = Stack([])", List.of())));
        assertTrue(text.contains("(no earlier rounds)"), "empty history rendered explicitly");
    }
}
