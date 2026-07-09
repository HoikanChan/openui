/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.evalcli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.cloudsop.genui.core.GenerationSdk;
import com.huawei.cloudsop.genui.core.Json;
import com.huawei.cloudsop.genui.core.contract.ComponentPromptSpec;
import com.huawei.cloudsop.genui.core.contract.GenerationContract;
import com.huawei.cloudsop.genui.core.llm.GenUiGenerator;
import com.huawei.cloudsop.genui.core.llm.GenUiLlmConfig;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransport;
import com.huawei.cloudsop.genui.core.llm.transport.LlmTransportException;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

class EvalCliMainTest {
    @Test
    void parsesGenerateArgs() {
        EvalCliMain.CliArgs args = EvalCliMain.CliArgs
                .parse(new String[]{"generate", "--base-contract=c.json", "--jobs=jobs.json", "--concurrency=3"});
        assertEquals("generate", args.command());
        assertEquals("c.json", args.baseContractPath());
        assertEquals("jobs.json", args.jobsPath());
        assertEquals(3, args.concurrency());
    }

    @Test
    void printPromptAcceptsFlagStyleCommand() {
        EvalCliMain.CliArgs args = EvalCliMain.CliArgs.parse(new String[]{"--print-prompt", "--base-contract=c.json"});
        assertEquals("print-prompt", args.command());
    }

    @Test
    void rejectsMissingBaseContract() {
        assertThrows(EvalCliMain.CliUsageException.class,
                () -> EvalCliMain.CliArgs.parse(new String[]{"generate", "--jobs=jobs.json"}));
    }

    @Test
    void rejectsGenerateWithoutJobs() {
        assertThrows(EvalCliMain.CliUsageException.class,
                () -> EvalCliMain.CliArgs.parse(new String[]{"generate", "--base-contract=c.json"}));
    }

    @Test
    void rejectsUnknownCommandAndBadConcurrency() {
        assertThrows(EvalCliMain.CliUsageException.class, () -> EvalCliMain.CliArgs.parse(new String[]{"serve"}));
        assertThrows(EvalCliMain.CliUsageException.class, () -> EvalCliMain.CliArgs
                .parse(new String[]{"generate", "--base-contract=c.json", "--jobs=j.json", "--concurrency=0"}));
    }

    @Test
    void parsesJobsJson() {
        List<EvalCliMain.GenerationJob> jobs = EvalCliMain.GenerationJob
                .parseAll("[{\"id\":\"a\",\"userInput\":\"show table\",\"dataModel\":{\"rows\":[]}},{\"id\":\"b\"}]");
        assertEquals(2, jobs.size());
        assertEquals("a", jobs.get(0).id());
        assertEquals("show table", jobs.get(0).userInput());
        assertEquals(Map.of("rows", List.of()), jobs.get(0).dataModel());
        assertEquals("", jobs.get(1).userInput());
        assertTrue(jobs.get(1).dataModel().isEmpty());
    }

    @Test
    void rejectsJobWithoutId() {
        assertThrows(EvalCliMain.CliUsageException.class,
                () -> EvalCliMain.GenerationJob.parseAll("[{\"userInput\":\"x\"}]"));
    }

    @Test
    void canonicalPromptUsesPlaceholderDataModelOffline() {
        String prompt = EvalCliMain.assembleCanonicalPrompt(sdk());
        assertTrue(prompt.contains(EvalCliMain.PLACEHOLDER_KEY));
        assertTrue(prompt.contains("Stack(children)"));
    }

    @Test
    void jobRunnerEmitsOkAndErrorLines() throws Exception {
        GenUiGenerator generator = GenUiGenerator.withTransport(sdk(),
                GenUiLlmConfig.builder().defaultModel("test-model").temperature(0).build(), new FlakyTransport());
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        List<EvalCliMain.GenerationJob> jobs = EvalCliMain.GenerationJob
                .parseAll("[{\"id\":\"ok-case\",\"userInput\":\"u\"},{\"id\":\"fail-case\",\"userInput\":\"boom\"}]");

        JobRunner.runAll(jobs, 2, generator, new PrintStream(buffer, true, StandardCharsets.UTF_8));

        List<String> lines = buffer.toString(StandardCharsets.UTF_8).lines().toList();
        assertEquals(2, lines.size());
        Map<String, Object> byId1 = Json.asObject(Json.parse(lines.get(0)), "line");
        Map<String, Object> byId2 = Json.asObject(Json.parse(lines.get(1)), "line");
        Map<String, Object> ok = "ok-case".equals(byId1.get("id")) ? byId1 : byId2;
        Map<String, Object> fail = "fail-case".equals(byId1.get("id")) ? byId1 : byId2;
        assertEquals("ok", ok.get("status"));
        assertEquals("root = Stack([])", ok.get("dsl"));
        assertEquals("error", fail.get("status"));
        assertTrue(String.valueOf(fail.get("error")).contains("boom"));
    }

    private static GenerationSdk sdk() {
        GenerationContract contract = new GenerationContract("test@1.0.0", "Stack",
                Map.of("Stack", new ComponentPromptSpec("Stack(children)", "Layout container")), List.of(), List.of(),
                List.of(), List.of());
        return GenerationSdk.builder().baseContract(contract).build();
    }

    /** user message 含 boom 时抛错,否则返回固定 DSL 响应。 */
    private static final class FlakyTransport implements LlmTransport {
        @Override
        public String post(String body) throws LlmTransportException {
            if (body != null && body.contains("boom")) {
                throw new LlmTransportException("boom upstream");
            }
            return "{\"choices\":[{\"message\":{\"content\":\"```openui\\nroot = Stack([])\\n```\"}}]}";
        }

        @Override
        public InputStream postStream(String body) throws LlmTransportException {
            throw new LlmTransportException("stream not used in eval CLI");
        }
    }
}
