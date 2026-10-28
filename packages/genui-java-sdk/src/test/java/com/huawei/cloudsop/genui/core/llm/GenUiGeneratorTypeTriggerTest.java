/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.core.validation.ValidationResult;
import com.huawei.cloudsop.genui.core.validation.ValidationStatus;
import com.huawei.cloudsop.genui.core.validation.repair.RepairPromptBuilder;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * #90: 类型检查结论与修复触发条件在公开生成入口上的契约。
 *
 * <p>
 * 只有确定的类型错误(ERROR → {@link ValidationStatus#INVALID})才消费一次修复调用;仅有未证明(UNPROVEN)且无阻塞问题时状态仍是 VALID,不触发修复。测试使用受控
 * {@code ScriptedTransport},不访问真实模型。
 */
class GenUiGeneratorTypeTriggerTest {

    private static final String REPAIR_MARKER = RepairPromptBuilder.INCREMENTAL_REPAIR_MARKER;

    private static String frame(String content) {
        return "data: {\"choices\":[{\"delta\":{\"content\":" + Json.stringify(content) + "}}]}\n\n";
    }

    private static Map<String, Object> rows() {
        return Map.of("users", List.of(Map.of("name", "Ada")));
    }

    /** 格式化内建在契约里只有未证明的输出类型：校验通过且无阻塞诊断,仅留下 UNPROVEN 覆盖项。 */
    private static String unprovenOnlyDsl() {
        return "root = TextContent(@String(@Abs(@FormatNumber(data.value))))";
    }

    private static Map<String, Object> numberData() {
        return Map.of("value", 1.5);
    }

    // ── 确定类型错误 → 消费修复调用 ──────────────────────────────────────────

    @Test
    void determinateTypeErrorIsConsumedByRepair() {
        GenUiGeneratorRepairTest.ScriptedTransport transport = GenUiGeneratorRepairTest.ScriptedTransport
                .streams(frame("root = TextContent(data.missing.value)") + "data: [DONE]\n\n",
                        frame("root = Stack([])") + "data: [DONE]\n\n");

        GenUiGenerationResult result = GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), transport)
                .generateStream(UiGenerationRequest.builder().userInput("show users").response(rows()).build(),
                        envelope -> {
                        });

        assertEquals(ValidationStatus.VALID, result.validationStatus());
        assertEquals(GenerationEndReason.CHECK_PASSED, result.endReason());
        assertEquals(2, transport.streamCount, "one initial round + one repair round for a determinate type error");
        assertTrue(transport.bodies.get(1).contains(REPAIR_MARKER), "second call is the incremental repair prompt");
        assertTrue(transport.bodies.get(1).contains("type-data-path-missing"),
                () -> "repair prompt must carry the determinate diagnostic, got: " + transport.bodies.get(1));
    }

    // ── 仅未证明 → 不触发修复 ────────────────────────────────────────────────

    @Test
    void unprovenOnlyResultDoesNotTriggerRepair() {
        GenUiGeneratorRepairTest.ScriptedTransport transport = GenUiGeneratorRepairTest.ScriptedTransport
                .streams(frame(unprovenOnlyDsl()) + "data: [DONE]\n\n");

        GenUiGenerationResult result = GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), transport)
                .generateStream(UiGenerationRequest.builder().userInput("show value").response(numberData()).build(),
                        envelope -> {
                        });

        assertEquals(ValidationStatus.VALID, result.validationStatus());
        assertEquals(GenerationEndReason.CHECK_PASSED, result.endReason());
        assertEquals(1, transport.streamCount, "UNPROVEN alone must not be a repair trigger");
        Map<String, String> extra = result.validationResult().metadata().extra();
        assertTrue(Long.parseLong(extra.get("typeChecks.unproven")) > 0, () -> extra.toString());
        assertEquals("0", extra.get("typeChecks.mismatched"));
        assertEquals("true", extra.get("typeChecks.analysisComplete"));
    }

    @Test
    void streamingEntryPointAlsoTreatsUnprovenAsNonBlocking() {
        GenUiGeneratorRepairTest.ScriptedTransport transport = GenUiGeneratorRepairTest.ScriptedTransport
                .streams(frame(unprovenOnlyDsl()) + "data: [DONE]\n\n");

        GenUiGenerationResult result = GenUiGenerator.withTransport(GenUiLlmConfig.defaults(), transport)
                .generateStream(UiGenerationRequest.builder().userInput("show value").response(numberData()).build(),
                        envelope -> {
                        });

        assertEquals(ValidationStatus.VALID, result.validationStatus());
        assertEquals(1, transport.streamCount, "streaming entry point must not repair an UNPROVEN-only result");
        ValidationResult validation = result.validationResult();
        assertTrue(Long.parseLong(validation.metadata().extra().get("typeChecks.unproven")) > 0,
                () -> validation.metadata().extra().toString());
    }
}
