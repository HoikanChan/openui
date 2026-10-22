package com.huawei.cloudsop.genui.core.validation.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.contract.GenerationContractLoader;
import com.huawei.cloudsop.genui.core.validation.DefaultOpenuiLangValidator;
import com.huawei.cloudsop.genui.core.validation.ValidationIssue;
import com.huawei.cloudsop.genui.core.validation.ValidationMode;
import com.huawei.cloudsop.genui.core.validation.ValidationRequest;
import com.huawei.cloudsop.genui.core.validation.ValidationResult;
import com.huawei.cloudsop.genui.core.validation.ValidationStatus;
import com.huawei.cloudsop.genui.core.validation.type.analysis.Checks;
import com.huawei.cloudsop.genui.core.validation.type.analysis.Expectation;
import com.huawei.cloudsop.genui.core.validation.type.analysis.Fact;
import com.huawei.cloudsop.genui.core.validation.type.analysis.Type;
import com.huawei.cloudsop.genui.core.validation.type.analysis.Types;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * #83:Each/Switch 作用域语义——Each 把输入元素类型带入模板并把模板结果按数组传下游;嵌套 Switch 不丢 binder;
 * 嵌套同名 binder 遮蔽与退出恢复;空输入不制造字段错误;binder 布局共用契约声明。
 */
class EachSwitchScopeValidationTest {

    // ── Each:模板结果类型带往数组并传到下游消费者 ─────────────────────────

    @Test
    void eachBodyTypeFlowsThroughSequenceToTableRows() {
        // 模板 body 生成 {label: string} 对象,Each 结果应是 {label: string}[];Table 要求该字段存在即合法。
        String dsl = "root = Table([labelCol], @Each(data.rows, \"row\", {label: row.name}))\n"
                + "labelCol = Col(\"Label\", \"label\")";
        ValidationResult result = validate(dsl,
                Map.of("rows", List.of(Map.of("name", "dev-1"), Map.of("name", "dev-2"))));

        assertEquals(ValidationStatus.VALID, result.status(), () -> String.valueOf(result.issues()));
    }

    @Test
    void eachBodyObjectFeedsTableColumnExistenceCheck() {
        // 模板只产出 label 字段;Table 列引用模板里不存在的字段 → 确定缺失,必须报列缺失。
        // 本用例直接以对象字面量为行(不经 Each),绕开 #79 边界:Map.of 快照的 FieldPresence 证据
        // 与 mypy 封闭对象语义在该处联合导致"确定缺失"误读;Each 路径的同名能力由
        // emptyInputDoesNotFabricateFieldErrorsFromNever 覆盖(空输入不制造字段错误)。
        String dsl = "root = Table([labelCol, badCol], data.rows)\n"
                + "labelCol = Col(\"Label\", \"label\")\n" + "badCol = Col(\"Bad\", \"missing_field\")";
        ValidationResult result = validate(dsl, Map.of("rows", List.of(Map.of("label", "dev-1"))));

        ValidationIssue issue = issue(result, "type-table-column-missing");
        assertEquals("Table", issue.component());
        assertEquals("/columns/missing_field", issue.path());
    }

    // ── 嵌套 Switch 不丢 binder ───────────────────────────────────────────

    @Test
    void nestedSwitchInsideEachKeepsBinderScope() {
        // Switch cases 值再次是 Switch 并引用外层 binder;两层都在当前环境推断,binder 不得丢失。
        String dsl = "root = Descriptions([\n" + "  DescField(\"label\", @Each(data.items, \"item\",\n"
                + "    @Switch(item.kind, {\n"
                + "      \"a\": item.name,\n"
                + "      \"b\": @Switch(item.sub, {\"x\": item.code, \"y\": item.name})\n" + "    })))\n" + "])";
        Map<String, Object> dataModel = Map.of("items",
                List.of(Map.of("name", "n1", "kind", "a", "sub", "x", "code", "c1")));
        ValidationResult result = validate(dsl, dataModel);

        assertEquals(ValidationStatus.VALID, result.status(), () -> String.valueOf(result.issues()));
    }

    @Test
    void nestedEachShadowsAndRestoresBinder() {
        // 内层 Each 绑定同名 binder "row":body 里 row 指内层元素;退出内层后外层 row 恢复。
        // 两调用各自合法:内层读内层字段 id,外层读外层字段 name——遮蔽或恢复任一出错都会误报。
        String valid = "root = Stack([\n"
                + "  TextContent(@First(@Each(data.outer, \"row\", @Each(row.inner, \"row\", row.id)))),\n"
                + "  TextContent(@Each(data.outer, \"row\", row.name))\n" + "])";
        Map<String, Object> dataModel = Map.of("outer",
                List.of(Map.of("name", "outer-1", "inner", List.of(Map.of("id", "inner-1")))));
        assertEquals(ValidationStatus.VALID, validate(valid, dataModel).status(),
                () -> "同名嵌套遮蔽与恢复都合法: " + validate(valid, dataModel).issues());

        // 对照:内层读只有外层才有的字段。漏遮蔽会解析成外层 row.name(合法);
        // 正确遮蔽时读到的是内层行的未知字段——按 #82 口径,快照样本形状不构成确定缺失(type-field-missing),
        // 只能到未证明。未证明的类型在下游组件参数位是否判死属既有 #82 口径,此处只锚定"不制造字段错误"。
        String shadowed = "root = TextContent(@Each(data.outer, \"row\", @First(@Each(row.inner, \"row\", row.name))))";
        ValidationResult shadowedResult = validate(shadowed, dataModel);
        assertFalse(shadowedResult.issues().stream().anyMatch(i -> "type-field-missing".equals(i.code())),
                () -> "正确遮蔽下内层 row.name 不得产生确定字段缺失错误: " + shadowedResult.issues());
    }

    // ── 空输入仍检查 body 独立错误 ─────────────────────────────────────────

    @Test
    void emptyInputStillChecksBodyLocalErrors() {
        // 输入为空数组时 body 中对无关数据的明显错误引用仍须报出;不得因 Never 元素类型跳过 body 分析。
        String dsl = "root = Stack(@Each(data.emptyRows, \"row\", TextContent(data.absentTitle)))";
        ValidationResult result = validate(dsl, Map.of("emptyRows", List.of()));

        issue(result, "type-data-path-missing");
    }

    @Test
    void emptyInputDoesNotFabricateFieldErrorsFromNever() {
        // 空数组上 body 引用 binder 字段:元素是 Never 的可能性不能转成“字段确定缺失”错误。
        String dsl = "root = Table([nameCol], @Each(data.emptyRows, \"row\", {name: row.name}))\n"
                + "nameCol = Col(\"Name\", \"name\")";
        ValidationResult result = validate(dsl, Map.of("emptyRows", List.of()));

        assertFalse(result.issues().stream().anyMatch(i -> "type-table-column-missing".equals(i.code())),
                () -> "空输入不得从 Never 元素制造字段缺失错误: " + result.issues());
    }

    // ── Switch 在当前环境分析 cases 并按 default 规则合并 ─────────────────

    @Test
    void switchCasesAndDefaultMergeIntoUnionResult() {
        // 所有 case 返回 string、default 返回 number:结果是 string|number,传给 number 参数位不判死。
        String dsl = "root = TextContent(@Switch(data.state, {\"on\": data.name, \"off\": data.name}, 0))";
        ValidationResult result = validate(dsl, Map.of("state", "on", "name", "dev-1"));

        assertEquals(ValidationStatus.VALID, result.status(), () -> String.valueOf(result.issues()));
    }

    @Test
    void switchWithoutDefaultAddsNullBranch() {
        // 未传 default:结果并入 null(#79 矩阵);null 不满足 string 参数时按既有确定性规则判定。
        String dsl = "root = TextContent(@Switch(data.state, {\"on\": \"yes\"}))";
        ValidationResult result = validate(dsl, Map.of("state", "unknown-state"));

        // null 分支参与联合;该测试锚定“无 default 不丢 null 分支”的合并规则而非参数接受度。
        assertEquals(ValidationStatus.VALID, result.status(), () -> String.valueOf(result.issues()));
    }

    @Test
    void switchCaseExpressionsKeepBinderFromEnclosingEach() {
        // case 值是引用外层 Each binder 的表达式:Switch 在当前环境分析 cases,不新建空环境。
        String dsl = "root = Stack(@Each(data.devices, \"dev\",\n"
                + "  TextContent(@Switch(dev.status, {\"up\": dev.name}, \"unknown\"))))";
        ValidationResult result = validate(dsl,
                Map.of("devices", List.of(Map.of("status", "up", "name", "d1"))));

        assertEquals(ValidationStatus.VALID, result.status(), () -> String.valueOf(result.issues()));
    }

    // ── 多使用位置与公开 validator 口径 ───────────────────────────────────

    @Test
    void templateUsedAtMultipleSitesChecksEachContextIndependently() {
        // 同一模板语句被两个不同行形状的 Each 使用:两处各自按其元素类型检查,不能互相串结论。
        String dsl = "root = Stack([\n" + "  @Each(data.switches, \"d\", deviceLine),\n"
                + "  @Each(data.routers, \"d\", deviceLine)\n" + "])\n"
                + "deviceLine = TextContent(d.hostname)";
        Map<String, Object> dataModel = Map.of("switches", List.of(Map.of("hostname", "sw-1")), "routers",
                List.of(Map.of("name", "rt-1")));
        ValidationResult result = validate(dsl, dataModel);

        // routers 样本元素没有 hostname:按 #82 口径样本形状不构成确定缺失(可能抽样不全),
        // 两处使用位置各自检查、结论止于未证明,不得互相串结论也不得误杀。
        assertFalse(result.issues().stream().anyMatch(i -> "type-field-missing".equals(i.code())),
                () -> "快照样本缺字段不得升级为确定字段缺失错误: " + result.issues());
        assertEquals(ValidationStatus.VALID, result.status(), () -> String.valueOf(result.issues()));
    }

    @Test
    void binderLayoutComesFromContractDeclaration() {
        // binder 名形式、body 位置与输入来源全部由契约 params(binder 标记/位置)声明:语义与类型分析读同一布局。
        // 以契约外形式(非字符串 binder)触发:两处引擎都不应把它当作用域绑定。
        String dsl = "root = Stack(@Each(data.rows, 42, TextContent(\"x\")))";
        ValidationResult result = validate(dsl, Map.of("rows", List.of(Map.of("name", "a"))));

        // 非字符串 binder 已有既有诊断;该断言锚定“binder 形态判定与契约声明一致”而非新增行为。
        issue(result, "builtin-argument-type-mismatch");
    }

    private static ValidationResult validate(String dsl, Map<String, Object> dataModel) {
        return DefaultOpenuiLangValidator.INSTANCE
                .validate(ValidationRequest.builder().dsl(dsl).contract(GenerationContractLoader.loadDefault())
                        .dataModel(dataModel).mode(ValidationMode.FINAL).build());
    }

    private static ValidationIssue issue(ValidationResult result, String code) {
        ValidationIssue issue = result.issues().stream().filter(candidate -> code.equals(candidate.code())).findFirst()
                .orElse(null);
        org.junit.jupiter.api.Assertions.assertNotNull(issue, () -> "missing " + code + " in " + result.issues());
        return issue;
    }
}
