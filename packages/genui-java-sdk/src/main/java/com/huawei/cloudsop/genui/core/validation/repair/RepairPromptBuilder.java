/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.repair;

import com.huawei.cloudsop.genui.core.contract.CapabilityGapSpec;
import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import com.huawei.cloudsop.genui.core.llm.protocol.ChatMessage;
import com.huawei.cloudsop.genui.core.prompt.PromptRenderer;
import com.huawei.cloudsop.genui.core.validation.ValidationIssue;
import com.huawei.cloudsop.genui.core.validation.ValidationIssueReducer;
import com.huawei.cloudsop.genui.core.validation.ValidationResult;
import com.huawei.cloudsop.genui.core.validation.ValidationSeverity;
import com.huawei.cloudsop.genui.core.validation.parser.BuiltinCatalog;
import com.huawei.cloudsop.genui.core.validation.parser.OpenuiLexer;
import com.huawei.cloudsop.genui.core.validation.parser.StatementSplitter;
import com.huawei.cloudsop.genui.core.validation.semantic.RepairHints;
import com.huawei.cloudsop.genui.core.validation.semantic.StatementRelations;
import com.huawei.cloudsop.genui.core.validation.stream.RoundEndResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 构建增量修复（#58 / #75）的确定性对话消息：模型不再重写整页，只输出需要覆盖或新增的 DSL 语句。
 *
 * <p>
 * 消息分工：
 *
 * <ul>
 * <li><b>system</b> —— 原生成 system prompt 原文（只出现一次）、（若命中能力越界则置于最前的 {@code ## Capability boundary}
 * 小节）、固定增量规则，以及 由 {@link RepairGuidance} 按当前诊断选取的专项指导。
 * <li><b>user</b> —— 原始需求（越界时标为 {@code for context only}）、当前有效 DSL、本轮原始输出、最新轮末诊断、诊断语句的 AST/作用域关联，以及必要的历史轮 原始输出与检查摘要。
 * </ul>
 *
 * <p>
 * <b>不信任历史标签。</b>历史轮次的错误码只作为“当时检查过”的记录；当前 DSL 与最新诊断才是修复依据。未知调用按当前语法 （{@code @name(} 是 builtin、{@code Name(}
 * 是组件、裸标识符是语句引用）、契约和语句位置判断，而不是照搬旧错误标签。
 *
 * <p>
 * <b>数据诚实的边界。</b>{@code dataContext} 由调用方原样传入（结构、样本与规模）；本类不解析、不补全、不推断。提示词明确
 * 要求模型不得把样本当全量、不得编造函数/数据/统计；数据不足以支撑时，渲染真实已知数据并在页面说明限制。
 *
 * <p>
 * 纯函数、无副作用；消息文本由单元测试固定断言必要片段。
 *
 * @since 2026
 */
public final class RepairPromptBuilder {

    /** 机器可解析的标记，供测试/日志识别增量修复系统消息。 */
    public static final String INCREMENTAL_REPAIR_MARKER = "[REPAIR:INCREMENTAL]";

    /** 历史只保留最近 N 轮：当前 DSL 与最新诊断才是权威，更早的轮次仅作趋势参考。 */
    private static final int HISTORY_ROUND_LIMIT = 3;

    /**
     * 单轮历史原始输出上限；超出部分截断，避免提示词逐轮膨胀。截断只落在完整语句边界——半句表达式会让模型以为当前程序残缺， 从而去“修”一个并不存在的断裂。
     */
    private static final int HISTORY_RAW_OUTPUT_LIMIT = 2048;

    private RepairPromptBuilder() {
    }

    /**
     * 无历史轮次的便捷入口。
     *
     * @param originalSystemPrompt
     *            初次生成请求使用的完整 system prompt，可为 {@code null}
     * @param originalUserMessage
     *            原始用户需求，可为 {@code null}
     * @param contract
     *            当前生效的完整合并契约，可为 {@code null}
     * @param current
     *            本轮轮末结果（当前有效 DSL + 最新诊断 + 原始输出）
     * @return 系统消息 + 用户消息组成的对话消息列表
     */
    public static List<ChatMessage> build(String originalSystemPrompt, String originalUserMessage,
            GenerationContract contract, RoundEndResult current) {
        return build(originalSystemPrompt, originalUserMessage, contract, current, List.of(), null);
    }

    /**
     * 带历史轮次、但不带调用方数据描述的便捷入口。
     *
     * @param originalSystemPrompt
     *            初次生成请求使用的完整 system prompt，可为 {@code null}
     * @param originalUserMessage
     *            原始用户需求，可为 {@code null}
     * @param contract
     *            当前生效的完整合并契约，可为 {@code null}
     * @param current
     *            本轮轮末结果
     * @param history
     *            早前轮次记录，可为 {@code null}
     * @return 系统消息 + 用户消息组成的对话消息列表
     */
    public static List<ChatMessage> build(String originalSystemPrompt, String originalUserMessage,
            GenerationContract contract, RoundEndResult current, List<RoundEndResult> history) {
        return build(originalSystemPrompt, originalUserMessage, contract, current, history, null);
    }

    /**
     * 构建增量修复提示词。
     *
     * @param originalSystemPrompt
     *            初次生成请求使用的完整 system prompt，可为 {@code null}
     * @param originalUserMessage
     *            原始用户需求，可为 {@code null}
     * @param contract
     *            当前生效的完整合并契约，可为 {@code null}
     * @param current
     *            本轮轮末结果；不可为 {@code null}
     * @param history
     *            早前轮次记录；{@code null} 视同空列表，只用于原始输出/检查摘要，不覆盖当前状态
     * @param dataContext
     *            兼容旧调用保留；数据上下文已包含在原始 system prompt 中，此参数不再重复插入
     * @return 系统消息 + 用户消息组成的对话消息列表
     */
    public static List<ChatMessage> build(String originalSystemPrompt, String originalUserMessage,
            GenerationContract contract, RoundEndResult current, List<RoundEndResult> history, String dataContext) {
        Objects.requireNonNull(current, "current");

        List<ValidationIssue> latestIssues = latestActionableIssues(current);
        List<CapabilityGapSpec> matchedGaps = matchedCapabilityGaps(contract, latestIssues);
        List<Map<String, Object>> capabilityBoundary = capabilityModels(matchedGaps);
        List<Map<String, Object>> diagnostics = diagnosticModels(latestIssues);
        List<Map<String, Object>> relatedStatements = relatedModels(current, latestIssues, contract);
        List<Map<String, Object>> historyEntries = historyModels(repeatedFailureHistory(history, latestIssues));
        String currentProgram = stripped(current.currentDefinitions());
        String rawOutput = stripped(current.rawOutput());

        Map<String, Object> model = new LinkedHashMap<>();
        model.put("marker", INCREMENTAL_REPAIR_MARKER);
        model.put("originalSystem", originalSystemPrompt == null ? "" : originalSystemPrompt);
        model.put("capabilityBoundaryActive", !capabilityBoundary.isEmpty());
        model.put("capabilityBoundary", capabilityBoundary);


        model.putAll(RepairGuidance.forIssues(latestIssues));
        model.put("originalRequest", stripped(originalUserMessage));
        model.put("currentProgram", currentProgram);
        boolean syntaxFailure = latestIssues.stream().anyMatch(issue -> "syntax".equals(issue.source())
                || issue.code() != null && issue.code().startsWith("syntax-"));
        model.put("rawOutput", syntaxFailure ? rawOutput : "");
        model.put("diagnostics", diagnostics);
        model.put("relatedStatements", relatedStatements);
        model.put("history", historyEntries);

        String base = "prompts/repair/";
        return List.of(ChatMessage.system(PromptRenderer.render(base + "system.md", model)),
                ChatMessage.user(PromptRenderer.render(base + "user.md", model)));
    }

    // ── system message ───────────────────────────────────────────────────────

    /**
     * 为当前诊断命中的能力缺口准备模板循环条目，保持契约匹配顺序。
     */
    private static List<Map<String, Object>> capabilityModels(List<CapabilityGapSpec> matchedGaps) {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (int index = 0; index < matchedGaps.size(); index++) {
            CapabilityGapSpec gap = matchedGaps.get(index);
            entries.add(Map.of("multiple", matchedGaps.size() > 1,
                    "index", index + 1, "name", gap.name(), "id", gap.id(), "fallback", gap.fallback(),
                    "notice", gap.noticeStatement()));
        }
        return entries;
    }

    /**
     * 仅根据当前契约 + 最新一轮 actionable issues 匹配能力缺口（按遇到顺序去重，绝不读取历史轮次）。
     */
    private static List<CapabilityGapSpec> matchedCapabilityGaps(GenerationContract contract,
            List<ValidationIssue> actionableIssues) {
        if (contract == null || contract.capabilityGaps() == null || contract.capabilityGaps().isEmpty()
                || actionableIssues == null || actionableIssues.isEmpty()) {
            return List.of();
        }
        Map<String, CapabilityGapSpec> matched = new LinkedHashMap<>();
        for (ValidationIssue issue : actionableIssues) {
            if (issue == null || issue.severity() != ValidationSeverity.ERROR
                    || !"unknown-component".equals(issue.code())) {
                continue;
            }
            CapabilityGapSpec gap = RepairHints.findCapabilityGapByAlias(issue.component(), contract.capabilityGaps());
            if (gap != null) {
                matched.putIfAbsent(gap.id(), gap);
            }
        }
        return List.copyOf(matched.values());
    }

    private static List<Map<String, Object>> diagnosticModels(List<ValidationIssue> latestIssues) {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (ValidationIssue issue : latestIssues) {
            Map<String, Object> model = new LinkedHashMap<>();
            model.put("code", stripped(issue.code()));
            model.put("message", stripped(issue.message()));
            model.put("component", stripped(issue.component()));
            model.put("path", stripped(issue.path()));
            model.put("location", formatLocation(issue));
            model.put("hint", stripped(issue.hint()));
            entries.add(model);
        }
        return entries;
    }

    private static List<Map<String, Object>> relatedModels(RoundEndResult current, List<ValidationIssue> latestIssues,
            GenerationContract contract) {
        Set<String> diagnosedIds = new LinkedHashSet<>();
        for (ValidationIssue issue : latestIssues) {
            if (!isBlank(issue.statementId())) {
                diagnosedIds.add(issue.statementId());
            }
        }
        if (diagnosedIds.isEmpty()) {
            return List.of();
        }

        Map<String, List<String>> related = StatementRelations.related(
                current.currentDefinitions() == null ? "" : current.currentDefinitions(), latestIssues,
                BuiltinCatalog.fromContract(contract));
        List<Map<String, Object>> entries = new ArrayList<>();
        for (String statementId : diagnosedIds) {
            List<String> relatedIds = related == null ? null : related.get(statementId);
            if (relatedIds == null || relatedIds.isEmpty()) {
                continue;
            }
            List<String> quoted = new ArrayList<>();
            for (String relatedId : relatedIds) {
                quoted.add("`" + relatedId + "`");
            }
            entries.add(Map.of("id", statementId, "ids", String.join(", ", quoted)));
        }
        return entries;
    }

    private static List<RoundEndResult> repeatedFailureHistory(List<RoundEndResult> history,
            List<ValidationIssue> currentIssues) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        Set<String> currentCodes = new LinkedHashSet<>();
        for (ValidationIssue issue : currentIssues) {
            if (!isBlank(issue.code())) {
                currentCodes.add(issue.code());
            }
        }
        return history.stream().filter(Objects::nonNull).filter(round -> round.validationResult() != null
                && round.validationResult().issues().stream().anyMatch(issue -> issue != null
                        && currentCodes.contains(issue.code()))).toList();
    }

    private static List<Map<String, Object>> historyModels(List<RoundEndResult> history) {
        List<RoundEndResult> rounds = history == null ? List.of() : history.stream().filter(Objects::nonNull).toList();
        int first = Math.max(0, rounds.size() - HISTORY_ROUND_LIMIT);
        List<Map<String, Object>> entries = new ArrayList<>();
        for (int index = first; index < rounds.size(); index++) {
            RoundEndResult round = rounds.get(index);
            ValidationResult result = round.validationResult();
            String codes = issueCodes(result);
            Map<String, Object> excerpt = historyExcerpt(round.rawOutput());
            Map<String, Object> model = new LinkedHashMap<>();
            model.put("index", index + 1);
            model.put("valid", result != null && result.isValid());
            model.put("invalid", result != null && !result.isValid());
            model.put("codes", codes);
            model.putAll(excerpt);
            entries.add(model);
        }
        return entries;
    }

    /**
     * 历史轮原始输出的截断视图。只在完整语句边界截断,并显式声明被截断:前缀再长也只是叙述,当前 DSL 与最新诊断才是修复依据。
     */
    private static Map<String, Object> historyExcerpt(String raw) {
        String text = stripped(raw);
        boolean truncated = text.length() > HISTORY_RAW_OUTPUT_LIMIT;
        int cut = truncated ? completeStatementEnd(text) : 0;
        String excerpt = truncated ? (cut > 0 ? text.substring(0, cut).stripTrailing() : "") : text;
        return Map.of("overLimit", truncated && cut <= 0, "truncated", truncated && cut > 0,
                "raw", excerpt);
    }

    /**
     * 在 {@link #HISTORY_RAW_OUTPUT_LIMIT} 内最后一个完整语句的结束偏移；找不到时退回最后一个换行边界，再退回 {@code 0}。
     */
    private static int completeStatementEnd(String raw) {
        int limit = HISTORY_RAW_OUTPUT_LIMIT;
        int boundary = 0;
        try {
            for (StatementSplitter.RawStmt statement : StatementSplitter.split(OpenuiLexer.tokenize(raw))) {
                int end = statement.span().endOffset();
                if (end < 0 || end > limit) {
                    break;
                }
                boundary = Math.max(boundary, end);
            }
        } catch (RuntimeException ignored) {
            // Fall through to the newline boundary: a malformed fragment must not break prompt building.
            boundary = 0;
        }
        if (boundary > 0) {
            return boundary;
        }
        int newline = raw.lastIndexOf('\n', limit - 1);
        return newline < 0 ? 0 : newline + 1;
    }

    // ── shared rendering ─────────────────────────────────────────────────────

    private static List<ValidationIssue> latestActionableIssues(RoundEndResult current) {
        ValidationResult result = current.validationResult();
        if (result == null) {
            return List.of();
        }
        List<ValidationIssue> actionable = ValidationIssueReducer.actionable(result.issues());
        List<ValidationIssue> blocking = new ArrayList<>();
        for (ValidationIssue issue : actionable) {
            if (issue != null && issue.severity() == ValidationSeverity.ERROR) {
                blocking.add(issue);
            }
        }
        return List.copyOf(blocking);
    }

    /**
     * 诊断位置的行内后缀；无语句 ID 且无行号时返回空字符串。保持 “ (stmt=…, line X:Y)” 的既有分隔格式。
     */
    private static String formatLocation(ValidationIssue issue) {
        List<String> parts = new ArrayList<>();
        if (!isBlank(issue.statementId())) {
            parts.add("stmt=" + issue.statementId());
        }
        if (issue.line() > 0) {
            parts.add("line %d:%d".formatted(issue.line(), Math.max(issue.column(), 1)));
        }
        return parts.isEmpty() ? "" : " (%s)".formatted(String.join(", ", parts));
    }

    private static String issueCodes(ValidationResult result) {
        if (result == null) {
            return "";
        }
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        for (ValidationIssue issue : result.issues()) {
            if (issue != null && !isBlank(issue.code())) {
                codes.add(issue.code());
            }
        }
        return String.join(", ", codes);
    }



    private static String stripped(String value) {
        return value == null ? "" : value.strip();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
