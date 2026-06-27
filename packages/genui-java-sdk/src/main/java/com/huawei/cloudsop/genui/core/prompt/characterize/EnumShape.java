package com.huawei.cloudsop.genui.core.prompt.characterize;

import java.util.List;

/**
 * A string column/array whose complete, lexicographically-sorted value domain fits within the
 * configured cardinality cap.
 *
 * @param domain the complete enum domain, sorted lexicographically
 */
public record EnumShape(List<String> domain) implements ShapeNode {}
