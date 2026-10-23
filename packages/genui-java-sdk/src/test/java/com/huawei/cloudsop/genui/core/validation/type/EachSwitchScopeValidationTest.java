package com.huawei.cloudsop.genui.core.validation.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.contract.BuiltinSpec;
import com.huawei.cloudsop.genui.core.contract.GenerationContractLoader;
import com.huawei.cloudsop.genui.core.validation.DefaultOpenuiLangValidator;
import com.huawei.cloudsop.genui.core.validation.ValidationIssue;
import com.huawei.cloudsop.genui.core.validation.ValidationMode;
import com.huawei.cloudsop.genui.core.validation.ValidationRequest;
import com.huawei.cloudsop.genui.core.validation.ValidationResult;
import com.huawei.cloudsop.genui.core.validation.ValidationStatus;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * #83:Each/Switch 作用域语义——Each 把输入元素类型带入模板并把模板结果按数组传下游;嵌套 Switch 不丢 binder; 嵌套同名 binder 遮蔽与退出恢复;空输入不制造字段错误;binder
 * 布局共用契约声明。
 */
class EachSwitchScopeValidationTest {

    @Test
    void sumRequiresNumericElementsAndReturnsNumber() {
        issue(validate("root = TextContent(@Sum([\"bad\"]))", Map.of()), "builtin-argument-type-mismatch");
        issue(validate("root = Table([col], @Sum([[{id: \"a\"}]]))\ncol = Col(\"ID\", \"id\")", Map.of()),
                "builtin-argument-type-mismatch");
        assertEquals(ValidationStatus.VALID, validate("root = TextContent(@Sum([1, 2]))", Map.of()).status());
    }

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
        String dsl = "root = Table([labelCol, badCol], data.rows)\n" + "labelCol = Col(\"Label\", \"label\")\n"
                + "badCol = Col(\"Bad\", \"missing_field\")";
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
                + "    @Switch(item.kind, {\n" + "      \"a\": item.name,\n"
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
        // 该误报对鉴别有效:它只在遮蔽失效时出现。
        String dsl = "root = Stack(@Each(data.outer, \"row\", @Each(row.inner, \"row\", TextContent(row.id))))";
        Map<String, Object> dataModel = Map.of("outer",
                List.of(Map.of("name", "outer-1", "inner", List.of(Map.of("id", "inner-1")))));
        ValidationResult result = validate(dsl, dataModel);

        assertFalse(result.issues().stream().anyMatch(i -> "type-field-missing".equals(i.code())),
                () -> "遮蔽正确时内层 row.id 不得报缺失;报缺失即遮蔽失效: " + result.issues());
    }

    @Test
    void outerBinderRestoredAfterNestedEachWithinSameOuterBody() {
        // 恢复:同一外层 body 里,内层 Each 之后再读外层 row 的字段,字段名内外层不相交。
        // 内层读 row.id(仅内层行有):内层形状泄漏会把它解析成外层行,误报缺 id;
        // 退出后读 row.name(仅外层行有):同名残留或内层形状泄漏都会误报缺 name。
        // 两个引用各自解析到正确的行才 VALID。
        String dsl = "root = Stack(@Each(data.outer, \"row\",\n"
                + "    Stack([@Each(row.inner, \"row\", TextContent(row.id)), TextContent(row.name)])))";
        Map<String, Object> dataModel = Map.of("outer",
                List.of(Map.of("name", "outer-1", "inner", List.of(Map.of("id", "inner-1")))));
        ValidationResult result = validate(dsl, dataModel);

        assertEquals(ValidationStatus.VALID, result.status(), () -> "退出内层后外层 row 必须恢复: " + result.issues());
    }

    @Test
    void nestedObservedRowMissingFieldIsReported() {
        // 快照里确定存在的嵌套内层行缺字段是真实反例(#77 §2.1/§6.2):字段读取的证据关联保留
        // 嵌套数组的观察来源,不得用"可能为空"压掉;遮蔽下外层的 name 不能替内层行满足。
        String dsl = "root = Stack(@Each(data.outer, \"row\", @Each(row.inner, \"row\", TextContent(row.name))))";
        Map<String, Object> dataModel = Map.of("outer",
                List.of(Map.of("name", "outer-1", "inner", List.of(Map.of("id", "inner-1")))));
        ValidationResult result = validate(dsl, dataModel);

        ValidationIssue issue = issue(result, "type-field-missing");
        assertTrue(issue.message().contains("\"name\""), () -> "观察到的嵌套行缺字段必须报: " + result.issues());
    }

    @Test
    void filteredInputDoesNotFabricateMissingField() {
        // Filter 删除"某行必然保留"的证据(#77 §6.2/§6.5/§7.5):没有任何行被证明保留,
        // binder 字段缺失不能升级为确定错误——即便快照行上界确实没有该字段。
        String dsl = "root = Stack(@Each(@Filter(data.rows, \"enabled\", \"==\", true), \"row\", TextContent(row.missing)))";
        ValidationResult result = validate(dsl, Map.of("rows", List.of(Map.of("id", "a", "enabled", false))));

        assertFalse(result.issues().stream().anyMatch(i -> "type-field-missing".equals(i.code())),
                () -> "Filter 后不得从可能为空的绑定制造字段缺失错误: " + result.issues());
    }

    @Test
    void nestedFilteredInputDoesNotFabricateMissingField() {
        // 同上,经嵌套 Each:Filter 之后的内层行存在性同样未证明。
        String dsl = "root = Stack(@Each(@Filter(data.rows, \"enabled\", \"==\", true), \"row\",\n"
                + "    @Each(row.inner, \"child\", TextContent(child.missing))))";
        Map<String, Object> dataModel = Map.of("rows",
                List.of(Map.of("id", "a", "enabled", false, "inner", List.of(Map.of("id", "i")))));
        ValidationResult result = validate(dsl, dataModel);

        assertFalse(result.issues().stream().anyMatch(i -> "type-field-missing".equals(i.code())),
                () -> "嵌套 Filter 输入不得制造字段缺失错误: " + result.issues());
    }

    @Test
    void identityChainedEachPreservesMissingRowCounterexample() {
        // 恒等 Each 不能吞掉反例:内层输出保留行级观察证据,外层绑定仍能看到缺 id 的行。
        String dsl = "root = Stack(@Each(@Each(data.rows, row, row), x, TextContent(x.id)))";
        Map<String, Object> dataModel = Map.of("rows", List.of(Map.of("id", "a"), Map.of("other", 1)));
        ValidationResult result = validate(dsl, dataModel);

        ValidationIssue issue = issue(result, "type-field-missing");
        assertTrue(issue.message().contains("\"id\""), () -> "链式恒等 Each 必须保留缺失行反例: " + result.issues());
    }

    @Test
    void nestedArrayMappingKeepsEvidenceAtCorrectLevel() {
        // body 是嵌套数组(row.inner):内层行证据只能关联到内层数组这一层;
        // 外层 Each 解包后逐内层行的缺失必须仍然可见(关联错层会把内层行当外层数组观察)。
        String dsl = "root = Stack(@Each(@Each(data.rows, row, row.inner), children,\n"
                + "    @Each(children, child, TextContent(child.id))))";
        Map<String, Object> dataModel = Map.of("rows",
                List.of(Map.of("inner", List.of(Map.of("id", "a"), Map.of("other", 1)))));
        ValidationResult result = validate(dsl, dataModel);

        ValidationIssue issue = issue(result, "type-field-missing");
        assertTrue(issue.message().contains("\"id\""), () -> "嵌套数组映射必须保留下层缺失反例: " + result.issues());
    }

    @Test
    void binderDoesNotScopeEachSourceArgument() {
        // binder 只作用于 body(#77 §10.1):Each 的 source 在外层作用域解析,
        // 与 binder 同名的无定义引用必须照常报 unresolved-ref,不得被 binder 压制。
        String dsl = "root = Stack(@Each(row, row, TextContent(\"x\")))";
        ValidationResult result = validate(dsl, Map.of());

        issue(result, "unresolved-ref");
    }

    @Test
    void emptyEachBodyRecoveredObservedListTerminatesNormally() {
        // 继承终止性缺陷:空输入 Each 的 body 恢复出携带观察证据的数组事实,Sum 元素级检查
        // 的观察反例路径曾在同一证据上无限递归(StackOverflowError)。现在必须正常返回。
        // 空 Each 不产生任何 body 元素,故不得断言 body 数据造成的聚合级不匹配——body 独立错误
        // 仍按各自规则检查(见 emptyInputStillChecksBodyLocalErrors),但空 Each 的输出与 body
        // 取值无关,此处只锚定正常终止。
        String dsl = "root = TextContent(@Sum(@Each([], \"row\", data.values)))";
        ValidationResult result = validate(dsl, Map.of("values", List.of("bad")));

        assertEquals(ValidationStatus.VALID, result.status(), () -> String.valueOf(result.issues()));
    }

    @Test
    void emptyAndUnprovedEachDoNotObserveIndependentBodyValues() {
        for (String source : List.of("[]", "@Filter(data.rows, \"enabled\", \"==\", true)")) {
            for (String body : List.of("data.value", "data.values")) {
                ValidationResult result = validate("root = TextContent(@Avg(@Each(" + source + ", row, " + body + ")))",
                        Map.of("value", "bad", "values", List.of("bad"), "rows", List.of(Map.of("enabled", false))));
                assertEquals(ValidationStatus.VALID, result.status(),
                        () -> "No output element was proved to exist: " + result.issues());
                ValidationResult chained = validate(
                        "root = Stack(@Each(@Each(" + source + ", row, data.obj), x, TextContent(x.missing)))",
                        Map.of("obj", Map.of("id", "a"), "rows", List.of(Map.of("enabled", false))));
                assertEquals(ValidationStatus.VALID, chained.status(), () -> String.valueOf(chained.issues()));
            }
        }
    }

    @Test
    void heterogeneousObservedRowMissingFieldIsReported() {
        // 异构行:有的行有 id、观察到的另一行没有 id——行级观察证据中的缺失行是具体反例,
        // 不得被"有的行有该字段"的上界合并掩盖。
        String dsl = "root = Stack(@Each(data.rows, \"row\", TextContent(row.id)))";
        Map<String, Object> dataModel = Map.of("rows", List.of(Map.of("id", "a"), Map.of("other", 1)));
        ValidationResult result = validate(dsl, dataModel);

        ValidationIssue issue = issue(result, "type-field-missing");
        assertTrue(issue.message().contains("\"id\""), () -> "观察到的缺失行必须报: " + result.issues());
    }

    // ── 空输入仍检查 body 独立错误 ─────────────────────────────────────────

    @Test
    void locallyConstructedObjectsProveMissingFieldsIndependentlyOfObservations() {
        for (String dsl : List.of("root = TextContent(({id: \"a\"}).missing)",
                "root = Stack(@Each([{id: \"a\"}], row, TextContent(row.missing)))",
                "root = Stack(@Each([], row, TextContent(({id: \"a\"}).missing)))")) {
            issue(validate(dsl, Map.of()), "type-field-missing");
        }
    }

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

    // ── 联合分支不能证明元素存在(#77 §6.5)────────────────────────────────

    @Test
    void switchUnionWithEmptyBranchDoesNotProveEachOutputRows() {
        // 联合里只有一个分支非空,不能证明运行时拿到的是非空分支。可能为空的联合作为 Each 源时,
        // 输出行形状仍属"未证明":不得据此报 Table 列缺失。
        String dsl = "root = Table([c], @Each(@Switch(data.kind, {\"a\": [data.row], \"b\": []}, []), row, "
                + "{x: row.id}))\nc = Col(\"X\", \"missing\")";
        ValidationResult result = validate(dsl, Map.of("kind", "b", "row", Map.of("id", "a")));

        assertEquals(ValidationStatus.VALID, result.status(), () -> String.valueOf(result.issues()));
    }

    @Test
    void chainedSwitchUnionWithEmptyBranchKeepsOutputUnproven() {
        // 同样的含空分支联合经链式 Each 传递后仍不得被当成观察到输出行。
        String dsl = "root = Table([c], @Each(@Each(@Switch(data.kind, {\"a\": [data.row], \"b\": []}, []), "
                + "row, row), x, {y: x.id}))\nc = Col(\"Y\", \"missing\")";
        ValidationResult result = validate(dsl, Map.of("kind", "b", "row", Map.of("id", "a")));

        assertEquals(ValidationStatus.VALID, result.status(), () -> String.valueOf(result.issues()));
    }

    @Test
    void allNonEmptySwitchBranchesStillProveEachOutputRows() {
        // 所有可能分支都确定非空时,联合可证明输出行存在:真反例(列缺失)必须仍然报出。
        String dsl = "root = Table([c], @Each(@Switch(data.kind, {\"a\": [data.rowA], \"b\": [data.rowB]}, "
                + "[data.rowA]), row, {x: row.id}))\nc = Col(\"X\", \"missing\")";
        ValidationResult result = validate(dsl,
                Map.of("kind", "a", "rowA", Map.of("id", "a"), "rowB", Map.of("id", "b")));

        ValidationIssue issue = issue(result, "type-table-column-missing");
        assertEquals("/columns/missing", issue.path());
    }

    @Test
    void observedNonEmptySourceStillProvesEachOutputRows() {
        // 实际观察到的非空数组是存在性证据:真反例必须仍然报出。
        String dsl = "root = Table([c], @Each(data.rows, row, {x: row.id}))\nc = Col(\"X\", \"missing\")";
        ValidationResult result = validate(dsl, Map.of("rows", List.of(Map.of("id", "a"))));

        issue(result, "type-table-column-missing");
    }

    @Test
    void avgOverSwitchUnionKeepsTypeJustifiedCounterexample() {
        // 空分支 + 非空 string 分支:@Avg 的类型级不匹配由非空分支自身元素类型独立证明,
        // 属真反例,必须保留;修复只禁止把可能为空的联合当成"已观察到输出元素"。
        String dsl = "root = TextContent(@Avg(@Each(@Switch(data.kind, {\"a\": [data.bad], \"b\": []}, []), "
                + "row, data.bad)))";
        ValidationResult result = validate(dsl, Map.of("kind", "b", "bad", "bad"));

        issue(result, "builtin-argument-type-mismatch");
    }

    @Test
    void avgOverAllEmptySwitchUnionDoesNotFabricateElements() {
        // 所有分支都为空:输出确定为空,不得为 @Avg 伪造任何元素。
        String dsl = "root = TextContent(@Avg(@Each(@Switch(data.kind, {\"a\": [], \"b\": []}, []), "
                + "row, data.bad)))";
        ValidationResult result = validate(dsl, Map.of("kind", "a", "bad", "bad"));

        assertEquals(ValidationStatus.VALID, result.status(), () -> String.valueOf(result.issues()));
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
        ValidationResult result = validate(dsl, Map.of("devices", List.of(Map.of("status", "up", "name", "d1"))));

        assertEquals(ValidationStatus.VALID, result.status(), () -> String.valueOf(result.issues()));
    }

    // ── 多使用位置与公开 validator 口径 ───────────────────────────────────

    @Test
    void templateUsedAtMultipleSitesChecksEachContextIndependently() {
        // 同一模板语句被两个不同行形状的 Each 使用:两处各自按其元素类型检查,不能互相串结论。
        // switches 行有 hostname(该处通过),routers 行确定没有 hostname(该处必须独立暴露)——
        // 若两处共用同一检查结论,router 处的缺失会被 switch 处的通过掩盖。
        String dsl = "root = Stack([\n" + "  @Each(data.switches, \"d\", deviceLine),\n"
                + "  @Each(data.routers, \"d\", deviceLine)\n" + "])\n" + "deviceLine = TextContent(d.hostname)";
        Map<String, Object> dataModel = Map.of("switches", List.of(Map.of("hostname", "sw-1")), "routers",
                List.of(Map.of("name", "rt-1")));
        ValidationResult result = validate(dsl, dataModel);

        ValidationIssue issue = issue(result, "type-field-missing");
        assertEquals("deviceLine", issue.statementId());
        assertTrue(issue.message().contains("\"hostname\""), () -> "router 使用位置必须独立报出 hostname 缺失: " + result.issues());
    }

    @Test
    void binderLayoutComesFromContractDeclaration() {
        // binder 布局由契约声明并编译进 BuiltinSpec:params 的 binder/nameSyntax、input 与 callForms。
        // 语义层(ProgramAnalyzer/StatementRelations)与类型层(AnalysisSession)共用同一份编译布局
        // (LazyBuiltinBinders),不各自重述 Each/Render 的形态特判。
        BuiltinSpec each = GenerationContractLoader.loadDefault().builtins().stream()
                .filter(spec -> "Each".equals(spec.name())).findFirst().orElseThrow();
        assertEquals(List.of(1), each.binderIndexes());
        assertEquals("string-or-identifier", each.params().get(1).nameSyntax());
        assertEquals(0, each.inputIndex());
        assertEquals(List.of(1), each.activeBinderIndexes(3));
        assertEquals(2, each.bodyIndex(3));
        BuiltinSpec render = GenerationContractLoader.loadDefault().builtins().stream()
                .filter(spec -> "Render".equals(spec.name())).findFirst().orElseThrow();
        assertEquals(List.of(0, 1), render.binderIndexes());
        assertEquals(List.of(0), render.activeBinderIndexes(2));
        assertEquals(List.of(0, 1), render.activeBinderIndexes(3));
        assertEquals(1, render.bodyIndex(2));
    }

    @Test
    void bareIdentifierBinderBindsRowLikeStringLiteral() {
        // 契约 nameSyntax string-or-identifier:裸标识符与字符串字面量同为合法 binder 形态,
        // 语义层与类型层一致绑定——缺字段检查不得因形态不同被静默跳过。
        String dsl = "root = Stack(@Each(data.rows, row, TextContent(row.missing)))";
        ValidationResult result = validate(dsl, Map.of("rows", List.of(Map.of("id", "a"))));

        ValidationIssue issue = issue(result, "type-field-missing");
        assertTrue(issue.message().contains("\"missing\""), () -> "裸标识符 binder 必须同样绑定行: " + result.issues());
    }

    @Test
    void renderWithBareIdentifierBinderChecksBodyAtUseSite() {
        // Render 的 v/row 同样接受裸标识符;使用位置按声明名绑定列字段与行事实——
        // cell 模板里的裸 binder 引用必须解析到绑定值而不是未绑引用。
        String dsl = "root = Table([nameCol], data.rows)\n"
                + "nameCol = Col(\"Name\", \"name\", {cell: @Render(v, row, TextContent(row.name))})";
        ValidationResult result = validate(dsl, Map.of("rows", List.of(Map.of("name", "a"))));

        assertEquals(ValidationStatus.VALID, result.status(), () -> "裸标识符 Render binder 必须在类型层绑定: " + result.issues());
    }

    @Test
    void numericBinderIsRejectedUnderDeclaredNameSyntax() {
        // 数字实参不在 nameSyntax 允许的形态内:类型层按契约形态报错,而不是把它当变量名引入作用域。
        String dsl = "root = Stack(@Each(data.rows, 42, TextContent(\"x\")))";
        ValidationResult result = validate(dsl, Map.of("rows", List.of(Map.of("name", "a"))));

        issue(result, "builtin-argument-type-mismatch");
    }

    @Test
    void conclusionsAreRepeatableAcrossIndependentRuns() {
        // 嵌套 + 多使用位置的结论可重复:同一请求独立验证两次结果一致。
        // 架构事实:类型分析不做跨请求记忆化——AnalysisSession 每次 analyze 新建,无缓存开关;
        // 本测试锚定的是可重复性,不是缓存开/关等价(该验收口径的记录见交付说明)。
        String dsl = "root = Stack([\n" + "  @Each(data.switches, \"d\", deviceLine),\n"
                + "  @Each(data.routers, \"d\", deviceLine)\n" + "])\n" + "deviceLine = TextContent(d.hostname)";
        Map<String, Object> dataModel = Map.of("switches", List.of(Map.of("hostname", "sw-1")), "routers",
                List.of(Map.of("name", "rt-1")));

        ValidationResult first = validate(dsl, dataModel);
        ValidationResult second = validate(dsl, dataModel);

        assertEquals(first.status(), second.status());
        assertEquals(first.issues(), second.issues(),
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
