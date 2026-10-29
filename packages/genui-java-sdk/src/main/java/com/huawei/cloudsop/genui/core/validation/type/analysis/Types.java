/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.type.analysis;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link Type} 的构造与归一化入口。
 *
 * <p>归一化规则(#77 第 5.1 节):
 *
 * <ul>
 *   <li>{@code union}:展平嵌套联合、删除 Never 分支、按结构去重;全 Never 收敛为 {@link Type.Never};含 Unknown 收敛为 {@link
 *       Type.Unknown}(已知分支的证据改由 {@link Fact} 的 evidence 承载,不进类型);只有一个可
 *       证明被其他分支包含的成员时删除;归一后单分支退化为该分支。
 *   <li>{@code sequence}:Never 元素且允许零长度归一为确知空数组 {@code Sequence(never, 0, 0)};Never 元素且下界大于 零归一为
 *       {@link Type.Never}。
 *   <li>数量区间非法(下界大于上界或负值)拒绝构造,不通过修改上下界修复。
 * </ul>
 *
 * @since 2026
 */
public final class Types {

    private Types() {}

    /** 基础标量。 */
    public static Type scalar(Type.ScalarKind kind) {
        return new Type.Scalar(kind);
    }

    /** 字符串字面值集合;空集合按契约归一为 {@link Type.Never}。 */
    public static Type stringLiterals(Set<String> values) {
        return values == null || values.isEmpty()
                ? Type.Never.INSTANCE
                : new Type.StringLiterals(values);
    }

    /**
     * 数组类型,执行 Never 元素归一规则: {@code Sequence(never, 0, max)} → {@code Sequence(never, 0, 0)}(确知空数组);
     * {@code Sequence(never, min>0, max)} → {@link Type.Never}。
     */
    public static Type sequence(Type element, long minLength, long maxLength) {
        if (element == null) {
            throw new IllegalArgumentException("sequence element type is required");
        }
        if (element instanceof Type.Never) {
            return minLength > 0 ? Type.Never.INSTANCE : new Type.Sequence(element, 0, 0);
        }
        return new Type.Sequence(element, minLength, maxLength);
    }

    /** 确知空数组。 */
    public static Type emptySequence() {
        return new Type.Sequence(Type.Never.INSTANCE, 0, 0);
    }

    /** 元素与数量均未知的数组。 */
    public static Type unknownSequence() {
        return new Type.Sequence(Type.Unknown.INSTANCE, 0, Type.Sequence.UNBOUNDED);
    }

    /** 结构对象;{@code extra} 为 null 时按关闭处理。 */
    public static Type record(Map<String, Type.Field> fields, Type.Extra extra) {
        return new Type.RecordType(
                fields == null ? Map.of() : fields, extra == null ? Type.Extra.closed() : extra);
    }

    /** 开放结构对象:无已声明字段,额外字段任意。 */
    public static Type openRecord() {
        return record(Map.of(), Type.Extra.open(Type.Unknown.INSTANCE));
    }

    /** 精确联合:展平、去 Never、按结构去重;全 Never → Never;含 Unknown → Unknown;可被证明包含的成员删除; 结果按稳定结构键排序。 */
    public static Type union(List<? extends Type> alternatives) {
        if (alternatives == null || alternatives.isEmpty()) {
            return Type.Never.INSTANCE;
        }
        Set<String> seen = new LinkedHashSet<>();
        List<Type> flat = new ArrayList<>();
        for (Type alternative : alternatives) {
            if (alternative == null || alternative instanceof Type.Never) {
                continue;
            }
            if (alternative instanceof Type.Unknown) {
                return Type.Unknown.INSTANCE;
            }
            if (alternative instanceof Type.Union nested) {
                for (Type member : nested.alternatives()) {
                    if (seen.add(structuralKey(member))) {
                        flat.add(member);
                    }
                }
            } else if (seen.add(structuralKey(alternative))) {
                flat.add(alternative);
            }
        }
        if (flat.isEmpty()) {
            return Type.Never.INSTANCE;
        }
        // 标量子集吸收:字面值集合 ⊆ string 标量时删除字面值分支。
        boolean hasStringScalar = flat.stream().anyMatch(Types::isStringScalar);
        if (hasStringScalar && flat.size() > 1) {
            flat.removeIf(t -> t instanceof Type.StringLiterals);
        }
        if (flat.size() == 1) {
            return flat.getFirst();
        }
        flat.sort((a, b) -> structuralKey(a).compareTo(structuralKey(b)));
        return new Type.Union(flat);
    }

    /** 两个类型的联合。 */
    public static Type union(Type left, Type right) {
        return union(List.of(left, right));
    }

    /**
     * 稳定结构键,用于联合去重与排序;与 {@code equals} 一致。字符串与容器元素均带长度前缀编码,
     * 值内部的分隔符(逗号、竖线、冒号等)不会改变键的结构,避免不同分支生成相同键而被误去重。
     */
    static String structuralKey(Type type) {
        StringBuilder key = new StringBuilder();
        appendStructuralKey(key, type);
        return key.toString();
    }

    private static void appendStructuralKey(StringBuilder key, Type type) {
        switch (type) {
            case Type.Never ignored -> key.append("0;");
            case Type.Unknown ignored -> key.append("1;");
            case Type.Scalar scalar -> appendText(key.append("2;"), scalar.kind().name());
            case Type.StringLiterals literals -> {
                List<String> values = new ArrayList<>(literals.values());
                values.sort(String::compareTo);
                key.append("3;").append(values.size()).append(';');
                values.forEach(value -> appendText(key, value));
            }
            case Type.Sequence sequence -> {
                key.append("4;");
                appendStructuralKey(key, sequence.element());
                key.append(sequence.minLength())
                        .append(';')
                        .append(sequence.maxLength())
                        .append(';');
            }
            case Type.RecordType record -> {
                key.append("5;");
                List<Map.Entry<String, Type.Field>> fields =
                        new ArrayList<>(record.fields().entrySet());
                fields.sort(Map.Entry.comparingByKey());
                key.append(fields.size()).append(';');
                for (Map.Entry<String, Type.Field> entry : fields) {
                    appendText(key, entry.getKey());
                    key.append(entry.getValue().presence() == Type.Presence.REQUIRED ? '!' : '?');
                    appendStructuralKey(key, entry.getValue().type());
                }
                if (record.extra().isClosed()) {
                    key.append("c;");
                } else {
                    key.append("o;");
                    appendStructuralKey(key, record.extra().valueType());
                }
            }
            case Type.Union union -> {
                key.append("6;").append(union.alternatives().size()).append(';');
                union.alternatives().forEach(alternative -> appendStructuralKey(key, alternative));
            }
            case Type.Component component -> appendText(key.append("7;"), component.name());
            case Type.Template ignored -> key.append("8;");
        }
    }

    private static void appendText(StringBuilder key, String text) {
        key.append(text.length()).append(':').append(text);
    }

    private static boolean isStringScalar(Type type) {
        return type instanceof Type.Scalar scalar && scalar.kind() == Type.ScalarKind.STRING;
    }
}
