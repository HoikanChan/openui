/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.type;

import com.huawei.cloudsop.genui.core.validation.type.ValueType.Primitive;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.PrimitiveKind;
import com.huawei.cloudsop.genui.core.validation.type.ValueType.UnionType;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Shared normalization and predicates for the validation value-type algebra.
 *
 * @since 2026
 */
final class ValueTypes {

    private ValueTypes() {
    }

    static boolean isNumber(ValueType type) {
        if (type instanceof Primitive primitive) {
            return primitive.kind() == PrimitiveKind.NUMBER;
        }
        return type instanceof UnionType union && !union.alternatives().isEmpty()
                && union.alternatives().stream().allMatch(ValueTypes::isNumber);
    }

    static ValueType union(List<ValueType> values) {
        Set<ValueType> distinct = new LinkedHashSet<>();
        for (ValueType value : values) {
            if (value instanceof UnionType union) {
                distinct.addAll(union.alternatives());
            } else {
                distinct.add(value);
            }
        }
        return distinct.size() == 1 ? distinct.iterator().next() : new UnionType(List.copyOf(distinct));
    }
}
