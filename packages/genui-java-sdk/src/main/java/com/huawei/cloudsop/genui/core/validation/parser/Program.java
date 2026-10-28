/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.parser;

import java.util.List;

/**
 * 解析 openui-lang 源字符串得到的结果。
 *
 * <p>
 * TS 侧称之为 {@code ParseResult}，并把物化/schema 校验也并入其中；此处的解析器仅停留在语法层。因此 {@code Program} 携带按源码顺序排列的 {@link Statement}
 * 列表，以及结构化的 {@link ParseDiagnostic}。后续章节据此 做语义/合约校验。
 *
 * @param statements
 *            按源码顺序解析出的语句；不会为 {@code null}
 * @param diagnostics
 *            结构化的语法诊断信息；不会为 {@code null}
 * @param incomplete
 *            若为自动闭合修复了未闭合的括号/字符串（流式或截断输入）则为 {@code true}
 *
 * @since 2026
 */
public record Program(List<Statement> statements, List<ParseDiagnostic> diagnostics, boolean incomplete) {

    /**
     * 紧凑构造方法，将各列表归一化为不可变列表。
     */
    public Program {
        statements = statements == null ? List.of() : List.copyOf(statements);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    /**
     * 按语句 id 去重的当前有效语句(同名 last-wins:保留首次出现顺序,值为最新定义)。对应 TS 侧 {@code stmtMap} 的 {@code Map.set}
     * 语义,是语义/类型检查共用的唯一去重口径(#56:历史定义不产生当前错误)。
     *
     * @return id → 最新语句的有序映射;不会为 {@code null}
     */
    public java.util.LinkedHashMap<String, Statement> currentById() {
        java.util.LinkedHashMap<String, Statement> current = new java.util.LinkedHashMap<>();
        for (Statement statement : statements) {
            current.put(statement.id(), statement);
        }
        return current;
    }
}
