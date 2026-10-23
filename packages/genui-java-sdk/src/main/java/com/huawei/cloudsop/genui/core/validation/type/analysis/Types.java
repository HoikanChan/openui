package com.huawei.cloudsop.genui.core.validation.type.analysis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link Type} 的构造与归一化入口。
 *
 * <p>
 * 归一化规则(#77 第 5.1 节):
 * <ul>
 * <li>{@code union}:展平嵌套联合、删除 Never 分支、按结构去重;全 Never 收敛为 {@link Type.Never};含
 * Unknown 收敛为 {@link Type.Unknown}(已知分支的证据改由 {@link Fact} 的 evidence 承载,不进类型);只有一个可
 * 证明被其他分支包含的成员时删除;归一后单分支退化为该分支。
 * <li>{@code sequence}:Never 元素且允许零长度归一为确知空数组 {@code Sequence(never, 0, 0)};Never 元素且下界大于
 * 零归一为 {@link Type.Never}。
 * <li>数量区间非法(下界大于上界或负值)拒绝构造,不通过修改上下界修复。
 * </ul>
 *
 * @since 2026
 */
public final class Types {

    private Types() {
    }

    /** 全类型单例。 */
    public static Type unknown() {
        return Type.Unknown.INSTANCE;
    }

    /** 底类型单例。 */
    public static Type never() {
        return Type.Never.INSTANCE;
    }

    /** 基础标量。 */
    public static Type scalar(Type.ScalarKind kind) {
        return new Type.Scalar(kind);
    }

    /** 字符串字面值集合;空集合按契约归一为 {@link Type.Never}。 */
    public static Type stringLiterals(Set<String> values) {
        return values == null || values.isEmpty() ? Type.Never.INSTANCE : new Type.StringLiterals(values);
    }

    /**
     * 数组类型,执行 Never 元素归一规则:
     * {@code Sequence(never, 0, max)} → {@code Sequence(never, 0, 0)}(确知空数组);
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
        return new Type.RecordType(fields == null ? Map.of() : fields,
                extra == null ? Type.Extra.closed() : extra);
    }

    /**
     * 精确联合:展平、去 Never、按结构去重;全 Never → Never;含 Unknown → Unknown;可被证明包含的成员删除;
     * 结果按稳定结构键排序。
     */
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

    /** 稳定结构键,用于联合去重与排序;与 {@code equals} 一致。 */
    static String structuralKey(Type type) {
        return switch (type) {
            case Type.Never ignored -> "0:never";
            case Type.Unknown ignored -> "1:unknown";
            case Type.Scalar scalar -> "2:scalar:" + scalar.kind().name();
            case Type.StringLiterals literals -> "3:literals:" + String.join(",", new java.util.TreeSet<>(literals.values()));
            case Type.Sequence sequence -> "4:seq:" + structuralKey(sequence.element()) + ":" + sequence.minLength()
                    + ":" + sequence.maxLength();
            case Type.RecordType record -> "5:record:" + recordKey(record);
            case Type.Union union -> "6:union:" + union.alternatives().stream().map(Types::structuralKey)
                    .reduce((a, b) -> a + "|" + b).orElse("");
            case Type.Component component -> "7:component:" + component.name();
            case Type.Template ignored -> "8:template";
        };
    }

    private static String recordKey(Type.RecordType record) {
        StringBuilder key = new StringBuilder();
        record.fields().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> key.append(entry.getKey())
                .append(entry.getValue().presence() == Type.Presence.REQUIRED ? '!' : '?')
                .append(structuralKey(entry.getValue().type())).append(';'));
        key.append(record.extra().isClosed() ? "#closed" : "#open:" + structuralKey(record.extra().valueType()));
        return key.toString();
    }

    private static boolean isStringScalar(Type type) {
        return type instanceof Type.Scalar scalar && scalar.kind() == Type.ScalarKind.STRING;
    }
}
