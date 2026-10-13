/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

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
 * {@link OpenuiLangValidator} 的默认 Java 原生实现。
 *
 * <p>
 * 处理流程：
 * <ol>
 * <li>通过 {@link OpenuiParser#parse(String, ParseMode)} 预处理并解析 DSL。</li>
 * <li>根据请求中的合约构建 {@link ContractCatalog}（合约为 null 时得到空目录，导致每个组件都被报告为未知组件；不支持
 * “仅语法”模式）。</li>
 * <li>执行 {@link ProgramAnalyzer#analyze(Program, ValidationMode, String, java.util.Set)}。</li>
 * <li>将问题映射为 {@link ValidationStatus}：只要存在 {@link ValidationSeverity#ERROR} 级别问题即为 {@code INVALID}；
 * 仅在 {@link ValidationMode#STREAMING} 下存在非 ERROR 问题时为 {@code PARTIAL}；否则为 {@code VALID}。</li>
 * </ol>
 *
 * <p>
 * 无状态，可安全并发使用。
 *
 * @since 2026
 */
public final class DefaultOpenuiLangValidator implements OpenuiLangValidator {

    /** 单例句柄 —— 本类无状态、无配置。 */
    public static final DefaultOpenuiLangValidator INSTANCE = new DefaultOpenuiLangValidator();

    /**
     * 构造校验器实例。
     *
     * <p>
     * 构造方法保持 public，便于调用方通过 {@code new} 自行注入；优先使用 {@link #INSTANCE} 这一无参句柄。
     */
    public DefaultOpenuiLangValidator() {
    }

    @Override
    public ValidationResult validate(ValidationRequest request) {
        Objects.requireNonNull(request, "request");

        // 1. Preprocess + parse (ParseMode mirrors ValidationMode 1:1).
        ParseMode parseMode = request.mode() == ValidationMode.STREAMING ? ParseMode.STREAMING : ParseMode.FINAL;
        Program program = OpenuiParser.parse(request.dsl() != null ? request.dsl() : "", parseMode);

        // 2. Build catalog. A null contract yields an EMPTY catalog, so every component is reported
        // as unknown-component; callers validating real generations must pass the merged
        // GenerationContract. Contract-less syntax-only validation is not supported.
        ContractCatalog catalog = ContractCatalog.from(request.contract());

        // 3. Semantic analysis.
        ProgramAnalyzer analyzer = new ProgramAnalyzer(catalog);
        ProgramAnalysis analysis = analyzer.analyze(program, request.mode(), request.rootName(),
                request.externalRefs());

        // The normalized DSL is the preprocessed text (with whitespace trimmed / auto-close applied in
        // STREAMING). OpenuiParser internally calls OpenuiPreprocessor.process() but does not expose the
        // cleaned text directly. We re-derive it the same way: call preprocess() directly.
        String normalizedDsl = com.huawei.cloudsop.genui.core.validation.parser.OpenuiPreprocessor
                .process(request.dsl() != null ? request.dsl() : "", parseMode).text();

        // 4. Compute status from issue severities.
        List<ValidationIssue> issues = analysis.issues();
        boolean hasError = issues.stream().anyMatch(i -> i.severity() == ValidationSeverity.ERROR);

        ValidationMetadata metadata = new ValidationMetadata(analysis.statementCount(), analysis.entryId(),
                request.mode(), null);

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
