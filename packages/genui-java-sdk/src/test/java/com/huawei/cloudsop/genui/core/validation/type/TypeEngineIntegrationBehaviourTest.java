package com.huawei.cloudsop.genui.core.validation.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.contract.GenerationContractLoader;
import com.huawei.cloudsop.genui.core.validation.DefaultOpenuiLangValidator;
import com.huawei.cloudsop.genui.core.validation.ValidationMode;
import com.huawei.cloudsop.genui.core.validation.ValidationRequest;
import com.huawei.cloudsop.genui.core.validation.ValidationResult;
import com.huawei.cloudsop.genui.core.validation.ValidationStatus;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * #90 集成行为:类型分析只对入口可达的当前定义负责,且不修改调用方输入。
 */
class TypeEngineIntegrationBehaviourTest {

    private static ValidationResult validate(String dsl, Map<String, Object> dataModel) {
        return DefaultOpenuiLangValidator.INSTANCE
                .validate(ValidationRequest.builder().dsl(dsl).contract(GenerationContractLoader.loadDefault())
                        .dataModel(dataModel).mode(ValidationMode.FINAL).build());
    }

    @Test
    void unreachableHelperProducesNoCurrentTypeError() {
        // orphan 未被 root 引用:不是当前可渲染程序的一部分,其坏数据路径不得产生当前错误。
        String dsl = "root = Stack([])\norphan = CardHeader(data.missing.value)";

        ValidationResult result = validate(dsl, Map.of("device", Map.of("name", "leaf-1")));

        assertEquals(ValidationStatus.VALID, result.status(), () -> String.valueOf(result.issues()));
        assertFalse(result.issues().stream().anyMatch(issue -> "type-data-path-missing".equals(issue.code())),
                () -> String.valueOf(result.issues()));
        assertEquals("0", result.metadata().extra().get("typeChecks.mismatched"));
    }

    @Test
    void reachableHelperWithSameBodyIsStillChecked() {
        // 同样的坏路径,一旦被 root 引用就必须报错——证明上一条不是靠跳过检查通过的。
        String dsl = "root = Stack([orphan])\norphan = CardHeader(data.missing.value)";

        ValidationResult result = validate(dsl, Map.of("device", Map.of("name", "leaf-1")));

        assertEquals(ValidationStatus.INVALID, result.status());
        assertTrue(result.issues().stream().anyMatch(issue -> "type-data-path-missing".equals(issue.code())),
                () -> String.valueOf(result.issues()));
    }

    @Test
    void validationDoesNotMutateCallerDataModel() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", "Ada");
        row.put("tags", new ArrayList<>(List.of("a", "b")));
        Map<String, Object> dataModel = new LinkedHashMap<>();
        dataModel.put("users", new ArrayList<>(List.of(row)));
        dataModel.put("value", 1.5);

        Map<String, Object> snapshot = deepCopy(dataModel);

        validate("root = TextContent(@String(@Abs(@FormatNumber(data.value))))", dataModel);
        validate("root = CardHeader(data.users.name)", dataModel);

        assertEquals(snapshot, dataModel, "validation must not mutate the caller's Render Data Model");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepCopy(Map<String, Object> value) {
        Map<String, Object> copy = new LinkedHashMap<>();
        value.forEach((key, item) -> {
            if (item instanceof Map<?, ?> map) {
                copy.put(key, deepCopy((Map<String, Object>) map));
            } else if (item instanceof List<?> list) {
                List<Object> items = new ArrayList<>();
                for (Object element : list) {
                    items.add(element instanceof Map<?, ?> map ? deepCopy((Map<String, Object>) map) : element);
                }
                copy.put(key, items);
            } else {
                copy.put(key, item);
            }
        });
        return copy;
    }
}
