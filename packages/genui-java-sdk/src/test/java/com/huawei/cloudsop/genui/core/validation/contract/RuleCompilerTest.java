/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import com.huawei.cloudsop.genui.core.contract.GenerationContractLoader;
import com.huawei.cloudsop.genui.core.validation.contract.RulePlan.Op;
import com.huawei.cloudsop.genui.core.validation.contract.RulePlan.Rule;
import com.huawei.cloudsop.genui.core.validation.contract.RulePlan.TypePattern;
import com.huawei.cloudsop.genui.core.validation.contract.RulePlan.TypeRule;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/** 结构化类型规则的加载期验证：非法运算、变量、参数引用和调用形式必须在加载期失败。 */
class RuleCompilerTest {

    @Test
    void compilesDeclaredRuleIntoFinitePlan() {
        TypeRule rule = RuleCompiler.compile("Demo",
                Map.of("typeRulesVersion", 1, "arity", Map.of("min", 2, "max", "unbounded"), "parameters",
                        List.of(Map.of("name", "objects", "rest", true, "accept", "object")), "result",
                        Map.of("op", "record.overlay", "operands", List.of(Map.of("argumentsFrom", 0)))));

        assertEquals(2, rule.arity().min());
        assertTrue(rule.arity().max().isEmpty());
        assertEquals(1, rule.parameters().size());
        assertTrue(rule.parameters().get(0).rest());
        assertEquals(new TypePattern.AnyRecord(), rule.parameters().get(0).accept());
        Rule.Apply apply = (Rule.Apply) rule.result();
        assertEquals(Op.RECORD_OVERLAY, apply.op());
    }

    @Test
    void rejectsUnknownOperationAtLoadTime() {
        RuleCompilationException error = assertThrows(RuleCompilationException.class,
                () -> RuleCompiler.compile("Demo", baseRule(Map.of("op", "script.run", "operands", List.of()))));
        assertTrue(error.getMessage().contains("script.run"), error.getMessage());
    }

    @Test
    void rejectsUnknownTypeTagAtLoadTime() {
        assertThrows(RuleCompilationException.class,
                () -> RuleCompiler.compile("Demo", baseRule(Map.of("type", "integer"))));
    }

    @Test
    void rejectsOutOfRangeArgumentReferenceAtLoadTime() {
        RuleCompilationException error = assertThrows(RuleCompilationException.class,
                () -> RuleCompiler.compile("Demo", baseRule(Map.of("argument", 3))));
        assertTrue(error.getMessage().contains("out of range"), error.getMessage());
    }

    @Test
    void rejectsIllegalArityAtLoadTime() {
        assertThrows(RuleCompilationException.class,
                () -> RuleCompiler.compile("Demo", Map.of("typeRulesVersion", 1, "arity", Map.of("min", 3, "max", 2),
                        "parameters", List.of(Map.of("name", "x")), "result", Map.of("type", "number"))));
    }

    @Test
    void rejectsUnsupportedVersionAtLoadTime() {
        assertThrows(RuleCompilationException.class,
                () -> RuleCompiler.compile("Demo", Map.of("typeRulesVersion", 99, "arity", Map.of("min", 1),
                        "parameters", List.of(Map.of("name", "x")), "result", Map.of("type", "number"))));
    }

    @Test
    void rejectsRestParameterInNonFinalPosition() {
        assertThrows(RuleCompilationException.class,
                () -> RuleCompiler.compile("Demo",
                        Map.of("typeRulesVersion", 1, "arity", Map.of("min", 1), "parameters",
                                List.of(Map.of("name", "xs", "rest", true), Map.of("name", "y")), "result",
                                Map.of("type", "number"))));
    }

    @Test
    void rejectsArgumentsFromOnNonRestParameter() {
        assertThrows(RuleCompilationException.class,
                () -> RuleCompiler.compile("Demo",
                        Map.of("typeRulesVersion", 1, "arity", Map.of("min", 1), "parameters",
                                List.of(Map.of("name", "x"), Map.of("name", "y")), "result",
                                Map.of("op", "record.overlay", "operands", List.of(Map.of("argumentsFrom", 0))))));
    }

    @Test
    void contractLoaderCompilesTypeRulesAlongsideVocabulary() {
        GenerationContract contract = GenerationContractLoader.loadDefault();
        long withRules = contract.builtins().stream().filter(b -> b.typeRule() != null).count();
        // 迁移基数：#81 的 15 个普通 builtin；后续各票只增不减（本票加入 Flatten/First/Last 的 sequence 运算）。
        assertTrue(withRules >= 15, "expected the migrated builtin rules to stay compiled, got " + withRules);
        for (String name : List.of("Sort", "Filter", "Flatten", "First", "Last")) {
            assertNotNull(contract.builtins().stream().filter(b -> name.equals(b.name())).findFirst().orElseThrow()
                    .typeRule(), name + " must carry a compiled structured rule");
        }
    }

    @Test
    void contractLoaderFailsFastOnUnknownOp() {
        String json = """
                {"contractVersion":"test","root":"x","components":{},"builtins":[{
                  "name":"Bogus","signature":"Bogus(x)","description":"d","templateBuiltin":false,
                  "params":[{"name":"x","type":"any"}],"returnType":"number",
                  "typeRule":{"typeRulesVersion":1,"arity":{"min":1,"max":1},
                    "parameters":[{"name":"x"}],
                    "result":{"op":"eval.javascript","operands":[{"argument":0}]}}}]}""";
        assertThrows(RuleCompilationException.class, () -> GenerationContractLoader.fromJson(json));
    }

    private static Map<String, Object> baseRule(Map<String, Object> result) {
        return Map.of("typeRulesVersion", 1, "arity", Map.of("min", 1), "parameters", List.of(Map.of("name", "x")),
                "result", result);
    }
}
