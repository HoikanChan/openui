package com.huawei.cloudsop.genui.core.prompt.characterize;

import java.util.LinkedHashMap;

/** An inferred object shape; field order matches first-seen key order. */
public record ObjectShape(LinkedHashMap<String, FieldShape> fields) implements ShapeNode {}
