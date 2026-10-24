/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.bench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.bench.RepairCasePack.RepairCase;
import com.huawei.cloudsop.genui.core.validation.DefaultOpenuiLangValidator;
import com.huawei.cloudsop.genui.core.validation.ValidationIssue;
import com.huawei.cloudsop.genui.core.validation.ValidationMode;
import com.huawei.cloudsop.genui.core.validation.ValidationRequest;
import com.huawei.cloudsop.genui.core.validation.ValidationResult;
import com.huawei.cloudsop.genui.core.validation.ValidationStatus;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 真实修复语料使用 SDK 默认契约的 object 枚举回归，检查字段证据及逐案例诊断。
 *
 * @since 2026
 */
class RepairCorpusObjectEnumerationRulesTest {

    /** 外部案例包目录覆盖开关；未显式指定自定义契约时仍使用 SDK 默认契约。 */
    private static final String CORPUS_OVERRIDE = "genui.repairCorpus";

    /**
     * SDK 默认契约下全部被跟踪案例的诊断基线（{@code 目录|状态|[code@path, ...]}）。
     *
     * <p>
     * case-21 是本次同步的有意差异：它把 {@code @Each(...)} 数组传给 {@code @ObjectEntries}，与该案例 issues.json 记录的
     * {@code builtin-argument-type-mismatch} 一致，因此由 VALID 变为 INVALID。其余案例必须保持不变。
     */
    private static final List<String> CORPUS_BASELINE = List.of(
            "case-03_GEN-00109 | INVALID | [type-table-column-missing@/columns/unit]",
            "case-04_GEN-00119 | INVALID | [type-data-path-missing@data.queryOpticalModuleMetrics[0].neName, type-table-column-missing@/columns/metricName, type-table-column-missing@/columns/filteredValue, type-table-column-missing@/columns/effcnt]",
            "case-05_GEN-00122 | INVALID | [type-table-column-missing@/columns/metricName, type-table-column-missing@/columns/opt_uniq_id]",
            "case-06_GEN-00009 | INVALID | [unknown-component@, unknown-component@, unresolved-ref@null]",
            "case-07_GEN-00036 | INVALID | [unknown-component@]", "case-08_GEN-00247 | INVALID | [unknown-component@]",
            "case-09_GEN-00042 | INVALID | [type-table-column-missing@/columns/patchFileVersion]",
            "case-10_GEN-00116 | INVALID | [type-table-column-missing@/columns/cpu_usage]",
            "case-11_GEN-00118 | INVALID | [type-table-column-missing@/columns/memory_usage]",
            "case-12_GEN-00120 | INVALID | [type-table-column-missing@/columns/cpu_usage]",
            "case-13_GEN-00124 | INVALID | [type-table-column-missing@/columns/send_drop_pkts_cnt]",
            "case-14_GEN-00030 | INVALID | [type-data-path-missing@data.deviceIp]",
            "case-15_GEN-00056 | INVALID | [unresolved-ref@null, type-data-path-missing@data.bash[0].bgp]",
            "case-16_GEN-00066 | INVALID | [type-data-path-missing@data.queryOpticalModuleMetrics.telemetryData[0].voltage]",
            "case-17_GEN-00232 | INVALID | [type-data-path-missing@data.deviceInfo[0].neName, type-data-path-missing@data.deviceInfo[0].neName, type-data-path-missing@data.deviceInfo[0].neIp, type-data-path-missing@data.deviceInfo[0].neRole, type-data-path-missing@data.deviceInfo[0].neState, type-data-path-missing@data.deviceInfo[0].neType, type-data-path-missing@data.deviceInfo[0].neVendor, type-data-path-missing@data.deviceInfo[0].neMac, type-data-path-missing@data.deviceInfo[0].neDn, type-data-path-missing@data.deviceInfo[0].neEsn, type-data-path-missing@data.deviceInfo[0].neVersion, type-data-path-missing@data.deviceInfo[0].fabricName]",
            "case-18_GEN-00233 | INVALID | [type-data-path-missing@data.deviceInfo[0].neName, type-data-path-missing@data.deviceInfo[0].neName, type-data-path-missing@data.deviceInfo[0].neIp, type-data-path-missing@data.deviceInfo[0].neState, type-data-path-missing@data.deviceInfo[0].neRole, type-data-path-missing@data.deviceInfo[0].neCategory, type-data-path-missing@data.deviceInfo[0].neType, type-data-path-missing@data.deviceInfo[0].neVendor, type-data-path-missing@data.deviceInfo[0].neMac, type-data-path-missing@data.deviceInfo[0].neEsn, type-data-path-missing@data.deviceInfo[0].neVersion, type-data-path-missing@data.deviceInfo[0].fabricName]",
            "case-19_GEN-00349 | INVALID | [type-data-path-missing@data.deviceInfo[0].neName, type-data-path-missing@data.deviceInfo[0].neName, type-data-path-missing@data.deviceInfo[0].neIp, type-data-path-missing@data.deviceInfo[0].neState, type-data-path-missing@data.deviceInfo[0].neType, type-data-path-missing@data.deviceInfo[0].neRole, type-data-path-missing@data.deviceInfo[0].neVendor, type-data-path-missing@data.deviceInfo[0].neVersion, type-data-path-missing@data.deviceInfo[0].neMac, type-data-path-missing@data.deviceInfo[0].neEsn, type-data-path-missing@data.deviceInfo[0].neCategory, type-data-path-missing@data.deviceInfo[0].fabricName]",
            "case-21_en-device-distribution-fabric-3 | INVALID | [builtin-argument-type-mismatch@/args/0]",
            "case-23_GEN-00094 | INVALID | [type-table-row-shape-mismatch@/rows, type-table-column-missing@/columns/name, type-table-column-missing@/columns/value]",
            "case-24_GEN-00076 | INVALID | [type-field-missing@null]",
            "case-25_GEN-00088 | INVALID | [type-table-row-shape-mismatch@/rows, type-table-column-missing@/columns/name, type-table-column-missing@/columns/value]",
            "case-26_GEN-00150 | INVALID | [unknown-component@]", "case-27_GEN-00022 | INVALID | [unknown-component@]",
            "case-28_GEN-00111 | INVALID | [syntax-unclosed-bracket@null, syntax-unexpected-token@null, unresolved-ref@null]",
            "case-29_GEN-00126 | INVALID | [unknown-component@]");

    /** 定位仓库根：从 CWD 向上第一个含 {@code .git} 的目录（与 RepairBenchCli 同规约）。 */
    private static Path repoRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve(".git"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("cannot locate repository root (no .git ancestor of "
                + System.getProperty("user.dir") + ")");
    }

    private static Path corpusRoot() {
        String override = System.getProperty(CORPUS_OVERRIDE);
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        return repoRoot().resolve("packages/genui-java-sdk/repair-cases");
    }

    private static List<RepairCase> loadCorpus() throws Exception {
        Path corpus = corpusRoot();
        assertTrue(Files.isDirectory(corpus), "corpus directory not found at " + corpus);
        return RepairCasePack.load(corpus, null);
    }

    private static ValidationResult validate(String dsl, RepairCase benchCase) {
        return DefaultOpenuiLangValidator.INSTANCE.validate(ValidationRequest.builder().dsl(dsl)
                .contract(benchCase.contract())
                .rootName(benchCase.contract().root() == null ? "root" : benchCase.contract().root())
                .externalRefs(Set.of("data")).dataModel(benchCase.dataModel()).mode(ValidationMode.FINAL).build());
    }

    private static List<String> codeAtPath(ValidationResult result) {
        List<String> lines = new ArrayList<>();
        for (ValidationIssue issue : result.issues()) {
            lines.add(issue.code() + "@" + issue.path());
        }
        return lines;
    }

    @Test
    void defaultContractObjectEnumerationKeepsFieldEvidence() throws Exception {
        RepairCase benchCase = loadCorpus().get(0);
        String entries = "root = Stack(@Each(@ObjectEntries({port: 1}), e, TextContent(e.port)))";
        ValidationResult entriesResult = validate(entries, benchCase);
        assertEquals(ValidationStatus.INVALID, entriesResult.status(),
                "e.port must be reported absent; genericResult would silently accept it: " + codeAtPath(entriesResult));
        assertEquals(List.of("type-field-missing@null"), codeAtPath(entriesResult));
        assertTrue(entriesResult.issues().get(0).message().contains("\"port\" is absent"), entriesResult.issues()
                .get(0).message());

        String values = "root = TextContent(@ObjectValues({port: 1}))";
        ValidationResult valuesResult = validate(values, benchCase);
        assertEquals(ValidationStatus.INVALID, valuesResult.status());
        assertEquals(List.of("type-prop-mismatch@/text"), codeAtPath(valuesResult));
        assertTrue(valuesResult.issues().get(0).message().contains("received number[1]"),
                "@ObjectValues must keep the value element type, not degrade to unknown: "
                        + valuesResult.issues().get(0).message());

        String entriesValue = "root = Stack(@Each(@ObjectEntries({port: 1, name: \"a\"}), e, TextContent(e.value)))";
        assertEquals(ValidationStatus.VALID, validate(entriesValue, benchCase).status(),
                "e.value must stay fully proven (key/value branches preserved)");

        String keys = "root = Stack(@Each(@ObjectKeys({a: 1}), k, TextContent(k)))";
        assertEquals(ValidationStatus.VALID, validate(keys, benchCase).status(), "@ObjectKeys must stay usable");
    }

    @Test
    void corpusDiagnosticsStayStableApartFromTheIntendedArrayArgumentRejection() throws Exception {
        List<RepairCase> loaded = loadCorpus();
        for (RepairCase benchCase : loaded) {
            ValidationResult result = validate(benchCase.dsl(), benchCase);
            String actual = benchCase.dirName() + " | " + result.status() + " | " + codeAtPath(result);
            CORPUS_BASELINE.stream().filter(line -> line.startsWith(benchCase.dirName() + " |")).findFirst()
                    .ifPresent(expected -> assertEquals(expected, actual,
                            benchCase.dirName() + " diagnostics changed under the SDK default contract"));
        }
        for (String expected : CORPUS_BASELINE) {
            String dirName = expected.substring(0, expected.indexOf('|')).trim();
            assertTrue(loaded.stream().anyMatch(benchCase -> benchCase.dirName().equals(dirName)),
                    "pinned corpus case missing: " + dirName);
        }
    }
}
