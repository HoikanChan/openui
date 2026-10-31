/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.lint;

import net.sourceforge.pmd.lang.java.ast.ASTExpression;
import net.sourceforge.pmd.lang.java.ast.ASTNumericLiteral;
import net.sourceforge.pmd.lang.java.ast.ASTUnaryExpression;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRule;
import net.sourceforge.pmd.lang.java.types.JPrimitiveType.PrimitiveTypeKind;
import net.sourceforge.pmd.lang.java.types.JTypeMirror;
import net.sourceforge.pmd.lang.java.types.ast.ExprContext;

/**
 * Reports numeric literals that lack the suffix required by their converted type.
 *
 * <p>G.FMT.20 requires an {@code L} suffix for literals converted to {@code long} and an {@code F}
 * suffix for literals converted to {@code float}. The check uses PMD type resolution, so it covers
 * initializers, assignment right-hand sides, return values, and invocation arguments.
 *
 * @since 2026
 */
public class MissingNumericSuffixRule extends AbstractJavaRule {

    private static final String LONG_MESSAGE = "G.FMT.20: long literal should use the 'L' suffix";
    private static final String FLOAT_MESSAGE = "G.FMT.20: float literal should use the 'F' suffix";

    @Override
    public Object visit(ASTNumericLiteral node, Object data) {
        JTypeMirror target = convertedTargetType(node);
        if (target != null && target.isPrimitive(PrimitiveTypeKind.LONG) && node.isIntLiteral()) {
            asCtx(data).addViolationWithMessage(node, LONG_MESSAGE);
        } else if (target != null
                && target.isPrimitive(PrimitiveTypeKind.FLOAT)
                && node.isIntLiteral()) {
            asCtx(data).addViolationWithMessage(node, FLOAT_MESSAGE);
        }
        return data;
    }

    private static JTypeMirror convertedTargetType(ASTNumericLiteral node) {
        ASTExpression contextExpression = contextExpression(node);
        ExprContext context = contextExpression.getConversionContext();
        if (context.isMissing()) {
            return null;
        }
        // Comparisons and arithmetic use NUMERIC contexts. They are out of scope: the rule only
        // guards the places where a literal directly supplies a long/float value.
        if (context.getKind() != ExprContext.ExprContextKind.ASSIGNMENT
                && context.getKind() != ExprContext.ExprContextKind.INVOCATION) {
            return null;
        }
        return context.getTargetType();
    }

    private static ASTExpression contextExpression(ASTNumericLiteral node) {
        ASTExpression expression = node;
        while (expression.getParent() instanceof ASTUnaryExpression unary
                && unary.getOperand() == expression) {
            expression = unary;
        }
        return expression;
    }
}
