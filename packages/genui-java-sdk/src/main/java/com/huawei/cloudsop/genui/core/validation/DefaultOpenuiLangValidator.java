/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation;

import com.huawei.cloudsop.genui.core.validation.type.analysis.AnalysisBudget;
import com.huawei.cloudsop.genui.core.validation.type.analysis.TypeAnalysis;

import java.util.ArrayList;
import java.util.Objects;

/**
 * Java 原生校验器:从同一不可变快照执行语法、语义及类型分析。
 *
 * <p>无 Render Data Model 时仍检查静态可知部分;数据依赖部分的未证明结论进入 metadata。 分析预算为不可变配置,每次请求独立计数,实例可安全并发使用。
 *
 * @since 2026
 */
public final class DefaultOpenuiLangValidator implements OpenuiLangValidator {

    /** 使用默认分析预算的共享实例。 */
    public static final DefaultOpenuiLangValidator INSTANCE = new DefaultOpenuiLangValidator();

    private final AnalysisBudget analysisBudget;

    /** 构造使用默认分析预算的校验器。 */
    public DefaultOpenuiLangValidator() {
        this(null);
    }

    /**
     * 使用指定的单次分析预算;null 使用默认预算。
     *
     * @param analysisBudget 单次分析预算；{@code null} 表示使用默认预算
     */
    public DefaultOpenuiLangValidator(AnalysisBudget analysisBudget) {
        this.analysisBudget = analysisBudget;
    }

    @Override
    public ValidationResult validate(ValidationRequest request) {
        Objects.requireNonNull(request, "request");

        ValidationSnapshot snapshot = ValidationSnapshot.build(request);
        var analysis = snapshot.analysis();
        String normalizedDsl = snapshot.normalizedDsl();
        var issues = new ArrayList<>(analysis.issues());
        java.util.Map<String, String> extra = new java.util.LinkedHashMap<>();
        TypeAnalysis.Result typeResult =
                TypeAnalysis.analyze(
                        snapshot.program(),
                        snapshot.catalog(),
                        snapshot.builtins(),
                        snapshot.dataModel(),
                        analysis.entryId(),
                        analysisBudget);
        issues.addAll(typeResult.issues());
        extra.putAll(typeResult.coverage().metadataSummary());
        boolean hasError = issues.stream().anyMatch(i -> i.severity() == ValidationSeverity.ERROR);
        ValidationMetadata metadata =
                new ValidationMetadata(analysis.statementCount(), analysis.entryId(), extra);

        if (hasError) {
            return ValidationResult.invalid(issues, metadata);
        }
        return ValidationResult.valid(normalizedDsl, issues, metadata);
    }
}
