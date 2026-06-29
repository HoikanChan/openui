package com.huawei.cloudsop.genui.core.prompt.characterize;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Single-pass, deterministic walker that reduces a host-data value into a small same-shape
 * "sample" tree plus an inferred {@link ShapeNode} schema tree.
 *
 * <p>The sample is always the first {@code sampleRows} elements of any array (never a random or
 * reordered subset), enum domains are sorted lexicographically so they are robust to upstream row
 * reordering, and {@link Map} key order is preserved. Given the same input and config, {@link
 * #walk} produces a byte-identical sample and a structurally-equal shape every time.
 */
public final class ShapeWalker {
  private ShapeWalker() {}

  /** The single Unicode ellipsis character (U+2026) used to mark truncated strings. */
  private static final String ELLIPSIS = "…";

  public static Characterized walk(Object value, CharacterizationConfig cfg, int depth) {
    if (value == null) {
      return new Characterized(null, new ScalarShape(ScalarType.NULL));
    }
    if (value instanceof String s) {
      return walkString(s, cfg);
    }
    if (value instanceof Long || value instanceof Double) {
      return new Characterized(value, new ScalarShape(ScalarType.NUMBER));
    }
    if (value instanceof Boolean) {
      return new Characterized(value, new ScalarShape(ScalarType.BOOLEAN));
    }
    if (value instanceof Map<?, ?> map) {
      return walkObject(map, cfg, depth);
    }
    if (value instanceof List<?> list) {
      return walkArray(list, cfg, depth);
    }
    return new Characterized(value, new ScalarShape(ScalarType.UNKNOWN));
  }

  private static Characterized walkString(String s, CharacterizationConfig cfg) {
    String sample =
        s.length() > cfg.maxStringLen() ? s.substring(0, cfg.maxStringLen()) + ELLIPSIS : s;
    return new Characterized(sample, new ScalarShape(ScalarType.STRING));
  }

  private static Characterized walkObject(Map<?, ?> map, CharacterizationConfig cfg, int depth) {
    LinkedHashMap<String, Object> sample = new LinkedHashMap<>();
    LinkedHashMap<String, FieldShape> fields = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      String key = String.valueOf(entry.getKey());
      Characterized child = walk(entry.getValue(), cfg, depth + 1);
      sample.put(key, child.sample());
      fields.put(key, new FieldShape(child.shape(), false, false));
    }
    return new Characterized(sample, new ObjectShape(fields));
  }

  private static Characterized walkArray(List<?> list, CharacterizationConfig cfg, int depth) {
    int n = list.size();
    int k = Math.min(cfg.sampleRows(), n);

    // Pass A: sample, first K elements only.
    List<Object> sample = new ArrayList<>(k);
    for (int i = 0; i < k; i++) {
      sample.add(walk(list.get(i), cfg, depth + 1).sample());
    }

    // Pass B: feature scan over all n elements to infer the element shape.
    ShapeNode elementShape = scanElementShape(list, cfg, depth);

    boolean truncated = n > cfg.sampleRows();
    return new Characterized(sample, new ArrayShape(elementShape, n, truncated));
  }

  private static ShapeNode scanElementShape(List<?> list, CharacterizationConfig cfg, int depth) {
    if (list.isEmpty()) {
      return new ScalarShape(ScalarType.UNKNOWN);
    }

    Object first = list.get(0);
    if (first instanceof Map) {
      return scanObjectArray(list, cfg, depth);
    }
    if (first instanceof Long || first instanceof Double) {
      return scanNumberArray(list);
    }
    if (first instanceof String) {
      return scanStringArray(list, cfg);
    }
    // Heterogeneous / nested array element: v1 does not go deeper.
    return walk(first, cfg, depth + 1).shape();
  }

  private static ShapeNode scanNumberArray(List<?> list) {
    for (Object element : list) {
      if (!(element instanceof Long) && !(element instanceof Double)) {
        return new ScalarShape(ScalarType.UNKNOWN);
      }
    }
    return new ScalarShape(ScalarType.NUMBER);
  }

  private static ShapeNode scanStringArray(List<?> list, CharacterizationConfig cfg) {
    ColumnAccumulator accumulator = new ColumnAccumulator(cfg.enumMaxDistinct());
    long total = 0;
    for (Object element : list) {
      if (!(element instanceof String s)) {
        return new ScalarShape(ScalarType.UNKNOWN);
      }
      accumulator.observePresent(s);
      total++;
    }
    return finalizeStringShape(accumulator, total, cfg);
  }

  private static ShapeNode scanObjectArray(List<?> list, CharacterizationConfig cfg, int depth) {
    LinkedHashMap<String, ColumnAccumulator> columns = new LinkedHashMap<>();
    long n = 0;

    for (Object rawRow : list) {
      n++;
      if (!(rawRow instanceof Map<?, ?> row)) {
        // A heterogeneous element among object rows: treat the whole array as opaque.
        return new ScalarShape(ScalarType.UNKNOWN);
      }
      long rowsSeenBeforeThis = n - 1;
      for (Map.Entry<?, ?> entry : row.entrySet()) {
        String key = String.valueOf(entry.getKey());
        columns.computeIfAbsent(
            key,
            k -> {
              ColumnAccumulator accumulator = new ColumnAccumulator(cfg.enumMaxDistinct());
              // Backfill: every earlier row lacked this key, so it was missing there.
              for (long i = 0; i < rowsSeenBeforeThis; i++) {
                accumulator.observeMissing();
              }
              return accumulator;
            });
      }
      for (Map.Entry<String, ColumnAccumulator> columnEntry : columns.entrySet()) {
        String key = columnEntry.getKey();
        ColumnAccumulator accumulator = columnEntry.getValue();
        if (!row.containsKey(key)) {
          accumulator.observeMissing();
          continue;
        }
        observeColumnValue(accumulator, row.get(key), cfg, depth, n <= cfg.deepScanLimit());
      }
    }

    LinkedHashMap<String, FieldShape> fields = new LinkedHashMap<>();
    for (Map.Entry<String, ColumnAccumulator> entry : columns.entrySet()) {
      fields.put(entry.getKey(), finalizeColumn(entry.getValue(), n, cfg));
    }
    return new ObjectShape(fields);
  }

  private static void observeColumnValue(
      ColumnAccumulator accumulator,
      Object value,
      CharacterizationConfig cfg,
      int depth,
      boolean withinDeepScanLimit) {
    if (value == null) {
      accumulator.observeNull();
      return;
    }
    if (value instanceof String s) {
      accumulator.observePresent(s);
      accumulator.observeType(ScalarType.STRING);
      return;
    }
    if (value instanceof Long || value instanceof Double) {
      accumulator.observeScalarType(ScalarType.NUMBER);
      return;
    }
    if (value instanceof Boolean) {
      accumulator.observeScalarType(ScalarType.BOOLEAN);
      return;
    }
    if (value instanceof Map || value instanceof List) {
      accumulator.observeScalarType(ScalarType.UNKNOWN);
      if (withinDeepScanLimit && accumulator.deepScanned() < cfg.deepScanLimit()) {
        accumulator.incrementDeepScanned();
        // v1 best-effort: the nested-object column's deep schema is last-row-wins, not a union
        // across rows. For columns whose nested objects have differing inner keys, the sidecar
        // reflects the most recently scanned (within deepScanLimit) row's shape. Top-level
        // enum/count completeness is unaffected; widening this to a union is deferred (see design).
        ShapeNode deepShape = walk(value, cfg, depth + 1).shape();
        accumulator.setChildDeepShape(deepShape);
      }
      return;
    }
    accumulator.observeScalarType(ScalarType.UNKNOWN);
  }

  private static FieldShape finalizeColumn(
      ColumnAccumulator accumulator, long n, CharacterizationConfig cfg) {
    boolean nullable = accumulator.nullCount() > 0;
    boolean optional = accumulator.occurrences() < n;

    EnumSet<ScalarType> types = accumulator.scalarTypesSeen();
    ShapeNode node;
    if (types.isEmpty()) {
      // Every observed row was null (or the column never had a non-null value).
      node = new ScalarShape(ScalarType.NULL);
    } else if (types.size() == 1 && types.contains(ScalarType.STRING)) {
      long nonNullTotal = accumulator.occurrences() - accumulator.nullCount();
      node = finalizeStringShape(accumulator, nonNullTotal, cfg);
    } else if (types.size() == 1 && types.contains(ScalarType.UNKNOWN)
        && accumulator.childDeepShape() != null) {
      node = accumulator.childDeepShape();
    } else if (types.size() == 1) {
      node = new ScalarShape(types.iterator().next());
    } else {
      node = new ScalarShape(ScalarType.UNKNOWN);
    }

    return new FieldShape(node, optional, nullable);
  }

  private static ShapeNode finalizeStringShape(
      ColumnAccumulator accumulator, long total, CharacterizationConfig cfg) {
    if (!accumulator.distinctOverflowed()) {
      long distinct = accumulator.distinctStrings().size();
      double ratio = total == 0 ? 0.0 : distinct / (double) total;
      if (distinct <= cfg.enumMaxDistinct() && ratio <= cfg.enumMaxRatio()) {
        return new EnumShape(new ArrayList<>(new TreeSet<>(accumulator.distinctStrings())));
      }
    }
    return new ScalarShape(ScalarType.STRING);
  }
}
