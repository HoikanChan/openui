/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.type.analysis;

/**
 * 类型关系集中判定(#77 第 6 节)。
 *
 * <p>分开三个问题:类型同一性直接用 {@code equals}(类型构造即归一化);{@link #proveIncluded} 回答“能否证明 A 的 全部值被 B
 * 接受”——未成功不等于当前值出错,调用方不得把未证明直接转成用户错误;{@link #proveDisjoint} 回答 “能否证明两者没有共同值”。
 *
 * @since 2026
 */
public final class Relations {

    private Relations() {}

    /**
     * 证明 A 的全部可能值都被 B 接受(γ(A) ⊆ γ(B))。
     *
     * <p>证明失败不区分“确有不兼容”与“精度不足”,由检查层结合证据定夺。
     *
     * <p>规则要点:Unknown 只被 Unknown 接受;Never 被任何类型接受;StringLiterals ⊆ string 标量;字面值集合按子集
     * 判定;联合要求源联合全部分支被目标接受、目标联合允许任一分支;RecordType 按字段与额外约束结构判定; Component 按名义;Template 只接受 Template。
     *
     * @param source 被包含方(可能值的来源)
     * @param target 包含方(接受这些值的类型)
     * @return 能证明 γ(source) ⊆ γ(target) 时为 {@code true};{@code false} 只表示未证明,不代表值出错
     */
    public static boolean proveIncluded(Type source, Type target) {
        return included(source, target);
    }

    /**
     * 证明两者没有共同值(用于“全部不兼容”证据)。
     *
     * @param left 左侧类型
     * @param right 右侧类型
     * @return 能证明两者值域不相交时为 {@code true};{@code false} 表示未证明,可能仍有交集
     */
    public static boolean proveDisjoint(Type left, Type right) {
        return disjoint(left, right);
    }

    private static boolean included(Type source, Type target) {
        if (source.equals(target)
                || source instanceof Type.Never
                || target instanceof Type.Unknown) {
            return true;
        }
        if (source instanceof Type.Unknown) {
            return false;
        }
        if (source instanceof Type.StringLiterals literals) {
            return includedStringLiterals(literals, target);
        }
        if (source instanceof Type.Scalar scalar) {
            return target instanceof Type.Scalar targetScalar
                            && scalar.kind() == targetScalar.kind()
                    || includedIntoUnion(source, target);
        }
        if (source instanceof Type.Union union) {
            return union.alternatives().stream()
                    .allMatch(alternative -> included(alternative, target));
        }
        if (source instanceof Type.Sequence sequence) {
            if (target instanceof Type.Sequence targetSequence) {
                return included(sequence.element(), targetSequence.element())
                        && sequence.minLength() >= targetSequence.minLength()
                        && sequence.maxLength() <= targetSequence.maxLength();
            }
            return includedIntoUnion(source, target);
        }
        if (source instanceof Type.RecordType record) {
            if (target instanceof Type.RecordType targetRecord) {
                return recordIncluded(record, targetRecord);
            }
            return includedIntoUnion(source, target);
        }
        if (source instanceof Type.Component) {
            return includedIntoUnion(source, target);
        }
        if (source instanceof Type.Template) {
            return target instanceof Type.Template || includedIntoUnion(source, target);
        }
        return false;
    }

    /**
     * 字面值集合的包含:目标标量为 string、目标字面值集合是超集,或目标联合包含它。
     *
     * @param literals 源字面值集合
     * @param target 目标类型
     * @return 能证明字面值集合被目标包含时为 {@code true}
     */
    private static boolean includedStringLiterals(Type.StringLiterals literals, Type target) {
        if (target instanceof Type.Scalar scalar) {
            return scalar.kind() == Type.ScalarKind.STRING;
        }
        if (target instanceof Type.StringLiterals targetLiterals) {
            return targetLiterals.values().containsAll(literals.values());
        }
        return includedIntoUnion(literals, target);
    }

    /**
     * 目标为联合时:任一分支包含源类型即可证明包含。
     *
     * @param source 被包含方
     * @param target 目标类型
     * @return 目标联合的某个分支包含源类型时为 {@code true}
     */
    private static boolean includedIntoUnion(Type source, Type target) {
        return target instanceof Type.Union union
                && union.alternatives().stream()
                        .anyMatch(alternative -> included(source, alternative));
    }

    /**
     * 结构对象包含:目标每个必有字段,源必须必有且值被包含;源必有/可有字段若目标没有对应字段,须被目标 extra 接受;源 extra 与目标 extra 逐层判定。
     *
     * @param source 源记录类型
     * @param target 目标记录类型
     * @return 能证明源记录的值被目标记录接受时为 {@code true}
     */
    private static boolean recordIncluded(Type.RecordType source, Type.RecordType target) {
        for (var entry : target.fields().entrySet()) {
            Type.Field targetField = entry.getValue();
            Type.Field sourceField = source.fields().get(entry.getKey());
            if (targetField.presence() == Type.Presence.REQUIRED) {
                if (sourceField == null || sourceField.presence() != Type.Presence.REQUIRED) {
                    return false;
                }
                if (!included(sourceField.type(), targetField.type())) {
                    return false;
                }
            } else if (sourceField != null && !included(sourceField.type(), targetField.type())) {
                return false;
            }
        }
        for (var entry : source.fields().entrySet()) {
            if (target.fields().containsKey(entry.getKey())) {
                continue;
            }
            if (!included(entry.getValue().type(), target.extra().valueType())) {
                return false;
            }
        }
        return included(source.extra().valueType(), target.extra().valueType());
    }

    private static boolean disjoint(Type left, Type right) {
        if (left instanceof Type.Never || right instanceof Type.Never) {
            return true;
        }
        if (left instanceof Type.Unknown || right instanceof Type.Unknown) {
            return false;
        }
        if (left instanceof Type.Union union) {
            return union.alternatives().stream()
                    .allMatch(alternative -> disjoint(alternative, right));
        }
        if (right instanceof Type.Union union) {
            return union.alternatives().stream()
                    .allMatch(alternative -> disjoint(left, alternative));
        }
        if (left instanceof Type.StringLiterals literals) {
            return disjointStringLiterals(literals, right);
        }
        if (right instanceof Type.StringLiterals) {
            return disjoint(right, left);
        }
        if (left instanceof Type.Scalar scalar) {
            return disjointScalar(scalar, right);
        }
        if (right instanceof Type.Scalar) {
            return true;
        }
        if (left instanceof Type.Sequence && right instanceof Type.Sequence) {
            return disjointSequences((Type.Sequence) left, (Type.Sequence) right);
        }
        if (left instanceof Type.RecordType && right instanceof Type.RecordType) {
            return disjointRecords((Type.RecordType) left, (Type.RecordType) right);
        }
        return componentsOrKinds(left, right);
    }

    /**
     * 字面值集合与其他类型:与字面值集合按无交集判定,与 string 标量互补。
     *
     * @param literals 左侧字面值集合
     * @param right 右侧类型
     * @return 能证明值域不相交时为 {@code true}
     */
    private static boolean disjointStringLiterals(Type.StringLiterals literals, Type right) {
        if (right instanceof Type.StringLiterals rightLiterals) {
            return literals.values().stream().noneMatch(rightLiterals.values()::contains);
        }
        if (right instanceof Type.Scalar scalar) {
            return scalar.kind() != Type.ScalarKind.STRING;
        }
        return true;
    }

    /**
     * 标量与右侧类型:右侧也是标量时按种类是否相同判定。
     *
     * @param scalar 左侧标量
     * @param right 右侧类型
     * @return 能证明值域不相交时为 {@code true}
     */
    private static boolean disjointScalar(Type.Scalar scalar, Type right) {
        return !(right instanceof Type.Scalar rightScalar) || scalar.kind() != rightScalar.kind();
    }

    /**
     * 序列之间的不相交:区间不交必不相交;同区间且元素互斥。
     *
     * @param left 左侧序列
     * @param right 右侧序列
     * @return 能证明值域不相交时为 {@code true}
     */
    private static boolean disjointSequences(Type.Sequence left, Type.Sequence right) {
        // 区间不交必不相交;同区间且元素互斥:若长度可能为零则存在共同的空数组值。
        if (left.maxLength() < right.minLength() || right.maxLength() < left.minLength()) {
            return true;
        }
        if (left.minLength() == 0 || right.minLength() == 0) {
            return false;
        }
        return disjoint(left.element(), right.element());
    }

    /**
     * 结构记录:必有且值互斥的同名字段才证明不相交(宽度不造成不相交)。
     *
     * @param left 左侧记录类型
     * @param right 右侧记录类型
     * @return 能证明值域不相交时为 {@code true}
     */
    private static boolean disjointRecords(Type.RecordType left, Type.RecordType right) {
        // 结构记录:必有且值互斥的同名字段才证明不相交(宽度不造成不相交)。
        for (var entry : left.fields().entrySet()) {
            Type.Field rightField = right.fields().get(entry.getKey());
            if (rightField != null
                    && entry.getValue().presence() == Type.Presence.REQUIRED
                    && rightField.presence() == Type.Presence.REQUIRED
                    && disjoint(entry.getValue().type(), rightField.type())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 组件按名义判定;不同种类之间互不相交。
     *
     * @param left 左侧类型
     * @param right 右侧类型
     * @return 能证明值域不相交时为 {@code true}
     */
    private static boolean componentsOrKinds(Type left, Type right) {
        if (left instanceof Type.Component leftComponent
                && right instanceof Type.Component rightComponent) {
            // 组件按名义:不同名即互斥。
            return !leftComponent.name().equals(rightComponent.name());
        }
        // 不同种类之间:数组/对象/组件/模板互不相交。
        return left.getClass() != right.getClass();
    }
}
