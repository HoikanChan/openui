/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.contract;

import java.util.List;

/**
 * 契约声明的组件分组，用于组织组件清单与提示词段落。
 *
 * @param name 分组名称，契约内唯一
 * @param components 组内组件名，保持声明顺序
 * @param notes 该分组的补充说明
 * @since 2026
 */
public record ComponentGroup(String name, List<String> components, List<String> notes) {
    /** 规范化构造：{@code null} 值归一为空列表，其余复制为不可变列表。 */
    public ComponentGroup {
        components = components == null ? List.of() : List.copyOf(components);
        notes = notes == null ? List.of() : List.copyOf(notes);
    }
}
