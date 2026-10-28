/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.repair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.contract.BuiltinSpec;
import com.huawei.cloudsop.genui.core.contract.CapabilityGapSpec;
import com.huawei.cloudsop.genui.core.contract.ComponentPromptSpec;
import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import com.huawei.cloudsop.genui.core.contract.ToolSpec;
import com.huawei.cloudsop.genui.core.llm.protocol.ChatMessage;
import com.huawei.cloudsop.genui.core.validation.ValidationIssue;
import com.huawei.cloudsop.genui.core.validation.ValidationResult;
import com.huawei.cloudsop.genui.core.validation.ValidationSeverity;
import com.huawei.cloudsop.genui.core.validation.semantic.RepairHints;
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
class RepairPromptBuilderTest {

    private static final String SYSTEM_PROMPT = "ORIGINAL SYSTEM PROMPT: use openui-lang only.";

    private static final CapabilityGapSpec GROUP_BY_GAP = new CapabilityGapSpec("group-by", "data", "分组展示",
            List.of("GroupBy", "Distinct", "Unique", "CountBy", "Pivot", "Aggregate", "Partition", "ObjectFromEntries"),
            List.of("按X分组", "每个X下列出", "按X汇总", "去重"), "用一张 Table 展示全部行；用 @Sort 按分组字段排序，并把该字段放在第一列");

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
                List.of(each), List.of(GROUP_BY_GAP));
    }

    private static ValidationIssue issue(String code, String source, String message, String statementId, String hint) {
        return new ValidationIssue(code, ValidationSeverity.ERROR, source, message, statementId, null, null, 4, 9, hint,
                false);
    }

    private static ValidationIssue componentIssue(String code, String source, String message, String statementId,
            String component, String hint) {
        return new ValidationIssue(code, ValidationSeverity.ERROR, source, message, statementId, component, "", 4, 9,
                hint, false);
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
    void guidanceHasFixedOrderRegardlessOfDiagnosticOrder() {
        ValidationIssue syntax = issue("syntax-unclosed-bracket", "syntax", "broken", "a", null);
        ValidationIssue reference = issue("unresolved-ref", "reference", "missing", "b", null);
        String forward = RepairPromptBuilder.build(null, null, null,
                round("", "", List.of(syntax, reference))).get(0).content();
        String reversed = RepairPromptBuilder.build(null, null, null,
                round("", "", List.of(reference, syntax))).get(0).content();
        assertEquals(forward, reversed);
        assertTrue(forward.indexOf("Rewrite the whole diagnosed statement")
                < forward.indexOf("Classify the unresolved name"));
    }

    @Test
    void preservesOriginalSystemPrefixAcrossRepairRoundsWithoutDuplicatingContext() {
        String original = "  original contract" + (char) 13 + (char) 10 + "{{literal}}" + (char) 10 + " data context  ";
        for (List<ValidationIssue> issues : List.of(List.<ValidationIssue>of(),
                List.of(issue("syntax-unclosed-bracket", "syntax", "broken", "root", null)))) {
            String system = RepairPromptBuilder.build(original, "x", contract(),
                    round("", "root = Stack([])", issues), List.of())
                    .get(0).content();
            assertTrue(system.startsWith(original));
            assertEquals(1, occurrences(system, original));
            assertFalse(system.contains("Stack(children: Component[])"));
            assertTrue(system.indexOf(RepairPromptBuilder.INCREMENTAL_REPAIR_MARKER)
                    >= original.length());
        }
    }

    @Test
    void buildsSystemThenUserAndKeepsOriginalSystemPromptExactlyOnce() {
        List<ChatMessage> messages = RepairPromptBuilder.build(SYSTEM_PROMPT, "make a dashboard", contract(),
                round("header = Header(\"Hi\")", "header = Header(\"Hi\")", List.of()));

        assertEquals(2, messages.size());
        assertEquals("system", messages.get(0).role());
        assertEquals("user", messages.get(1).role());
        assertTrue(messages.get(0).content().contains(RepairPromptBuilder.INCREMENTAL_REPAIR_MARKER));
        assertEquals(1, occurrences(join(messages), SYSTEM_PROMPT), "system prompt reproduced once");
    }

    @Test
    void carriesRequestCurrentProgramLatestDiagnosticsAndLocation() {
        String definitions = "root = Stack([header])\nheader = Header(\"Hi\")";
        ValidationIssue unresolved = issue("unresolved-ref", "reference", "unresolved reference \"title\"", "header",
                "define a statement named \"title\" earlier in the document");
        String text = join(RepairPromptBuilder.build(SYSTEM_PROMPT, "show the header", contract(),
                round("header = Header(title)", definitions, List.of(unresolved))));

        assertTrue(text.contains("show the header"), "original request present");
        assertTrue(text.contains(definitions), "current effective DSL present");
        assertTrue(text.contains("unresolved-ref"), "latest diagnostic code present");
        assertTrue(text.contains("unresolved reference \"title\""), "latest diagnostic message present");
        assertTrue(text.contains("(stmt=header, line 4:9)"), "diagnostic location present");
        assertTrue(text.contains("define a statement named \"title\""), "diagnostic hint present");
    }





    @Test
    void selectsGuidanceFromCurrentDiagnosticsOnly() {
        ValidationIssue syntax = issue("syntax-unclosed-bracket", "syntax", "unclosed '('", "header", null);
        String text = join(RepairPromptBuilder.build(SYSTEM_PROMPT, "x", contract(),
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
        String text = join(RepairPromptBuilder.build(SYSTEM_PROMPT, "x", contract(),
                round("rows = Each([])", "rows = Each([])", List.of(unresolved))));

        assertTrue(text.contains("`@name(...)` is a builtin call"), "call classification guidance present");
        assertTrue(text.contains("add the leading `@`"), "missing @ guidance selected");
    }

    @Test
    void fixedRulesPinPatchOnlyScopeAndStaleLabelHandling() {
        String text = join(RepairPromptBuilder.build(SYSTEM_PROMPT, "x", contract(),
                round("", "root = Stack([])", List.of())));

        assertTrue(text.contains("This is a patch, not a page"), "patch-only scope");
        assertTrue(text.contains("Do not output a full page, JSON"), "no full page/JSON");
        assertTrue(text.contains("Never emit a deletion"), "no deletion");
        assertTrue(text.contains("last-wins"), "override semantics");

    }

    @Test
    void rendersRelatedStatementsForDiagnosedId() {
        String definitions = "root = Stack([header])\nheader = Header(title)\ntitle = TextContent(\"Hi\")";
        ValidationIssue issue = issue("type-prop-mismatch", "type", "title must be string", "header", null);
        String text = join(RepairPromptBuilder.build(SYSTEM_PROMPT, "x", contract(),
                round("header = Header(title)", definitions, List.of(issue))));

        assertTrue(text.contains("Related statements for diagnosed ids"), "related-statements section present");
        assertTrue(text.contains("upstream/downstream related statements"), "direction context present");
        assertTrue(text.contains("`header`"), "diagnosed statement id present in relation context");
    }

    @Test
    void historyKeepsRawOutputAndCompactCodesButNotFullMessages() {
        ValidationIssue old = issue("old-history-code", "contract", "HISTORY_MESSAGE_MUST_NOT_APPEAR", "header", null);
        RoundEndResult earlier = round("OLD_RAW_OUTPUT = Header(\"old\")", "header = Header(\"old\")", List.of(old));
        String text = join(RepairPromptBuilder.build(SYSTEM_PROMPT, "x", contract(),
                round("header = Header(\"new\")", "header = Header(\"new\")", List.of(issue("old-history-code", "contract", "current", "header", null))), List.of(earlier)));

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
        String text = join(RepairPromptBuilder.build(SYSTEM_PROMPT, "x", contract(),
                round("kpi = TextContent(\"a\", \"b\", \"c\")", "kpi = TextContent(\"a\", \"b\", \"c\")",
                        List.of(syntax, derived, warning))));

        assertTrue(text.contains("syntax-unexpected-token"), "syntax root cause kept");
        assertFalse(text.contains("excess-args"), "derived issue suppressed in latest view");
        assertFalse(text.contains("WARNING_MUST_NOT_APPEAR"), "warnings do not drive repair");
    }



    @Test
    void rawOutputIsOnlyIncludedForSyntaxFailures() {
        String raw = "RAW_OUTPUT_ONLY";
        ValidationIssue type = issue("type-prop-mismatch", "type", "wrong type", "root", null);
        ValidationIssue syntax = issue("syntax-unexpected-token", "syntax", "broken", "root", null);
        assertFalse(join(RepairPromptBuilder.build(SYSTEM_PROMPT, "x", contract(),
                round(raw, "root = Stack([])", List.of(type)))).contains(raw));
        assertTrue(join(RepairPromptBuilder.build(SYSTEM_PROMPT, "x", contract(),
                round(raw, "root = Stack([])", List.of(syntax)))).contains(raw));
    }

    @Test
    void unrelatedHistoryIsNotIncluded() {
        RoundEndResult earlier = round("UNRELATED_HISTORY", "", List.of(issue("old", "contract", "old", "a", null)));
        String text = join(RepairPromptBuilder.build(SYSTEM_PROMPT, "x", contract(),
                round("", "root = Stack([])", List.of(issue("new", "contract", "new", "b", null))), List.of(earlier)));
        assertFalse(text.contains("UNRELATED_HISTORY"));
    }

    @Test
    void currentRoundIsRequired() {
        assertThrows(NullPointerException.class,
                () -> RepairPromptBuilder.build(SYSTEM_PROMPT, "x", contract(), null));
    }



    @Test
    void longHistoryRawOutputIsCutAtACompleteStatementBoundary() {
        // 每行 63 字符:2048 恰好落在 s032 语句中间。截断必须退回到 s031 的结尾。
        StringBuilder raw = new StringBuilder();
        for (int i = 0; i < 80; i++) {
            raw.append(String.format("s%03d = TextContent(\"%s\")\n", i, "x".repeat(40)));
        }
        String text = join(RepairPromptBuilder.build(SYSTEM_PROMPT, "x", contract(),
                round("root = Stack([])", "root = Stack([])", List.of(issue("repeated", "contract", "still failing", "root", null))),
                List.of(round(raw.toString(), "root = Stack([])", List.of(issue("repeated", "contract", "still failing", "root", null))))));

        assertTrue(text.contains("truncated at a complete statement boundary"), "truncation is stated, not silent");
        assertTrue(text.contains("s031 = TextContent("), "the last statement that fits the limit is kept whole");
        assertFalse(text.contains("s032"), "the statement straddling the limit is dropped, never emitted half-cut");
    }

    @Test
    void historyRawOutputWithNoCompleteStatementIsOmittedNotCut() {
        String raw = "huge = TextContent(\"" + "x".repeat(3000) + "\")\n";
        String text = join(RepairPromptBuilder.build(SYSTEM_PROMPT, "x", contract(),
                round("root = Stack([])", "root = Stack([])", List.of(issue("repeated", "contract", "still failing", "root", null))),
                List.of(round(raw, "root = Stack([])", List.of(issue("repeated", "contract", "still failing", "root", null))))));

        assertTrue(text.contains("exceeds the history excerpt limit"), "an unrepresentable history round is omitted");
        assertFalse(text.contains("huge = TextContent"), "no partial single statement is pasted into the prompt");
    }

    @Test
    void capabilityGapTier2RendersOverrideSectionAtTopAndMarksOriginalRequestForContextOnly() {
        String dsl = "grouped = @GroupBy(data.items, \"model\")\n" + "byModel = @ObjectFromEntries(grouped)\n"
                + "root = Stack([byModel])";
        ValidationIssue groupByIssue = componentIssue("unknown-component", "contract",
                "Unknown component \"GroupBy\" — not found in catalog or builtins", "grouped", "GroupBy",
                RepairHints.unknownComponentHint("GroupBy", List.of(GROUP_BY_GAP)));
        ValidationIssue fromEntriesIssue = componentIssue("unknown-component", "contract",
                "Unknown component \"ObjectFromEntries\" — not found in catalog or builtins", "byModel",
                "ObjectFromEntries", RepairHints.unknownComponentHint("ObjectFromEntries", List.of(GROUP_BY_GAP)));

        List<ChatMessage> messages = RepairPromptBuilder.build(SYSTEM_PROMPT, "按型号分组展示板卡", contract(),
                round(dsl, dsl, List.of(groupByIssue, fromEntriesIssue)));
        String system = messages.get(0).content();
        String user = messages.get(1).content();

        int boundaryIdx = system.indexOf("## Capability boundary (overrides the original request)");
        int fixedRulesIdx = system.indexOf("## Incremental repair rules (fixed)");
        assertTrue(boundaryIdx > 0, "system prompt must contain Capability boundary section");
        assertTrue(boundaryIdx > fixedRulesIdx,
                "dynamic capability guidance follows fixed repair rules to preserve their shared prefix");
        assertEquals(1, occurrences(system,
                "The original request asks for \"分组展示\", which is NOT supported as a dynamic operation in openui-lang."),
                "the same capability gap must be rendered only once even when multiple aliases match");
        assertTrue(system.contains(
                "Instead: 用一张 Table 展示全部行；用 @Sort 按分组字段排序，并把该字段放在第一列. Also redefine every diagnosed statement id that called the unsupported function with a valid expression so no invalid definition remains in the program."));
        assertTrue(system.contains("capabilityNotice = TextCallout(\"info\", \"部分需求暂不支持\", \"暂不支持分组展示，以下按原始数据展示\")"));

        assertTrue(user.contains("## Original request (for context only)"),
                "user message must mark Original request as for context only");
        assertFalse(user.contains("## Original request\n"),
                "plain Original request heading must be replaced when capability boundary is active");
    }

    @Test
    void ordinaryUnknownComponentDoesNotRenderCapabilityBoundaryEvenIfHistoryHadGroupBy() {
        ValidationIssue oldGroupBy = componentIssue("unknown-component", "contract",
                "Unknown component \"GroupBy\" — not found in catalog or builtins", "grouped", "GroupBy",
                RepairHints.unknownComponentHint("GroupBy", List.of(GROUP_BY_GAP)));
        RoundEndResult earlier = round("grouped = @GroupBy(data.items, \"model\")",
                "grouped = @GroupBy(data.items, \"model\")", List.of(oldGroupBy));

        ValidationIssue currentChartCard = componentIssue("unknown-component", "contract",
                "Unknown component \"ChartCard\" — not found in catalog or builtins", "card", "ChartCard",
                RepairHints.unknownComponentHint("ChartCard", List.of(GROUP_BY_GAP)));
        RoundEndResult current = round("card = ChartCard(\"T\")\nroot = Stack([card])",
                "card = ChartCard(\"T\")\nroot = Stack([card])", List.of(currentChartCard));

        List<ChatMessage> messages = RepairPromptBuilder.build(SYSTEM_PROMPT, "按型号分组展示板卡", contract(),
                current, List.of(earlier));
        String system = messages.get(0).content();
        String user = messages.get(1).content();

        assertFalse(system.contains("## Capability boundary (overrides the original request)"),
                "must evaluate capability boundary strictly from current actionable issues, never from history");
        assertTrue(user.contains("## Original request\n"),
                "user message heading must remain ## Original request when no tier-2 issue is active");
        assertFalse(user.contains("## Original request (for context only)"));
    }

    @Test
    void multipleMatchedCapabilityGapsRenderNumberedSubHeadingsAndLanguageNote() {
        CapabilityGapSpec topNGap = new CapabilityGapSpec("top-n", "data", "截取前 N 条", List.of("Slice", "Take"),
                List.of("前N"), "不截取，展示全部数据");
        GenerationContract multiGapContract = new GenerationContract("contract-7", "Stack", contract().components(),
                List.of(), contract().tools(), contract().examples(), contract().additionalRules(),
                contract().builtins(), List.of(GROUP_BY_GAP, topNGap));

        String dsl = "grouped = @GroupBy(data.items, \"model\")\ntop = @Slice(grouped, 5)\nroot = Stack([top])";
        ValidationIssue groupByIssue = componentIssue("unknown-component", "contract", "Unknown component \"GroupBy\"",
                "grouped", "GroupBy", RepairHints.unknownComponentHint("GroupBy", List.of(GROUP_BY_GAP, topNGap)));
        ValidationIssue sliceIssue = componentIssue("unknown-component", "contract", "Unknown component \"Slice\"",
                "top", "Slice", RepairHints.unknownComponentHint("Slice", List.of(GROUP_BY_GAP, topNGap)));

        List<ChatMessage> messages = RepairPromptBuilder.build(SYSTEM_PROMPT, "按型号分组并展示前5条",
                multiGapContract, round(dsl, dsl, List.of(groupByIssue, sliceIssue)));
        String system = messages.get(0).content();

        assertTrue(system.contains("### 1. 分组展示 (group-by)"), "first matched gap gets numbered sub-heading");
        assertTrue(system.contains("### 2. 截取前 N 条 (top-n)"), "second matched gap gets numbered sub-heading");
        assertTrue(system.contains("write the notice in the same language as the user's request"),
                "capability boundary section includes language consistency instruction");
    }
}
