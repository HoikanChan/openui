package com.huawei.cloudsop.genui.core.validation.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class DataPathResolverTest {

    private final DataPathResolver resolver = new DataPathResolver();

    @Test
    void resolvesExactObjectPath() {
        DataPathResolution.Resolved resolved = assertInstanceOf(DataPathResolution.Resolved.class,
                resolver.resolve(Map.of("device", Map.of("name", "leaf-1")), DataPath.parse("data.device.name")));

        assertEquals("string", resolved.type().displayName());
    }

    @Test
    void preservesArrayPluckSemantics() {
        Map<String, Object> data = Map.of("users", List.of(Map.of("name", "Ada"), Map.of("name", "Lin")));

        DataPathResolution.Resolved resolved = assertInstanceOf(DataPathResolution.Resolved.class,
                resolver.resolve(data, DataPath.parse("data.users.name")));

        assertEquals("string[]", resolved.type().displayName());
        assertEquals(2, resolved.evidence().present());
        assertEquals(2, resolved.evidence().total());
    }

    @Test
    void keepsPartialMissingAndNullAsEvidenceWithoutFailing() {
        Map<String, Object> nullName = new LinkedHashMap<>();
        nullName.put("name", null);
        Map<String, Object> data = Map.of("users",
                List.of(Map.of("name", "Ada"), Map.of("other", "ignored"), nullName));

        DataPathResolution.Resolved resolved = assertInstanceOf(DataPathResolution.Resolved.class,
                resolver.resolve(data, DataPath.parse("data.users.name")));

        // 显式 null 不是缺失：投影结果的元素类型保留 null 分支，缺失分支单独记为证据。
        ValueType.ArrayType array = assertInstanceOf(ValueType.ArrayType.class, resolved.type());
        assertEquals("null | string", array.elementType().displayName());
        assertEquals(1, resolved.evidence().present());
        assertEquals(1, resolved.evidence().missing());
        assertEquals(1, resolved.evidence().nulls());
        assertTrue(resolved.evidence().plucked());
    }

    @Test
    void pluckKeepsOneArrayLevelPerProjection() {
        Map<String, Object> data = Map.of("groups",
                List.of(Map.of("items", List.of(Map.of("name", "a"))),
                        Map.of("items", List.of(Map.of("name", "a")))));

        DataPathResolution.Resolved resolved = assertInstanceOf(DataPathResolution.Resolved.class,
                resolver.resolve(data, DataPath.parse("data.groups.items")));

        // 每个 group 的 items 是 object[]；投影后是 items[][]，不隐式展平。
        ValueType.ArrayType outer = assertInstanceOf(ValueType.ArrayType.class, resolved.type());
        assertInstanceOf(ValueType.ArrayType.class, outer.elementType());
        assertEquals(2, resolved.evidence().present());
        assertTrue(resolved.evidence().plucked());
    }

    @Test
    void plainFieldAccessIsNotMarkedAsPluck() {
        DataPathResolution.Resolved resolved = assertInstanceOf(DataPathResolution.Resolved.class,
                resolver.resolve(Map.of("rows", List.of(Map.of("name", "Ada"))), DataPath.parse("data.rows")));

        assertFalse(resolved.evidence().plucked());
    }

    @Test
    void distinguishesMissingInvalidTraversalAndEmptyArrayEvidence() {
        assertInstanceOf(DataPathResolution.Missing.class,
                resolver.resolve(Map.of("device", Map.of()), DataPath.parse("data.device.name")));

        assertInstanceOf(DataPathResolution.InvalidTraversal.class,
                resolver.resolve(Map.of("device", "leaf-1"), DataPath.parse("data.device.name")));

        assertInstanceOf(DataPathResolution.Unprovable.class,
                resolver.resolve(Map.of("users", List.of()), DataPath.parse("data.users.name")));
    }

    @Test
    void resolvesLiteralIndexesAndRejectsOutOfRangeIndexes() {
        Map<String, Object> data = Map.of("users", List.of(Map.of("name", "Ada")));

        DataPathResolution.Resolved resolved = assertInstanceOf(DataPathResolution.Resolved.class,
                resolver.resolve(data, DataPath.parse("data.users[0].name")));
        assertEquals("string", resolved.type().displayName());

        assertInstanceOf(DataPathResolution.Missing.class,
                resolver.resolve(data, DataPath.parse("data.users[2].name")));
    }

    @Test
    void rejectsLengthWithCountRepairHint() {
        DataPathResolution.InvalidTraversal failure = assertInstanceOf(DataPathResolution.InvalidTraversal.class,
                resolver.resolve(Map.of("users", List.of(Map.of("name", "Ada"))), DataPath.parse("data.users.length")));

        assertTrue(failure.hint().contains("@Count"));
    }
}
