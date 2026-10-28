package com.huawei.cloudsop.genui.core.validation.type.analysis;

/**
 * 类型关系集中判定(#77 第 6 节)。
 *
 * <p>
 * 分开三个问题:类型同一性直接用 {@code equals}(类型构造即归一化);{@link #proveIncluded} 回答“能否证明 A 的
 * 全部值被 B 接受”——未成功不等于当前值出错,调用方不得把未证明直接转成用户错误;{@link #proveDisjoint} 回答
 * “能否证明两者没有共同值”。
 *
 * @since 2026
 */
public final class Relations {

    private Relations() {
    }

    /**
     * 证明 A 的全部可能值都被 B 接受(γ(A) ⊆ γ(B))。
     *
     * <p>
     * 证明失败不区分“确有不兼容”与“精度不足”,由检查层结合证据定夺。
     *
     * <p>
     * 规则要点:Unknown 只被 Unknown 接受;Never 被任何类型接受;StringLiterals ⊆ string 标量;字面值集合按子集
     * 判定;联合要求源联合全部分支被目标接受、目标联合允许任一分支;RecordType 按字段与额外约束结构判定;
     * Component 按名义;Template 只接受 Template。
     */
    public static boolean proveIncluded(Type source, Type target) {
        return included(source, target);
    }

    /** 证明两者没有共同值(用于“全部不兼容”证据)。 */
    public static boolean proveDisjoint(Type left, Type right) {
        return disjoint(left, right);
    }

    private static boolean included(Type source, Type target) {
        if (source.equals(target) || source instanceof Type.Never || target instanceof Type.Unknown) {
            return true;
        }
        if (source instanceof Type.Unknown) {
            return false;
        }
        if (source instanceof Type.StringLiterals literals) {
            if (target instanceof Type.Scalar scalar) {
                return scalar.kind() == Type.ScalarKind.STRING;
            }
            if (target instanceof Type.StringLiterals targetLiterals) {
                return targetLiterals.values().containsAll(literals.values());
            }
            return includedIntoUnion(source, target);
        }
        if (source instanceof Type.Scalar scalar) {
            return target instanceof Type.Scalar targetScalar && scalar.kind() == targetScalar.kind()
                    || includedIntoUnion(source, target);
        }
        if (source instanceof Type.Union union) {
            return union.alternatives().stream().allMatch(alternative -> included(alternative, target));
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

    private static boolean includedIntoUnion(Type source, Type target) {
        return target instanceof Type.Union union
                && union.alternatives().stream().anyMatch(alternative -> included(source, alternative));
    }

    /**
     * 结构对象包含:目标每个必有字段,源必须必有且值被包含;源必有/可有字段若目标没有对应字段,须被目标
     * extra 接受;源 extra 与目标 extra 逐层判定。
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
            return union.alternatives().stream().allMatch(alternative -> disjoint(alternative, right));
        }
        if (right instanceof Type.Union union) {
            return union.alternatives().stream().allMatch(alternative -> disjoint(left, alternative));
        }
        if (left instanceof Type.StringLiterals literals) {
            if (right instanceof Type.StringLiterals rightLiterals) {
                return literals.values().stream().noneMatch(rightLiterals.values()::contains);
            }
            if (right instanceof Type.Scalar scalar) {
                return scalar.kind() != Type.ScalarKind.STRING;
            }
            return true;
        }
        if (right instanceof Type.StringLiterals) {
            return disjoint(right, left);
        }
        if (left instanceof Type.Scalar scalar) {
            return !(right instanceof Type.Scalar rightScalar) || scalar.kind() != rightScalar.kind();
        }
        if (right instanceof Type.Scalar) {
            return true;
        }
        if (left instanceof Type.Sequence && right instanceof Type.Sequence) {
            // 区间不交必不相交;同区间且元素互斥:若长度可能为零则存在共同的空数组值。
            Type.Sequence a = (Type.Sequence) left;
            Type.Sequence b = (Type.Sequence) right;
            if (a.maxLength() < b.minLength() || b.maxLength() < a.minLength()) {
                return true;
            }
            if (a.minLength() == 0 || b.minLength() == 0) {
                return false;
            }
            return disjoint(a.element(), b.element());
        }
        if (left instanceof Type.RecordType && right instanceof Type.RecordType) {
            // 结构记录:必有且值互斥的同名字段才证明不相交(宽度不造成不相交)。
            Type.RecordType a = (Type.RecordType) left;
            Type.RecordType b = (Type.RecordType) right;
            for (var entry : a.fields().entrySet()) {
                Type.Field bField = b.fields().get(entry.getKey());
                if (bField != null && entry.getValue().presence() == Type.Presence.REQUIRED
                        && bField.presence() == Type.Presence.REQUIRED
                        && disjoint(entry.getValue().type(), bField.type())) {
                    return true;
                }
            }
            return false;
        }
        if (left instanceof Type.Component leftComponent && right instanceof Type.Component rightComponent) {
            // 组件按名义:不同名即互斥。
            return !leftComponent.name().equals(rightComponent.name());
        }
        // 不同种类之间:数组/对象/组件/模板互不相交。
        return left.getClass() != right.getClass();
    }
}
