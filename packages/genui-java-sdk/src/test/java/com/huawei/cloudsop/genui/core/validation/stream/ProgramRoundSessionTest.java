/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.core.validation.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import com.huawei.cloudsop.genui.core.contract.GenerationContractLoader;
import com.huawei.cloudsop.genui.core.validation.DefaultOpenuiLangValidator;
import com.huawei.cloudsop.genui.core.validation.ValidationIssue;
import com.huawei.cloudsop.genui.core.validation.ValidationMode;
import com.huawei.cloudsop.genui.core.validation.ValidationRequest;
import com.huawei.cloudsop.genui.core.validation.ValidationResult;
import com.huawei.cloudsop.genui.core.validation.ValidationSeverity;
import com.huawei.cloudsop.genui.core.validation.ValidationStatus;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * #56 轮末统一校验会话。断言:增量喂入按同名 last-wins 维护当前程序,轮结束后仅在当前有效定义上做一次 FINAL 校验,并记录原始输出、当前定义、诊断、耗时与契约版本。
 */
class ProgramRoundSessionTest {

    private static GenerationContract baseContract() {
        return GenerationContractLoader.loadDefault();
    }

    private static ProgramRoundSession session(GenerationContract contract, Set<String> externalRefs,
            Map<String, Object> dataModel) {
        return new ProgramRoundSession(new DefaultOpenuiLangValidator(), contract, contract.root(), externalRefs,
                dataModel);
    }

    // ── 当前程序:last-wins 覆盖 ─────────────────────────────────────────────

    @Test
    void sameIdOverridesOldDefinitionAndOldErrorsDisappear() {
        // 先输出错误定义(root=Bogus 未知组件),再输出同名正确定义(root=Stack)。轮末校验只看当前定义 → VALID。
        ProgramRoundSession s = session(baseContract(), Set.of(), null);
        s.onDelta("root = Bogus(\"x\")\n");
        s.onDelta("root = Stack([CardHeader(\"A\")])\n");

        RoundEndResult round = s.onRoundEnd();

        assertEquals("root = Stack([CardHeader(\"A\")])", round.currentDefinitions(), "同名语句以最新定义为准,旧语句不进当前程序");
        assertEquals(ValidationStatus.VALID, round.validationResult().status(), "旧定义的 unknown-component 不得继续产生当前错误");
        assertTrue(round.durationMs() >= 0);
        assertEquals(baseContract().contractVersion(), round.contractVersion());
        assertNull(round.endReason(), "正常完成的轮末检查不记录退出原因");
        assertTrue(round.rawOutput().contains("Bogus"), "原始输出保留历史定义供排查");
    }

    @Test
    void currentDefinitionsKeepFirstInsertionOrderWithLatestText() {
        // a 先定义、b 后定义,a 被同名覆盖 → 顺序仍是 a,b,但 a 的文本是新的。
        // 最后一条语句在轮末 drain 才确定性完整,故断言放在 onRoundEnd 之后。
        ProgramRoundSession s = session(baseContract(), Set.of(), null);
        s.onDelta("a = CardHeader(\"old\")\nb = Stack([])\n");
        s.onDelta("a = CardHeader(\"new\")\n");

        RoundEndResult round = s.onRoundEnd();
        assertEquals("a = CardHeader(\"new\")\nb = Stack([])", round.currentDefinitions());
    }

    @Test
    void incompleteTailIsDrainedAndAutoClosedAtRoundEnd() {
        // 轮中只有半条语句;轮末 drain 自动补齐括号后成为完整语句。
        ProgramRoundSession s = session(baseContract(), Set.of(), null);
        s.onDelta("root = Stack([CardHeader(\"A\")");

        RoundEndResult round = s.onRoundEnd();
        assertTrue(round.currentDefinitions().contains("CardHeader"), "自动闭合后的尾部语句进入当前程序");
    }

    @Test
    void beginRoundStartsFreshBufferAndKeepsDrainedTailSeparate() {
        // 首轮半句在轮末被 drain 自动闭合;新一轮是小请求的独立输出,不得拼接到上一请求的半句上。
        ProgramRoundSession s = session(baseContract(), Set.of(), null);
        s.onDelta("keep = TextContent(\"k\")\nlate = Stack([");
        RoundEndResult first = s.onRoundEnd();
        assertTrue(first.currentDefinitions().contains("late = Stack([])"), "late fragment is drained at round end");

        s.beginRound();
        s.onDelta("root = Stack([keep])\n");
        RoundEndResult second = s.onRoundEnd();

        assertEquals("root = Stack([keep])\n", second.rawOutput(), "a new request starts from an empty raw buffer");
        assertEquals("keep = TextContent(\"k\")\nlate = Stack([])\nroot = Stack([keep])", second.currentDefinitions(),
                "prior program survives, while the new request's statements are parsed independently");
    }

    @Test
    void newNamesAreAppendedAndReferenced() {
        // 新增辅助定义可被引用:rows 定义后 root 引用 rows。
        ProgramRoundSession s = session(baseContract(), Set.of(), null);
        s.onDelta("root = Stack([row])\n");
        s.onDelta("row = CardHeader(\"R\")\n");

        RoundEndResult round = s.onRoundEnd();
        assertEquals(ValidationStatus.VALID, round.validationResult().status(), "轮内后置定义在轮末统一校验时已生效,不因前向引用报错");
    }

    @Test
    void missingReferenceAtRoundEndIsAnError() {
        ProgramRoundSession s = session(baseContract(), Set.of(), null);
        s.onDelta("root = Stack([missing])\n");

        RoundEndResult round = s.onRoundEnd();
        assertEquals(ValidationStatus.INVALID, round.validationResult().status());
        assertTrue(
                round.validationResult().issues().stream()
                        .anyMatch(i -> "unresolved-ref".equals(i.code()) && i.severity() == ValidationSeverity.ERROR),
                "轮结束仍缺失的引用按错误处理,而不是可重试警告");
    }

    // ── 任意分片一致性 ──────────────────────────────────────────────────────

    @Test
    void arbitraryChunkingYieldsSameProgramAndVerdict() {
        String dsl = "root = Stack([CardHeader(\"A\")])\nrow = CardHeader(data.name)\n";
        ProgramRoundSession whole = session(baseContract(), Set.of("data"), Map.of("name", "dev"));
        whole.onDelta(dsl);

        ProgramRoundSession chopped = session(baseContract(), Set.of("data"), Map.of("name", "dev"));
        for (String piece : new String[]{"root = St", "ack([CardHea", "der(\"A\")])\nrow = Card", "Header(data.",
                "name)\n"}) {
            chopped.onDelta(piece);
        }

        assertEquals(whole.currentDefinitions(), chopped.currentDefinitions());
        assertEquals(whole.onRoundEnd().validationResult().status(), chopped.onRoundEnd().validationResult().status());
    }

    // ── 校验请求透传:dataModel / externalRefs / contract ────────────────────

    @Test
    void roundEndValidationReceivesCurrentDefinitionsContractAndDataModel() {
        AtomicReference<ValidationRequest> captured = new AtomicReference<>();
        ProgramRoundSession s = new ProgramRoundSession(request -> {
            captured.set(request);
            return ValidationResult.valid(request.dsl(), List.of(),
                    new com.huawei.cloudsop.genui.core.validation.ValidationMetadata(1, "root", request.mode(), null));
        }, baseContract(), "Stack", Set.of("data"), Map.of("name", "dev"));
        s.onDelta("root = Stack([])\n");
        s.onRoundEnd();

        ValidationRequest request = captured.get();
        assertNotNull(request, "轮末必须执行一次校验");
        assertEquals("root = Stack([])", request.dsl(), "校验对象是当前有效定义");
        assertEquals(ValidationMode.FINAL, request.mode(), "轮末统一校验为 FINAL");
        assertEquals(Map.of("name", "dev"), request.dataModel(), "完整 Render Data Model 透传");
        assertTrue(request.externalRefs().contains("data"));
    }

    @Test
    void dataRefValidWithDataModelUnresolvedWithout() {
        // 有 dataModel:data.* 是外部引用,轮末 VALID;无 dataModel:unresolved-ref ERROR。
        ProgramRoundSession with = session(baseContract(), Set.of("data"), Map.of("name", "dev"));
        with.onDelta("root = Stack([TextContent(data.name)])\n");
        assertEquals(ValidationStatus.VALID, with.onRoundEnd().validationResult().status());

        ProgramRoundSession without = session(baseContract(), Set.of(), null);
        without.onDelta("root = Stack([TextContent(data.name)])\n");
        RoundEndResult round = without.onRoundEnd();
        assertEquals(ValidationStatus.INVALID, round.validationResult().status());
        assertTrue(round.validationResult().issues().stream().anyMatch(i -> "unresolved-ref".equals(i.code())));
    }

    // ── Each/Render 作用域规则保持 ──────────────────────────────────────────

    @Test
    void eachRenderBinderScopeSurvivesRedefinition() {
        // 模板语句 rowEach 先用错误形状(binder 出现在非模板语句),后被同名覆盖为正确 Each 模板:
        // 覆盖后的当前定义里,binder 只在模板内可见,轮末校验通过。
        Map<String, Object> dataModel = Map.of("items", List.of(Map.of("label", "A")));
        ProgramRoundSession s = session(baseContract(), Set.of("data"), dataModel);
        s.onDelta("rowEach = item.label\n"); // 前置错误定义:引用未绑定 binder
        s.onDelta("rowEach = @Each(data.items, \"item\", {label: item.label})\n");
        s.onDelta("root = Stack([rowEach])\n");

        RoundEndResult round = s.onRoundEnd();
        assertEquals(ValidationStatus.VALID, round.validationResult().status(),
                "Each binder 在覆盖后的模板内作用域不变: " + round.validationResult().issues());
        assertFalse(round.currentDefinitions().contains("rowEach = item.label"), "被覆盖的错误定义不进当前程序");
    }

    @Test
    void overriddenEachTemplateLosesBinderScopeOutsideTemplate() {
        // binder 名在模板外不可见:用同名覆盖把模板语句改成普通引用 item 后,item 未定义 → 轮末报 unresolved-ref。
        Map<String, Object> dataModel = Map.of("items", List.of(Map.of("label", "A")));
        ProgramRoundSession s = session(baseContract(), Set.of("data"), dataModel);
        s.onDelta("tpl = @Each(data.items, \"item\", item.label)\n");
        s.onDelta("tpl = item.label\n"); // 覆盖:binder 作用域随旧模板一起失效
        s.onDelta("root = Stack([tpl])\n");

        RoundEndResult round = s.onRoundEnd();
        assertEquals(ValidationStatus.INVALID, round.validationResult().status(), "被覆盖模板的 binder 不得在新定义中继续可见");
        assertTrue(round.validationResult().issues().stream().anyMatch(i -> "unresolved-ref".equals(i.code())),
                "expect unresolved-ref for item: " + round.validationResult().issues());
    }

    // ── 语法错误语句进入当前程序并在轮末报告 ─────────────────────────────────

    @Test
    void syntacticallyBrokenStatementStaysInProgramAndIsReportedAtRoundEnd() {
        // Col 里用了命名参数(语法错误)。该语句保留在当前程序里,轮末校验报告 syntax 问题。
        ProgramRoundSession s = session(baseContract(), Set.of(), null);
        s.onDelta("root = Table([Col(\"N\", field: \"name\")], data.rows)\n");

        RoundEndResult round = s.onRoundEnd();
        ValidationIssue syntax = round.validationResult().issues().stream().filter(i -> i.code().startsWith("syntax-"))
                .findFirst().orElse(null);
        assertNotNull(syntax, "语法错误必须在轮末统一校验中报告: " + round.validationResult().issues());
    }

    @Test
    void overriddenBrokenStatementNoLongerReports() {
        // 先坏后好的同名语句:坏语句被覆盖后,其语法/语义/类型错误全部消失。
        ProgramRoundSession s = session(baseContract(), Set.of(), null);
        s.onDelta("root = Bogus(\"x\")\n");
        s.onDelta("root = Table([Col(\"N\", field: \"name\")], data.rows)\n");
        s.onDelta("root = Stack([CardHeader(\"ok\")])\n");

        RoundEndResult round = s.onRoundEnd();
        assertEquals(ValidationStatus.VALID, round.validationResult().status(),
                "被覆盖语句的历史错误不得继续产生当前错误: " + round.validationResult().issues());
        assertFalse(round.currentDefinitions().contains("Bogus"));
        assertFalse(round.currentDefinitions().contains("field:"));
    }

    @Test
    void rawOutputRecordsOriginalStreamText() {
        ProgramRoundSession s = session(baseContract(), Set.of(), null);
        s.onDelta("```openui\n");
        s.onDelta("root = Stack([])\n");
        s.onDelta("```");
        s.onRoundEnd();

        assertTrue(s.rawOutput().contains("```"), "rawOutput 记录本轮原始输出(含围栏)");
        assertEquals("root = Stack([])", s.currentDefinitions(), "当前定义是预处理后的有效语句");
    }
}
