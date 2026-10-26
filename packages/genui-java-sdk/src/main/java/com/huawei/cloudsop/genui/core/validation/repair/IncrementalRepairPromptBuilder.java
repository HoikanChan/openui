/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.repair;

import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.core.contract.BuiltinSpec;
import com.huawei.cloudsop.genui.core.contract.CapabilityGapSpec;
import com.huawei.cloudsop.genui.core.contract.ComponentGroup;
import com.huawei.cloudsop.genui.core.contract.ComponentPromptSpec;
import com.huawei.cloudsop.genui.core.contract.ComponentPropsSchema;
import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import com.huawei.cloudsop.genui.core.contract.ToolSpec;
import com.huawei.cloudsop.genui.core.llm.protocol.ChatMessage;
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
 * 小节）、固定增量规则、<b>当前完整契约</b>、调用方数据描述，以及 由 {@link RepairGuidance} 按当前诊断选取的专项指导。
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
public final class IncrementalRepairPromptBuilder {

    /** 机器可解析的标记，供测试/日志识别增量修复系统消息。 */
    public static final String INCREMENTAL_REPAIR_MARKER = "[REPAIR:INCREMENTAL]";

    /** 历史只保留最近 N 轮：当前 DSL 与最新诊断才是权威，更早的轮次仅作趋势参考。 */
    private static final int HISTORY_ROUND_LIMIT = 3;

    /**
     * 单轮历史原始输出上限；超出部分截断，避免提示词逐轮膨胀。截断只落在完整语句边界——半句表达式会让模型以为当前程序残缺， 从而去“修”一个并不存在的断裂。
     */
    private static final int HISTORY_RAW_OUTPUT_LIMIT = 2048;

    private IncrementalRepairPromptBuilder() {
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
     *            调用方渲染的数据结构、样本与规模描述；原样嵌入，可为 {@code null}
     * @return 系统消息 + 用户消息组成的对话消息列表
     */
    public static List<ChatMessage> build(String originalSystemPrompt, String originalUserMessage,
            GenerationContract contract, RoundEndResult current, List<RoundEndResult> history, String dataContext) {
        Objects.requireNonNull(current, "current");

        List<ValidationIssue> latestIssues = latestActionableIssues(current);
        List<CapabilityGapSpec> matchedGaps = matchedCapabilityGaps(contract, latestIssues);
        StringBuilder system = new StringBuilder();
        system.append(INCREMENTAL_REPAIR_MARKER).append('\n');
        appendOriginalSystemPrompt(system, originalSystemPrompt);
        appendCapabilityBoundaryOverride(system, matchedGaps);
        appendFixedRules(system);
        appendContract(system, contract);
        appendDataContext(system, dataContext);
        appendSelectedGuidance(system, latestIssues);

        StringBuilder user = new StringBuilder();
        appendOriginalRequest(user, originalUserMessage, !matchedGaps.isEmpty());
        appendCurrentProgram(user, current);
        appendLatestDiagnostics(user, latestIssues);
        appendRelatedStatements(user, current, latestIssues, contract);
        appendHistory(user, history);

        return List.of(ChatMessage.system(system.toString()), ChatMessage.user(user.toString()));
    }

    // ── system message ───────────────────────────────────────────────────────

    private static void appendOriginalSystemPrompt(StringBuilder sb, String originalSystemPrompt) {
        sb.append("## Original generation system prompt (verbatim, once)\n");
        if (isBlank(originalSystemPrompt)) {
            sb.append("No original system prompt was supplied. Use only the current full contract and the "
                    + "current DSL below as the language/component/data contract.\n\n");
            return;
        }
        sb.append(originalSystemPrompt.strip()).append("\n\n");
        sb.append("The prompt above remains authoritative for the openui-lang language, component, builtin, and "
                + "data contract. The incremental repair instructions below override only the response shape "
                + "and patch scope.\n\n");
    }

    /**
     * 当最新一轮 actionable issues 中存在命中 {@code capabilityGaps.aliases} 的越界诊断时，在修复指令最前面输出独立小节
     * {@code ## Capability boundary (overrides the original request)}（每个命中的 gap 渲染一次）。
     */
    private static void appendCapabilityBoundaryOverride(StringBuilder sb, List<CapabilityGapSpec> matchedGaps) {
        if (matchedGaps == null || matchedGaps.isEmpty()) {
            return;
        }
        sb.append("## Capability boundary (overrides the original request)\n");
        for (int i = 0; i < matchedGaps.size(); i++) {
            CapabilityGapSpec gap = matchedGaps.get(i);
            if (matchedGaps.size() > 1) {
                sb.append("### ").append(i + 1).append(". ").append(gap.name()).append(" (").append(gap.id())
                        .append(")\n");
            }
            sb.append("1. The original request asks for \"").append(gap.name()).append(
                    "\", which is NOT supported as a dynamic operation in openui-lang. If the input data already contains pre-aggregated/grouped rows, display that existing data directly; otherwise do NOT try to implement or approximate \"")
                    .append(gap.name()).append("\".\n");
            sb.append("2. Instead: ").append(gap.fallback()).append(
                    ". Also redefine every diagnosed statement id that called the unsupported function with a valid expression so no invalid definition remains in the program.\n");
            sb.append(
                    "3. Redefine `root` so its first child is (write the notice in the same language as the user's request):\n");
            sb.append("   ").append(gap.noticeStatement()).append('\n');
        }
        sb.append('\n');
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

    private static void appendFixedRules(StringBuilder sb) {
        sb.append("## Incremental repair rules (fixed)\n");
        sb.append("1. Return ONLY complete openui-lang statements to change or add. This is a patch, not a page.\n");
        sb.append("2. Keep existing statement ids exactly. Re-issuing `id = ...` replaces that definition "
                + "(last-wins); a new id appends. Do not rename ids or re-emit unchanged statements.\n");
        sb.append("3. Do not output a full page, JSON, prose explanations, or markdown outside a single "
                + "```openui fenced block.\n");
        sb.append("4. Never emit a deletion. To remove behavior, redefine the owning parent so the old statement "
                + "is no longer referenced. Definitions remain in the program and are still type-checked; "
                + "also replace any invalid unused definition with a valid expression.\n");
        sb.append("5. Keep exactly one reachable `root` and keep every reference resolvable. Everything not "
                + "redefined stays exactly as it is in the current effective DSL.\n");
        sb.append("6. Use only components, builtins, tools, examples, and rules present in the current full "
                + "contract below. Do not invent names, fields, functions, data, or statistics.\n");
        sb.append("7. Classify every call from the current program, not from a past label: `@name(` is a builtin "
                + "call, `Name(` is a component call, and a bare identifier is a statement reference. Check the "
                + "current contract and the diagnosed statement's location/related statements. An earlier round's "
                + "error code may be stale after overrides; do not repeat it unless the current DSL still supports "
                + "it.\n");
        sb.append("8. Treat caller-supplied data as a description with a stated scale, not automatically the full "
                + "render data model. Never present sample rows as complete data, and never fabricate statistics. "
                + "If the available data cannot support a value, render the real known data and state the "
                + "limitation in the page (treating the caller-supplied data model as the primary subject to "
                + "render and the user request as auxiliary context: render the actual data in `data` using its "
                + "own entity names, titles, and fields, and state any limitation in natural user-facing language "
                + "with a neutral notice title such as \"提示\" / \"信息说明\", without exposing internal field/path "
                + "names like `data.xxx` / `queryXxx` or system terms like \"数据源不匹配\").\n");
        sb.append("9. Diagnose from the current DSL and the latest diagnostics. Historical rounds explain how the "
                + "program changed; they do not override the current program.\n\n");
    }

    private static void appendContract(StringBuilder sb, GenerationContract contract) {
        sb.append("## Current full contract (authoritative)\n");
        if (contract == null) {
            sb.append("No contract object was supplied. Use only names already present in the original system "
                    + "prompt and the current DSL; invent nothing.\n\n");
            return;
        }

        sb.append("- contractVersion: ").append(orUnknown(contract.contractVersion())).append('\n');
        sb.append("- root: ").append(orUnknown(contract.root())).append('\n');

        sb.append("\n### Components\n");
        Map<String, ComponentPromptSpec> components = contract.components();
        if (components == null || components.isEmpty()) {
            sb.append("(none declared in this contract)\n");
        } else {
            for (Map.Entry<String, ComponentPromptSpec> entry : components.entrySet()) {
                sb.append("- ").append(ComponentPropsSchema.formatSignature(entry.getKey(), entry.getValue()));
                appendDescription(sb, entry.getValue() == null ? null : entry.getValue().description());
                sb.append('\n');
            }
        }

        List<ComponentGroup> groups = contract.componentGroups();
        if (groups != null && !groups.isEmpty()) {
            sb.append("\n### Component groups\n");
            for (ComponentGroup group : groups) {
                sb.append("- ").append(orUnknown(group.name())).append(": ")
                        .append(String.join(", ", group.components())).append('\n');
                for (String note : group.notes()) {
                    sb.append("  - ").append(note).append('\n');
                }
            }
        }

        sb.append("\n### Builtins\n");
        List<BuiltinSpec> builtins = contract.builtins();
        if (builtins == null || builtins.isEmpty()) {
            sb.append("(none declared in this contract)\n");
        } else {
            for (BuiltinSpec builtin : builtins) {
                sb.append("- ").append(formatBuiltinSignature(builtin.signature()));
                sb.append(builtin.templateBuiltin() ? " [template]" : " [data]");
                appendDescription(sb, builtin.description());
                sb.append('\n');
            }
        }

        sb.append("\n### Tools\n");
        List<ToolSpec> tools = contract.tools();
        if (tools == null || tools.isEmpty()) {
            sb.append("(none declared in this contract)\n");
        } else {
            for (ToolSpec tool : tools) {
                sb.append("- ").append(orUnknown(tool.name()));
                appendDescription(sb, tool.description());
                sb.append('\n');
                sb.append("  - inputSchema: ").append(Json.stringify(tool.inputSchema())).append('\n');
                sb.append("  - outputSchema: ").append(Json.stringify(tool.outputSchema())).append('\n');
            }
        }

        sb.append("\n### Examples\n");
        if (contract.examples() == null || contract.examples().isEmpty()) {
            sb.append("(none declared in this contract)\n");
        } else {
            for (String example : contract.examples()) {
                sb.append(example).append("\n\n");
            }
        }

        sb.append("### Additional rules\n");
        if (contract.additionalRules() == null || contract.additionalRules().isEmpty()) {
            sb.append("(none declared in this contract)\n\n");
        } else {
            for (String rule : contract.additionalRules()) {
                sb.append("- ").append(rule).append('\n');
            }
            sb.append('\n');
        }
    }

    private static void appendDataContext(StringBuilder sb, String dataContext) {
        sb.append("## Caller-supplied data context (structure, samples, and scale)\n");
        if (isBlank(dataContext)) {
            sb.append("No data context was supplied by the caller. Treat the data shape as unknown: do not invent "
                    + "fields, values, functions, or statistics. Render only what the current DSL actually has and "
                    + "state any missing-data limitation in the page.\n\n");
            return;
        }
        sb.append(dataContext.strip()).append("\n\n");
        sb.append("The block above is caller-supplied and may describe only part of the render data model. Use "
                + "its stated counts/scale exactly as given. Do not treat sample rows as the full dataset, do not "
                + "invent fields, functions, data, or statistics, and do not compute a total from a sample unless "
                + "the context explicitly says it is the full data. When the real data cannot support a value, show "
                + "the real known data and state the limitation in the page (treating the caller-supplied data "
                + "model as the primary subject to render and the user request as auxiliary context, using a "
                + "neutral notice title such as \"提示\" / \"信息说明\" and natural user-facing language without "
                + "exposing internal field/path names like `data.xxx` / `queryXxx` or system terms like \"数据源不匹配\").\n\n");
    }

    private static void appendSelectedGuidance(StringBuilder sb, List<ValidationIssue> latestIssues) {
        List<String> guidance = RepairGuidance.forIssues(latestIssues);
        if (guidance.isEmpty()) {
            return;
        }
        sb.append("## Diagnostic-selected guidance\n");
        for (String line : guidance) {
            sb.append("- ").append(line).append('\n');
        }
        sb.append('\n');
    }

    // ── user message ─────────────────────────────────────────────────────────

    private static void appendOriginalRequest(StringBuilder sb, String originalUserMessage,
            boolean capabilityBoundaryActive) {
        sb.append(capabilityBoundaryActive ? "## Original request (for context only)\n" : "## Original request\n");
        if (isBlank(originalUserMessage)) {
            sb.append("(no original user message was supplied)\n\n");
            return;
        }
        sb.append(originalUserMessage.strip()).append("\n\n");
    }

    private static void appendCurrentProgram(StringBuilder sb, RoundEndResult current) {
        sb.append("## Current effective DSL (the whole program; last-wins by statement id)\n");
        sb.append("```openui\n");
        sb.append(current.currentDefinitions() == null ? "" : current.currentDefinitions().strip());
        sb.append("\n```\n\n");
        sb.append("Every definition not re-emitted stays in force. Only override or append statements.\n\n");

        sb.append("### Current round raw model output (verbatim)\n");
        appendTextBlock(sb, current.rawOutput());
    }

    private static void appendLatestDiagnostics(StringBuilder sb, List<ValidationIssue> latestIssues) {
        sb.append("## Latest round-end diagnostics (authoritative for this round)\n");
        if (latestIssues.isEmpty()) {
            sb.append("- (no blocking ERROR diagnostics were supplied for the current program)\n\n");
            return;
        }
        for (ValidationIssue issue : latestIssues) {
            sb.append("- [").append(isBlank(issue.code()) ? "(unknown)" : issue.code()).append("] ")
                    .append(issue.message() == null ? "(no message)" : issue.message());
            if (!isBlank(issue.component())) {
                sb.append(" (component: ").append(issue.component()).append(')');
            }
            if (!isBlank(issue.path())) {
                sb.append(" (at ").append(issue.path()).append(')');
            }
            appendLocation(sb, issue);
            if (!isBlank(issue.hint())) {
                sb.append("\n  hint: ").append(issue.hint());
            }
            sb.append('\n');
        }
        sb.append('\n');
    }

    private static void appendRelatedStatements(StringBuilder sb, RoundEndResult current,
            List<ValidationIssue> latestIssues, GenerationContract contract) {
        Set<String> diagnosedIds = new LinkedHashSet<>();
        for (ValidationIssue issue : latestIssues) {
            if (!isBlank(issue.statementId())) {
                diagnosedIds.add(issue.statementId());
            }
        }
        sb.append("## Related statements for diagnosed ids (derived from the current AST/scope)\n");
        if (diagnosedIds.isEmpty()) {
            sb.append("- (latest diagnostics do not attribute to a statement id)\n\n");
            return;
        }

        Map<String, List<String>> related = StatementRelations.related(
                current.currentDefinitions() == null ? "" : current.currentDefinitions(), latestIssues,
                BuiltinCatalog.fromContract(contract));
        boolean rendered = false;
        for (String statementId : diagnosedIds) {
            List<String> relatedIds = related == null ? null : related.get(statementId);
            if (relatedIds == null || relatedIds.isEmpty()) {
                continue;
            }
            rendered = true;
            List<String> quoted = new ArrayList<>();
            for (String relatedId : relatedIds) {
                quoted.add("`" + relatedId + "`");
            }
            sb.append("- ").append('`').append(statementId).append("`: upstream/downstream related statements: ")
                    .append(String.join(", ", quoted)).append('\n');
        }
        if (!rendered) {
            sb.append("- (no related statements were reported for the diagnosed ids)\n");
        }
        sb.append('\n');
    }

    private static void appendHistory(StringBuilder sb, List<RoundEndResult> history) {
        List<RoundEndResult> rounds = new ArrayList<>();
        if (history != null) {
            for (RoundEndResult round : history) {
                if (round != null) {
                    rounds.add(round);
                }
            }
        }
        sb.append("## Earlier round history (compact)\n");
        if (rounds.isEmpty()) {
            sb.append("- (no earlier rounds)\n");
            return;
        }
        sb.append("Historical diagnostics describe past states. They never override the current DSL or the "
                + "latest diagnostics above.\n\n");
        int first = Math.max(0, rounds.size() - HISTORY_ROUND_LIMIT);
        if (first > 0) {
            sb.append("- Earlier rounds omitted: ").append(first).append('\n');
        }
        for (int index = first; index < rounds.size(); index++) {
            RoundEndResult round = rounds.get(index);
            ValidationResult result = round.validationResult();
            sb.append("### Earlier round ").append(index + 1).append(" — ")
                    .append(result == null ? "NO CHECK" : result.isValid() ? "VALID" : "INVALID");
            if (!isBlank(round.contractVersion())) {
                sb.append(" (contract ").append(round.contractVersion()).append(')');
            }
            sb.append(" (").append(round.durationMs()).append(" ms)\n");
            sb.append("- issue codes: ").append(issueCodes(result)).append('\n');
            sb.append("- raw output:\n");
            appendTextBlock(sb, historyExcerpt(round.rawOutput()));
        }
    }

    /**
     * 历史轮原始输出的截断视图。只在完整语句边界截断,并显式声明被截断:前缀再长也只是叙述,当前 DSL 与最新诊断才是修复依据。
     */
    private static String historyExcerpt(String raw) {
        if (raw == null || raw.length() <= HISTORY_RAW_OUTPUT_LIMIT) {
            return raw;
        }
        int cut = completeStatementEnd(raw);
        if (cut <= 0) {
            return "(the first statement of this earlier output exceeds the history excerpt limit; "
                    + "read the current effective DSL instead)";
        }
        return raw.substring(0, cut).stripTrailing()
                + "\n[history excerpt truncated at a complete statement boundary; the current effective DSL above "
                + "is the authoritative state — do not patch from this excerpt alone]";
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

    private static void appendLocation(StringBuilder sb, ValidationIssue issue) {
        boolean hasStmt = !isBlank(issue.statementId());
        boolean hasLine = issue.line() > 0;
        if (!hasStmt && !hasLine) {
            return;
        }
        sb.append(" (");
        if (hasStmt) {
            sb.append("stmt=").append(issue.statementId());
        }
        if (hasLine) {
            if (hasStmt) {
                sb.append(", ");
            }
            sb.append("line ").append(issue.line()).append(':').append(Math.max(issue.column(), 1));
        }
        sb.append(')');
    }

    private static String issueCodes(ValidationResult result) {
        if (result == null) {
            return "(none — no round-end check)";
        }
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        for (ValidationIssue issue : result.issues()) {
            if (issue != null && !isBlank(issue.code())) {
                codes.add(issue.code());
            }
        }
        return codes.isEmpty() ? "(none)" : String.join(", ", codes);
    }

    private static void appendDescription(StringBuilder sb, String description) {
        if (!isBlank(description)) {
            sb.append(" — ").append(description.strip());
        }
    }

    private static void appendTextBlock(StringBuilder sb, String text) {
        sb.append("```text\n");
        sb.append(text == null ? "" : text.strip());
        sb.append("\n```\n\n");
    }

    private static String formatBuiltinSignature(String signature) {
        if (signature == null || signature.isBlank()) {
            return "(signature not supplied)";
        }
        List<String> prefixed = new ArrayList<>();
        for (String variant : signature.split(" / ")) {
            String trimmed = variant.strip();
            prefixed.add(trimmed.startsWith("@") ? trimmed : "@" + trimmed);
        }
        return String.join(" / ", prefixed);
    }

    private static String orUnknown(String value) {
        return isBlank(value) ? "(not supplied)" : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
