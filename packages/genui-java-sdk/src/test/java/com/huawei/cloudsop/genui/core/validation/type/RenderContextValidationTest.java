package com.huawei.cloudsop.genui.core.validation.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.contract.BuiltinSpec;
import com.huawei.cloudsop.genui.core.contract.GenerationContractLoader;
import com.huawei.cloudsop.genui.core.validation.DefaultOpenuiLangValidator;
import com.huawei.cloudsop.genui.core.validation.ValidationIssue;
import com.huawei.cloudsop.genui.core.validation.ValidationRequest;
import com.huawei.cloudsop.genui.core.validation.ValidationResult;
import com.huawei.cloudsop.genui.core.validation.ValidationStatus;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * #87:同一 Render 模板按各列使用位置独立检查——契约声明两种 arity 与 binder 布局、Template 与 Component 不混为一类、保留声明环境并按 ADR
 * 0006 继承使用位置绑定、诊断落到实际列并保留模板声明位置、缺上下文仍检查 独立表达式、无关列不受阻断。
 */
class RenderContextValidationTest {

    private static final String MISMATCH = "builtin-argument-type-mismatch";

    // ── 契约布局与 Template/Component 种类 ────────────────────────────────

    @Test
    void renderCallFormsComeFromContract() {
        BuiltinSpec render =
                GenerationContractLoader.loadDefault().builtins().stream()
                        .filter(spec -> "Render".equals(spec.name()))
                        .findFirst()
                        .orElseThrow();

        // 两参形式只引入 v;三参形式引入 v/row,body 下标随调用形式变化。
        assertEquals(List.of(0), render.activeBinderIndexes(2));
        assertEquals(1, render.bodyIndex(2));
        assertEquals(List.of(0, 1), render.activeBinderIndexes(3));
        assertEquals(2, render.bodyIndex(3));
        assertEquals("string-or-identifier", render.params().get(0).nameSyntax());
        assertEquals("string-or-identifier", render.params().get(1).nameSyntax());
    }

    @Test
    void renderArityComesFromContractBinderLayout() {
        // 两参 Render 不绑定 row:row 在 body 里是未解析引用,不能被当作已声明 binder。
        ValidationResult twoArity =
                validate(
                        "root = Table([c], data.rows)\n"
                                + "c = Col(\"C\", \"name\", {cell: @Render(\"v\", TextContent(row.name))})",
                        Map.of("rows", List.of(Map.of("name", "Ada"))));
        assertTrue(
                twoArity.issues().stream().anyMatch(issue -> "unresolved-ref".equals(issue.code())),
                () -> "两参 Render 不引入 row: " + twoArity.issues());

        // 三参 Render 绑定 row:同一表达式合法。arity 与 binder 位置来自契约,不在代码里按末参猜 body。
        ValidationResult threeArity =
                validate(
                        "root = Table([c], data.rows)\n"
                                + "c = Col(\"C\", \"name\", {cell: @Render(\"v\", \"row\", TextContent(row.name))})",
                        Map.of("rows", List.of(Map.of("name", "Ada"))));
        assertEquals(
                ValidationStatus.VALID,
                threeArity.status(),
                () -> String.valueOf(threeArity.issues()));
    }

    @Test
    void templateIsNotAComponentValue() {
        // Template 类型只与 Template 兼容:把 Render 模板放进组件槽位必须检出,不能当作已产生的组件值。
        String dsl = "root = Table([@Render(\"v\", TextContent(v))], data.rows)";
        ValidationResult result = validate(dsl, Map.of("rows", List.of(Map.of("name", "Ada"))));

        ValidationIssue issue = issue(result, "component-slot-type-mismatch");
        assertEquals("Table", issue.component());
        assertEquals("/columns", issue.path());
    }

    // ── 同一模板按各列上下文独立检查 ─────────────────────────────────────

    @Test
    void sameTemplateChecksEachColumnWithItsOwnContext() {
        // 同一模板用于数字列与字符串列:数字列通过,字符串列在它自己的使用处报错。
        String dsl =
                "root = Table([countCol, nameCol], data.rows)\n"
                        + "countCol = Col(\"Count\", \"count\", {cell: tpl})\n"
                        + "nameCol = Col(\"Name\", \"name\", {cell: tpl})\n"
                        + "tpl = @Render(\"v\", TextContent(@Round(v)))";
        ValidationResult result =
                validate(dsl, Map.of("rows", List.of(Map.of("count", 1, "name", "Ada"))));

        assertEquals(1, result.issues().size(), () -> "只有字符串列应报错: " + result.issues());
        ValidationIssue issue = issue(result, MISMATCH);
        assertEquals("nameCol", issue.statementId(), () -> "诊断必须定位实际列使用处: " + result.issues());
        assertEquals("/columns/name/cell/args/0", issue.path());
        assertTrue(issue.hint().contains("tpl"), () -> "必须保留模板声明位置: " + issue.hint());
    }

    @Test
    void sharedTemplateReportsEverySameTypedUseSite() {
        // 两列字段类型相同、观察值不同:相同的类型不构成去重依据,前一列结果不得吞掉后一列。
        String dsl =
                "root = Table([firstCol, lastCol], data.rows)\n"
                        + "firstCol = Col(\"First\", \"name\", {cell: tpl})\n"
                        + "lastCol = Col(\"Last\", \"alias\", {cell: tpl})\n"
                        + "tpl = @Render(\"v\", TextContent(@Round(v)))";
        ValidationResult result =
                validate(dsl, Map.of("rows", List.of(Map.of("name", "Ada", "alias", "Bob"))));

        List<ValidationIssue> mismatches =
                result.issues().stream().filter(issue -> MISMATCH.equals(issue.code())).toList();
        assertEquals(2, mismatches.size(), () -> "每个使用位置都要独立检查: " + result.issues());
        assertEquals(2, mismatches.stream().map(ValidationIssue::path).distinct().count());
        assertTrue(mismatches.stream().anyMatch(issue -> "firstCol".equals(issue.statementId())));
        assertTrue(mismatches.stream().anyMatch(issue -> "lastCol".equals(issue.statementId())));
    }

    // ── 声明环境与使用位置继承(ADR 0006)───────────────────────────────

    @Test
    void renderBodyInheritsUseSiteBindingThroughReferencedTemplates() {
        // 独立模板语句及其传递依赖在 Each 使用处继承 group 绑定;Render 的 row/v 只覆盖自己的 binder。
        String dsl =
                "root = Tabs(@Each(data.groups, \"group\", tabTpl))\n"
                        + "tabTpl = TabItem(group.model, group.model, [tbl])\n"
                        + "tbl = Table([c], group.devices)\n"
                        + "c = Col(\"Model\", \"model\", {cell: @Render(\"v\", \"row\", "
                        + "TextContent(row.model ?? group.model))})";
        ValidationResult result =
                validate(
                        dsl,
                        Map.of(
                                "groups",
                                List.of(
                                        Map.of(
                                                "model",
                                                "CE12804S",
                                                "devices",
                                                List.of(Map.of("model", "X"))))));

        assertEquals(
                ValidationStatus.VALID, result.status(), () -> "使用位置绑定必须传入模板: " + result.issues());
    }

    @Test
    void renderBindersShadowSameNameUseSiteBinding() {
        // 使用位置恰好把外层 Each 元素绑定为 v;Render 自己的 v 必须覆盖它。
        // 若遮蔽失效,TextContent(v) 收到 group 对象而不是列字段字符串,会报类型错。
        String dsl =
                "root = Tabs(@Each(data.groups, \"v\", tabTpl))\n"
                        + "tabTpl = TabItem(v.model, v.model, [tbl])\n"
                        + "tbl = Table([c], v.devices)\n"
                        + "c = Col(\"Model\", \"model\", {cell: @Render(\"v\", TextContent(v))})";
        ValidationResult result =
                validate(
                        dsl,
                        Map.of(
                                "groups",
                                List.of(
                                        Map.of(
                                                "model",
                                                "CE12804S",
                                                "devices",
                                                List.of(Map.of("model", "X"))))));

        assertEquals(
                ValidationStatus.VALID,
                result.status(),
                () -> "Render binder 必须遮蔽同名捕获: " + result.issues());
    }

    @Test
    void outerBindingIsRestoredAfterTemplateUse() {
        // 退出模板后外层 group 绑定恢复:模板之外的普通引用继续按外层 Each 环境解析。
        String dsl =
                "root = Tabs(@Each(data.groups, \"group\", tabTpl))\n"
                        + "tabTpl = TabItem(group.model, group.model, [tbl])\n"
                        + "tbl = Table([c], group.devices)\n"
                        + "c = Col(\"Model\", \"model\", {cell: @Render(\"v\", TextContent(v))})";
        ValidationResult result =
                validate(
                        dsl,
                        Map.of(
                                "groups",
                                List.of(
                                        Map.of(
                                                "model",
                                                "CE12804S",
                                                "devices",
                                                List.of(Map.of("model", "X"))))));

        assertEquals(
                ValidationStatus.VALID, result.status(), () -> "退出模板后外层绑定必须恢复: " + result.issues());
    }

    // ── 无使用上下文与无关列继续检查 ─────────────────────────────────────

    @Test
    void templateWithoutUseContextStillChecksIndependentExpressions() {
        // expandRow 处的 Render 没有可用上下文:独立错误照常报出,依赖 row 的检查登记 missing-context。
        String dsl =
                "root = Table([c], data.rows, "
                        + "@Render(\"row\", Stack([TextContent(row.name), TextContent(@Round(\"bad\"))])))\n"
                        + "c = Col(\"Name\", \"name\")";
        ValidationResult result = validate(dsl, Map.of("rows", List.of(Map.of("name", "Ada"))));

        ValidationIssue issue = issue(result, MISMATCH);
        assertTrue(issue.message().contains("@Round"), () -> "独立错误必须报出: " + result.issues());
        assertEquals(
                1,
                result.issues().stream()
                        .filter(candidate -> MISMATCH.equals(candidate.code()))
                        .count(),
                () -> "同一独立错误只报一次: " + result.issues());
        assertTrue(
                unprovenReasons(result).contains("MISSING_CONTEXT"),
                () -> "依赖 row 的检查必须登记 missing-context: " + result.metadata().extra());
    }

    @Test
    void blockedColumnDoesNotStopUnrelatedColumnChecks() {
        // 一列确定缺字段,不影响另一列 cell 模板自身的类型检查。
        String dsl =
                "root = Table([missingCol, nameCol], data.rows)\n"
                        + "missingCol = Col(\"Missing\", \"missing\")\n"
                        + "nameCol = Col(\"Name\", \"name\", {cell: @Render(\"v\", TextContent(@Round(v)))})";
        ValidationResult result = validate(dsl, Map.of("rows", List.of(Map.of("name", "Ada"))));

        issue(result, "type-table-column-missing");
        ValidationIssue cellIssue = issue(result, MISMATCH);
        assertEquals("nameCol", cellIssue.statementId());
    }

    @Test
    void upstreamErrorInOneColumnDoesNotBlockAnotherColumn() {
        // 一列模板自身报错并把结果标为 blocked-by-error;另一列不共享该上游错误,仍须继续检查。
        String dsl =
                "root = Table([badCol, okCol], data.rows)\n"
                        + "badCol = Col(\"Bad\", \"name\", {cell: @Render(\"v\", TextContent(({id: \"a\"}).missing))})\n"
                        + "okCol = Col(\"Ok\", \"alias\", {cell: @Render(\"v\", TextContent(@Round(v)))})";
        ValidationResult result =
                validate(dsl, Map.of("rows", List.of(Map.of("name", "Ada", "alias", "A"))));

        issue(result, "type-field-missing");
        ValidationIssue cellIssue = issue(result, MISMATCH);
        assertEquals("okCol", cellIssue.statementId(), () -> "无关列必须继续检查: " + result.issues());
    }

    @Test
    void templateNestedInAnotherTemplateIsStillAnalyzed() {
        // body 里再出现 Render 时,延迟检查不能在遍历模板列表时中断;内层模板的独立错误仍要检查。
        String dsl =
                "root = Table([c], data.rows, "
                        + "@Render(\"row\", TextContent(@Render(\"x\", TextContent(@Round(\"bad\"))))))\n"
                        + "c = Col(\"Name\", \"name\")";
        ValidationResult result = validate(dsl, Map.of("rows", List.of(Map.of("name", "Ada"))));

        ValidationIssue issue = issue(result, MISMATCH);
        assertTrue(issue.message().contains("@Round"), () -> "内层模板独立错误必须检查: " + result.issues());
    }

    @Test
    void conclusionsAreRepeatableWithoutAnAnalysisCache() {
        // 本实现不做跨使用位置的结果记忆化:同一输入独立分析两次必须给出完全相同结论。
        String dsl =
                "root = Table([firstCol, lastCol], data.rows)\n"
                        + "firstCol = Col(\"First\", \"name\", {cell: tpl})\n"
                        + "lastCol = Col(\"Last\", \"alias\", {cell: tpl})\n"
                        + "tpl = @Render(\"v\", TextContent(@Round(v)))";
        Map<String, Object> dataModel =
                Map.of("rows", List.of(Map.of("name", "Ada", "alias", "Bob")));

        ValidationResult first = validate(dsl, dataModel);
        ValidationResult second = validate(dsl, dataModel);

        assertEquals(first.status(), second.status());
        assertEquals(first.issues(), second.issues(), () -> "重复分析结论必须一致: " + first.issues());
        assertNotEquals(0, first.issues().size());
    }

    private static String unprovenReasons(ValidationResult result) {
        return result.metadata().extra().getOrDefault("typeChecks.unprovenReasons", "");
    }

    private static ValidationResult validate(String dsl, Map<String, Object> dataModel) {
        return DefaultOpenuiLangValidator.INSTANCE.validate(
                ValidationRequest.builder()
                        .dsl(dsl)
                        .contract(GenerationContractLoader.loadDefault())
                        .dataModel(dataModel)
                        .build());
    }

    private static ValidationIssue issue(ValidationResult result, String code) {
        ValidationIssue issue =
                result.issues().stream()
                        .filter(candidate -> code.equals(candidate.code()))
                        .findFirst()
                        .orElse(null);
        org.junit.jupiter.api.Assertions.assertNotNull(
                issue, () -> "missing " + code + " in " + result.issues());
        return issue;
    }
}
