/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.semantic;

import com.huawei.cloudsop.genui.core.validation.ValidationIssue;

import java.util.List;

/**
 * 已解析的 {@code Program} 针对 {@link ContractCatalog} 做出的语义分析结果。
 *
 * <p>
 * 这是后续章节用于计算 {@code ValidationStatus} 的公共类型。它携带按模式分级好的 {@link ValidationIssue}
 * 列表，以及顶层校验器所需的结构性事实：哪条语句被选为根节点、该根节点是否解析为可渲染元素，以及未解析/孤立
 * 引用集合。
 *
 * @param issues
 *            全部问题（语法+合约+结构），已按当前模式完成严重程度分级
 * @param entryId
 *            被选为根节点的语句 id（程序无语句时为 {@code null}）
 * @param rootResolved
 *            {@code entryId} 解析为可渲染的目录组件/未知组件时为 {@code true}（对应 TS 侧
 *            {@code root !== null}，即一个 {@code ElementNode}）
 * @param unresolvedRefs
 *            被引用但未定义/非外部的名称（TS 侧 {@code meta.unresolved}）；按首次出现顺序排列，保留重复项以
 *            对应 TS {@code unres} 数组语义
 * @param orphaned
 *            从根节点不可达的取值语句 id（TS 侧 {@code meta.orphaned}）
 * @param statementCount
 *            去重后的语句数量（TS 侧 {@code meta.statementCount}）
 *
 * @since 2026
 */
public record ProgramAnalysis(List<ValidationIssue> issues, String entryId, boolean rootResolved,
        List<String> unresolvedRefs, List<String> orphaned, int statementCount) {

    /**
     * 紧凑构造方法，将各列表归一化为不可变列表。
     */
    public ProgramAnalysis {
        issues = issues == null ? List.of() : List.copyOf(issues);
        unresolvedRefs = unresolvedRefs == null ? List.of() : List.copyOf(unresolvedRefs);
        orphaned = orphaned == null ? List.of() : List.copyOf(orphaned);
    }
}
