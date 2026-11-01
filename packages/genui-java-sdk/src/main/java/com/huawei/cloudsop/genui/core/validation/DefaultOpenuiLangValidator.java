/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation;

import com.huawei.cloudsop.genui.core.validation.type.analysis.TypeAnalysis;

import java.util.ArrayList;
import java.util.Objects;

/**
 * Java 原生校验器:从同一不可变快照执行语法、语义及类型分析。
 *
 * <p>无 Render Data Model 时仍检查静态可知部分;数据依赖部分的未证明结论进入 metadata。 分析限制由内部管理,每次请求独立记录完整性,实例可安全并发使用。
 *
 * @since 2026
 */
public final class DefaultOpenuiLangValidator implements OpenuiLangValidator {

    /** 使用内部分析限制的共享实例。 */
    public static final DefaultOpenuiLangValidator INSTANCE = new DefaultOpenuiLangValidator();

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
                        analysis.entryId());
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
