/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.type;

import com.huawei.cloudsop.genui.core.validation.type.ValueType.UnionType;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 跨包共享的值类型归一化工具。与包私有的 {@link ValueTypes} 同一语义：展平联合、去重、单分支折叠。
 *
 * @since 2026
 */
public final class ValueTypeOps {

    private ValueTypeOps() {
    }

    /** 值集合并：展平联合分支、按结构去重、单分支折叠。证据作为类型身份参与去重，不被丢弃。 */
    public static ValueType union(List<ValueType> values) {
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
