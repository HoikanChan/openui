package com.huawei.cloudsop.genui.core.prompt.characterize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.Json;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ShapeWalkerTest {

  // ---- 6.1 Isomorphism ----

  @Test
  void objectArrayStaysAnArrayWithNoInjectedKeysAndMatchingKeySets() {
    List<Object> rows = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      LinkedHashMap<String, Object> row = new LinkedHashMap<>();
      row.put("id", (long) i);
      row.put("name", "item-" + i);
      rows.add(row);
    }
    CharacterizationConfig cfg = CharacterizationConfig.builder().sampleRows(3).build();

    Characterized result = ShapeWalker.walk(rows, cfg, 0);

    List<?> sample = (List<?>) result.sample();
    assertEquals(3, sample.size());
    for (Object element : sample) {
      Map<?, ?> row = (Map<?, ?>) element;
      assertEquals(List.of("id", "name"), new ArrayList<>(row.keySet()));
    }
    assertInstanceOf(ArrayShape.class, result.shape());
  }

  @Test
  void plainObjectKeepsAllKeysInOrderWithNoInjectedFields() {
    LinkedHashMap<String, Object> input = new LinkedHashMap<>();
    input.put("a", 1L);
    input.put("b", "x");
    input.put("c", null);
    CharacterizationConfig cfg = CharacterizationConfig.defaults();

    Characterized result = ShapeWalker.walk(input, cfg, 0);

    Map<?, ?> sample = (Map<?, ?>) result.sample();
    assertEquals(List.of("a", "b", "c"), new ArrayList<>(sample.keySet()));
    ObjectShape shape = (ObjectShape) result.shape();
    assertEquals(List.of("a", "b", "c"), new ArrayList<>(shape.fields().keySet()));
  }

  // ---- 6.2 Sampling ----

  @Test
  void largeArraySamplesExactlyKElementsAndRecordsTrueCount() {
    List<Object> rows = new ArrayList<>();
    for (int i = 0; i < 10000; i++) {
      LinkedHashMap<String, Object> row = new LinkedHashMap<>();
      row.put("id", (long) i);
      rows.add(row);
    }
    CharacterizationConfig cfg = CharacterizationConfig.builder().sampleRows(3).build();

    Characterized result = ShapeWalker.walk(rows, cfg, 0);

    List<?> sample = (List<?>) result.sample();
    assertEquals(3, sample.size());
    ArrayShape shape = (ArrayShape) result.shape();
    assertEquals(10000, shape.count());
    assertTrue(shape.truncated());
  }

  @Test
  void smallArrayIsKeptAsIsWithoutTruncation() {
    List<Object> rows = List.of(1L, 2L);
    CharacterizationConfig cfg = CharacterizationConfig.builder().sampleRows(3).build();

    Characterized result = ShapeWalker.walk(rows, cfg, 0);

    List<?> sample = (List<?>) result.sample();
    assertEquals(2, sample.size());
    ArrayShape shape = (ArrayShape) result.shape();
    assertEquals(2, shape.count());
    assertFalse(shape.truncated());
  }

  // ---- 6.3 Enum ----

  @Test
  void enumColumnDomainIsCompleteAndSortedEvenWhenSampleMissesAValue() {
    List<Object> rows = new ArrayList<>();
    String[] statuses = {"open", "closed", "open", "closed", "pending", "open"};
    for (String status : statuses) {
      LinkedHashMap<String, Object> row = new LinkedHashMap<>();
      row.put("status", status);
      rows.add(row);
    }
    CharacterizationConfig cfg = CharacterizationConfig.builder().sampleRows(3).build();

    Characterized result = ShapeWalker.walk(rows, cfg, 0);

    ArrayShape arrayShape = (ArrayShape) result.shape();
    ObjectShape elementShape = (ObjectShape) arrayShape.element();
    FieldShape statusField = elementShape.fields().get("status");
    assertInstanceOf(EnumShape.class, statusField.node());
    EnumShape enumShape = (EnumShape) statusField.node();
    assertEquals(List.of("closed", "open", "pending"), enumShape.domain());
  }

  @Test
  void highCardinalityColumnDegradesToFreeTextString() {
    List<Object> rows = new ArrayList<>();
    for (int i = 0; i < 8000; i++) {
      LinkedHashMap<String, Object> row = new LinkedHashMap<>();
      row.put("token", "value-" + i);
      rows.add(row);
    }
    CharacterizationConfig cfg =
        CharacterizationConfig.builder().sampleRows(3).enumMaxDistinct(50).enumMaxRatio(0.5)
            .build();

    Characterized result = ShapeWalker.walk(rows, cfg, 0);

    ArrayShape arrayShape = (ArrayShape) result.shape();
    ObjectShape elementShape = (ObjectShape) arrayShape.element();
    FieldShape tokenField = elementShape.fields().get("token");
    assertInstanceOf(ScalarShape.class, tokenField.node());
    assertEquals(ScalarType.STRING, ((ScalarShape) tokenField.node()).type());
  }

  @Test
  void stringArrayIsTreatedAsOneEnumColumn() {
    List<Object> rows =
        new ArrayList<>(
            List.of(
                "red", "green", "blue", "red", "green", "blue", "red", "green", "blue", "red"));
    CharacterizationConfig cfg = CharacterizationConfig.builder().sampleRows(2).build();

    Characterized result = ShapeWalker.walk(rows, cfg, 0);

    ArrayShape arrayShape = (ArrayShape) result.shape();
    assertInstanceOf(EnumShape.class, arrayShape.element());
    EnumShape enumShape = (EnumShape) arrayShape.element();
    assertEquals(List.of("blue", "green", "red"), enumShape.domain());
  }

  @Test
  void numberArrayIsASeriesWithNoDownsamplingOfShape() {
    List<Object> rows = new ArrayList<>();
    for (int i = 0; i < 100; i++) rows.add((long) i);
    CharacterizationConfig cfg = CharacterizationConfig.builder().sampleRows(3).build();

    Characterized result = ShapeWalker.walk(rows, cfg, 0);

    ArrayShape arrayShape = (ArrayShape) result.shape();
    assertInstanceOf(ScalarShape.class, arrayShape.element());
    assertEquals(ScalarType.NUMBER, ((ScalarShape) arrayShape.element()).type());
    assertEquals(100, arrayShape.count());
  }

  // ---- 6.4 String truncation + size gate ----

  @Test
  void longStringIsTruncatedWithEllipsisInSampleButShapeStaysString() {
    String longValue = "x".repeat(5000);
    CharacterizationConfig cfg = CharacterizationConfig.builder().maxStringLen(80).build();

    Characterized result = ShapeWalker.walk(longValue, cfg, 0);

    String sample = (String) result.sample();
    assertEquals(81, sample.length());
    assertEquals("x".repeat(80) + "…", sample);
    assertEquals(ScalarType.STRING, ((ScalarShape) result.shape()).type());
  }

  @Test
  void scalarsOtherThanLongStringsAreKeptVerbatim() {
    CharacterizationConfig cfg = CharacterizationConfig.defaults();

    Characterized number = ShapeWalker.walk(42L, cfg, 0);
    assertEquals(42L, number.sample());
    assertEquals(ScalarType.NUMBER, ((ScalarShape) number.shape()).type());

    Characterized decimal = ShapeWalker.walk(1.5, cfg, 0);
    assertEquals(1.5, decimal.sample());
    assertEquals(ScalarType.NUMBER, ((ScalarShape) decimal.shape()).type());

    Characterized bool = ShapeWalker.walk(true, cfg, 0);
    assertEquals(true, bool.sample());
    assertEquals(ScalarType.BOOLEAN, ((ScalarShape) bool.shape()).type());

    Characterized nullValue = ShapeWalker.walk(null, cfg, 0);
    assertEquals(null, nullValue.sample());
    assertEquals(ScalarType.NULL, ((ScalarShape) nullValue.shape()).type());
  }

  @Test
  void smallRawPassesThroughUnchangedViaCharacterizer() {
    LinkedHashMap<String, Object> raw = new LinkedHashMap<>();
    raw.put("a", 1L);
    String json = Json.stringify(raw);
    assertTrue(json.length() < 500);

    CharacterizationConfig cfg = CharacterizationConfig.defaults();
    Characterized result = Characterizer.characterize(raw, cfg);

    assertEquals(raw, result.sample());
  }

  @Test
  void largeRawTriggersWalkAndShrinksSample() {
    List<Object> rows = new ArrayList<>();
    for (int i = 0; i < 50000; i++) {
      LinkedHashMap<String, Object> row = new LinkedHashMap<>();
      row.put("id", (long) i);
      row.put("payload", "padding-data-to-grow-the-payload-" + i);
      rows.add(row);
    }
    LinkedHashMap<String, Object> raw = new LinkedHashMap<>();
    raw.put("rows", rows);
    String json = Json.stringify(raw);
    assertTrue(json.length() > 2_000_000 / 4); // sanity: comfortably above default trigger

    CharacterizationConfig cfg = CharacterizationConfig.defaults();
    Characterized result = Characterizer.characterize(raw, cfg);

    Map<?, ?> sample = (Map<?, ?>) result.sample();
    List<?> sampledRows = (List<?>) sample.get("rows");
    assertEquals(cfg.sampleRows(), sampledRows.size());
  }

  // ---- 6.5 Determinism ----

  @Test
  void sameInputProducesByteIdenticalSampleAndEqualShapeTwice() {
    List<Object> rows = new ArrayList<>();
    for (int i = 0; i < 50; i++) {
      LinkedHashMap<String, Object> row = new LinkedHashMap<>();
      row.put("id", (long) i);
      row.put("status", i % 2 == 0 ? "open" : "closed");
      rows.add(row);
    }
    CharacterizationConfig cfg = CharacterizationConfig.builder().sampleRows(5).build();

    Characterized first = ShapeWalker.walk(rows, cfg, 0);
    Characterized second = ShapeWalker.walk(rows, cfg, 0);

    assertEquals(Json.stringify(first.sample()), Json.stringify(second.sample()));
    assertEquals(first.shape(), second.shape());
  }

  // ---- nullable / optional / mixed-type columns ----

  @Test
  void columnWithSomeNullValuesIsMarkedNullable() {
    List<Object> rows = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      LinkedHashMap<String, Object> row = new LinkedHashMap<>();
      row.put("note", i == 0 ? null : "text-" + i);
      rows.add(row);
    }
    CharacterizationConfig cfg = CharacterizationConfig.builder().sampleRows(2).build();

    Characterized result = ShapeWalker.walk(rows, cfg, 0);

    ArrayShape arrayShape = (ArrayShape) result.shape();
    ObjectShape elementShape = (ObjectShape) arrayShape.element();
    FieldShape noteField = elementShape.fields().get("note");
    assertTrue(noteField.nullable());
    assertFalse(noteField.optional());
  }

  @Test
  void columnMissingFromSomeRowsIsMarkedOptional() {
    List<Object> rows = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      LinkedHashMap<String, Object> row = new LinkedHashMap<>();
      row.put("id", (long) i);
      if (i % 2 == 0) {
        row.put("extra", "yes");
      }
      rows.add(row);
    }
    CharacterizationConfig cfg = CharacterizationConfig.builder().sampleRows(2).build();

    Characterized result = ShapeWalker.walk(rows, cfg, 0);

    ArrayShape arrayShape = (ArrayShape) result.shape();
    ObjectShape elementShape = (ObjectShape) arrayShape.element();
    FieldShape extraField = elementShape.fields().get("extra");
    assertTrue(extraField.optional());
    assertFalse(extraField.nullable());
  }

  @Test
  void mixedScalarTypeColumnBecomesUnknown() {
    List<Object> rows = new ArrayList<>();
    LinkedHashMap<String, Object> row1 = new LinkedHashMap<>();
    row1.put("value", 1L);
    rows.add(row1);
    LinkedHashMap<String, Object> row2 = new LinkedHashMap<>();
    row2.put("value", "two");
    rows.add(row2);
    LinkedHashMap<String, Object> row3 = new LinkedHashMap<>();
    row3.put("value", true);
    rows.add(row3);
    CharacterizationConfig cfg = CharacterizationConfig.builder().sampleRows(2).build();

    Characterized result = ShapeWalker.walk(rows, cfg, 0);

    ArrayShape arrayShape = (ArrayShape) result.shape();
    ObjectShape elementShape = (ObjectShape) arrayShape.element();
    FieldShape valueField = elementShape.fields().get("value");
    assertInstanceOf(ScalarShape.class, valueField.node());
    assertEquals(ScalarType.UNKNOWN, ((ScalarShape) valueField.node()).type());
  }

  @Test
  void columnFirstAppearingMidArrayIsOptional() {
    List<Object> rows = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      LinkedHashMap<String, Object> row = new LinkedHashMap<>();
      row.put("id", (long) i);
      // "x" is absent from rows 0-3 and present from row 4 onward.
      if (i >= 4) {
        row.put("x", "present");
      }
      rows.add(row);
    }
    CharacterizationConfig cfg = CharacterizationConfig.builder().sampleRows(2).build();

    Characterized result = ShapeWalker.walk(rows, cfg, 0);

    ArrayShape arrayShape = (ArrayShape) result.shape();
    ObjectShape elementShape = (ObjectShape) arrayShape.element();
    FieldShape xField = elementShape.fields().get("x");
    assertTrue(xField.optional());
    assertFalse(xField.nullable());
  }
}
