package com.huawei.cloudsop.genui.core.validation.type;

import com.huawei.cloudsop.genui.core.validation.type.ExpectedType.ArrayConstraint;
import com.huawei.cloudsop.genui.core.validation.type.ExpectedType.ComponentConstraint;
import com.huawei.cloudsop.genui.core.validation.type.ExpectedType.EnumConstraint;
import com.huawei.cloudsop.genui.core.validation.type.ExpectedType.ObjectConstraint;
import com.huawei.cloudsop.genui.core.validation.type.ExpectedType.PrimitiveConstraint;
import com.huawei.cloudsop.genui.core.validation.type.ExpectedType.UnionConstraint;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.ArrayType;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.ComponentType;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.EmptyArrayType;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.ObjectType;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.Primitive;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.PrimitiveKind;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.StringDomainType;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.UnionType;

/** Evidence-based assignability between proven values and supported Contract constraints. */
final class TypeAssignability {

    enum Result {
        MATCH, MISMATCH, UNPROVEN
    }

    Result check(ValueType actual, ExpectedType expected) {
        if (actual instanceof UnionType union) {
            boolean unproven = false;
            for (ValueType alternative : union.alternatives()) {
                Result result = check(alternative, expected);
                if (result == Result.MISMATCH) {
                    return Result.MISMATCH;
                }
                unproven |= result == Result.UNPROVEN;
            }
            return unproven ? Result.UNPROVEN : Result.MATCH;
        }
        if (expected instanceof UnionConstraint union) {
            boolean unproven = false;
            for (ExpectedType alternative : union.alternatives()) {
                Result result = check(actual, alternative);
                if (result == Result.MATCH) {
                    return Result.MATCH;
                }
                unproven |= result == Result.UNPROVEN;
            }
            return unproven ? Result.UNPROVEN : Result.MISMATCH;
        }
        if (expected instanceof ArrayConstraint array) {
            if (actual instanceof EmptyArrayType) {
                return array.elementConstraint() == null ? Result.MATCH : Result.UNPROVEN;
            }
            if (!(actual instanceof ArrayType actualArray)) {
                return Result.MISMATCH;
            }
            return array.elementConstraint() == null
                    ? Result.MATCH
                    : check(actualArray.elementType(), array.elementConstraint());
        }
        if (expected instanceof ObjectConstraint object) {
            if (!(actual instanceof ObjectType actualObject)) {
                return Result.MISMATCH;
            }
            for (var field : object.fields().entrySet()) {
                ValueType actualField = actualObject.fields().get(field.getKey());
                if (actualField != null && check(actualField, field.getValue()) == Result.MISMATCH) {
                    return Result.MISMATCH;
                }
            }
            return Result.MATCH;
        }
        if (expected instanceof ComponentConstraint component) {
            return actual instanceof ComponentType actualComponent && component.name().equals(actualComponent.name())
                    ? Result.MATCH
                    : Result.MISMATCH;
        }
        if (expected instanceof EnumConstraint enumConstraint) {
            if (actual instanceof StringDomainType domain) {
                return enumConstraint.values().containsAll(domain.values()) ? Result.MATCH : Result.MISMATCH;
            }
            return actual instanceof Primitive primitive && primitive.kind() == PrimitiveKind.STRING
                    ? Result.UNPROVEN
                    : Result.MISMATCH;
        }
        if (expected instanceof PrimitiveConstraint primitiveConstraint && actual instanceof StringDomainType) {
            return primitiveConstraint.kind() == PrimitiveKind.STRING ? Result.MATCH : Result.MISMATCH;
        }
        if (expected instanceof PrimitiveConstraint primitiveConstraint && actual instanceof Primitive primitive) {
            return primitive.kind() == primitiveConstraint.kind() ? Result.MATCH : Result.MISMATCH;
        }
        return Result.MISMATCH;
    }
}
