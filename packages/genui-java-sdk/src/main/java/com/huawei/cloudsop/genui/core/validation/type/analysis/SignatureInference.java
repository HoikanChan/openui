/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.type.analysis;

import com.huawei.cloudsop.genui.core.contract.typeexpr.TypeExpr;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 简洁契约签名的通用推导:直接把 {@link TypeExpr} 映射为既有 {@link Expectation} 与 {@link Fact},不引入第二套参数类型模型, 也不转译回旧规则树
 * (#119)。
 *
 * <p>三个能力都只被 {@link BuiltinAnalysis} 的会话路径调用,不是公开注册接口:
 *
 * <ul>
 *   <li>{@link #expectation} 把单个参数类型编译成入口检查要求;
 *   <li>{@link #bind} 从实参事实收集命名类型变量的绑定,重复出现取并集,未知输入不能被其他已知实参掩盖;
 *   <li>{@link #instantiate} 按绑定实例化 returnType,未绑定变量与 {@code any} 保持未证明。
 * </ul>
 *
 * <p>结果不携带实参的观测证据,也不伪造数组数量或键值关联:返回类型只由声明结构与绑定决定。
 *
 * @since 2026
 */
final class SignatureInference {

    private SignatureInference() {}

    /**
     * 参数类型 → 入口检查要求。
     *
     * @param pattern 契约参数类型表达式;{@code null} 表示无可检查要求
     * @return 检查要求;无法静态表达时为 {@code null}
     */
    static Expectation expectation(TypeExpr pattern) {
        if (pattern == null) {
            return null;
        }
        if (pattern instanceof TypeExpr.Base base) {
            return baseExpectation(base.name());
        }
        if (pattern instanceof TypeExpr.ArrayOf array) {
            Expectation element = expectation(array.element());
            return element == null ? null : new Expectation.SequenceOf(element);
        }
        if (pattern instanceof TypeExpr.RecordOf record) {
            return recordExpectation(record);
        }
        if (pattern instanceof TypeExpr.ObjectLiteral literal) {
            return objectLiteralExpectation(literal);
        }
        if (pattern instanceof TypeExpr.UnionOf union) {
            List<Expectation> alternatives = new ArrayList<>();
            for (TypeExpr alternative : union.alternatives()) {
                Expectation alternativeExpectation = expectation(alternative);
                if (alternativeExpectation == null) {
                    return null;
                }
                alternatives.add(alternativeExpectation);
            }
            return alternatives.isEmpty() ? null : new Expectation.AnyOf(alternatives);
        }
        if (pattern instanceof TypeExpr.TypeVar) {
            // 泛型位置由绑定收集约束,入口接受任意实参。
            return new Expectation.Accept(Type.Unknown.INSTANCE);
        }
        return null;
    }

    /** {@code Record<string, X>}:对象形状 + 所有值满足 X;X 为类型变量时值要求退化为接受任意值。 */
    private static Expectation recordExpectation(TypeExpr.RecordOf record) {
        Expectation value = expectation(record.value());
        if (value == null) {
            return new Expectation.Accept(Types.openRecord());
        }
        return new Expectation.AllOf(
                List.of(
                        new Expectation.Accept(Types.openRecord()),
                        new Expectation.RecordValues(value)));
    }

    /** 对象字面量参数:每个声明字段都须存在且满足其类型要求。 */
    private static Expectation objectLiteralExpectation(TypeExpr.ObjectLiteral literal) {
        List<Expectation> fields = new ArrayList<>();
        for (TypeExpr.ObjectLiteral.Field field : literal.fields()) {
            Expectation fieldExpectation = expectation(field.type());
            if (fieldExpectation == null) {
                return new Expectation.Accept(Types.openRecord());
            }
            fields.add(new Expectation.RequireField(field.name(), fieldExpectation));
        }
        return fields.isEmpty()
                ? new Expectation.Accept(Types.openRecord())
                : new Expectation.AllOf(fields);
    }

    /** 基础类型关键字 → 检查要求;未知关键字返回 {@code null}。 */
    private static Expectation baseExpectation(String name) {
        return switch (name) {
            case "any" -> new Expectation.Accept(Type.Unknown.INSTANCE);
            case "number" -> new Expectation.Accept(Types.scalar(Type.ScalarKind.NUMBER));
            case "string" -> new Expectation.Accept(Types.scalar(Type.ScalarKind.STRING));
            case "boolean" -> new Expectation.Accept(Types.scalar(Type.ScalarKind.BOOLEAN));
            case "null" -> new Expectation.Accept(Types.scalar(Type.ScalarKind.NULL));
            case "object" -> new Expectation.Accept(Types.openRecord());
            case "array" -> new Expectation.Accept(Types.unknownSequence());
            default -> null;
        };
    }

    /**
     * 从一次实参绑定命名类型变量:重复出现按并集合并,未知输入把相关变量保持为未知。
     *
     * <p>只有直接落在变量位置上的实参才按“带精度损失即未知”处理;数组元素与对象字段按类型结构递归,与既有规则求值口径一致。
     *
     * @param pattern 参数类型表达式
     * @param actual 实参事实
     * @param bounds 本次调用的变量绑定表,原地更新
     */
    static void bind(TypeExpr pattern, Fact actual, Map<String, Type> bounds) {
        if (pattern == null || actual == null) {
            return;
        }
        if (pattern instanceof TypeExpr.TypeVar variable) {
            merge(bounds, variable.name(), preciseType(actual));
            return;
        }
        if (actual.hasUpstreamErrors() || actual.type() instanceof Type.Unknown) {
            bindUnknown(pattern, bounds);
            return;
        }
        bindType(pattern, actual.type(), bounds);
    }

    /**
     * 省略的可选参数按 null 参与绑定:依赖该参数的返回变量得到 {@code null} 分支,而不是凭空的具体类型。
     *
     * @param pattern 省略参数的声明类型
     * @param bounds 本次调用的变量绑定表,原地更新
     */
    static void bindAbsent(TypeExpr pattern, Map<String, Type> bounds) {
        bindType(pattern, Types.scalar(Type.ScalarKind.NULL), bounds);
    }

    /**
     * 按绑定实例化返回类型。未绑定变量记 {@link Fact.Loss#UNBOUND_TYPE_VARIABLE},{@code any} 与未知输入记 {@link
     * Fact.Loss#UNKNOWN_INPUT},结果不携带观测证据。
     *
     * @param returnType 契约返回类型;{@code null} 表示没有可推导结果
     * @param bounds 本次调用的变量绑定表
     * @return 调用结果事实
     */
    static Fact instantiate(TypeExpr returnType, Map<String, Type> bounds) {
        if (returnType == null) {
            return Fact.unknown(Fact.Loss.UNSUPPORTED_RULE);
        }
        Set<Fact.Loss> losses = new LinkedHashSet<>();
        Type type = instantiateType(returnType, bounds, losses);
        return new Fact(type, Set.of(), losses, Set.of());
    }

    // ───────────────────────────────────────────────────────────────────────
    // 绑定
    // ───────────────────────────────────────────────────────────────────────

    private static void bindType(TypeExpr pattern, Type type, Map<String, Type> bounds) {
        if (pattern == null) {
            return;
        }
        if (pattern instanceof TypeExpr.TypeVar variable) {
            merge(bounds, variable.name(), type);
            return;
        }
        if (type instanceof Type.Unknown) {
            bindUnknown(pattern, bounds);
            return;
        }
        if (pattern instanceof TypeExpr.UnionOf union) {
            bindUnion(union, type, bounds);
            return;
        }
        if (type instanceof Type.Union actualUnion) {
            for (Type branch : actualUnion.alternatives()) {
                bindType(pattern, branch, bounds);
            }
            return;
        }
        if (pattern instanceof TypeExpr.ArrayOf array) {
            if (type instanceof Type.Sequence sequence) {
                bindType(array.element(), sequence.element(), bounds);
            } else {
                bindUnknown(array.element(), bounds);
            }
            return;
        }
        if (pattern instanceof TypeExpr.RecordOf record) {
            if (type instanceof Type.RecordType recordType) {
                for (Type.Field field : recordType.fields().values()) {
                    bindType(record.value(), field.type(), bounds);
                }
                if (!recordType.extra().isClosed()) {
                    bindType(record.value(), recordType.extra().valueType(), bounds);
                }
            } else {
                bindUnknown(record.value(), bounds);
            }
            return;
        }
        if (pattern instanceof TypeExpr.ObjectLiteral literal) {
            bindObjectLiteral(literal, type, bounds);
        }
        // Base 不产生变量绑定。
    }

    /** 对象字面量参数:已声明字段从实际对象读取;缺失或存在性未定的字段按未知绑定。 */
    private static void bindObjectLiteral(
            TypeExpr.ObjectLiteral literal, Type type, Map<String, Type> bounds) {
        if (!(type instanceof Type.RecordType recordType)) {
            for (TypeExpr.ObjectLiteral.Field field : literal.fields()) {
                bindUnknown(field.type(), bounds);
            }
            return;
        }
        for (TypeExpr.ObjectLiteral.Field field : literal.fields()) {
            Type.Field actualField = recordType.fields().get(field.name());
            if (actualField == null || actualField.presence() != Type.Presence.REQUIRED) {
                bindUnknown(field.type(), bounds);
            } else {
                bindType(field.type(), actualField.type(), bounds);
            }
        }
    }

    /** 联合参数:只从与当前输入相容的分支绑定,不给不相干分支强加字段约束;没有适用分支时变量保持未知。 */
    private static void bindUnion(TypeExpr.UnionOf union, Type type, Map<String, Type> bounds) {
        List<Type> inputs = TypeOps.flatten(type);
        boolean bound = false;
        for (Type input : inputs) {
            for (TypeExpr alternative : union.alternatives()) {
                if (applies(alternative, input)) {
                    bindType(alternative, input, bounds);
                    bound = true;
                }
            }
        }
        if (!bound) {
            bindUnknown(union, bounds);
        }
    }

    /** 分支是否可能接受该输入;无法判断时按适用处理,由递归绑定的未知口径兜底。 */
    private static boolean applies(TypeExpr alternative, Type input) {
        Expectation expectation = expectation(alternative);
        if (expectation == null) {
            return true;
        }
        Fact inputFact = Fact.of(input);
        return Checks.check(inputFact, expectation, null).verdict() != Checks.Verdict.MISMATCH;
    }

    /** 未知输入:模式内所有变量并入未知,避免被其他已知实参掩盖。 */
    private static void bindUnknown(TypeExpr pattern, Map<String, Type> bounds) {
        if (pattern == null) {
            return;
        }
        if (pattern instanceof TypeExpr.TypeVar variable) {
            merge(bounds, variable.name(), Type.Unknown.INSTANCE);
            return;
        }
        if (pattern instanceof TypeExpr.ArrayOf array) {
            bindUnknown(array.element(), bounds);
        } else if (pattern instanceof TypeExpr.RecordOf record) {
            bindUnknown(record.value(), bounds);
        } else if (pattern instanceof TypeExpr.ObjectLiteral literal) {
            for (TypeExpr.ObjectLiteral.Field field : literal.fields()) {
                bindUnknown(field.type(), bounds);
            }
        } else if (pattern instanceof TypeExpr.UnionOf union) {
            for (TypeExpr alternative : union.alternatives()) {
                bindUnknown(alternative, bounds);
            }
        }
    }

    /** 带精度损失的事实不具备可信的精确类型,按未知参与绑定(与旧规则求值口径一致)。 */
    private static Type preciseType(Fact fact) {
        return fact.losses().isEmpty() ? fact.type() : Type.Unknown.INSTANCE;
    }

    private static void merge(Map<String, Type> bounds, String name, Type type) {
        Type bound = type == null ? Type.Unknown.INSTANCE : type;
        bounds.merge(name, bound, Types::union);
    }

    // ───────────────────────────────────────────────────────────────────────
    // 返回类型实例化
    // ───────────────────────────────────────────────────────────────────────

    private static Type instantiateType(
            TypeExpr expression, Map<String, Type> bounds, Set<Fact.Loss> losses) {
        if (expression instanceof TypeExpr.TypeVar variable) {
            Type bound = bounds.get(variable.name());
            if (bound == null) {
                losses.add(Fact.Loss.UNBOUND_TYPE_VARIABLE);
                return Type.Unknown.INSTANCE;
            }
            if (bound instanceof Type.Unknown) {
                losses.add(Fact.Loss.UNKNOWN_INPUT);
            }
            return bound;
        }
        if (expression instanceof TypeExpr.Base base) {
            return baseType(base.name(), losses);
        }
        if (expression instanceof TypeExpr.ArrayOf array) {
            Type element = instantiateType(array.element(), bounds, losses);
            return Types.sequence(element, 0L, Type.Sequence.UNBOUNDED);
        }
        if (expression instanceof TypeExpr.RecordOf record) {
            Type value = instantiateType(record.value(), bounds, losses);
            // 具体键名未知:开放尾部表达任意字符串键,不伪造键值关联。
            return Types.record(Map.of(), Type.Extra.open(value));
        }
        if (expression instanceof TypeExpr.ObjectLiteral literal) {
            Map<String, Type.Field> fields = new java.util.LinkedHashMap<>();
            for (TypeExpr.ObjectLiteral.Field field : literal.fields()) {
                fields.put(
                        field.name(),
                        Type.Field.required(instantiateType(field.type(), bounds, losses)));
            }
            // 返回对象允许携带声明之外的字段:不得凭空断言对象是闭合的。
            return Types.record(fields, Type.Extra.open(Type.Unknown.INSTANCE));
        }
        if (expression instanceof TypeExpr.UnionOf union) {
            List<Type> alternatives = new ArrayList<>();
            for (TypeExpr alternative : union.alternatives()) {
                alternatives.add(instantiateType(alternative, bounds, losses));
            }
            return Types.union(alternatives);
        }
        // 封闭文法之外的构造不应到达:保持未知,不伪造具体类型。
        losses.add(Fact.Loss.UNSUPPORTED_RULE);
        return Type.Unknown.INSTANCE;
    }

    private static Type baseType(String name, Set<Fact.Loss> losses) {
        return switch (name) {
            case "any" -> {
                losses.add(Fact.Loss.UNKNOWN_INPUT);
                yield Type.Unknown.INSTANCE;
            }
            case "number" -> Types.scalar(Type.ScalarKind.NUMBER);
            case "string" -> Types.scalar(Type.ScalarKind.STRING);
            case "boolean" -> Types.scalar(Type.ScalarKind.BOOLEAN);
            case "null" -> Types.scalar(Type.ScalarKind.NULL);
            case "object" -> Types.openRecord();
            case "array" -> {
                losses.add(Fact.Loss.UNKNOWN_INPUT);
                yield Types.unknownSequence();
            }
            default -> {
                losses.add(Fact.Loss.UNSUPPORTED_RULE);
                yield Type.Unknown.INSTANCE;
            }
        };
    }
}
