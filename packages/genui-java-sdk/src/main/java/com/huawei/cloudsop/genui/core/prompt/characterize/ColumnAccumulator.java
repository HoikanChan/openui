package com.huawei.cloudsop.genui.core.prompt.characterize;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * Per-column accumulation state used while feature-scanning an object-array (table) during {@link
 * ShapeWalker}'s Pass B.
 *
 * <p>Tracks, across all observed rows: the set of {@link ScalarType}s seen, a capacity-bounded
 * distinct-string set (stops growing once it exceeds {@code enumMaxDistinct + 1}, at which point
 * the column degrades to free-text {@code string}), the occurrence count (rows where the key was
 * present), and the null count (rows where the key was present but the value was {@code null}).
 * Missing-row tracking is exposed separately so the caller can derive {@code optional}.
 */
final class ColumnAccumulator {
  private final int distinctCap;
  private final Set<String> distinctStrings = new HashSet<>();
  private final EnumSet<ScalarType> scalarTypesSeen = EnumSet.noneOf(ScalarType.class);
  private boolean distinctOverflowed;
  private long occurrences;
  private long nullCount;
  private long missingCount;
  private ColumnAccumulator childAccumulator;
  private ShapeNode childDeepShape;
  private long deepScanned;

  ColumnAccumulator(int distinctCap) {
    this.distinctCap = distinctCap;
  }

  /** Records a row where the key was present with a non-null string value. */
  void observePresent(String value) {
    occurrences++;
    if (!distinctOverflowed) {
      if (distinctStrings.size() >= distinctCap + 1 && !distinctStrings.contains(value)) {
        distinctOverflowed = true;
      } else {
        distinctStrings.add(value);
      }
    }
  }

  /** Records a row where the key was present with a non-null, non-string scalar value. */
  void observeScalarType(ScalarType type) {
    occurrences++;
    scalarTypesSeen.add(type);
  }

  /** Records a row where the key was present but the value was {@code null}. */
  void observeNull() {
    occurrences++;
    nullCount++;
  }

  /** Records a row where the key was absent entirely. */
  void observeMissing() {
    missingCount++;
  }

  /** Records the type seen for a non-string, non-null scalar without consuming the type set. */
  void observeType(ScalarType type) {
    scalarTypesSeen.add(type);
  }

  long occurrences() {
    return occurrences;
  }

  long nullCount() {
    return nullCount;
  }

  long missingCount() {
    return missingCount;
  }

  boolean distinctOverflowed() {
    return distinctOverflowed;
  }

  Set<String> distinctStrings() {
    return distinctStrings;
  }

  EnumSet<ScalarType> scalarTypesSeen() {
    return scalarTypesSeen;
  }

  /** Lazily creates and returns the nested accumulator for an object/array column. */
  ColumnAccumulator childAccumulator() {
    if (childAccumulator == null) {
      childAccumulator = new ColumnAccumulator(distinctCap);
    }
    return childAccumulator;
  }

  ShapeNode childDeepShape() {
    return childDeepShape;
  }

  void setChildDeepShape(ShapeNode shape) {
    this.childDeepShape = shape;
  }

  long deepScanned() {
    return deepScanned;
  }

  void incrementDeepScanned() {
    deepScanned++;
  }
}
