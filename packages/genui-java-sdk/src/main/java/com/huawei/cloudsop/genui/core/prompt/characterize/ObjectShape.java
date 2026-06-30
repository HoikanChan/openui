/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.prompt.characterize;

import java.util.LinkedHashMap;

/**
 * An inferred object shape; field order matches first-seen key order.
 *
 * @since 2026
 */
public record ObjectShape(LinkedHashMap<String, FieldShape> fields) implements ShapeNode {
}
