/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.bench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.bench.RepairCasePack.PackLoadException;
import com.huawei.cloudsop.genui.bench.RepairCasePack.RepairCase;
import com.huawei.cloudsop.genui.core.contract.GenerationContractLoader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * {@link RepairCasePack} 加载：共用 SDK 默认契约、显式自定义契约优先、无效契约整批终止；dataModel null 语义；id 重复 / dsl.txt 缺失或空 / case.json 非法 / issues.json 非法
 * / 零案例目录 → 整批终止且报错列出全部问题案例；issues.json 透传。
 *
 * @since 2026
 */
class RepairCasePackTest {

    private static final String CONTRACT = "{\"contractVersion\":\"v1\",\"root\":\"Stack\",\"components\":{}}";

    @TempDir
    Path tempDir;

    private Path writeCase(String dirName, String caseJson, String dsl) throws Exception {
        return writeCase(dirName, caseJson, dsl, null);
    }

    private Path writeCase(String dirName, String caseJson, String dsl, String contractJson) throws Exception {
        Path dir = Files.createDirectories(tempDir.resolve(dirName));
        Files.writeString(dir.resolve("case.json"), caseJson, StandardCharsets.UTF_8);
        if (dsl != null) {
            Files.writeString(dir.resolve("dsl.txt"), dsl, StandardCharsets.UTF_8);
        }
        if (contractJson != null) {
            Files.writeString(dir.resolve("contract.json"), contractJson, StandardCharsets.UTF_8);
        }
        return dir;
    }

    @Test
    void dataModelAbsentMeansNullEmptyObjectMeansEmptyMap() throws Exception {
        Files.writeString(tempDir.resolve("contract.json"), CONTRACT, StandardCharsets.UTF_8);
        writeCase("case-a", "{\"id\":\"a\"}", "root = Stack([])");
        writeCase("case-b", "{\"id\":\"b\",\"dataModel\":{}}", "root = Stack([])");
        writeCase("case-c", "{\"id\":\"c\",\"dataModel\":{\"x\":1}}", "root = Stack([])");

        List<RepairCase> cases = RepairCasePack.load(tempDir, null);
        assertEquals(3, cases.size());
        RepairCase a = cases.stream().filter(item -> item.id().equals("a")).findFirst().orElseThrow();
        RepairCase b = cases.stream().filter(item -> item.id().equals("b")).findFirst().orElseThrow();
        RepairCase c = cases.stream().filter(item -> item.id().equals("c")).findFirst().orElseThrow();
        assertNull(a.dataModel(), "absent dataModel must stay null (NOT normalized to an empty map)");
        assertEquals(Map.of(), b.dataModel(), "explicit {} is a concrete empty model");
        assertEquals(Map.of("x", 1L), c.dataModel());
    }

    @Test
    void caseContractWinsOverCorpusContract() throws Exception {
        Files.writeString(tempDir.resolve("contract.json"), CONTRACT, StandardCharsets.UTF_8);
        String caseContract = "{\"contractVersion\":\"v2\",\"root\":\"Stack\",\"components\":{}}";
        writeCase("case-a", "{\"id\":\"a\"}", "root = Stack([])", caseContract);
        writeCase("case-b", "{\"id\":\"b\"}", "root = Stack([])");

        List<RepairCase> cases = RepairCasePack.load(tempDir, null);
        RepairCase a = cases.stream().filter(item -> item.id().equals("a")).findFirst().orElseThrow();
        RepairCase b = cases.stream().filter(item -> item.id().equals("b")).findFirst().orElseThrow();
        assertEquals("v2", a.contract().contractVersion(), "case-level contract takes precedence");
        assertEquals("v1", b.contract().contractVersion(), "falls back to corpus contract");
    }

    @Test
    void casesWithoutOverridesShareSdkDefaultContract() throws Exception {
        writeCase("case-a", "{\"id\":\"a\"}", "root = Stack([])");
        writeCase("case-b", "{\"id\":\"b\"}", "root = Stack([])");
        List<RepairCase> cases = RepairCasePack.load(tempDir, null);
        assertEquals(GenerationContractLoader.loadDefault(), cases.get(0).contract());
        assertSame(cases.get(0).contract(), cases.get(1).contract());
    }

    @Test
    void invalidExplicitContractDoesNotFallBackToDefault() throws Exception {
        Files.writeString(tempDir.resolve("contract.json"), "not-json", StandardCharsets.UTF_8);
        writeCase("case-a", "{\"id\":\"a\"}", "root = Stack([])");
        PackLoadException error = assertThrows(PackLoadException.class, () -> RepairCasePack.load(tempDir, null));
        assertTrue(error.getMessage().contains("corpus contract.json invalid"), error.getMessage());
    }

    @Test
    void duplicateIdAbortsWholeBatch() throws Exception {
        Files.writeString(tempDir.resolve("contract.json"), CONTRACT, StandardCharsets.UTF_8);
        writeCase("case-a", "{\"id\":\"same\"}", "root = Stack([])");
        writeCase("case-b", "{\"id\":\"same\"}", "root = Stack([])");
        PackLoadException error = assertThrows(PackLoadException.class, () -> RepairCasePack.load(tempDir, null));
        assertTrue(error.getMessage().contains("duplicate case id 'same'"), error.getMessage());
    }

    @Test
    void emptyDslAbortsWholeBatch() throws Exception {
        Files.writeString(tempDir.resolve("contract.json"), CONTRACT, StandardCharsets.UTF_8);
        writeCase("case-a", "{\"id\":\"a\"}", "   ");
        PackLoadException error = assertThrows(PackLoadException.class, () -> RepairCasePack.load(tempDir, null));
        assertTrue(error.getMessage().contains("dsl.txt is empty"), error.getMessage());
    }

    @Test
    void invalidCaseJsonAbortsWholeBatch() throws Exception {
        Files.writeString(tempDir.resolve("contract.json"), CONTRACT, StandardCharsets.UTF_8);
        writeCase("case-a", "not-json", "root = Stack([])");
        PackLoadException error = assertThrows(PackLoadException.class, () -> RepairCasePack.load(tempDir, null));
        assertTrue(error.getMessage().contains("case.json invalid"), error.getMessage());
    }

    @Test
    void allProblemsListedInOneError() throws Exception {
        Files.writeString(tempDir.resolve("contract.json"), CONTRACT, StandardCharsets.UTF_8);
        writeCase("case-a", "not-json", "root = Stack([])");
        writeCase("case-b", "{\"id\":\"b\"}", null);
        PackLoadException error = assertThrows(PackLoadException.class, () -> RepairCasePack.load(tempDir, null));
        assertTrue(error.getMessage().contains("case-a"), error.getMessage());
        assertTrue(error.getMessage().contains("case-b"), error.getMessage());
    }

    @Test
    void issuesJsonPassthrough() throws Exception {
        Files.writeString(tempDir.resolve("contract.json"), CONTRACT, StandardCharsets.UTF_8);
        Path dir = writeCase("case-a", "{\"id\":\"a\"}", "root = Stack([])");
        Files.writeString(dir.resolve("issues.json"),
                "[{\"code\":\"type-prop-mismatch\",\"message\":\"m\"},{\"code\":\"syntax\"}]", StandardCharsets.UTF_8);

        List<RepairCase> cases = RepairCasePack.load(tempDir, null);
        assertEquals(List.of("type-prop-mismatch", "syntax"), cases.get(0).internalIssueCodes());
    }

    @Test
    void missingIssuesJsonYieldsNullCodes() throws Exception {
        Files.writeString(tempDir.resolve("contract.json"), CONTRACT, StandardCharsets.UTF_8);
        writeCase("case-a", "{\"id\":\"a\"}", "root = Stack([])");
        List<RepairCase> cases = RepairCasePack.load(tempDir, null);
        assertNull(cases.get(0).internalIssueCodes(), "missing issues.json → null (output layer omits the field)");
    }

    @Test
    void zeroCaseDirectoriesAborts() throws Exception {
        Files.writeString(tempDir.resolve("contract.json"), CONTRACT, StandardCharsets.UTF_8);
        PackLoadException error = assertThrows(PackLoadException.class, () -> RepairCasePack.load(tempDir, null));
        assertTrue(error.getMessage().contains("no case directories"), error.getMessage());
    }

    @Test
    void nonDirectoryCasesRootAborts() throws Exception {
        PackLoadException error = assertThrows(PackLoadException.class,
                () -> RepairCasePack.load(tempDir.resolve("does-not-exist"), null));
        assertTrue(error.getMessage().contains("not a directory"), error.getMessage());
    }

    @Test
    void limitTakesFirstNByDirectoryName() throws Exception {
        Files.writeString(tempDir.resolve("contract.json"), CONTRACT, StandardCharsets.UTF_8);
        writeCase("case-003", "{\"id\":\"c\"}", "root = Stack([])");
        writeCase("case-001", "{\"id\":\"a\"}", "root = Stack([])");
        writeCase("case-002", "{\"id\":\"b\"}", "root = Stack([])");

        List<RepairCase> cases = RepairCasePack.load(tempDir, 2);
        assertEquals(List.of("a", "b"), cases.stream().map(RepairCase::id).toList());
    }

    @Test
    void dslIsNotStripped() throws Exception {
        Files.writeString(tempDir.resolve("contract.json"), CONTRACT, StandardCharsets.UTF_8);
        writeCase("case-a", "{\"id\":\"a\"}", "  root = Stack([])\n\n");
        List<RepairCase> cases = RepairCasePack.load(tempDir, null);
        assertEquals("  root = Stack([])\n\n", cases.get(0).dsl(), "dsl.txt kept verbatim (evidence first)");
    }
}
