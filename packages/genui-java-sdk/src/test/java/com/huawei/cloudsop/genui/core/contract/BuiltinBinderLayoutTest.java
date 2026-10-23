package com.huawei.cloudsop.genui.core.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.huawei.cloudsop.genui.core.GenerationSdkException;
import com.huawei.cloudsop.genui.core.Json;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * #83:binder 布局元数据的加载期校验——Each 的 input 下标必须是整数、在参数表内、且不得指向 binder 位或
 * 延迟 body 位(#77 §4.1/§8.4:布局配置错误在加载时失败,不做有损数值转换)。
 */
class BuiltinBinderLayoutTest {

    @Test
    void fractionalInputIndexIsRejected() {
        GenerationSdkException error = assertThrows(GenerationSdkException.class,
                () -> loadWithEachInput(0.5));
        org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains("input"),
                () -> "应指明 input 非法: " + error.getMessage());
    }

    @Test
    void overflowingInputIndexIsRejected() {
        assertThrows(GenerationSdkException.class, () -> loadWithEachInput(4294967296L));
    }

    @Test
    void inputPointingToBinderIsRejected() {
        GenerationSdkException error = assertThrows(GenerationSdkException.class, () -> loadWithEachInput(1));
        org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains("binder"),
                () -> "应指明 input 不得指向 binder: " + error.getMessage());
    }

    @Test
    void inputPointingToDeferredBodyIsRejected() {
        GenerationSdkException error = assertThrows(GenerationSdkException.class, () -> loadWithEachInput(2));
        org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains("body"),
                () -> "应指明 input 不得指向 body: " + error.getMessage());
    }

    @Test
    void defaultContractEachLayoutIsValid() {
        // 默认契约的 Each 布局:input=0(array)、binder=1(varName, string-or-identifier)、body 恒为末位实参。
        BuiltinSpec each = GenerationContractLoader.loadDefault().builtins().stream()
                .filter(spec -> "Each".equals(spec.name())).findFirst().orElseThrow();
        assertEquals(0, each.inputIndex());
        assertEquals(List.of(1), each.activeBinderIndexes(3));
        assertEquals(2, each.bodyIndex(3));
    }

    /** 复制默认契约 JSON,仅替换 Each 的 input 声明,走公开 fromJson 加载。 */
    private static GenerationContract loadWithEachInput(Object input) {
        String json = Json.stringify(mutateEachInput(input));
        return GenerationContractLoader.fromJson(json);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mutateEachInput(Object input) {
        String original;
        try (var stream = BuiltinBinderLayoutTest.class.getResourceAsStream("/openui/base-contract.json")) {
            original = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        Map<String, Object> raw = (Map<String, Object>) Json.parse(original);
        for (Object entry : (List<Object>) raw.get("builtins")) {
            Map<String, Object> builtin = (Map<String, Object>) entry;
            if ("Each".equals(builtin.get("name"))) {
                builtin.put("input", input);
            }
        }
        return raw;
    }
}
