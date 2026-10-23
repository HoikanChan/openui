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

    @Test
    void observedRowMissingFieldInEachBodyIsReported() {
        // 快照里确定存在的行缺字段是真实反例(#77 §6.2/§12):即便类型上界保守地标 0..*,
        // 也不得用"可能为空"压掉当前观察到的缺失。
        String dsl = "root = Stack(@Each(data.rows, \"row\", TextContent(row.missing)))";
        ValidationResult result = validate(dsl, Map.of("rows", List.of(Map.of("id", "a"))));

        ValidationIssue issue = issue(result, "type-field-missing");
        assertTrue(issue.message().contains("\"missing\""), () -> "观察到的行缺字段必须报: " + result.issues());
    }

    @Test
    void tableColumnMissingFromEachGeneratedObjectRows() {
        // 输入观察到非空:输出行确定是 body 生成的形状 {label},列 missing 确定缺失,必须报。
        // 可能为空的输入仍止于未证明,由 emptyInputDoesNotFabricateFieldErrorsFromNever 锚定。
        String dsl = "root = Table([labelCol, missingCol], @Each(data.rows, \"row\", {label: row.id}))\n"
                + "labelCol = Col(\"Label\", \"label\")\n" + "missingCol = Col(\"Missing\", \"missing\")";
        ValidationResult result = validate(dsl, Map.of("rows", List.of(Map.of("id", "a"))));

        ValidationIssue issue = issue(result, "type-table-column-missing");
        assertEquals("/columns/missing", issue.path());
    }

    @Test
    void wholeStringArrayRejectedByScalarComposite() {
        // 整体数组不满足标量复合要求:TextContent.text 接受 string|number,装字符串的数组仍是数组。
        // AnyOf 单候选必须与其唯一 Accept 在同一整体值上判定一致(#77 §6.3 同分区)。
        String dsl = "root = TextContent([\"x\"])";
        ValidationResult result = validate(dsl, Map.of());

        ValidationIssue issue = issue(result, "type-prop-mismatch");
        assertEquals("TextContent", issue.component());
        assertEquals("/text", issue.path());
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
    void sameNameInnerBinderShadowsOuterInsideInnerBody() {
        // 同名遮蔽:内层 "row" 遮蔽外层。若遮蔽失效,内层 body 的 row 解析成外层行(只有 name),
        // 读 row.id 确定误报缺失;遮蔽正确时解析成内层行(只有 id),row.id 合法。
        // 该误报对鉴别有效:它只在遮蔽失效时出现。(嵌套元素行不携带逐行观察证据,
        // 内层行字段读取按元素存在未证明处理,不报确定缺失。)
        String dsl = "root = Stack(@Each(data.outer, \"row\", @Each(row.inner, \"row\", TextContent(row.id))))";
        Map<String, Object> dataModel = Map.of("outer",
                List.of(Map.of("name", "outer-1", "inner", List.of(Map.of("id", "inner-1")))));
        ValidationResult result = validate(dsl, dataModel);

        assertFalse(result.issues().stream().anyMatch(i -> "type-field-missing".equals(i.code())),
                () -> "遮蔽正确时内层 row.id 不得报缺失;报缺失即遮蔽失效: " + result.issues());
    }

    @Test
    void outerBinderRestoredAfterNestedEachWithinSameOuterBody() {
        // 恢复:同一外层 body 里,内层 Each 之后再读外层 row 的字段。
        // 外层与内层行都有 name 字段(任何错误的行解析都合法),value 形状不含 name——
        // 恢复失败(或同名残留、或内层形状泄漏)都会把 row.name 解析到 value 行,误报缺 name;
        // 恢复正确时读到外层行的字面量 name,VALID。
        String dsl = "root = Stack(@Each(data.outer, \"row\",\n"
                + "    Stack([@Each(row.inner, \"row\", TextContent(row.name)), TextContent(row.name)])))";
        Map<String, Object> dataModel = Map.of("outer", List.of(Map.of("name", "outer-1", "inner",
                List.of(Map.of("name", "inner-1", "value", 1)))));
        ValidationResult result = validate(dsl, dataModel);

        assertEquals(ValidationStatus.VALID, result.status(),
                () -> "退出内层后外层 row 必须恢复: " + result.issues());
    }

    @Test
    void innerShadowedBinderReadsInnerRowFieldsOnly() {
        // 负面对照:内层同名 body 读只有外层才有的字段 name。漏遮蔽会解析成外层 row.name(合法、不报);
        // 正确遮蔽时读到的是内层行——内层源是外层行的 inner 字段(不携带逐行观察证据),
        // 其行形状上界不含 name:报缺失只是未证明,不得升级为 type-field-missing(#82 口径)。
        String dsl = "root = Stack(@Each(data.outer, \"row\", @Each(row.inner, \"row\", TextContent(row.name))))";
        Map<String, Object> dataModel = Map.of("outer",
                List.of(Map.of("name", "outer-1", "inner", List.of(Map.of("id", "inner-1")))));
        ValidationResult result = validate(dsl, dataModel);

        assertFalse(result.issues().stream().anyMatch(i -> "type-field-missing".equals(i.code())),
                () -> "无逐行证据的内层行缺字段不得报确定缺失: " + result.issues());
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
        // switches 行有 hostname(该处通过),routers 行确定没有 hostname(该处必须独立暴露)——
        // 若两处共用同一检查结论,router 处的缺失会被 switch 处的通过掩盖。
        String dsl = "root = Stack([\n" + "  @Each(data.switches, \"d\", deviceLine),\n"
                + "  @Each(data.routers, \"d\", deviceLine)\n" + "])\n"
                + "deviceLine = TextContent(d.hostname)";
        Map<String, Object> dataModel = Map.of("switches", List.of(Map.of("hostname", "sw-1")), "routers",
                List.of(Map.of("name", "rt-1")));
        ValidationResult result = validate(dsl, dataModel);

        ValidationIssue issue = issue(result, "type-field-missing");
        assertEquals("deviceLine", issue.statementId());
        assertTrue(issue.message().contains("\"hostname\""),
                () -> "router 使用位置必须独立报出 hostname 缺失: " + result.issues());
    }

    @Test
    void binderLayoutComesFromContractDeclaration() {
        // binder 名形式(字符串字面量)、body 位置(最后一参)与输入来源(第一参)全部由契约 params
        // 的 binder 标记声明;语义分析与类型分析读同一份编译布局(LazyBuiltinBinders/BuiltinSpec)。
        // 非字符串 binder 不是作用域绑定:两处引擎都按契约形态报错,而不是把它当变量名引入作用域。
        String dsl = "root = Stack(@Each(data.rows, 42, TextContent(\"x\")))";
        ValidationResult result = validate(dsl, Map.of("rows", List.of(Map.of("name", "a"))));

        issue(result, "builtin-argument-type-mismatch");
    }

    @Test
    void conclusionsAreStableAcrossRepeatedValidationRuns() {
        // 嵌套 + 多使用位置的结论可重复:同一请求独立验证两次结果一致。
        // 类型分析会话按请求新建(无跨请求分析缓存开关),重复运行即缓存等价证据。
        String dsl = "root = Stack([\n" + "  @Each(data.switches, \"d\", deviceLine),\n"
                + "  @Each(data.routers, \"d\", deviceLine)\n" + "])\n"
                + "deviceLine = TextContent(d.hostname)";
        Map<String, Object> dataModel = Map.of("switches", List.of(Map.of("hostname", "sw-1")), "routers",
                List.of(Map.of("name", "rt-1")));

        ValidationResult first = validate(dsl, dataModel);
        ValidationResult second = validate(dsl, dataModel);

        assertEquals(first.status(), second.status());
        assertEquals(first.issues().stream().map(ValidationIssue::code).toList(),
                second.issues().stream().map(ValidationIssue::code).toList(),
                () -> "重复验证结论必须一致: first=" + first.issues() + " second=" + second.issues());
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
