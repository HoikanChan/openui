/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.contract;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.core.contract.GenerationContractLoader;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class ObjectEnumerationRuleTest {
    @Test
    void loaderRejectsMalformedEnumerationOperands() {
        for (String op :
                List.of("record.keys", "record.values", "record.entries", "record.unknown")) {
            for (var operands :
                    List.of(
                            List.of(),
                            List.of(Map.of("argument", 0), Map.of("argument", 0)),
                            List.of(Map.of("argumentsFrom", 0)))) {
                var rule =
                        Map.of(
                                "typeRulesVersion",
                                1,
                                "arity",
                                Map.of("min", 1),
                                "parameters",
                                List.of(Map.of("name", "objects", "rest", true)),
                                "result",
                                Map.of("op", op, "operands", operands));
                String json =
                        Json.stringify(
                                Map.of(
                                        "contractVersion",
                                        "test",
                                        "root",
                                        "x",
                                        "components",
                                        Map.of(),
                                        "builtins",
                                        List.of(
                                                Map.of(
                                                        "name",
                                                        "Enumerate",
                                                        "signature",
                                                        "Enumerate(x)",
                                                        "description",
                                                        "d",
                                                        "params",
                                                        List.of(
                                                                Map.of(
                                                                        "name", "objects", "type",
                                                                        "object", "rest", true)),
                                                        "returnType",
                                                        "array",
                                                        "typeRule",
                                                        rule))));
                assertThrows(
                        RuleCompilationException.class,
                        () -> GenerationContractLoader.fromJson(json),
                        json);
            }
        }
    }
}
