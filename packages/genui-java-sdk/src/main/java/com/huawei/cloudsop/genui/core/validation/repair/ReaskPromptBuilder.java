/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.repair;

import com.huawei.cloudsop.genui.core.contract.ComponentPromptSpec;
import com.huawei.cloudsop.genui.core.contract.ComponentPropsSchema;
import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import com.huawei.cloudsop.genui.core.llm.protocol.ChatMessage;
import com.huawei.cloudsop.genui.core.prompt.PromptResources;
import com.huawei.cloudsop.genui.core.validation.ValidationIssue;
import com.huawei.cloudsop.genui.core.validation.ValidationIssueReducer;
import com.huawei.cloudsop.genui.core.validation.ValidationSeverity;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 为整份 DSL 反思修复（设计决策 #6）构建确定性的修复对话消息。纯函数、无副作用 —— 其输出文本被单元测试 直接断言。
 *
 * <ul>
 * <li>{@link #buildFullRepair} —— “这是你产出的完整 DSL；它因以下机器可读问题未通过校验；请重新生成一份修正后的 完整 openui-lang”。
 * </ul>
 *
 * <p>
 * 问题描述块由结构化的 {@link ValidationIssue} 字段（code / message / hint / component / path）构建 —— 绝不使用原始解析器异常文本。组件签名提示来自合并后的
 * {@link GenerationContract}，通过 {@link ComponentPropsSchema#formatSignature} 生成。
 *
 * <h2>修复消费可操作问题视图（原设计决策 11.4，机制迁移至 {@link ValidationIssueReducer}）</h2>
 *
 * <p>
 * 一次解析失败会扩散出派生噪声：该语句的表达式被拆解为虚假的 {@code excess-args}/{@code null-required} 参数问题，模板片段暴露为
 * {@code unresolved-ref}，根节点停止物化（{@code root-not-renderable}）。本类仅在 “提示词视图”这一层经
 * {@link ValidationIssueReducer#actionable} 过滤这些噪声 —— {@code ValidationResult} 本身保持完整，拦截语料库与 TS 对齐测试继续断言完整输出。reducer
 * 同时保留这样的 {@code unresolved-ref}：其 hint 本身就是根因（JS 全局函数 / 缺少 '@'，参见 {@code RepairHints#isRootCauseHint}）—— 移除它会只剩下 token
 * 层面的症状。
 *
 * @since 2026
 */
public final class ReaskPromptBuilder {

    /** 机器可解析的标记，供测试/日志识别修复类系统消息（完整修复）。 */
    public static final String FULL_REPAIR_MARKER = "[REPAIR:FULL]";

    /** Static prompt text for full repair lives beside the incremental resources of the same module. */
    private static final String RESOURCE_ROOT = "prompts/repair/full/";

    private static final Pattern COMPONENT_CALL = Pattern.compile("(?<![@A-Za-z0-9_])([A-Z][A-Za-z0-9_]*)\\s*\\(");

    private ReaskPromptBuilder() {
    }

    /**
     * 完整修复提示词：整份无效 DSL + 问题列表 → 重新生成一份修正后的完整 DSL。
     *
     * @param userIntent
     *            原始用户消息/意图，可为 {@code null}
     * @param invalidDsl
     *            未通过校验的完整 DSL
     * @param issues
     *            说明失败原因的结构化校验问题
     * @param contract
     *            用于生成签名提示的合并合约，可为 {@code null}
     * @return 系统消息 + 用户消息组成的对话消息列表
     */
    public static List<ChatMessage> buildFullRepair(String userIntent, String invalidDsl, List<ValidationIssue> issues,
            GenerationContract contract) {
        return buildFullRepair(userIntent, invalidDsl, issues, contract, null);
    }

    /**
     * 完整修复提示词，附带初次生成时的完整 system prompt，供修复模型复用同一语言/组件契约。
     *
     * @param userIntent
     *            原始用户消息/意图，可为 {@code null}
     * @param invalidDsl
     *            未通过校验的完整 DSL
     * @param issues
     *            说明失败原因的结构化校验问题
     * @param contract
     *            用于生成签名提示的合并合约，可为 {@code null}
     * @param originalSystemPrompt
     *            初次生成请求使用的完整 system prompt，可为 {@code null}
     * @return 系统消息 + 用户消息组成的对话消息列表
     */
    public static List<ChatMessage> buildFullRepair(String userIntent, String invalidDsl, List<ValidationIssue> issues,
            GenerationContract contract, String originalSystemPrompt) {
        StringBuilder system = new StringBuilder();
        system.append(FULL_REPAIR_MARKER).append('\n');
        appendOriginalSystemPrompt(system, originalSystemPrompt);
        system.append(load("main-instruction.txt"));
        appendCompactSyntaxRules(system);
        appendSyntaxRepairHints(system, issues);
        // 提示词视图消费 ValidationIssueReducer 的可操作子集（去重 + 级联抑制），完整 issues 仍可经
        // ValidationResult.issues() 获取 —— 分层原理见类级 Javadoc。
        appendIssues(system, ValidationIssueReducer.actionable(issues));
        appendInvalidStatementSignatures(system, invalidDsl, issues, contract);

        StringBuilder user = new StringBuilder();
        if (userIntent != null && !userIntent.isBlank()) {
            user.append(render("user-request.txt", Map.of("intent", userIntent.trim())));
        }
        user.append(render("user-dsl.txt", Map.of("dsl", invalidDsl == null ? "" : invalidDsl.trim())));

        return List.of(ChatMessage.system(system.toString()), ChatMessage.user(user.toString()));
    }

    // ── 共享渲染逻辑 ────────────────────────────────────────────────────────

    private static String load(String resource) {
        return PromptResources.load(RESOURCE_ROOT + resource);
    }

    private static String render(String resource, Map<String, String> variables) {
        return PromptResources.render(RESOURCE_ROOT + resource, variables);
    }

    private static void appendOriginalSystemPrompt(StringBuilder sb, String originalSystemPrompt) {
        if (originalSystemPrompt == null || originalSystemPrompt.isBlank()) {
            return;
        }
        sb.append(load("original-system-heading.txt"));
        sb.append(load("original-system-authority.txt"));
        sb.append(originalSystemPrompt.trim()).append("\n\n");
        sb.append(load("repair-instructions-heading.txt"));
    }

    private static void appendCompactSyntaxRules(StringBuilder sb) {
        sb.append(load("compact-rules.txt"));
    }

    private static void appendSyntaxRepairHints(StringBuilder sb, List<ValidationIssue> issues) {
        if (issues == null || issues.isEmpty()) {
            return;
        }
        LinkedHashSet<String> hints = new LinkedHashSet<>();
        for (ValidationIssue issue : ValidationIssueReducer.actionable(issues)) {
            if (issue == null || issue.severity() != ValidationSeverity.ERROR || issue.code() == null
                    || !issue.code().startsWith("syntax-")) {
                continue;
            }
            String message = issue.message() == null ? "" : issue.message();
            if (message.contains("COLON")) {
                hints.add(load("hint-named-arguments.txt"));
            }
            if ("syntax-unclosed-bracket".equals(issue.code())
                    || message.toLowerCase(java.util.Locale.ROOT).contains("unclosed")) {
                hints.add(load("hint-unclosed-bracket.txt"));
            }
            if (message.contains("ARROW") || message.contains("FAT_ARROW")) {
                hints.add(load("hint-arrow-function.txt"));
            }
            if (message.contains("TEMPLATE")) {
                hints.add(load("hint-template-string.txt"));
            }
            hints.add(load("hint-regenerate-statement.txt"));
        }
        if (hints.isEmpty()) {
            return;
        }
        sb.append(load("syntax-hints-heading.txt"));
        for (String hint : hints) {
            sb.append(hint);
        }
        sb.append('\n');
    }

    private static void appendIssues(StringBuilder sb, List<ValidationIssue> issues) {
        sb.append(load("issues-heading.txt"));
        if (issues == null || issues.isEmpty()) {
            sb.append(load("issues-empty.txt"));
            return;
        }
        for (ValidationIssue issue : issues) {
            if (issue == null || issue.severity() != ValidationSeverity.ERROR) {
                continue; // 机器可读：只有阻塞级别的问题才驱动修复
            }
            sb.append("- [").append(issue.code()).append("] ").append(issue.message());
            if (issue.component() != null && !issue.component().isBlank()) {
                sb.append(" (component: ").append(issue.component()).append(')');
            }
            if (issue.path() != null && !issue.path().isBlank()) {
                sb.append(" (at ").append(issue.path()).append(')');
            }
            appendLocation(sb, issue);
            if (issue.hint() != null && !issue.hint().isBlank()) {
                sb.append("\n  hint: ").append(issue.hint());
            }
            sb.append('\n');
        }
    }

    /**
     * 追加 {@code (stmt=id, line L:C)} 位置片段，便于修复模型在多语句文档中定位问题。未知部分（{@code null} statementId、{@code -1}
     * 行号）会被省略；两者都未知时不渲染任何内容。
     */
    private static void appendLocation(StringBuilder sb, ValidationIssue issue) {
        boolean hasStmt = issue.statementId() != null && !issue.statementId().isBlank();
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

    /**
     * 为问题中出现的每个组件名输出签名提示（使模型能看到它用错的东西的确切允许形状）。顺序确定：按问题出现顺序， 并去重。
     */
    private static void appendInvalidStatementSignatures(StringBuilder sb, String invalidStatement,
            List<ValidationIssue> issues, GenerationContract contract) {
        if (contract == null || issues == null) {
            return;
        }
        Map<String, ComponentPromptSpec> components = contract.components();
        if (components == null || components.isEmpty()) {
            return;
        }
        Set<String> named = new LinkedHashSet<>();
        for (ValidationIssue issue : issues) {
            if (issue != null && issue.component() != null && !issue.component().isBlank()) {
                named.add(issue.component());
            }
        }
        if (invalidStatement != null && !invalidStatement.isBlank()) {
            Matcher matcher = COMPONENT_CALL.matcher(invalidStatement);
            while (matcher.find()) {
                named.add(matcher.group(1));
            }
        }
        List<String> lines = new ArrayList<>();
        for (String name : named) {
            ComponentPromptSpec spec = components.get(name);
            if (spec != null) {
                lines.add("- " + ComponentPropsSchema.formatSignature(name, spec));
            }
        }
        if (lines.isEmpty()) {
            return;
        }
        sb.append(load("signatures-heading.txt"));
        for (String line : lines) {
            sb.append(line).append('\n');
        }
    }
}
