/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.type.analysis;

import com.huawei.cloudsop.genui.core.validation.parser.AstNode;

import java.util.Set;

/**
 * {@link BuiltinAnalysis} 需要的分析会话能力。
 *
 * <p>接口只暴露 builtin module 实际使用的预算、诊断、覆盖与符号解析入口,使分析会话可以持有 {@link BuiltinAnalysis},而后者不必反向引用具体会话类型。
 *
 * @since 2026
 */
interface BuiltinSession {

    /**
     * 在给定词法环境下推断表达式。
     *
     * @param node 待推断表达式
     * @param statementId 当前语句标识
     * @param env 词法环境
     * @param resolving 循环引用追踪集合
     * @return 推断结果事实
     */
    Fact synth(AstNode node, String statementId, LexicalEnvironment env, Set<String> resolving);

    /**
     * 发出一条诊断。
     *
     * @param code 诊断 code
     * @param message 诊断消息
     * @param statementId 当前语句标识
     * @param component 组件名
     * @param path 诊断路径
     * @param hint 修复提示
     */
    void emit(
            String code,
            String message,
            String statementId,
            String component,
            String path,
            String hint);

    /**
     * 登记一条参数检查义务并返回检查结果。
     *
     * @param statementId 当前语句标识
     * @param useSite 使用位置标识
     * @param kind 检查种类
     * @param contractPath 契约路径
     * @param fact 待检查的类型事实
     * @param expectation 参数检查要求
     * @param checkedPath 被检查值的路径;无路径时为 null
     * @param component 组件名
     * @param issuePath 诊断定位路径
     * @return 参数检查结果
     */
    Checks.CheckResult checkObligation(
            String statementId,
            String useSite,
            String kind,
            String contractPath,
            Fact fact,
            Expectation expectation,
            String checkedPath,
            String component,
            String issuePath);

    /**
     * 本次会话的类型运算入口。
     *
     * @return 共享会话完整性标记的类型运算器
     */
    TypeOps ops();

    /**
     * 解析引用到其最终表达式。
     *
     * @param node 待解析表达式
     * @param resolving 循环引用追踪集合
     * @return 解析后的表达式
     */
    AstNode resolveNode(AstNode node, Set<String> resolving);

    /**
     * 启动一次遍历,超限时标记会话不完整。
     *
     * @return 未超限时返回 true;超限时返回 false
     */
    boolean tryStartTraversal();

    /**
     * 进入模板使用位置。
     *
     * @param pathPrefix 诊断路径前缀
     * @param coverageUseSite 覆盖登记的使用位置标识
     * @param statementId 使用处语句 id
     * @param declarationStatementId 模板声明语句 id
     */
    void pushTemplateUseSite(
            String pathPrefix,
            String coverageUseSite,
            String statementId,
            String declarationStatementId);

    /** 离开模板使用位置。 */
    void popTemplateUseSite();
}
