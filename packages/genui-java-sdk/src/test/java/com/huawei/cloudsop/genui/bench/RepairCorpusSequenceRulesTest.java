/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.bench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * 真实修复语料使用 SDK 默认契约的 sequence 规则回归。
 *
 * <p>
 * Flatten/First/Last 的结构传播来自默认契约的 typeRule，检查真实缺失列仍被发现，存在的 metricValue 列不被误报。
 * 语料目录按仓库根定位，因此不依赖 mvn 的启动目录。
 *
 * @since 2026
 */
class RepairCorpusSequenceRulesTest {

    private static final List<String> CASE_DIRS = List.of("case-04_GEN-00119", "case-05_GEN-00122");

    /**
     * case-04 的真实诊断：neName 路径缺失 + 四个列在已证明的行形状中缺失（metricValue 是有效列，不得出现在此）。
     */
    private static final List<String> CASE_04_EXPECTED = List.of(
            "type-data-path-missing@data.queryOpticalModuleMetrics[0].neName",
            "type-table-column-missing@/columns/metricName", "type-table-column-missing@/columns/filteredValue",
            "type-table-column-missing@/columns/effcnt", "type-table-column-missing@/columns/laneId");

    /** case-05 的真实诊断：三个缺失列。 */
    private static final List<String> CASE_05_EXPECTED = List.of("type-table-column-missing@/columns/metricName",
            "type-table-column-missing@/columns/laneId", "type-table-column-missing@/columns/opt_uniq_id");

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

    private static ValidationResult validate(RepairCase benchCase) {
        return DefaultOpenuiLangValidator.INSTANCE.validate(ValidationRequest.builder().dsl(benchCase.dsl())
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
    void defaultContractSequencesKeepFlattenCasesFreeOfFalseColumnErrors() throws Exception {
        Path corpus = repoRoot().resolve("packages/genui-java-sdk/repair-cases");
        assertTrue(Files.isDirectory(corpus), "corpus directory not found at " + corpus);

        List<RepairCase> loaded = RepairCasePack.load(corpus, null);
        for (String dirName : CASE_DIRS) {
            RepairCase benchCase = loaded.stream().filter(c -> c.dirName().equals(dirName)).findFirst()
                    .orElseThrow(() -> new AssertionError("corpus case missing: " + dirName));
            ValidationResult result = validate(benchCase);
            List<String> diagnostics = codeAtPath(result);

            assertEquals(ValidationStatus.INVALID, result.status(), dirName + " must still reproduce");
            assertFalse(diagnostics.contains("type-table-column-missing@/columns/metricValue"),
                    dirName + " reports metricValue as missing although every row has it: " + diagnostics);
            List<String> expected = dirName.startsWith("case-04_") ? CASE_04_EXPECTED : CASE_05_EXPECTED;
            assertEquals(expected, diagnostics, dirName + " genuine diagnostics changed");
        }
    }
}
