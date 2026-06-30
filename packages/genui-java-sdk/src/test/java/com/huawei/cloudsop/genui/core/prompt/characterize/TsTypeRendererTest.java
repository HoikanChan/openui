/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.prompt.characterize;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

class TsTypeRendererTest {

    private static FieldShape field(ShapeNode node) {
        return new FieldShape(node, false, false);
    }

    private static FieldShape field(ShapeNode node, boolean optional, boolean nullable) {
        return new FieldShape(node, optional, nullable);
    }

    @Test
    void rendersTheReadmeShapedExampleWithSortedEnumDomain() {
        LinkedHashMap<String, FieldShape> rowFields = new LinkedHashMap<>();
        rowFields.put("id", field(new ScalarShape(ScalarType.NUMBER)));
        rowFields.put("name", field(new ScalarShape(ScalarType.STRING)));
        rowFields.put("status", field(new EnumShape(List.of("closed", "open", "pending"))));
        rowFields.put("revenue", field(new ScalarShape(ScalarType.NUMBER)));
        rowFields.put("note", field(new ScalarShape(ScalarType.STRING), true, true));
        ObjectShape rowShape = new ObjectShape(rowFields);
        ArrayShape rowsShape = new ArrayShape(rowShape, 10000, true);

        LinkedHashMap<String, FieldShape> rootFields = new LinkedHashMap<>();
        rootFields.put("title", field(new ScalarShape(ScalarType.STRING)));
        rootFields.put("rows", field(rowsShape));
        ObjectShape root = new ObjectShape(rootFields);

        String expected = "data: {\n" + "  title: string\n" + "  rows: {\n" + "    id: number\n" + "    name: string\n"
                + "    status: \"closed\" | \"open\" | \"pending\"\n" + "    revenue: number\n"
                + "    note?: string | null\n" + "  }[]  // 10000 items (showing 3)\n" + "}";

        assertEquals(expected, TsTypeRenderer.render(root, 3));
    }

    @Test
    void enumUnionIsSortedAndIncludesUnsampledValue() {
        EnumShape enumShape = new EnumShape(List.of("closed", "open", "pending"));
        LinkedHashMap<String, FieldShape> fields = new LinkedHashMap<>();
        fields.put("status", field(enumShape));
        ObjectShape root = new ObjectShape(fields);

        String result = TsTypeRenderer.render(root, 3);

        assertEquals("data: {\n  status: \"closed\" | \"open\" | \"pending\"\n}", result);
    }

    @Test
    void truncatedArrayAppendsItemCountComment() {
        ArrayShape array = new ArrayShape(new ScalarShape(ScalarType.NUMBER), 500, true);
        LinkedHashMap<String, FieldShape> fields = new LinkedHashMap<>();
        fields.put("values", field(array));
        ObjectShape root = new ObjectShape(fields);

        String result = TsTypeRenderer.render(root, 5);

        assertEquals("data: {\n  values: number[]  // 500 items (showing 5)\n}", result);
    }

    @Test
    void truncatedArrayShowingCountIsCappedAtActualCountWhenSmallerThanSampleRows() {
        ArrayShape array = new ArrayShape(new ScalarShape(ScalarType.NUMBER), 2, true);
        LinkedHashMap<String, FieldShape> fields = new LinkedHashMap<>();
        fields.put("values", field(array));
        ObjectShape root = new ObjectShape(fields);

        String result = TsTypeRenderer.render(root, 5);

        assertEquals("data: {\n  values: number[]  // 2 items (showing 2)\n}", result);
    }

    @Test
    void nonTruncatedArrayHasNoComment() {
        ArrayShape array = new ArrayShape(new ScalarShape(ScalarType.STRING), 2, false);
        LinkedHashMap<String, FieldShape> fields = new LinkedHashMap<>();
        fields.put("tags", field(array));
        ObjectShape root = new ObjectShape(fields);

        String result = TsTypeRenderer.render(root, 5);

        assertEquals("data: {\n  tags: string[]\n}", result);
    }

    @Test
    void optionalFieldGetsQuestionMarkSuffix() {
        LinkedHashMap<String, FieldShape> fields = new LinkedHashMap<>();
        fields.put("nickname", field(new ScalarShape(ScalarType.STRING), true, false));
        ObjectShape root = new ObjectShape(fields);

        String result = TsTypeRenderer.render(root, 3);

        assertEquals("data: {\n  nickname?: string\n}", result);
    }

    @Test
    void nullableFieldAppendsPipeNullSuffix() {
        LinkedHashMap<String, FieldShape> fields = new LinkedHashMap<>();
        fields.put("middleName", field(new ScalarShape(ScalarType.STRING), false, true));
        ObjectShape root = new ObjectShape(fields);

        String result = TsTypeRenderer.render(root, 3);

        assertEquals("data: {\n  middleName: string | null\n}", result);
    }

    @Test
    void optionalAndNullableFieldCombinesBothSuffixes() {
        LinkedHashMap<String, FieldShape> fields = new LinkedHashMap<>();
        fields.put("note", field(new ScalarShape(ScalarType.STRING), true, true));
        ObjectShape root = new ObjectShape(fields);

        String result = TsTypeRenderer.render(root, 3);

        assertEquals("data: {\n  note?: string | null\n}", result);
    }

    @Test
    void nestedObjectArrayIndentsEachNestingLevelCleanly() {
        LinkedHashMap<String, FieldShape> innerFields = new LinkedHashMap<>();
        innerFields.put("id", field(new ScalarShape(ScalarType.NUMBER)));
        ObjectShape inner = new ObjectShape(innerFields);
        ArrayShape innerArray = new ArrayShape(inner, 2, false);

        LinkedHashMap<String, FieldShape> outerFields = new LinkedHashMap<>();
        outerFields.put("items", field(innerArray));
        ObjectShape outer = new ObjectShape(outerFields);
        ArrayShape outerArray = new ArrayShape(outer, 2, false);

        LinkedHashMap<String, FieldShape> rootFields = new LinkedHashMap<>();
        rootFields.put("groups", field(outerArray));
        ObjectShape root = new ObjectShape(rootFields);

        String result = TsTypeRenderer.render(root, 3);

        String expected = "data: {\n" + "  groups: {\n" + "    items: {\n" + "      id: number\n" + "    }[]\n"
                + "  }[]\n" + "}";
        assertEquals(expected, result);
    }

    @Test
    void scalarStringRendersAsString() {
        LinkedHashMap<String, FieldShape> fields = new LinkedHashMap<>();
        fields.put("name", field(new ScalarShape(ScalarType.STRING)));
        ObjectShape root = new ObjectShape(fields);

        assertEquals("data: {\n  name: string\n}", TsTypeRenderer.render(root, 3));
    }

    @Test
    void scalarNumberRendersAsNumber() {
        LinkedHashMap<String, FieldShape> fields = new LinkedHashMap<>();
        fields.put("age", field(new ScalarShape(ScalarType.NUMBER)));
        ObjectShape root = new ObjectShape(fields);

        assertEquals("data: {\n  age: number\n}", TsTypeRenderer.render(root, 3));
    }

    @Test
    void scalarBooleanRendersAsBoolean() {
        LinkedHashMap<String, FieldShape> fields = new LinkedHashMap<>();
        fields.put("active", field(new ScalarShape(ScalarType.BOOLEAN)));
        ObjectShape root = new ObjectShape(fields);

        assertEquals("data: {\n  active: boolean\n}", TsTypeRenderer.render(root, 3));
    }

    @Test
    void scalarNullTypeRendersAsNullLiteral() {
        LinkedHashMap<String, FieldShape> fields = new LinkedHashMap<>();
        fields.put("deletedAt", field(new ScalarShape(ScalarType.NULL)));
        ObjectShape root = new ObjectShape(fields);

        assertEquals("data: {\n  deletedAt: null\n}", TsTypeRenderer.render(root, 3));
    }

    @Test
    void scalarUnknownTypeRendersAsUnknown() {
        LinkedHashMap<String, FieldShape> fields = new LinkedHashMap<>();
        fields.put("value", field(new ScalarShape(ScalarType.UNKNOWN)));
        ObjectShape root = new ObjectShape(fields);

        assertEquals("data: {\n  value: unknown\n}", TsTypeRenderer.render(root, 3));
    }

    @Test
    void enumValuesEscapeQuotesAndBackslashes() {
        EnumShape enumShape = new EnumShape(List.of("a\"b", "c\\d"));
        LinkedHashMap<String, FieldShape> fields = new LinkedHashMap<>();
        fields.put("tag", field(enumShape));
        ObjectShape root = new ObjectShape(fields);

        String result = TsTypeRenderer.render(root, 3);

        assertEquals("data: {\n  tag: \"a\\\"b\" | \"c\\\\d\"\n}", result);
    }

    @Test
    void nonObjectRootScalarRendersGracefully() {
        ScalarShape root = new ScalarShape(ScalarType.STRING);

        assertEquals("data: string", TsTypeRenderer.render(root, 3));
    }

    @Test
    void nonObjectRootArrayRendersGracefully() {
        ArrayShape root = new ArrayShape(new ScalarShape(ScalarType.NUMBER), 3, false);

        assertEquals("data: number[]", TsTypeRenderer.render(root, 3));
    }

    @Test
    void sameShapeProducesIdenticalStringTwice() {
        LinkedHashMap<String, FieldShape> fields = new LinkedHashMap<>();
        fields.put("id", field(new ScalarShape(ScalarType.NUMBER)));
        fields.put("status", field(new EnumShape(List.of("a", "b"))));
        ObjectShape root = new ObjectShape(fields);

        assertEquals(TsTypeRenderer.render(root, 3), TsTypeRenderer.render(root, 3));
    }
}
