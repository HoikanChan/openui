/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.prompt.characterize;

/**
 * An inferred array shape.
 *
 * @param element
 *            the inferred shape of the array's elements
 * @param count
 *            the true element count of the original array (not the sampled count)
 * @param truncated
 *            true if the sample tree dropped elements beyond the configured sample size
 *
 * @since 2026
 */
public record ArrayShape(ShapeNode element, long count, boolean truncated) implements ShapeNode {
}
