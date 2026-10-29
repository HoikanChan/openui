package com.huawei.cloudsop.genui.core.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.GenerationSdkException;
import com.huawei.cloudsop.genui.core.Json;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * #83:binder 布局元数据的加载期校验——Each 的 input 下标必须是整数、在参数表内、且不得指向 binder 位或 延迟 body 位(#77
 * §4.1/§8.4:布局配置错误在加载时失败,不做有损数值转换)。
 */
class BuiltinBinderLayoutTest {

    @Test
    void underflowingFractionalInputIndexIsRejected() {
        assertThrows(
                GenerationSdkException.class,
                () -> loadWithEachInput(new java.math.BigDecimal("1E-400")));
    }

    @Test
    void fractionalInputIndexIsRejected() {
        GenerationSdkException error =
                assertThrows(GenerationSdkException.class, () -> loadWithEachInput(0.5));
        org.junit.jupiter.api.Assertions.assertTrue(
                error.getMessage().contains("input"), () -> "应指明 input 非法: " + error.getMessage());
    }

    @Test
    void overflowingInputIndexIsRejected() {
        assertThrows(GenerationSdkException.class, () -> loadWithEachInput(4294967296L));
    }

    @Test
    void inputPointingToBinderIsRejected() {
        GenerationSdkException error =
                assertThrows(GenerationSdkException.class, () -> loadWithEachInput(1));
        org.junit.jupiter.api.Assertions.assertTrue(
                error.getMessage().contains("binder"),
                () -> "应指明 input 不得指向 binder: " + error.getMessage());
    }

    @Test
    void inputPointingToDeferredBodyIsRejected() {
        GenerationSdkException error =
                assertThrows(GenerationSdkException.class, () -> loadWithEachInput(2));
        org.junit.jupiter.api.Assertions.assertTrue(
                error.getMessage().contains("body"),
                () -> "应指明 input 不得指向 body: " + error.getMessage());
    }

    @Test
    void defaultContractEachLayoutIsValid() {
        // 默认契约声明 Each 的三参布局:input=0、binder=1、body=2。
        BuiltinSpec each =
                GenerationContractLoader.loadDefault().builtins().stream()
                        .filter(spec -> "Each".equals(spec.name()))
                        .findFirst()
                        .orElseThrow();
        assertEquals(0, each.inputIndex());
        assertEquals(List.of(1), each.activeBinderIndexes(3));
        assertEquals(2, each.bodyIndex(3));
    }

    @Test
    @SuppressWarnings("unchecked")
    void declaredBodyBeforeSourceIsSharedByValidatorConsumers() {
        Map<String, Object> raw = mutateEachInput(1);
        Map<String, Object> each =
                ((List<Map<String, Object>>) raw.get("builtins"))
                        .stream()
                                .filter(b -> "Each".equals(b.get("name")))
                                .findFirst()
                                .orElseThrow();
        List<Object> params = (List<Object>) each.get("params");
        each.put("params", List.of(params.get(2), params.get(0), params.get(1)));
        each.put("callForms", List.of(Map.of("arity", 3, "body", 0, "binders", List.of(2))));
        GenerationContract contract = GenerationContractLoader.fromJson(Json.stringify(raw));
        var result =
                com.huawei.cloudsop.genui.core.validation.DefaultOpenuiLangValidator.INSTANCE
                        .validate(
                                com.huawei.cloudsop.genui.core.validation.ValidationRequest
                                        .builder()
                                        .dsl(
                                                "root = Stack(@Each(TextContent(row.id), data.rows, row))")
                                        .contract(contract)
                                        .dataModel(Map.of("rows", List.of(Map.of("id", "a"))))
                                        .build());
        assertEquals(
                com.huawei.cloudsop.genui.core.validation.ValidationStatus.VALID,
                result.status(),
                () -> String.valueOf(result.issues()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void invalidAndAmbiguousCallFormsAreRejectedAtLoadTime() {
        for (Object forms :
                List.of(
                        List.of(Map.of("arity", 3, "body", 1, "binders", List.of(1))),
                        List.of(Map.of("arity", 3, "body", 3, "binders", List.of(1))),
                        List.of(Map.of("arity", 3, "body", 2, "binders", List.of(0))),
                        List.of(
                                Map.of("arity", 3, "body", 2, "binders", List.of(1)),
                                Map.of("arity", 3, "body", 2, "binders", List.of(1))),
                        List.of())) {
            Map<String, Object> raw = mutateEachInput(0);
            ((List<Map<String, Object>>) raw.get("builtins"))
                    .stream()
                            .filter(b -> "Each".equals(b.get("name")))
                            .findFirst()
                            .orElseThrow()
                            .put("callForms", forms);
            assertThrows(
                    GenerationSdkException.class,
                    () -> GenerationContractLoader.fromJson(Json.stringify(raw)));
        }
    }

    @Test
    void binderBuiltinWithoutInputDeclarationIsRejectedAtLoadTime() {
        // #83: binder builtin 省略 input 等于漏声明。旧行为会静默加载并让 Each 的元素级检查退化成
        // MISSING_CONTEXT;现在必须在加载期失败,不能把契约笔误变成"未证明"。
        for (String name : List.of("Each", "Render")) {
            Map<String, Object> raw = mutateBuiltin(name, builtin -> builtin.remove("input"));
            GenerationSdkException error =
                    assertThrows(
                            GenerationSdkException.class,
                            () -> GenerationContractLoader.fromJson(Json.stringify(raw)));
            assertTrue(
                    error.getMessage().contains("input"),
                    () -> name + " 应指明缺少 input: " + error.getMessage());
        }
    }

    @Test
    void explicitNullInputOnElementArrayBinderIsRejectedAtLoadTime() {
        // 显式 null 表示"没有元素来源",只能用于 Render 这类无数组参数的 binder builtin;
        // Each 声明了 T[] 参数,用 null 拒绝命名来源会绕过元素检查,加载期必须拒绝。
        GenerationSdkException error =
                assertThrows(GenerationSdkException.class, () -> loadWithEachInput(null));
        assertTrue(error.getMessage().contains("element array"), error::getMessage);
    }

    @Test
    void explicitNullInputOnConcreteArrayBinderIsRejectedAtLoadTime() {
        Map<String, Object> raw = mutateEachSource("number[]", null);
        GenerationSdkException error =
                assertThrows(
                        GenerationSdkException.class,
                        () -> GenerationContractLoader.fromJson(Json.stringify(raw)));
        assertTrue(error.getMessage().contains("element array"), error::getMessage);
    }

    @Test
    void concreteArraySourceStillChecksIndependentBodyErrors() {
        GenerationContract contract =
                GenerationContractLoader.fromJson(Json.stringify(mutateEachSource("number[]", 0)));
        var result =
                com.huawei.cloudsop.genui.core.validation.DefaultOpenuiLangValidator.INSTANCE
                        .validate(
                                com.huawei.cloudsop.genui.core.validation.ValidationRequest
                                        .builder()
                                        .dsl(
                                                "root = Stack(@Each([1], row, TextContent(@Round(\"bad\"))))")
                                        .contract(contract)
                                        .dataModel(Map.of())
                                        .build());
        assertEquals(
                com.huawei.cloudsop.genui.core.validation.ValidationStatus.INVALID,
                result.status());
        assertTrue(
                result.issues().stream()
                        .anyMatch(
                                issue ->
                                        "builtin-argument-type-mismatch".equals(issue.code())
                                                && issue.message().contains("@Round")),
                () -> String.valueOf(result.issues()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void renderDeclaresExplicitNullInputAsNoElementSource() {
        // 默认契约用 "input": null 显式声明 Render 没有元素来源;键存在且值为 null,不是省略。
        Map<String, Object> render = builtinEntry(rawDefaultContract(), "Render");
        assertTrue(render.containsKey("input"), "Render 必须显式声明 input 键");
        assertNull(render.get("input"), "Render 的 input 必须为 null");

        BuiltinSpec spec =
                GenerationContractLoader.loadDefault().builtins().stream()
                        .filter(candidate -> "Render".equals(candidate.name()))
                        .findFirst()
                        .orElseThrow();
        assertEquals(-1, spec.inputIndex());
        assertEquals(1, spec.bodyIndex(2));
        assertEquals(2, spec.bodyIndex(3));
    }

    /** 复制默认契约 JSON,仅替换 Each 的 input 声明,走公开 fromJson 加载。 */
    private static GenerationContract loadWithEachInput(Object input) {
        String json = Json.stringify(mutateEachInput(input));
        return GenerationContractLoader.fromJson(json);
    }

    private static Map<String, Object> mutateEachInput(Object input) {
        return mutateBuiltin("Each", builtin -> builtin.put("input", input));
    }

    private static Map<String, Object> mutateEachSource(String type, Object input) {
        Map<String, Object> raw = mutateEachInput(input);
        Map<String, Object> each = builtinEntry(raw, "Each");
        Json.asObject(Json.asList(each.get("params"), "params").get(0), "source").put("type", type);
        return raw;
    }

    /** 复制默认契约 JSON,按 builtin 名修改条目后返回原始 map。 */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> mutateBuiltin(
            String name, java.util.function.Consumer<Map<String, Object>> mutation) {
        Map<String, Object> raw = rawDefaultContract();
        for (Object entry : (List<Object>) raw.get("builtins")) {
            Map<String, Object> builtin = (Map<String, Object>) entry;
            if (name.equals(builtin.get("name"))) {
                mutation.accept(builtin);
            }
        }
        return raw;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> builtinEntry(Map<String, Object> raw, String name) {
        for (Object entry : (List<Object>) raw.get("builtins")) {
            Map<String, Object> builtin = (Map<String, Object>) entry;
            if (name.equals(builtin.get("name"))) {
                return builtin;
            }
        }
        throw new IllegalStateException("builtin not found: " + name);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> rawDefaultContract() {
        String original;
        try (var stream =
                BuiltinBinderLayoutTest.class.getResourceAsStream("/openui/base-contract.json")) {
            original = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        return (Map<String, Object>) Json.parse(original);
    }
}
